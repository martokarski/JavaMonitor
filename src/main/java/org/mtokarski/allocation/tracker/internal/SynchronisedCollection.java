package org.mtokarski.allocation.tracker.internal;

import org.mtokarski.allocation.tracker.CustomWeakReference;

import java.util.Arrays;

public class SynchronisedCollection {

    private static final int INITIAL_SIZE = 10_000;

    private CustomWeakReference[] backingCollection = new CustomWeakReference[INITIAL_SIZE];
    private volatile int counter = 0;

    public synchronized void add(CustomWeakReference ref) {
        ensureSize();
        backingCollection[counter++] = ref;
    }

    private void ensureSize() {
        if (counter >= backingCollection.length) {
            backingCollection = Arrays.copyOf(backingCollection, backingCollection.length * 2);
        }
    }

    public synchronized CustomWeakReference[] swapAndGetBackingCollection() {
        int length = counter;
        counter = 0;
        return Arrays.copyOf(backingCollection, length);
    }
}
