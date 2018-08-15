package org.mtokarski.allocation.util;

import org.mtokarski.allocation.model.MemoryState;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.lang.management.MemoryUsage;

public final class GCDataMappingUtil {

    private GCDataMappingUtil() {}


    public static MemoryState getMemoryState(TabularData afterUsage) {
        MemoryUsage eden = null;
        MemoryUsage survivor = null;
        MemoryUsage old = null;
        for (Object value : afterUsage.values()) {
            CompositeData data = (CompositeData) value;
            String key = (String) data.get("key");
            if (key.contains("Eden Space")) {
                eden = extractMemoryUsage((CompositeData) data.get("value"));
            } else if (isSurvivorSpace(key)) {
                survivor = extractMemoryUsage((CompositeData) data.get("value"));
            } else if (isOldGen(key)) {
                old = extractMemoryUsage((CompositeData) data.get("value"));
            }
        }
        return new MemoryState(eden, survivor, old);
    }

    private static MemoryUsage extractMemoryUsage(CompositeData data) {
        long committed = (long) data.get("committed");
        long init = (long) data.get("init");
        long max = (long) data.get("max");
        long used = (long) data.get("used");

        return new MemoryUsage(init, used, committed, max);
    }

    public static boolean isEdenSpace(String name) {
        return name.contains("Eden Space");
    }

    public static boolean isSurvivorSpace(String name) {
        return name.contains("Survivor Space");
    }

    public static boolean isOldGen(String name) {
        return name.contains("Old Gen") || name.contains("Tenured Gen");
    }
}
