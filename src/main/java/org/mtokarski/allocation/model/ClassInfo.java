package org.mtokarski.allocation.model;

public class ClassInfo {

    private final String className;
    private final int instancesCount;
    private final int[] histogram;
    private final StacktracePath stacktrace;

    public ClassInfo(String className, int instancesCount, int[] histogram, StacktracePath stacktrace) {
        this.className = className;
        this.instancesCount = instancesCount;
        this.histogram = histogram;
        this.stacktrace = stacktrace;
    }

    public String getClassName() {
        return className;
    }

    public int getInstancesCount() {
        return instancesCount;
    }

    public int[] getHistogram() {
        return histogram;
    }

    public StacktracePath getStacktrace() {
        return stacktrace;
    }
}
