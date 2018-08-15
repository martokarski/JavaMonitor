package org.mtokarski.allocation.model;

public class GCInfo {

    private final String gcType;
    private final long startTime;
    private final long endTime;
    private final MemoryState memoryBefore;
    private final MemoryState memoryAfter;

    public GCInfo(String gcType, long startTime, long endTime, MemoryState memoryBefore, MemoryState memoryAfter) {
        this.gcType = gcType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.memoryBefore = memoryBefore;
        this.memoryAfter = memoryAfter;
    }

    public String getGcType() {
        return gcType;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public MemoryState getMemoryBefore() {
        return memoryBefore;
    }

    public MemoryState getMemoryAfter() {
        return memoryAfter;
    }
}
