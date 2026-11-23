package org.recompile.mobile;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public final class InvalidInterfaceModifiersSmoke {
    private static final String INVALID_NAME =
            "org/recompile/mobile/InvalidInterfaceFlagsFixture";

    public static void main(String[] args) {
        byte[] invalid = makeInterface(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE,
                INVALID_NAME);
        require((readAccessFlags(invalid) & Opcodes.ACC_ABSTRACT) == 0,
                "fixture must omit the required ACC_ABSTRACT flag");

        byte[] adapted = ClassFileCompatibility.adaptMissingInterfaceAbstractFlag(
                invalid, INVALID_NAME.replace('/', '.'));
        require(adapted != invalid, "invalid interface must be copied and repaired");
        require((readAccessFlags(adapted) & Opcodes.ACC_ABSTRACT) != 0,
                "repaired interface must include ACC_ABSTRACT");
        require((readAccessFlags(invalid) & Opcodes.ACC_ABSTRACT) == 0,
                "the original JAR bytes must remain unchanged");

        int[] attempts = {0};
        TestLoader loader = new TestLoader();
        Class<?> loaded = ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
                INVALID_NAME.replace('/', '.'), invalid, classBytes -> {
                    attempts[0]++;
                    return loader.define(INVALID_NAME.replace('/', '.'), classBytes);
                });
        require(attempts[0] == 1, "normalization should allow the first definition attempt");
        require(loaded.isInterface(), "the repaired class must remain an interface");

        byte[] valid = makeInterface(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT
                | Opcodes.ACC_INTERFACE, "org/recompile/mobile/ValidInterfaceFlagsFixture");
        require(ClassFileCompatibility.adaptMissingInterfaceAbstractFlag(
                        valid, "org.recompile.mobile.ValidInterfaceFlagsFixture") == valid,
                "valid interfaces must not be rewritten");

        byte[] ordinaryClass = makeInterface(Opcodes.ACC_PUBLIC,
                "org/recompile/mobile/OrdinaryClassFlagsFixture");
        require(ClassFileCompatibility.adaptMissingInterfaceAbstractFlag(
                        ordinaryClass, "org.recompile.mobile.OrdinaryClassFlagsFixture")
                        == ordinaryClass,
                "ordinary classes must not be rewritten");

        System.out.println("InvalidInterfaceModifiersSmoke: malformed interface flags repaired");
    }

    private static byte[] makeInterface(int access, String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, access, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static int readAccessFlags(byte[] bytecode) {
        int constantPoolCount = readU2(bytecode, 8);
        int offset = 10;
        for (int index = 1; index < constantPoolCount; index++) {
            int tag = bytecode[offset++] & 0xff;
            switch (tag) {
                case 1:
                    offset += 2 + readU2(bytecode, offset);
                    break;
                case 3:
                case 4:
                    offset += 4;
                    break;
                case 5:
                case 6:
                    offset += 8;
                    index++;
                    break;
                case 7:
                case 8:
                case 16:
                    offset += 2;
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    offset += 4;
                    break;
                case 15:
                    offset += 3;
                    break;
                default:
                    throw new AssertionError("unexpected constant-pool tag " + tag);
            }
        }
        return readU2(bytecode, offset);
    }

    private static int readU2(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xff) << 8) | (bytecode[offset + 1] & 0xff);
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
