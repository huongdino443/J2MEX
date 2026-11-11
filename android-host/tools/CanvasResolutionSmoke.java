package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;

public final class CanvasResolutionSmoke {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass a game JAR to inspect");
        byte[] canvasClass;
        byte[] unrelatedClass;
        try (ZipFile jar = new ZipFile(args[0])) {
            canvasClass = read(jar, "o.class");
            unrelatedClass = read(jar, "n.class");
        }

        byte[] normalized = CanvasResolutionCompatibility.adapt(canvasClass, "o", 240, 320);
        require(hasConstant(normalized, "240x320"),
                "full-screen Canvas resolution string follows the actual 240x320 display");
        require(!hasConstant(normalized, "240x260"),
                "stale 240x260 logical resolution is removed from the adapted Canvas class");
        require(CanvasResolutionCompatibility.adapt(canvasClass, "o", 240, 260) == canvasClass,
                "matching screen dimensions leave the original Canvas class untouched");
        require(CanvasResolutionCompatibility.adapt(unrelatedClass, "n", 240, 320) == unrelatedClass,
                "classes without the full-screen resolution parser remain untouched");
        System.out.println("CanvasResolutionSmoke: " + checks
                + " checks passed; stale Canvas resolution remapped without modifying the JAR");
    }

    private static byte[] read(ZipFile jar, String path) throws Exception {
        ZipEntry entry = jar.getEntry(path);
        if (entry == null) throw new AssertionError("Missing test class " + path);
        try (InputStream input = jar.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }

    private static boolean hasConstant(byte[] bytecode, String expected) {
        final boolean[] found = {false};
        ClassAdapter visitor = new ClassAdapter(new ClassWriter(0)) {
            public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodAdapter(method) {
                    public void visitLdcInsn(Object value) {
                        if (expected.equals(value)) found[0] = true;
                        super.visitLdcInsn(value);
                    }
                };
            }
        };
        new ClassReader(bytecode).accept(visitor, 0);
        return found[0];
    }

    private static void require(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
