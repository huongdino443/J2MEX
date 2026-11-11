package org.recompile.mobile;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.jar.*;

/** Runs only the JAR's small resource helper; no MIDlet startup or networking. */
public final class CloneResourceSmoke {
    private static int checks;
    private static final String[] PATHS = {
        "/datahd/chunkdata/chunk0", "/mfont/tahoma_7b"
    };

    public static void main(String[] args) throws Exception {
        URL[] urls = {new File(args[0]).toURI().toURL()};
        Map<String, String> properties = new HashMap<String, String>();
        Map<String, byte[]> expected = new HashMap<String, byte[]>();
        try (JarFile jar = new JarFile(args[0])) {
            for (Map.Entry<Object, Object> entry : jar.getManifest().getMainAttributes().entrySet())
                properties.put(entry.getKey().toString(), entry.getValue().toString());
            for (String path : PATHS) {
                JarEntry entry = jar.getJarEntry(path.substring(1));
                require(entry != null, "test resource really exists: " + path);
                try (InputStream input = jar.getInputStream(entry)) {
                    expected.put(path, read(input));
                }
            }
        }
        MobilePlatform platform = new MobilePlatform(24, 32);
        Mobile.setPlatform(platform);
        MIDletLoader parent = new MIDletLoader(urls);
        platform.loader = parent;
        CloneClassSpace space = new CloneClassSpace(parent, urls, properties, "quyetdaik.quyetdaik");

        // The v16 parent path supplies the reference behavior before isolation.
        checkReads(parent.loadClass("a.fd"), expected);
        for (int i = 0; i < 5; i++) {
            String helperName = Character.toString((char)('a' + i)) + ".fd";
            Class<?> first = space.resolve(helperName, false);
            checkReads(first, expected);
            CloneClassSpace.retireLoader(first.getClassLoader());
            Class<?> next = space.resolve(helperName, false);
            require(next != first, "resource helper belongs to a fresh generation");
            checkReads(next, expected);
        }
        System.out.println("CloneResourceSmoke: " + checks
                + " checks passed; real binary resources, five clones and restarts; no MIDlet startup/network");
    }

    private static void checkReads(Class<?> helper, Map<String, byte[]> expected) throws Exception {
        java.lang.reflect.Method method = helper.getMethod("a", String.class);
        for (String path : PATHS) {
            InputStream input = (InputStream)method.invoke(null, path);
            require(input != null, helper.getName() + " must open existing " + path);
            try (InputStream stream = input) {
                require(Arrays.equals(read(stream), expected.get(path)), "resource bytes must match JAR");
            }
        }
        require(method.invoke(null, "/missing-clone-resource-test") == null,
                "missing resource must remain null, not fabricated");
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
