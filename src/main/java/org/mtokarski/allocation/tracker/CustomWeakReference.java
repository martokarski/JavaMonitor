package org.mtokarski.allocation.tracker;

import org.mtokarski.allocation.model.AllocationInfo;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A subclass of {@link WeakReference} that holds information about <i><b>class</b></i> of holding object
 */
public class CustomWeakReference<T> extends WeakReference<T> {

    private volatile int id = -1;
    private final Class objectClass;
    private final StackTraceElement[] stackTrace;
    private final long creationTime;

    public CustomWeakReference(T referent, ReferenceQueue queue, boolean gatherStacktrace) {
        super(referent, queue);
        objectClass = referent.getClass();
        referent = null;
        creationTime = System.currentTimeMillis();
        if (gatherStacktrace) {
            stackTrace = Thread.currentThread().getStackTrace();
        } else {
            stackTrace = null;
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Class getObjectClass() {
        return objectClass;
    }

    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public AllocationInfo getAllocationInfo() {
        return new AllocationInfo(objectClass.getName(), creationTime);
    }

    @Override
    public String toString() {
        return objectClass.toString() + " id: " + id;
    }

    @Override
    public int hashCode() {
        return objectClass.hashCode();
    }
}
