package org.mtokarski.allocation.util;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public final class ClassNameUtil {

    private ClassNameUtil() {}

    public static String parseClassName(String rawName) {
        int arrayDimension = rawName.lastIndexOf('[');
        if (arrayDimension != -1) {
            char arrayType = rawName.charAt(arrayDimension + 1);
            StringBuilder className = new StringBuilder();
            switch (arrayType) {
                case 'Z': {
                    className.append("boolean");
                    break;
                }
                case 'B': {
                    className.append("byte");
                    break;
                }
                case 'S': {
                    className.append("short");
                    break;
                }
                case 'C': {
                    className.append("char");
                    break;
                }
                case 'I': {
                    className.append("int");
                    break;
                }
                case 'J': {
                    className.append("long");
                    break;
                }
                case 'F': {
                    className.append("float");
                    break;
                }
                case 'D': {
                    className.append("double");
                    break;
                }
                default: {
                    className.append(rawName, arrayDimension + 2, rawName.length() - 1);
                }
            }

            for (int i = 0; i < arrayDimension + 1; i++) {
                className.append("[]");
            }

            return className.toString();
        } else {
            return rawName;
        }
    }

    public static String findMainPackage() {
        String[] command = System.getProperty("sun.java.command").split(" ");
        if (command.length > 0) {
            String mainClass = command[0];
            if (mainClass.endsWith(".jar")) {
                mainClass = extractMainFromJar(mainClass);
            }

            if (mainClass != null && !mainClass.isEmpty()) {
                return getPackageOfClass(mainClass);
            }
        }

        return null;
    }

    private static String getPackageOfClass(String mainClass) {
        String packageName = "";

        int classNameIndex = mainClass.lastIndexOf('.');
        if (classNameIndex != -1) {
            packageName = mainClass.substring(0, classNameIndex);
        }

        return packageName;
    }

    private static String extractMainFromJar(String jarPath) {
        String mainClass = null;
        File jarFile = new File(jarPath);
        if (jarFile.exists()) {
            try {
                JarFile jf = new JarFile(jarFile);
                mainClass = jf.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            } catch (IOException ignored) {
            }
        }

        return mainClass;
    }
}
