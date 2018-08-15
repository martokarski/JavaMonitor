package org.mtokarski.allocation.tracker;

import com.google.monitoring.runtime.instrumentation.Sampler;
import org.mtokarski.allocation.tracker.internal.SynchronisedCollection;

import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AllocationTracker implements Sampler {

    public static final ThreadLocal<Boolean> insideTracker = new ThreadLocal<>();
    private final SynchronisedCollection liveObjects = new SynchronisedCollection();
    private final ReferenceQueue<?> queue = new ReferenceQueue();
    private final GarbageCollectThread collectionThread;
    private final BlockingQueue<CustomWeakReference[]> resultQueue = new LinkedBlockingQueue<>();
    private final boolean gatherStacktrace;

    public AllocationTracker(boolean gatherStacktrace) {
        collectionThread = new GarbageCollectThread();
        this.gatherStacktrace = gatherStacktrace;
        collectionThread.setDaemon(true);
        collectionThread.start();
    }

    @Override
    public void sampleAllocation(int count, String desc, Object newObj, long size) {
        if (insideTracker.get() != Boolean.TRUE) {
            CustomWeakReference<?> a = new CustomWeakReference<>(newObj, queue, gatherStacktrace);
            newObj = null;
            liveObjects.add(a);
        }
    }

    public CustomWeakReference[] getCurrentStateBlocking() throws InterruptedException {
        return resultQueue.take();
    }

    public void stop() {
        collectionThread.interrupt();
    }

    private class GarbageCollectThread extends Thread {

        private static final int INITIAL_SIZE = 10;
        private static final long COMPACTION_TIMEOUT = 5000;
        private static final long waitTimeout = 100;
        private int removedInstances = 0;
        private long lastCompaction = 0;
        private int actualSize = 0;
        private int instanceCounter = 0;
        private CustomWeakReference[][] oldCollections = new CustomWeakReference[INITIAL_SIZE][];

        @Override
        public void run() {
            insideTracker.set(Boolean.TRUE);
            try {
                while (!this.isInterrupted()) {
                    CustomWeakReference<?> unused = (CustomWeakReference) queue.remove(waitTimeout);

                    if (unused != null) {
                        removeReferencesTimeBounded(unused);
                    }

                    createCheckpoint();
                }
            } catch (InterruptedException ignored) {
            }
        }

        private void createCheckpoint() {
            long timeSinceLastEvent = System.currentTimeMillis() - lastCompaction;
            if (timeSinceLastEvent >= COMPACTION_TIMEOUT) {
                compactCollections();
                lastCompaction = System.currentTimeMillis();
                CustomWeakReference[] copy = Arrays.copyOf(oldCollections[0], oldCollections[0].length);
                resultQueue.offer(copy);
            }
        }

        private void removeReferencesTimeBounded(CustomWeakReference<?> unused) {
            long startOfBoundEvent = System.currentTimeMillis();
            removeReference(unused);
            while ((unused = (CustomWeakReference) queue.poll()) != null) {
                removeReference(unused);

                if (System.currentTimeMillis() - startOfBoundEvent > waitTimeout) {
                    break;
                }
            }
        }

        private void removeReference(CustomWeakReference<?> unused) {
            if (actualSize == 0) {
                System.out.println("Initialise elements");
                fetchNewElements();
            }

            int objectIndex = unused.getId();
            if (objectIndex == -1) {
                fetchNewElements();
                objectIndex = unused.getId();
            }

            boolean removed = false;
            for (int i = 0; i < actualSize; i++) {
                CustomWeakReference[] oldCollection = oldCollections[i];
                if (tryRemoveReference(oldCollection, unused, objectIndex)) {
                    removed = true;
                    break;
                } else {
                    objectIndex -= oldCollection.length;
                }
            }
            if (!removed) {
                throw new RuntimeException("Index " + objectIndex + " is too big");
            }
        }

        private boolean tryRemoveReference(CustomWeakReference[] fromArray, CustomWeakReference<?> unused, int objectIndex) {
            if (objectIndex >= fromArray.length) {
                return false;
            }
            if (fromArray[objectIndex] != unused) {
                throw new RuntimeException("Object at index " + objectIndex + " is invalid");
            }
            fromArray[objectIndex] = null;
            removedInstances++;
            return true;
        }

        private void fetchNewElements() {
            ensureSize();
            oldCollections[actualSize] = liveObjects.swapAndGetBackingCollection();
            for (CustomWeakReference reference : oldCollections[actualSize]) {
                reference.setId(instanceCounter++);
            }
            actualSize++;
        }

        private void ensureSize() {
            if (actualSize == oldCollections.length) {
                oldCollections = Arrays.copyOf(oldCollections, oldCollections.length * 2);
            }
        }

        private void compactCollections() {
            fetchNewElements();
            int existingElements = instanceCounter - removedInstances;
            CustomWeakReference[] copy = new CustomWeakReference[existingElements];
            int index = 0;
            for (int i = 0; i < actualSize; i++) {
                for (int j = 0; j < oldCollections[i].length; j++) {
                    CustomWeakReference ref = oldCollections[i][j];
                    if (ref != null) {
                        ref.setId(index);
                        copy[index++] = ref;
                    }
                }
                oldCollections[i] = null;
            }
            instanceCounter = index;
            removedInstances = 0;
            oldCollections[0] = copy;
            actualSize = 1;
        }
    }
}
