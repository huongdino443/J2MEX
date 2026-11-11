package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** A classfile 45.3 header must be upgraded only when its code uses class-literal ldc. */
public final class Legacy45ClassLiteralSmoke {
    private static int checks;

    public static void main(String[] args) throws Exception {
        String literalName = Legacy45ClassLiteralSmoke.class.getName() + "$ClassLiteralFixture";
        byte[] originalLiteral = readClassBytes(literalName);
        byte[] legacyLiteral = asClassfile453(originalLiteral);
        byte[] upgraded = ClassFileCompatibility.adaptPre49ClassLiteralLdc(
                legacyLiteral, literalName);
        check(upgraded != legacyLiteral, "legacy class-literal bytecode must be adapted");
        check(readU2(upgraded, 4) == 0 && readU2(upgraded, 6) == 49,
                "class-literal adaptation targets classfile 49.0");
        check(readU2(legacyLiteral, 4) == 3 && readU2(legacyLiteral, 6) == 45,
                "the input bytecode remains unchanged");

        TestLoader loader = new TestLoader();
        Class<?> loaded = ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
				literalName, legacyLiteral, classBytes -> loader.define(literalName, classBytes));
        check(loaded.getClassLoader() == loader, "adapted fixture is defined by its test loader");
        check(loaded.getMethod("lockAndReturn").invoke(null) == loaded,
                "class literal and synchronized monitor work after adaptation");
        check(loaded.getDeclaredMethods().length == 1,
                "the adapted method passes full class verification");

        byte[] legacy47Literal = asClassfileVersion(originalLiteral, 0, 47);
        byte[] upgraded47 = ClassFileCompatibility.adaptPre49ClassLiteralLdc(
                legacy47Literal, literalName);
        check(upgraded47 != legacy47Literal && readU2(upgraded47, 6) == 49,
                "pre-49 classfile 47 with class-literal ldc is adapted");
        TestLoader loader47 = new TestLoader();
        Class<?> loaded47 = ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
                literalName, legacy47Literal,
                classBytes -> loader47.define(literalName, classBytes));
        check(loaded47.getMethod("lockAndReturn").invoke(null) == loaded47,
                "version-47 class literal passes full verification after adaptation");

        String plainName = Legacy45ClassLiteralSmoke.class.getName() + "$PlainFixture";
        byte[] legacyPlain = asClassfile453(readClassBytes(plainName));
        check(ClassFileCompatibility.adaptPre49ClassLiteralLdc(legacyPlain, plainName)
                        == legacyPlain,
                "ordinary 45.3 bytecode is not upgraded");
        TestLoader plainLoader = new TestLoader();
        Class<?> plain = plainLoader.define(plainName, legacyPlain);
        check(((Integer)plain.getMethod("value").invoke(null)).intValue() == 42,
                "unmodified legacy bytecode still loads");

        System.out.println("Legacy45ClassLiteralSmoke: " + checks
                + " checks passed; narrow pre-49 class-literal adaptation");
    }

    private static byte[] readClassBytes(String name) throws Exception {
        String resource = "/" + name.replace('.', '/') + ".class";
        try (InputStream input = Legacy45ClassLiteralSmoke.class.getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Missing fixture class: " + resource);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }

    private static byte[] asClassfile453(byte[] original) {
        return asClassfileVersion(original, 3, 45);
    }

    private static byte[] asClassfileVersion(byte[] original, int minor, int major) {
        byte[] legacy = original.clone();
        legacy[4] = (byte)(minor >>> 8);
        legacy[5] = (byte)minor;
        legacy[6] = (byte)(major >>> 8);
        legacy[7] = (byte)major;
        return legacy;
    }

    private static int readU2(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestLoader extends ClassLoader {
        Class<?> define(String name, byte[] bytecode) {
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }

    public static final class ClassLiteralFixture {
        public static Class<?> lockAndReturn() {
            synchronized (ClassLiteralFixture.class) {
                return ClassLiteralFixture.class;
            }
        }
    }

    public static final class PlainFixture {
        public static int value() { return 42; }
    }
}
