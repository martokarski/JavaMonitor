package org.mtokarski.allocation.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.util.List;

public abstract class AbstractClassTransformer implements ClassFileTransformer {

    private final List<String> monitoredClasses;

    public AbstractClassTransformer(List<String> monitoredClasses) {
        this.monitoredClasses = monitoredClasses;
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
