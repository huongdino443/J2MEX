package org.recompile.mobile;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class PackageQualifiedFactoryCompatibilitySmoke implements Opcodes {
    public static void main(String[] args) throws Exception {
        byte[] gameClass = makeFactoryClass("p/dl", "p/G", "Z", "Z");
        byte[] sibling = makeChildClass("p/Z", "p/G", "(Lp/dl;Ljava/lang/String;II)V");
        byte[] adapted = ClassFileCompatibility.adaptPackageQualifiedFactoryConstruction(
                gameClass, "p.dl", sibling);
        check(adapted != gameClass, "matching same-package subclass should repair the factory");

        Map<String, byte[]> classes = new HashMap<String, byte[]>();
        classes.put("p.G", makeBaseClass("p/G"));
        classes.put("p.Z", sibling);
        classes.put("p.dl", adapted);
        ClassLoader loader = new ByteMapClassLoader(classes);
        Class<?> factoryType = Class.forName("p.dl", true, loader);
        factoryType.getDeclaredMethods(); // force full verification
        Object factory = factoryType.newInstance();
        Method make = factoryType.getMethod("make", String.class, int.class, int.class);
        Object value = make.invoke(factory, "asset", Integer.valueOf(4), Integer.valueOf(9));
        check("p.Z".equals(value.getClass().getName()), "factory should return its package-local subclass");
        check(value.getClass().getSuperclass() == Class.forName("p.G", false, loader),
                "returned subclass must match the declared result hierarchy");

        byte[] wrongHierarchy = makeChildClass(
                "p/Z", "java/lang/Object", "(Lp/dl;Ljava/lang/String;II)V");
        check(ClassFileCompatibility.adaptPackageQualifiedFactoryConstruction(
                        gameClass, "p.dl", wrongHierarchy) == gameClass,
                "do not rewrite when the package-local candidate has the wrong superclass");
        check(ClassFileCompatibility.adaptPackageQualifiedFactoryConstruction(
                        gameClass, "other.dl", sibling) == gameClass,
                "do not rewrite when the class name does not match the classfile");
        System.out.println("PackageQualifiedFactoryCompatibilitySmoke: passed");
    }

    private static byte[] makeBaseClass(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(52, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor valueConstructor = writer.visitMethod(
                ACC_PUBLIC, "<init>", "(Ljava/lang/String;I)V", null, null);
        valueConstructor.visitCode();
        valueConstructor.visitVarInsn(ALOAD, 0);
        valueConstructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        valueConstructor.visitInsn(RETURN);
        valueConstructor.visitMaxs(1, 3);
        valueConstructor.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] makeChildClass(String name, String parent, String constructorDescriptor) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(52, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, name, null, parent, null);
        MethodVisitor constructor = writer.visitMethod(
                ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        if (constructorDescriptor.startsWith("(Lp/dl;")) {
            constructor.visitVarInsn(ALOAD, 2);
            constructor.visitVarInsn(ILOAD, 3);
        }
        constructor.visitMethodInsn(INVOKESPECIAL, parent, "<init>",
                constructorDescriptor.startsWith("(Lp/dl;")
                        ? "(Ljava/lang/String;I)V" : "()V");
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(3, 5);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] makeFactoryClass(
            String name, String resultType, String newType, String constructorOwner) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(52, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor factory = writer.visitMethod(ACC_PUBLIC, "a",
                "(Ljava/lang/String;II)L" + resultType + ";", null, null);
        factory.visitCode();
        factory.visitTypeInsn(NEW, newType);
        factory.visitInsn(DUP);
        factory.visitVarInsn(ALOAD, 0);
        factory.visitVarInsn(ALOAD, 1);
        factory.visitVarInsn(ILOAD, 2);
        factory.visitVarInsn(ILOAD, 3);
        factory.visitMethodInsn(INVOKESPECIAL, constructorOwner, "<init>",
                "(Lp/dl;Ljava/lang/String;II)V");
        factory.visitInsn(ARETURN);
        factory.visitMaxs(6, 4);
        factory.visitEnd();

        MethodVisitor alias = writer.visitMethod(ACC_PUBLIC, "make",
                "(Ljava/lang/String;II)L" + resultType + ";", null, null);
        alias.visitCode();
        alias.visitVarInsn(ALOAD, 0);
        alias.visitVarInsn(ALOAD, 1);
        alias.visitVarInsn(ILOAD, 2);
        alias.visitVarInsn(ILOAD, 3);
        alias.visitMethodInsn(INVOKEVIRTUAL, name, "a",
                "(Ljava/lang/String;II)L" + resultType + ";");
        alias.visitInsn(ARETURN);
        alias.visitMaxs(4, 4);
        alias.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ByteMapClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        ByteMapClassLoader(Map<String, byte[]> classes) {
            super(PackageQualifiedFactoryCompatibilitySmoke.class.getClassLoader());
            this.classes = classes;
        }

        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
