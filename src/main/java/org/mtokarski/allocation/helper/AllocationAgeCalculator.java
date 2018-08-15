package org.mtokarski.allocation.helper;

import org.mtokarski.allocation.tracker.CustomWeakReference;
import org.mtokarski.allocation.model.GCInfo;

import java.util.List;

public class AllocationAgeCalculator {

    private final List<GCInfo> gcHistory;
    private final long startTime;

    public AllocationAgeCalculator(List<GCInfo> gcHistory, long startTime) {
        this.gcHistory = gcHistory;
        this.startTime = startTime;
    }

    public int calculateAllocationAge(CustomWeakReference allocation) {
        int allocationAge = 0;
        for (GCInfo gcInfo : gcHistory) {
            if (allocation.getCreationTime() - startTime >= gcInfo.getStartTime()) {
                allocationAge++;
            } else {
                break;
            }
        }
        return allocationAge;
    }
}
