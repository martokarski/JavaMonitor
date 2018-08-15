package org.mtokarski.allocation.model;

import java.lang.management.MemoryUsage;

public class MemoryState {

    private final MemoryUsage eden;
    private final MemoryUsage survivor;
    private final MemoryUsage old;

    public MemoryState(MemoryUsage eden, MemoryUsage survivor, MemoryUsage old) {
        this.eden = eden;
        this.survivor = survivor;
        this.old = old;
    }

    public MemoryUsage getEden() {
        return eden;
    }

    public MemoryUsage getSurvivor() {
        return survivor;
    }

    public MemoryUsage getOld() {
        return old;
    }
}
