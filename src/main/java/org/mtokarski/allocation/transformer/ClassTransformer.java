package org.mtokarski.allocation.transformer;

import com.google.monitoring.runtime.instrumentation.AllocationInstrumenter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.List;

public class ClassTransformer extends AbstractClassTransformer {

    public ClassTransformer(List<String> monitoredClasses) {
        super(monitoredClasses);
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

    private void saveClass(byte[] classBuffer, boolean beforeTransformation) throws IOException {
        File file;
        if (beforeTransformation) {
            file = new File("before.class");
        } else {
            file = new File("after.class");
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(classBuffer);
        }
    }
}
