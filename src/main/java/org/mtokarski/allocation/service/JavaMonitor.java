package org.mtokarski.allocation.service;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import org.mtokarski.allocation.SimpleAgent;
import org.mtokarski.allocation.helper.AllocationAgeCalculator;
import org.mtokarski.allocation.helper.ClassTransformer;
import org.mtokarski.allocation.model.ClassInfo;
import org.mtokarski.allocation.model.GCInfo;
import org.mtokarski.allocation.model.StacktracePath;
import org.mtokarski.allocation.tracker.AllocationTracker;
import org.mtokarski.allocation.tracker.CustomWeakReference;
import org.mtokarski.allocation.util.ClassNameUtil;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaMonitor {

    private static final Pattern LAMBDA_RUNTIME_CLASS = Pattern.compile(".*/[0-9]+$");

    private boolean working;
    private List<String> includedClasses = new ArrayList<>();
    private ClassTransformer classTransformer;
    private AllocationTracker allocationTracker;
    private GCCollector gcCollector;
    private InternalThread internalThread;
    private volatile Checkpoint checkpoint;

    public JavaMonitor(GCCollector gcCollector) {
        this.gcCollector = gcCollector;

        String mainPackage = ClassNameUtil.findMainPackage();
        if (mainPackage != null) {
            includedClasses.add(mainPackage);
        }
    }

    public void setGcCollector(GCCollector gcCollector) {
        this.gcCollector = gcCollector;
    }

    public boolean isWorking() {
        return working;
    }

    public List<String> getIncludedClasses() {
        return new ArrayList<>(includedClasses);
    }

    public void stop() {
        Instrumentation instrumentation = SimpleAgent.getInstrumentation();
        instrumentation.removeTransformer(classTransformer);
        AllocationRecorder.removeSampler(allocationTracker);
        allocationTracker.stop();
        internalThread.interrupt();
        working = false;

        try {
            instrumentation.retransformClasses(getModifiableClasses());
        } catch (UnmodifiableClassException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ClassInfo> getState(long lastStatus) {
        Checkpoint checkpoint = this.checkpoint;

        if (checkpoint != null && checkpoint.time > lastStatus) {
            return checkpoint.status;
        }

        return null;
    }

    public void start(Settings settings) {
        if (working) {
            throw new RuntimeException("Profiler is working, you have to stop it first");
        }
        this.includedClasses = settings.getIncludedClasses().stream()
                .filter(val -> !val.isEmpty())
                .collect(Collectors.toList());
        classTransformer = new ClassTransformer(includedClasses);
        allocationTracker = new AllocationTracker(settings.isGatherStacktrace());
        internalThread = new InternalThread();

        Instrumentation instrumentation = SimpleAgent.getInstrumentation();
        instrumentation.addTransformer(classTransformer, true);
        try {
            Class[] modifableClasses = getModifiableClasses();
            instrumentation.retransformClasses(modifableClasses);
            AllocationRecorder.addSampler(allocationTracker);
            internalThread.start();
            working = true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (!working) {
                AllocationRecorder.removeSampler(allocationTracker);
                instrumentation.removeTransformer(classTransformer);
            }
        }
    }

    private Class[] getModifiableClasses() {
        Instrumentation instrumentation = SimpleAgent.getInstrumentation();
        Class[] allLoadedClasses = instrumentation.getAllLoadedClasses();
        List<Class> modifiableClasses = new ArrayList<>();
        for (Class clazz : allLoadedClasses) {
            if (instrumentation.isModifiableClass(clazz) && !LAMBDA_RUNTIME_CLASS.matcher(clazz.getName()).matches() && classTransformer.shouldRetransform(clazz.getName())) {
                modifiableClasses.add(clazz);
            }
        }

        Class[] allowedClasses = new Class[modifiableClasses.size()];
        return modifiableClasses.toArray(allowedClasses);
    }

    private class InternalThread extends Thread {

        private InternalThread() {
            this.setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    CustomWeakReference[] currentStateBlocking = allocationTracker.getCurrentStateBlocking();

                    Map<String, List<CustomWeakReference>> collect = Stream.of(currentStateBlocking)
                            .collect(Collectors
                                    .groupingBy(allocation -> allocation.getObjectClass().getName()));

                    List<ClassInfo> status = new ArrayList<>(collect.size());
                    List<GCInfo> copy = gcCollector.getCopy();
                    AllocationAgeCalculator allocationAgeCalculator = new AllocationAgeCalculator(copy, gcCollector.getStartTime());
                    collect.forEach((className, allocations) -> {
                        int allocationsCount = allocations.size();
                        StacktracePath stacktrace = allocations.stream()
                                .map(CustomWeakReference::getStackTrace)
                                .filter(Objects::nonNull)
                                .reduce(
                                        StacktracePath.createRoot(),
                                        (root, element) -> root.addStacktrace(element, 4),
                                        (first, second) -> first);  //we effectively ignore second result, it cannot be paralleled
                        int[] ages = allocations.stream()
                                .mapToInt(allocationAgeCalculator::calculateAllocationAge)
                                .collect(
                                        () -> new int[copy.size() + 1],
                                        (tab, age) -> tab[age]++,
                                        (tab1, tab2) -> {
                                            for (int i = 0; i < tab1.length; i++) {
                                                tab1[i] += tab2[i];
                                            }
                                        });
                        ClassInfo classInfo = new ClassInfo(ClassNameUtil.parseClassName(className), allocationsCount, ages, stacktrace);
                        status.add(classInfo);
                    });

                    status.sort(Comparator.comparingInt(ClassInfo::getInstancesCount).reversed());
                    checkpoint = new Checkpoint(status, System.currentTimeMillis());
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static class Checkpoint {

        private final List<ClassInfo> status;
        private final long time;

        public Checkpoint(List<ClassInfo> status, long time) {
            this.status = status;
            this.time = time;
        }

        public List<ClassInfo> getStatus() {
            return status;
        }

        public long getTime() {
            return time;
        }
    }

    public static class Settings {
        private final List<String> includedClasses;
        private final boolean gatherStacktrace;

        public Settings(List<String> includedClasses, boolean gatherStacktrace) {
            this.includedClasses = includedClasses;
            this.gatherStacktrace = gatherStacktrace;
        }

        public List<String> getIncludedClasses() {
            return includedClasses;
        }

        public boolean isGatherStacktrace() {
            return gatherStacktrace;
        }
    }
}
