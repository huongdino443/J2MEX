package org.recompile.mobile;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.jar.*;

/** Verifies the supplied real JAR without initializing or running game classes. */
public final class CloneJarIsolationSmoke {
    private static int checks;
    public static void main(String[] args) throws Exception {
        URL[] urls = {new File(args[0]).toURI().toURL()};
        Map<String, String> properties = new HashMap<String, String>();
        try (JarFile jar = new JarFile(args[0])) {
            for (Map.Entry<Object, Object> entry : jar.getManifest().getMainAttributes().entrySet())
                properties.put(entry.getKey().toString(), entry.getValue().toString());
        }
        MIDletLoader parent = new MIDletLoader(urls);
        CloneClassSpace space = new CloneClassSpace(parent, urls, properties, "quyetdaik.quyetdaik");
        Class.forName(space.factoryClass, false, parent).getDeclaredMethods();
        require(space.isFactory("quyetdaik.a"), "real factory is recognized and verifies");
        Class<?> sharedMidlet = Class.forName("lib.MIDlet", false, parent);
        int count = Integer.parseInt(properties.get("num"));
        Class<?>[] first = new Class<?>[count];
        String[] names = new String[count];
        for (int i = 0; i < first.length; i++) {
            String descriptor = properties.get(Integer.toString(i));
            String[] fields = descriptor == null ? new String[0] : descriptor.split(",", -1);
            require(fields.length >= 3, "manifest clone descriptor is valid");
            names[i] = fields[2].trim();
            int dot = names[i].indexOf('.');
            require(dot > 0, "clone has a private package namespace");
            String prefix = names[i].substring(0, dot + 1);
            first[i] = space.resolve(names[i], false);
            require(first[i].getSuperclass() == sharedMidlet, "real clone shares launcher API");
            require(first[i].getClassLoader() != parent, "real clone owns a private class space");
            Class<?> privateClass = Class.forName(prefix + "es", false, first[i].getClassLoader());
            privateClass.getDeclaredMethods();
            require(privateClass.getClassLoader() == first[i].getClassLoader(),
                    "clone-private support class stays in its generation");
            require(Class.forName("lib.Class", false, first[i].getClassLoader()).getClassLoader()
                    == first[i].getClassLoader(), "reflection helper is generation local");
        }
        for (int i = 0; i < first.length; i++) {
            CloneClassSpace.retireLoader(first[i].getClassLoader());
            Class<?> next = space.resolve(names[i], false);
            require(next != first[i] && next.getSuperclass() == sharedMidlet,
                    "real clone restarts with fresh classes but shared MIDlet API");
            if (i + 1 < first.length)
                require(space.resolve(first[i + 1].getName(), false) == first[i + 1],
                        "real sibling class space is preserved");
        }
        System.out.println("CloneJarIsolationSmoke: " + checks
                + " checks passed on real " + count
                + "-clone JAR; no game class initialization/network");
    }
    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
