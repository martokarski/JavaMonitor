package org.mtokarski.allocation.service;

import org.mtokarski.allocation.model.GCInfo;
import org.mtokarski.allocation.model.MemoryState;
import org.mtokarski.allocation.util.GCDataMappingUtil;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GCCollector {

    private final long startTime;
    private MemoryPoolMXBean edenPool;
    private MemoryPoolMXBean survivorPool;
    private MemoryPoolMXBean oldPool;
    private final List<GCInfo> gcHistory = Collections.synchronizedList(new ArrayList<>());

    public GCCollector() {
        startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        initMemoryPools();
        initGCListener();
    }

    public long getStartTime() {
        return startTime;
    }

    public List<GCInfo> filterGC(long filterTime) {
        synchronized (gcHistory) {
            List<GCInfo> gc = gcHistory.stream()
                    .filter((item) -> item.getEndTime() > filterTime)
                    .collect(Collectors.toList());
            MemoryState memoryAfter = new MemoryState(edenPool.getUsage(), survivorPool.getUsage(), oldPool.getUsage());
            gc.add(new GCInfo("current", startTime, System.currentTimeMillis() - startTime, null, memoryAfter));
            return gc;
        }
    }

    public List<GCInfo> getCopy() {
        synchronized (gcHistory) {
            return new ArrayList<>(gcHistory);
        }
    }

    private void listen(Notification notification, Object handback) {
        CompositeData userData = (CompositeData) notification.getUserData();
        CompositeData gcInfo = (CompositeData) userData.get("gcInfo");

        String gcName = (String) userData.get("gcName");
        String gcCause = (String) userData.get("gcCause");
        long startTime = (long) gcInfo.get("startTime");
        long endTime = (long) gcInfo.get("endTime");
        MemoryState memoryBefore = GCDataMappingUtil.getMemoryState((TabularData) gcInfo.get("memoryUsageBeforeGc"));
        MemoryState memoryAfter = GCDataMappingUtil.getMemoryState((TabularData) gcInfo.get("memoryUsageAfterGc"));
        gcHistory.add(new GCInfo(String.format("%s (%s)", gcName, gcCause), startTime, endTime, memoryBefore, memoryAfter));
    }

    private void initMemoryPools() {
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            String name = memoryPoolMXBean.getName();
            if (GCDataMappingUtil.isEdenSpace(name)) {
                edenPool = memoryPoolMXBean;
            } else if (GCDataMappingUtil.isSurvivorSpace(name)) {
                survivorPool = memoryPoolMXBean;
            } else if (GCDataMappingUtil.isOldGen(name)) {
                oldPool = memoryPoolMXBean;
            }
        }
    }

    private void initGCListener() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcMBean : garbageCollectorMXBeans) {
            NotificationEmitter emitter = (NotificationEmitter) gcMBean;
            emitter.addNotificationListener(this::listen, null, null);
        }
    }
}
