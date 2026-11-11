package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LegacyCodeAttributeVersionSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Pass a JAR and one or more class names");
        }

        try (ZipFile jar = new ZipFile(args[0])) {
            for (int i = 1; i < args.length; i++) {
                String className = args[i];
                ZipEntry entry = jar.getEntry(className.replace('.', '/') + ".class");
                if (entry == null) throw new AssertionError("Missing class: " + className);

                byte[] original;
                try (InputStream input = jar.getInputStream(entry)) {
                    original = readAll(input);
                }

                byte[] normalized = ClassFileCompatibility.adaptLegacy45CodeAttributeVersion(original);
                if (normalized == original) {
                    System.out.println("LegacyCodeAttributeVersionSmoke: " + className
                            + " does not need the 45.3 layout fallback");
                    continue;
                }

                require(readU2(normalized, 4) == 3 && readU2(normalized, 6) == 45,
                        "legacy class header must be normalized to 45.3");
                require(readU2(original, 4) <= 2 && readU2(original, 6) == 45,
                        "the original class is a pre-45.3 classfile");
                require(normalized.length == original.length,
                        "normalization must not change class contents or length");
                for (int offset = 0; offset < original.length; offset++) {
                    if (offset != 5) require(normalized[offset] == original[offset],
                            "normalization may only change the minor-version byte");
                }

                TestLoader testLoader = new TestLoader();
                int[] attempts = {0};
                Class<?> loaded = ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
                        className, original, classBytes -> {
                            attempts[0]++;
                            if (attempts[0] == 1) {
                                throw new ClassFormatError(
                                        "Invalid code attribute name index 46848 in class file "
                                                + className);
                            }
                            return testLoader.define(className, classBytes);
                        });
                require(attempts[0] == 2, "fallback must retry exactly once");
                require(loaded.getClassLoader() == testLoader,
                        "normalized class must be defined by the requested loader");
                System.out.println("LegacyCodeAttributeVersionSmoke: " + className
                        + " verified after 45.3 fallback");
            }
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toByteArray();
    }

    private static int readU2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestLoader extends ClassLoader {
        Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }
}
