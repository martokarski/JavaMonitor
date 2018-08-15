package org.mtokarski.allocation.helper;

import com.google.monitoring.runtime.instrumentation.AllocationInstrumenter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;

public class ClassTransformer implements ClassFileTransformer {

    private final List<String> monitoredClasses;

    public ClassTransformer(List<String> monitoredClasses) {
        this.monitoredClasses = monitoredClasses;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className != null && shouldRetransform(className)) {
            System.out.println("Transformed class: " + className);
            try {
                return AllocationInstrumenter.instrument(classfileBuffer, loader);
            } catch (Exception e) {
                System.out.println("Failed to transform class: " + className + " due to " + e);
                e.printStackTrace();
                return null;
            }
        }

        //null should be returned if no transformation was performed
        return null;
    }

    public boolean shouldRetransform(String className) {
        String properClassName = className.replace('/', '.');

        //ThreadLocal is used internally during allocation tracking process, if it was also instrumented native errors can occur
        if (properClassName.startsWith("java.lang.ThreadLocal")) {
            return false;
        }
        for (String classPrefix : monitoredClasses) {
            if (properClassName.startsWith(classPrefix)) {
                return true;
            }
        }

        return false;
    }
}
