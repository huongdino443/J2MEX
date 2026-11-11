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
        Class.forName("quyetdaik.a", false, parent).getDeclaredMethods();
        require(space.isFactory("quyetdaik.a"), "real factory is recognized and verifies");
        Class<?> sharedMidlet = Class.forName("lib.MIDlet", false, parent);
        Class<?>[] first = new Class<?>[5];
        for (int i = 0; i < first.length; i++) {
            String prefix = Character.toString((char)('a' + i));
            first[i] = space.resolve(prefix + ".code.main.GameMidlet", false);
            require(first[i].getSuperclass() == sharedMidlet, "real clone shares launcher API");
            require(first[i].getClassLoader() != parent, "real clone owns a private class space");
            Class<?> canvas = Class.forName(prefix + ".es", false, first[i].getClassLoader());
            canvas.getDeclaredMethods();
            require(canvas.getClassLoader() == first[i].getClassLoader(), "canvas stays in clone generation");
            require(Class.forName("lib.Class", false, first[i].getClassLoader()).getClassLoader()
                    == first[i].getClassLoader(), "reflection helper is generation local");
        }
        for (int i = 0; i < first.length; i++) {
            String name = first[i].getName();
            CloneClassSpace.retireLoader(first[i].getClassLoader());
            Class<?> next = space.resolve(name, false);
            require(next != first[i] && next.getSuperclass() == sharedMidlet,
                    "real clone restarts with fresh classes but shared MIDlet API");
            if (i + 1 < first.length)
                require(space.resolve(first[i + 1].getName(), false) == first[i + 1],
                        "real sibling class space is preserved");
        }
        System.out.println("CloneJarIsolationSmoke: " + checks
                + " checks passed on real 5-clone JAR; no game class initialization/network");
    }
    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
