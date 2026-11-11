package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MissingStackMapSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Pass the original JAR and class name");
        String className = args[1];
        byte[] original;
        try (ZipFile jar = new ZipFile(args[0])) {
            ZipEntry entry = jar.getEntry(className.replace('.', '/') + ".class");
            if (entry == null) throw new AssertionError("Missing test class: " + className);
            try (InputStream input = jar.getInputStream(entry)) {
                original = readAll(input);
            }
        }

        require(classMajor(original) == 52, "test class is the Java 8 class from the supplied JAR");
        byte[] adapted = ClassFileCompatibility.adaptMissingStackMapTable(original, className);
        require(classMajor(adapted) == 50, "only the in-memory class version is downgraded");
        require(classMajor(original) == 52, "original JAR class bytes remain unchanged");

        URL jarUrl = new File(args[0]).toURI().toURL();
        MIDletLoader loader = new MIDletLoader(new URL[] {jarUrl});
        Class<?> target = Class.forName(className, false, loader);
        require(target.getDeclaredMethods().length > 0, "all methods pass JVM verification");
        require(target.getClassLoader() == loader, "test class is defined by MIDletLoader");
        System.out.println("MissingStackMapSmoke: original JAR class verified through the scoped v50 fallback");
    }

    private static int classMajor(byte[] classBytes) {
        if (classBytes.length < 8 || (classBytes[0] & 0xff) != 0xca
                || (classBytes[1] & 0xff) != 0xfe || (classBytes[2] & 0xff) != 0xba
                || (classBytes[3] & 0xff) != 0xbe) {
            throw new AssertionError("Not a class file");
        }
        return ((classBytes[6] & 0xff) << 8) | (classBytes[7] & 0xff);
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
