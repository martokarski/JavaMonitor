package org.mtokarski.allocation.model;

import java.io.Serializable;

public class AllocationInfo implements Serializable {

    private final String className;
    private final long creationTime;

    public AllocationInfo(String className, long creationTime) {
        this.className = className;
        this.creationTime = creationTime;
    }

    public String getClassName() {
        return className;
    }

    public long getCreationTime() {
        return creationTime;
    }
}
