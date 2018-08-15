package org.mtokarski.allocation;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * Created by Marek
 */
public class SimpleAgent {

    private static volatile Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        start(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        start(agentArgs, inst);
    }

    private static void start(String agentArgs, Instrumentation inst) {
        try {
            instrumentation = inst;
            appendJarToBootstrapClassloader(agentArgs);
            injectInstrumentationToRecordingClass();
            new SimpleServer();
            System.out.println("Started new server!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void appendJarToBootstrapClassloader(String jarPath) throws IOException, URISyntaxException {
        File file;
        if (jarPath == null || jarPath.isEmpty()) {
            file = tryLoadJarFromDefaultLocation();
        } else {
            file = new File(jarPath);
        }
        System.out.println("Location of current jar is: " + file.getPath());
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(file));
        ClassLoader classLoader = AllocationRecorder.class.getClassLoader();
        if (classLoader != null) {
            throw new RuntimeException("AllocationTrackerTemp was not loaded by bootstrap loader!");
        }
    }

    private static File tryLoadJarFromDefaultLocation() throws URISyntaxException {
        File file;
        URL location = SimpleAgent.class.getProtectionDomain().getCodeSource().getLocation();
        URI resolve = location.toURI().resolve("lib");
        File file1 = new File(resolve);
        if (file1.exists() && file1.isDirectory()) {
            File[] files = file1.listFiles((dir, name) -> name.contains("java-allocation-instrumenter"));
            if (files == null || files.length == 0) {
                throw new RuntimeException("Cannot find library java allocation instrumenter");
            }
            file = files[0];
        } else {
            throw new RuntimeException("Cannot find library java allocation instrumenter");
        }
        return file;
    }

    //Allocation instrumenter is only used partially, internal classes have to be initialized
    private static void injectInstrumentationToRecordingClass() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setInstrumentation = AllocationRecorder.class.getDeclaredMethod("setInstrumentation", Instrumentation.class);
        setInstrumentation.setAccessible(true);
        setInstrumentation.invoke(null, instrumentation);
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
