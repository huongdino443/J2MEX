package org.recompile.mobile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Aligns a legacy Canvas subclass's parsed reference resolution with the
 * actual full-screen Canvas when the class explicitly parses a WxH value.
 */
final class CanvasResolutionCompatibility {
    private static final Pattern RESOLUTION = Pattern.compile("^([1-9][0-9]{1,3})x([1-9][0-9]{1,3})$");

    private CanvasResolutionCompatibility() { }

    static byte[] adapt(byte[] bytecode, String className, int width, int height) {
        if (bytecode == null || width <= 0 || height <= 0) return bytecode;

        ResolutionScan scan = new ResolutionScan();
        try {
            new ClassReader(bytecode).accept(scan, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException malformedClass) {
            return bytecode;
        }

        if (!scan.isResolutionParserForFullScreenCanvas()) return bytecode;

        String candidate = scan.findCandidate(width, height);
        if (candidate == null) return bytecode;
        String replacement = width + "x" + height;
        if (candidate.equals(replacement)) return bytecode;

        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassAdapter(writer) {
            public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodAdapter(method) {
                    public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(candidate.equals(value) ? replacement : value);
                    }
                };
            }
        };
        try {
            new ClassReader(bytecode).accept(visitor, 0);
            byte[] adapted = writer.toByteArray();
            System.out.println("MIDletLoader: adjusted parsed Canvas resolution for "
                    + className + " from " + candidate + " to " + replacement);
            return adapted;
        } catch (RuntimeException malformedClass) {
            return bytecode;
        }
    }

    private static final class ResolutionScan extends ClassAdapter {
        private boolean fullScreen;
        private boolean getsWidth;
        private boolean getsHeight;
        private boolean splitsResolution;
        private boolean parsesIntegers;
        private String resolution;

        ResolutionScan() { super(new ClassWriter(0)); }

        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            // The methods below provide the structural gate, including indirect Canvas subclasses.
        }

        public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
            MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
            return new MethodAdapter(method) {
                public void visitLdcInsn(Object value) {
                    if (!(value instanceof String)) return;
                    Matcher matcher = RESOLUTION.matcher((String)value);
                    if (matcher.matches()) resolution = (String)value;
                    super.visitLdcInsn(value);
                }

                public void visitMethodInsn(int opcode, String owner, String method, String descriptor) {
                    if ("javax/microedition/lcdui/Canvas".equals(owner)
                            && "setFullScreenMode".equals(method) && "(Z)V".equals(descriptor))
                        fullScreen = true;
                    if ("javax/microedition/lcdui/Displayable".equals(owner)
                            || "javax/microedition/lcdui/Canvas".equals(owner)) {
                        if ("getWidth".equals(method) && "()I".equals(descriptor)) getsWidth = true;
                        if ("getHeight".equals(method) && "()I".equals(descriptor)) getsHeight = true;
                    }
                    if ("java/lang/String".equals(owner)
                            && ("indexOf".equals(method) || "substring".equals(method)))
                        splitsResolution = true;
                    if ("java/lang/Integer".equals(owner) && "valueOf".equals(method)
                            && "(Ljava/lang/String;)Ljava/lang/Integer;".equals(descriptor))
                        parsesIntegers = true;
                    super.visitMethodInsn(opcode, owner, method, descriptor);
                }
            };
        }

        boolean isResolutionParserForFullScreenCanvas() {
            return fullScreen && getsWidth && getsHeight && splitsResolution && parsesIntegers;
        }

        String findCandidate(int width, int height) {
            if (resolution == null) return null;
            Matcher matcher = RESOLUTION.matcher(resolution);
            if (!matcher.matches()) return null;
            try {
                int encodedWidth = Integer.parseInt(matcher.group(1));
                int encodedHeight = Integer.parseInt(matcher.group(2));
                return encodedWidth == width && encodedHeight < height ? resolution : null;
            } catch (NumberFormatException invalidResolution) {
                return null;
            }
        }
    }
}
