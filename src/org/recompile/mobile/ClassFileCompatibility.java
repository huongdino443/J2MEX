package org.recompile.mobile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;

/**
 * Applies narrowly scoped class-file compatibility changes before definition.
 */
final class ClassFileCompatibility {
	private static final int LEGACY_VERIFIER_VERSION = 50;
	private static final int JAVA_5_CLASSFILE_VERSION = 49;

	private ClassFileCompatibility() { }

	interface ClassDefiner {
		Class<?> define(byte[] bytecode);
	}

	static Class<?> defineWithLegacy45CodeAttributeFallback(
			String className, byte[] bytecode, ClassDefiner definer) {
		bytecode = adaptComputedMaxStack(bytecode, className);
		bytecode = adaptLegacyDeadReturnMaxStack(bytecode, className);
		bytecode = adaptPre49ClassLiteralLdc(bytecode, className);
		try {
			return definer.define(bytecode);
		} catch (ClassFormatError originalError) {
			String message = originalError.getMessage();
			byte[] adapted = message != null && message.contains("Invalid code attribute name index")
					? adaptLegacy45CodeAttributeVersion(bytecode) : bytecode;
			if (adapted == bytecode) {
				System.out.println("ClassFileCompatibility: ClassFormatError loading " + className
						+ " [" + classByteSummary(bytecode) + "]");
				throw originalError;
			}

			System.out.println("ClassFileCompatibility: retrying " + className
					+ " as classfile 45.3 after a legacy Code attribute parse failure"
					+ " [" + classByteSummary(bytecode) + "]");
			try {
				Class<?> loaded = definer.define(adapted);
				System.out.println("ClassFileCompatibility: classfile 45.3 retry succeeded: "
						+ className);
				return loaded;
			} catch (ClassFormatError retryError) {
				originalError.addSuppressed(retryError);
				System.out.println("ClassFileCompatibility: classfile 45.3 retry failed for "
						+ className + ": " + retryError);
				throw originalError;
			}
		}
	}

	/**
	 * Recompute and patch only max_stack values. This mirrors the reference
	 * Android producer's COMPUTE_MAXS pass while keeping original bytecode,
	 * frames, and other class attributes untouched.
	 */
	static byte[] adaptComputedMaxStack(byte[] bytecode, String className) {
		if (!isSupportedMaxStackClass(bytecode)) return bytecode;
		try {
			Map<String, MaxStackInfo> original = readMethodMaxStacks(bytecode);
			if (original.isEmpty()) return bytecode;

			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			new ClassReader(bytecode).accept(writer, ClassReader.SKIP_FRAMES);
			Map<String, MaxStackInfo> computed = readMethodMaxStacks(writer.toByteArray());
			if (computed.isEmpty()) return bytecode;

			byte[] adapted = null;
			for (Map.Entry<String, MaxStackInfo> entry : original.entrySet()) {
				MaxStackInfo computedInfo = computed.get(entry.getKey());
				if (computedInfo == null || computedInfo.value <= entry.getValue().value) continue;
				if (adapted == null) adapted = bytecode.clone();
				int stack = computedInfo.value;
				int offset = entry.getValue().offset;
				adapted[offset] = (byte)(stack >>> 8);
				adapted[offset + 1] = (byte)stack;
				System.out.println("ClassFileCompatibility: recomputed max_stack "
						+ entry.getValue().value + " -> " + stack + ": "
						+ className + "." + entry.getKey().replace('\u0000', ' '));
			}
			return adapted == null ? bytecode : adapted;
		} catch (RuntimeException malformedOrUnsupportedClass) {
			return bytecode;
		} catch (LinkageError unavailableOrUnsupportedAsm) {
			return bytecode;
		}
	}

	/**
	 * Repairs a narrowly recognizable package-relocation error in multi-variant
	 * MIDlet JARs: a package-local factory declares a package-local result type
	 * but constructs the same-named class in the default package. Only redirect
	 * that constructor pair when the package-local sibling is present, extends
	 * the declared result type, and has the exact constructor signature.
	 */
	static byte[] adaptPackageQualifiedFactoryConstruction(
			byte[] bytecode, String className, byte[] siblingBytecode) {
		if (bytecode == null || siblingBytecode == null || className == null) return bytecode;
		try {
			final String internalName = className.replace('.', '/');
			final int separator = internalName.lastIndexOf('/');
			if (separator <= 0 || !"dl".equals(internalName.substring(separator + 1))) return bytecode;
			final String packageName = internalName.substring(0, separator);
			final String siblingName = packageName + "/Z";
			final String resultName = packageName + "/G";
			final String constructorDescriptor = "(L" + internalName
					+ ";Ljava/lang/String;II)V";
			if (!hasMatchingFactoryAndSibling(bytecode, internalName, siblingName,
					resultName, constructorDescriptor, siblingBytecode)) return bytecode;

			final int[] rewrites = new int[2];
			ClassWriter writer = new ClassWriter(0);
			ClassVisitorBridge visitor = new ClassVisitorBridge(writer, packageName,
					constructorDescriptor, rewrites);
			new ClassReader(bytecode).accept(visitor, 0);
			if (rewrites[0] != 1 || rewrites[1] != 1) return bytecode;

			byte[] adapted = writer.toByteArray();
			System.out.println("ClassFileCompatibility: repaired package-relative factory target "
					+ className + " -> " + siblingName);
			return adapted;
		} catch (RuntimeException malformedOrUnsupportedClass) {
			return bytecode;
		} catch (LinkageError unavailableOrUnsupportedAsm) {
			return bytecode;
		}
	}

	private static boolean hasMatchingFactoryAndSibling(
			byte[] bytecode, final String internalName, final String siblingName,
			final String resultName, final String constructorDescriptor, byte[] siblingBytecode) {
		final boolean[] classMatches = new boolean[1];
		final int[] factoryCounts = new int[3]; // method, NEW, constructor call
		new ClassReader(bytecode).accept(new ClassAdapter(new EmptyVisitor()) {
			public void visit(int version, int access, String name, String signature,
					String superName, String[] interfaces) {
				classMatches[0] = internalName.equals(name);
			}

			public MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
				if (!"a".equals(name)
						|| !("(Ljava/lang/String;II)L" + resultName + ";").equals(descriptor)) return null;
				factoryCounts[0]++;
				return new MethodAdapter(new EmptyVisitor()) {
					public void visitTypeInsn(int opcode, String type) {
						if (opcode == Opcodes.NEW && "Z".equals(type)) factoryCounts[1]++;
					}

					public void visitMethodInsn(int opcode, String owner, String methodName,
							String methodDescriptor) {
						if (opcode == Opcodes.INVOKESPECIAL && "Z".equals(owner)
								&& "<init>".equals(methodName)
								&& constructorDescriptor.equals(methodDescriptor)) factoryCounts[2]++;
					}
				};
			}
		}, 0);
		if (!classMatches[0] || factoryCounts[0] != 1
				|| factoryCounts[1] != 1 || factoryCounts[2] != 1) return false;

		final boolean[] siblingMatches = new boolean[2]; // class + matching constructor
		new ClassReader(siblingBytecode).accept(new ClassAdapter(new EmptyVisitor()) {
			public void visit(int version, int access, String name, String signature,
					String superName, String[] interfaces) {
				siblingMatches[0] = siblingName.equals(name) && resultName.equals(superName);
			}

			public MethodVisitor visitMethod(int access, String name, String descriptor,
					String signature, String[] exceptions) {
				if ("<init>".equals(name) && constructorDescriptor.equals(descriptor))
					siblingMatches[1] = true;
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return siblingMatches[0] && siblingMatches[1];
	}

	private static final class ClassVisitorBridge extends ClassAdapter {
		private final String packageName;
		private final String constructorDescriptor;
		private final int[] rewrites;

		ClassVisitorBridge(ClassVisitor visitor, String packageName,
				String constructorDescriptor, int[] rewrites) {
			super(visitor);
			this.packageName = packageName;
			this.constructorDescriptor = constructorDescriptor;
			this.rewrites = rewrites;
		}

		public MethodVisitor visitMethod(int access, String name, String descriptor,
				String signature, String[] exceptions) {
			MethodVisitor downstream = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (!"a".equals(name)
					|| !("(Ljava/lang/String;II)L" + packageName + "/G;").equals(descriptor))
				return downstream;
			return new MethodAdapter(downstream) {
				public void visitTypeInsn(int opcode, String type) {
					if (opcode == Opcodes.NEW && "Z".equals(type)) {
						rewrites[0]++;
						super.visitTypeInsn(opcode, packageName + "/Z");
					} else {
						super.visitTypeInsn(opcode, type);
					}
				}

				public void visitMethodInsn(int opcode, String owner, String methodName,
						String methodDescriptor) {
					if (opcode == Opcodes.INVOKESPECIAL && "Z".equals(owner)
							&& "<init>".equals(methodName)
							&& constructorDescriptor.equals(methodDescriptor)) {
						rewrites[1]++;
						super.visitMethodInsn(opcode, packageName + "/Z", methodName,
								methodDescriptor);
					} else {
						super.visitMethodInsn(opcode, owner, methodName, methodDescriptor);
					}
				}
			};
		}
	}

	private static boolean isSupportedMaxStackClass(byte[] data) {
		if (data == null || data.length < 10 || data.length > 1024 * 1024
				|| (data[0] & 0xff) != 0xca || (data[1] & 0xff) != 0xfe
				|| (data[2] & 0xff) != 0xba || (data[3] & 0xff) != 0xbe) {
			return false;
		}
		int minor = readU2(data, 4);
		int major = readU2(data, 6);
		return (major == 45 && minor == 3)
				|| (major >= 46 && major <= 52 && minor == 0);
	}

	private static Map<String, MaxStackInfo> readMethodMaxStacks(byte[] data) {
		int constantPoolCount = readU2(data, 8);
		String[] utf8 = new String[constantPoolCount];
		int offset = 10;
		for (int i = 1; i < constantPoolCount; i++) {
			int tag = readU1(data, offset++);
			switch (tag) {
				case 1: {
					int length = readU2(data, offset);
					offset += 2;
					requireRange(data, offset, length);
					utf8[i] = new String(data, offset, length, StandardCharsets.UTF_8);
					offset += length;
					break;
				}
				case 3:
				case 4:
					offset = checkedOffset(data, offset, 4);
					break;
				case 5:
				case 6:
					offset = checkedOffset(data, offset, 8);
					i++;
					break;
				case 7:
				case 8:
					offset = checkedOffset(data, offset, 2);
					break;
				case 9:
				case 10:
				case 11:
				case 12:
					offset = checkedOffset(data, offset, 4);
					break;
				case 15:
					offset = checkedOffset(data, offset, 3);
					break;
				case 16:
					offset = checkedOffset(data, offset, 2);
					break;
				case 17:
				case 18:
					offset = checkedOffset(data, offset, 4);
					break;
				case 19:
				case 20:
					offset = checkedOffset(data, offset, 2);
					break;
				default:
					return new HashMap<String, MaxStackInfo>();
			}
		}

		offset = checkedOffset(data, offset, 6);
		int interfaceCount = readU2(data, offset);
		offset = checkedOffset(data, offset + 2, interfaceCount * 2L);
		int fieldCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < fieldCount; i++) offset = skipMember(data, offset);

		Map<String, MaxStackInfo> result = new HashMap<String, MaxStackInfo>();
		int methodCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < methodCount; i++) {
			int methodNameIndex = readU2(data, offset + 2);
			int descriptorIndex = readU2(data, offset + 4);
			String methodName = methodNameIndex > 0 && methodNameIndex < utf8.length
					? utf8[methodNameIndex] : null;
			String descriptor = descriptorIndex > 0 && descriptorIndex < utf8.length
					? utf8[descriptorIndex] : null;
			offset = checkedOffset(data, offset, 6);
			int attributeCount = readU2(data, offset);
			offset += 2;
			for (int j = 0; j < attributeCount; j++) {
				int nameIndex = readU2(data, offset);
				long attributeLength = readU4(data, offset + 2);
				int attributeStart = checkedOffset(data, offset, 6);
				int attributeEnd = checkedOffset(data, attributeStart, attributeLength);
				if (nameIndex > 0 && nameIndex < utf8.length
						&& "Code".equals(utf8[nameIndex])
						&& methodName != null && descriptor != null) {
					String key = methodName + '\u0000' + descriptor;
					result.put(key, new MaxStackInfo(attributeStart, readU2(data, attributeStart)));
				}
				offset = attributeEnd;
			}
		}
		return result;
	}

	private static final class MaxStackInfo {
		final int offset;
		final int value;

		MaxStackInfo(int offset, int value) {
			this.offset = offset;
			this.value = value;
		}
	}

	/**
	 * Some repackaged pre-49 classes contain ldc instructions for CONSTANT_Class,
	 * which requires class-file version 49.0. Upgrade only structurally valid
	 * legacy classes; leave ordinary pre-49 bytecode untouched.
	 */
	static byte[] adaptPre49ClassLiteralLdc(byte[] bytecode, String className) {
		if (bytecode == null || bytecode.length < 10
				|| (bytecode[0] & 0xff) != 0xca || (bytecode[1] & 0xff) != 0xfe
				|| (bytecode[2] & 0xff) != 0xba || (bytecode[3] & 0xff) != 0xbe) {
			return bytecode;
		}
		int minor = readU2(bytecode, 4);
		int major = readU2(bytecode, 6);
		boolean supportedVersion = (major == 45 && minor == 3)
				|| (major >= 46 && major <= 48 && minor == 0);
		if (!supportedVersion) return bytecode;

		try {
			if (!hasLegacyClassLiteralLdc(bytecode)) return bytecode;
		} catch (IllegalArgumentException malformedClass) {
			return bytecode;
		}

		byte[] adapted = bytecode.clone();
		adapted[4] = 0;
		adapted[5] = 0;
		adapted[6] = 0;
		adapted[7] = (byte)JAVA_5_CLASSFILE_VERSION;
		System.out.println("ClassFileCompatibility: upgrading pre-49 classfile "
				+ major + "." + minor + " to 49.0 for class-literal ldc: " + className);
		return adapted;
	}

	/**
	 * Some legacy patch classes begin a void method with an unconditional return
	 * but retain a short unreachable body whose max_stack was left at zero.
	 * Verifiers still check that body, so provide a safe bound only for this
	 * narrow dead-entry pattern.
	 */
	static byte[] adaptLegacyDeadReturnMaxStack(byte[] bytecode, String className) {
		if (bytecode == null || bytecode.length < 10
				|| (bytecode[0] & 0xff) != 0xca || (bytecode[1] & 0xff) != 0xfe
				|| (bytecode[2] & 0xff) != 0xba || (bytecode[3] & 0xff) != 0xbe) {
			return bytecode;
		}
		int minor = readU2(bytecode, 4);
		int major = readU2(bytecode, 6);
		boolean supportedVersion = (major == 45 && minor == 3)
				|| (major >= 46 && major <= 49 && minor == 0);
		if (!supportedVersion) return bytecode;

		try {
			return raiseZeroMaxStackAfterReturn(bytecode, className);
		} catch (IllegalArgumentException malformedClass) {
			return bytecode;
		}
	}

	private static byte[] raiseZeroMaxStackAfterReturn(byte[] data, String className) {
		int constantPoolCount = readU2(data, 8);
		String[] utf8 = new String[constantPoolCount];
		int offset = 10;
		for (int i = 1; i < constantPoolCount; i++) {
			int tag = readU1(data, offset++);
			switch (tag) {
				case 1: {
					int length = readU2(data, offset);
					offset += 2;
					requireRange(data, offset, length);
					utf8[i] = new String(data, offset, length, StandardCharsets.UTF_8);
					offset += length;
					break;
				}
				case 3:
				case 4:
					offset = checkedOffset(data, offset, 4);
					break;
				case 5:
				case 6:
					offset = checkedOffset(data, offset, 8);
					i++;
					break;
				case 7:
				case 8:
					offset = checkedOffset(data, offset, 2);
					break;
				case 9:
				case 10:
				case 11:
				case 12:
					offset = checkedOffset(data, offset, 4);
					break;
				default:
					return data;
			}
		}

		offset = checkedOffset(data, offset, 6); // access flags, this class, super class
		int interfaceCount = readU2(data, offset);
		offset = checkedOffset(data, offset + 2, interfaceCount * 2L);
		int fieldCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < fieldCount; i++) offset = skipMember(data, offset);

		int methodCount = readU2(data, offset);
		offset += 2;
		byte[] adapted = null;
		for (int i = 0; i < methodCount; i++) {
			int methodNameIndex = readU2(data, offset + 2);
			int descriptorIndex = readU2(data, offset + 4);
			String methodName = methodNameIndex > 0 && methodNameIndex < utf8.length
					? utf8[methodNameIndex] : null;
			String descriptor = descriptorIndex > 0 && descriptorIndex < utf8.length
					? utf8[descriptorIndex] : null;
			offset = checkedOffset(data, offset, 6);
			int attributeCount = readU2(data, offset);
			offset += 2;
			for (int j = 0; j < attributeCount; j++) {
				int nameIndex = readU2(data, offset);
				long attributeLength = readU4(data, offset + 2);
				int attributeStart = checkedOffset(data, offset, 6);
				int attributeEnd = checkedOffset(data, attributeStart, attributeLength);
				if (nameIndex > 0 && nameIndex < utf8.length
						&& "Code".equals(utf8[nameIndex])
						&& descriptor != null && descriptor.endsWith(")V")
						&& readU2(data, attributeStart) == 0) {
					int codeLengthOffset = checkedOffset(data, attributeStart, 4);
					long codeLengthLong = readU4(data, codeLengthOffset);
					if (codeLengthLong > 1 && codeLengthLong <= 512) {
						int codeLength = (int)codeLengthLong;
						int codeStart = checkedOffset(data, codeLengthOffset, 4);
						int codeEnd = checkedOffset(data, codeStart, codeLength);
						if (codeEnd <= attributeEnd && readU1(data, codeStart) == 0xb1
								&& hasNontrivialDeadTail(data, codeStart + 1, codeEnd)) {
							if (adapted == null) adapted = data.clone();
							int safeMaxStack = Math.max(2, codeLength * 2);
							adapted[attributeStart] = (byte)(safeMaxStack >>> 8);
							adapted[attributeStart + 1] = (byte)safeMaxStack;
							System.out.println("ClassFileCompatibility: raising zero max_stack"
									+ " after entry return: " + className + "." + methodName);
						}
					}
				}
				offset = attributeEnd;
			}
		}
		return adapted == null ? data : adapted;
	}

	private static boolean hasNontrivialDeadTail(byte[] data, int start, int end) {
		for (int offset = start; offset < end; offset++) {
			int value = readU1(data, offset);
			if (value != 0x00 && value != 0xb1) return true;
		}
		return false;
	}

	private static boolean hasLegacyClassLiteralLdc(byte[] data) {
		int constantPoolCount = readU2(data, 8);
		byte[] tags = new byte[constantPoolCount];
		String[] utf8 = new String[constantPoolCount];
		int offset = 10;

		for (int i = 1; i < constantPoolCount; i++) {
			int tag = readU1(data, offset++);
			tags[i] = (byte)tag;
			switch (tag) {
				case 1: {
					int length = readU2(data, offset);
					offset += 2;
					requireRange(data, offset, length);
					utf8[i] = new String(data, offset, length, StandardCharsets.UTF_8);
					offset += length;
					break;
				}
				case 3:
				case 4:
					offset = checkedOffset(data, offset, 4);
					break;
				case 5:
				case 6:
					offset = checkedOffset(data, offset, 8);
					i++;
					break;
				case 7:
				case 8:
					offset = checkedOffset(data, offset, 2);
					break;
				case 9:
				case 10:
				case 11:
				case 12:
					offset = checkedOffset(data, offset, 4);
					break;
				default:
					// Do not reinterpret classes that already use newer CP semantics.
					return false;
			}
		}

		offset = checkedOffset(data, offset, 6); // access flags, this class, super class
		int interfaceCount = readU2(data, offset);
		offset = checkedOffset(data, offset + 2, interfaceCount * 2L);

		int fieldCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < fieldCount; i++) offset = skipMember(data, offset);

		int methodCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < methodCount; i++) {
			offset = checkedOffset(data, offset, 6); // access flags, name, descriptor
			int attributeCount = readU2(data, offset);
			offset += 2;
			for (int j = 0; j < attributeCount; j++) {
				int nameIndex = readU2(data, offset);
				long attributeLength = readU4(data, offset + 2);
				int attributeStart = checkedOffset(data, offset, 6);
				int attributeEnd = checkedOffset(data, attributeStart, attributeLength);
				if (nameIndex > 0 && nameIndex < utf8.length
						&& "Code".equals(utf8[nameIndex])
						&& codeHasClassLiteralLdc(data, attributeStart, attributeEnd, tags)) {
					return true;
				}
				offset = attributeEnd;
			}
		}
		return false;
	}

	private static boolean codeHasClassLiteralLdc(byte[] data, int start, int end, byte[] tags) {
		int codeLengthOffset = checkedOffset(data, start, 4);
		long codeLength = readU4(data, codeLengthOffset);
		int codeStart = checkedOffset(data, codeLengthOffset, 4);
		int codeEnd = checkedOffset(data, codeStart, codeLength);
		if (codeEnd > end) throw new IllegalArgumentException("Invalid Code attribute");

		for (int pc = codeStart; pc < codeEnd;) {
			int opcode = readU1(data, pc);
			if (opcode == 0x12) {
				int index = readU1(data, pc + 1);
				if (index > 0 && index < tags.length && tags[index] == 7) return true;
			} else if (opcode == 0x13) {
				int index = readU2(data, pc + 1);
				if (index > 0 && index < tags.length && tags[index] == 7) return true;
			}
			pc = nextInstructionOffset(data, codeStart, pc, codeEnd);
		}
		return false;
	}

	private static int nextInstructionOffset(byte[] data, int codeStart, int pc, int codeEnd) {
		int opcode = readU1(data, pc);
		int relative = pc - codeStart;
		long next;
		switch (opcode) {
			case 0xaa: { // tableswitch
				int aligned = (relative + 4) & ~3;
				int table = codeStart + aligned;
				int low = readI4(data, table + 4);
				int high = readI4(data, table + 8);
				long entries = (long)high - low + 1;
				if (entries < 0) throw new IllegalArgumentException("Invalid tableswitch");
				next = (long)table + 12 + entries * 4;
				break;
			}
			case 0xab: { // lookupswitch
				int aligned = (relative + 4) & ~3;
				int table = codeStart + aligned;
				int pairs = readI4(data, table + 4);
				if (pairs < 0) throw new IllegalArgumentException("Invalid lookupswitch");
				next = (long)table + 8 + (long)pairs * 8;
				break;
			}
			case 0xc4: { // wide
				int widenedOpcode = readU1(data, pc + 1);
				if (widenedOpcode == 0x84) next = (long)pc + 6;
				else if ((widenedOpcode >= 0x15 && widenedOpcode <= 0x19)
						|| (widenedOpcode >= 0x36 && widenedOpcode <= 0x3a)
						|| widenedOpcode == 0xa9) next = (long)pc + 4;
				else throw new IllegalArgumentException("Invalid wide opcode");
				break;
			}
			case 0x10: case 0x12: case 0x15: case 0x16: case 0x17: case 0x18: case 0x19:
			case 0x36: case 0x37: case 0x38: case 0x39: case 0x3a: case 0xa9: case 0xbc:
				next = (long)pc + 2;
				break;
			case 0x11: case 0x13: case 0x14: case 0x84:
			case 0x99: case 0x9a: case 0x9b: case 0x9c: case 0x9d: case 0x9e:
			case 0x9f: case 0xa0: case 0xa1: case 0xa2: case 0xa3: case 0xa4:
			case 0xa5: case 0xa6: case 0xa7: case 0xa8:
			case 0xb2: case 0xb3: case 0xb4: case 0xb5: case 0xb6: case 0xb7: case 0xb8:
			case 0xbb: case 0xbd: case 0xc0: case 0xc1: case 0xc6: case 0xc7:
				next = (long)pc + 3;
				break;
			case 0xb9: case 0xba: case 0xc8: case 0xc9:
				next = (long)pc + 5;
				break;
			case 0xc5:
				next = (long)pc + 4;
				break;
			default:
				if ((opcode >= 0x00 && opcode <= 0x0f)
						|| (opcode >= 0x1a && opcode <= 0x35)
						|| (opcode >= 0x3b && opcode <= 0x83)
						|| (opcode >= 0x85 && opcode <= 0x98)
						|| (opcode >= 0xac && opcode <= 0xb1)
						|| (opcode >= 0xbe && opcode <= 0xbf)
						|| (opcode >= 0xc2 && opcode <= 0xc3)) {
					next = (long)pc + 1;
				} else {
					throw new IllegalArgumentException("Invalid bytecode opcode");
				}
		}
		if (next <= pc || next > codeEnd) throw new IllegalArgumentException("Truncated bytecode");
		return (int)next;
	}

	private static int readI4(byte[] data, int offset) {
		return (readU1(data, offset) << 24) | (readU1(data, offset + 1) << 16)
				| (readU1(data, offset + 2) << 8) | readU1(data, offset + 3);
	}

	static byte[] adaptLegacy45CodeAttributeVersion(byte[] bytecode) {
		if (bytecode == null || bytecode.length < 8
				|| (bytecode[0] & 0xff) != 0xca || (bytecode[1] & 0xff) != 0xfe
				|| (bytecode[2] & 0xff) != 0xba || (bytecode[3] & 0xff) != 0xbe) {
			return bytecode;
		}

		int minor = readU2(bytecode, 4);
		int major = readU2(bytecode, 6);
		if (major != 45 || minor > 2) return bytecode;

		// Modern Code attribute layout uses u2 max_stack/max_locals and u4 code_length.
		// HotSpot 8 selects that layout starting at classfile 45.3.
		byte[] adapted = bytecode.clone();
		adapted[4] = 0;
		adapted[5] = 3;
		return adapted;
	}

	private static String classByteSummary(byte[] bytecode) {
		if (bytecode == null) return "bytes=0";
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(bytecode);
		int minor = bytecode.length < 8 ? -1 : readU2(bytecode, 4);
		int major = bytecode.length < 8 ? -1 : readU2(bytecode, 6);
		return "bytes=" + bytecode.length + ",major=" + major + ",minor=" + minor
				+ ",crc32=" + Long.toHexString(crc.getValue());
	}

	static byte[] adaptMissingStackMapTable(byte[] bytecode, String className) {
		if (bytecode == null || bytecode.length < 10
				|| (bytecode[0] & 0xff) != 0xca || (bytecode[1] & 0xff) != 0xfe
				|| (bytecode[2] & 0xff) != 0xba || (bytecode[3] & 0xff) != 0xbe) {
			return bytecode;
		}

		int major = readU2(bytecode, 6);
		if (major < 51 || major > 52) return bytecode;

		try {
			if (!hasMissingStackMapFrames(bytecode)) return bytecode;
		} catch (IllegalArgumentException malformedClass) {
			return bytecode;
		}

		byte[] adapted = bytecode.clone();
		adapted[6] = 0;
		adapted[7] = (byte)LEGACY_VERIFIER_VERSION;
		System.out.println("MIDletLoader: using Java 6 verifier fallback for incomplete "
				+ "StackMapTable class v" + major + ": " + className);
		return adapted;
	}

	private static boolean hasMissingStackMapFrames(byte[] data) {
		int constantPoolCount = readU2(data, 8);
		String[] utf8 = new String[constantPoolCount];
		int offset = 10;

		for (int i = 1; i < constantPoolCount; i++) {
			int tag = readU1(data, offset++);
			switch (tag) {
				case 1: {
					int length = readU2(data, offset);
					offset += 2;
					requireRange(data, offset, length);
					utf8[i] = new String(data, offset, length, StandardCharsets.UTF_8);
					offset += length;
					break;
				}
				case 3:
				case 4:
					offset = checkedOffset(data, offset, 4);
					break;
				case 5:
				case 6:
					offset = checkedOffset(data, offset, 8);
					i++;
					break;
				case 7:
				case 8:
				case 9:
				case 10:
				case 11:
				case 12:
					offset = checkedOffset(data, offset, tag == 7 || tag == 8 ? 2 : 4);
					break;
				case 15:
				case 16:
				case 17:
				case 18:
				case 19:
				case 20:
					// Method handles, dynamic call sites and module constants need newer semantics.
					return false;
				default:
					return false;
			}
		}

		int classAccessFlags = readU2(data, offset);
		if ((classAccessFlags & 0x0200) != 0) return false; // Interface default/static methods require v52.
		offset = checkedOffset(data, offset, 6); // access flags, this class, super class
		int interfaceCount = readU2(data, offset);
		offset = checkedOffset(data, offset + 2, interfaceCount * 2L);

		int fieldCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < fieldCount; i++) offset = skipMember(data, offset);

		int methodCount = readU2(data, offset);
		offset += 2;
		boolean missingFrames = false;
		for (int i = 0; i < methodCount; i++) {
			offset = checkedOffset(data, offset, 6); // access flags, name, descriptor
			int attributeCount = readU2(data, offset);
			offset += 2;
			for (int j = 0; j < attributeCount; j++) {
				int nameIndex = readU2(data, offset);
				long attributeLength = readU4(data, offset + 2);
				int attributeStart = checkedOffset(data, offset, 6);
				int attributeEnd = checkedOffset(data, attributeStart, attributeLength);
				if (nameIndex > 0 && nameIndex < utf8.length
						&& "Code".equals(utf8[nameIndex])
						&& codeHasMissingBranchFrames(data, attributeStart, attributeEnd, utf8)) {
					missingFrames = true;
				}
				offset = attributeEnd;
			}
		}
		return missingFrames;
	}

	private static int skipMember(byte[] data, int offset) {
		offset = checkedOffset(data, offset, 6); // access flags, name, descriptor
		int attributeCount = readU2(data, offset);
		offset += 2;
		for (int i = 0; i < attributeCount; i++) {
			long attributeLength = readU4(data, offset + 2);
			int attributeStart = checkedOffset(data, offset, 6);
			offset = checkedOffset(data, attributeStart, attributeLength);
		}
		return offset;
	}

	private static boolean codeHasMissingBranchFrames(
			byte[] data, int start, int end, String[] utf8) {
		int codeLengthOffset = checkedOffset(data, start, 4); // max stack and max locals
		long codeLengthLong = readU4(data, codeLengthOffset);
		if (codeLengthLong > 65535) throw new IllegalArgumentException("Invalid code length");
		int codeLength = (int)codeLengthLong;
		int codeStart = checkedOffset(data, codeLengthOffset, 4);
		int codeEnd = checkedOffset(data, codeStart, codeLength);
		if (codeEnd > end) throw new IllegalArgumentException("Invalid Code attribute");

		boolean[] requiredFrames = new boolean[codeLength];
		boolean[] presentFrames = new boolean[codeLength];
		if (codeLength > 0) presentFrames[0] = true; // the initial frame is implicit
		for (int pc = codeStart; pc < codeEnd;) {
			int opcode = readU1(data, pc);
			int relative = pc - codeStart;
			if ((opcode >= 0x99 && opcode <= 0xa8) || opcode == 0xc6 || opcode == 0xc7) {
				addBranchTarget(requiredFrames, relative + (short)readU2(data, pc + 1));
			} else if (opcode == 0xc8 || opcode == 0xc9) {
				addBranchTarget(requiredFrames, (long)relative + readI4(data, pc + 1));
			} else if (opcode == 0xaa) {
				int aligned = (relative + 4) & ~3;
				int table = codeStart + aligned;
				addBranchTarget(requiredFrames, (long)relative + readI4(data, table));
				int low = readI4(data, table + 4);
				int high = readI4(data, table + 8);
				long count = (long)high - low + 1;
				if (count < 0 || count > 65535)
					throw new IllegalArgumentException("Invalid tableswitch");
				for (int i = 0; i < count; i++)
					addBranchTarget(requiredFrames,
							(long)relative + readI4(data, table + 12 + i * 4));
			} else if (opcode == 0xab) {
				int aligned = (relative + 4) & ~3;
				int table = codeStart + aligned;
				addBranchTarget(requiredFrames, (long)relative + readI4(data, table));
				int pairs = readI4(data, table + 4);
				if (pairs < 0 || pairs > 65535)
					throw new IllegalArgumentException("Invalid lookupswitch");
				for (int i = 0; i < pairs; i++)
					addBranchTarget(requiredFrames,
							(long)relative + readI4(data, table + 12 + i * 8));
			}
			pc = nextInstructionOffset(data, codeStart, pc, codeEnd);
		}

		int exceptionCount = readU2(data, codeEnd);
		int exceptionStart = checkedOffset(data, codeEnd, 2);
		for (int i = 0; i < exceptionCount; i++) {
			int handlerOffset = checkedOffset(data, exceptionStart, i * 8 + 4);
			addBranchTarget(requiredFrames, readU2(data, handlerOffset));
		}
		int offset = checkedOffset(data, exceptionStart, exceptionCount * 8L);
		int attributeCount = readU2(data, offset);
		offset += 2;
		if (offset > end) throw new IllegalArgumentException("Invalid Code attribute");

		boolean hasStackMapTable = false;
		for (int i = 0; i < attributeCount; i++) {
			int nameIndex = readU2(data, offset);
			long attributeLength = readU4(data, offset + 2);
			int attributeStart = checkedOffset(data, offset, 6);
			int attributeEnd = checkedOffset(data, attributeStart, attributeLength);
			if (attributeEnd > end)
				throw new IllegalArgumentException("Invalid nested Code attribute");
			if (nameIndex > 0 && nameIndex < utf8.length
					&& "StackMapTable".equals(utf8[nameIndex])) {
				if (hasStackMapTable) throw new IllegalArgumentException("Duplicate StackMapTable");
				hasStackMapTable = true;
				readStackMapFrames(data, attributeStart, attributeEnd, presentFrames);
			}
			offset = attributeEnd;
		}
		if (offset != end) throw new IllegalArgumentException("Invalid Code attribute length");

		for (int i = 0; i < requiredFrames.length; i++) {
			if (requiredFrames[i] && !presentFrames[i]) return true;
		}
		return false;
	}

	private static void addBranchTarget(boolean[] targets, long offset) {
		if (offset < 0 || offset >= targets.length)
			throw new IllegalArgumentException("Branch target outside Code");
		targets[(int)offset] = true;
	}

	private static void readStackMapFrames(
			byte[] data, int start, int end, boolean[] frameOffsets) {
		int frameCount = readU2(data, start);
		int offset = checkedOffset(data, start, 2);
		int previousOffset = -1;
		for (int i = 0; i < frameCount; i++) {
			int frameType = readU1(data, offset++);
			int offsetDelta;
			if (frameType <= 63) {
				offsetDelta = frameType;
			} else if (frameType <= 127) {
				offsetDelta = frameType - 64;
				offset = skipVerificationTypeInfo(data, offset);
			} else if (frameType == 247) {
				offsetDelta = readU2(data, offset);
				offset = checkedOffset(data, offset, 2);
				offset = skipVerificationTypeInfo(data, offset);
			} else if (frameType >= 248 && frameType <= 251) {
				offsetDelta = readU2(data, offset);
				offset = checkedOffset(data, offset, 2);
			} else if (frameType >= 252 && frameType <= 254) {
				offsetDelta = readU2(data, offset);
				offset = checkedOffset(data, offset, 2);
				for (int local = 0; local < frameType - 251; local++)
					offset = skipVerificationTypeInfo(data, offset);
			} else if (frameType == 255) {
				offsetDelta = readU2(data, offset);
				offset = checkedOffset(data, offset, 2);
				int localCount = readU2(data, offset);
				offset = checkedOffset(data, offset, 2);
				for (int local = 0; local < localCount; local++)
					offset = skipVerificationTypeInfo(data, offset);
				int stackCount = readU2(data, offset);
				offset = checkedOffset(data, offset, 2);
				for (int stack = 0; stack < stackCount; stack++)
					offset = skipVerificationTypeInfo(data, offset);
			} else {
				throw new IllegalArgumentException("Reserved StackMapTable frame type");
			}

			long frameOffset = (long)previousOffset + offsetDelta + 1;
			if (frameOffset < 0 || frameOffset >= frameOffsets.length) {
				throw new IllegalArgumentException("Invalid StackMapTable frame offset");
			}
			frameOffsets[(int)frameOffset] = true;
			previousOffset = (int)frameOffset;
		}
		if (offset != end) throw new IllegalArgumentException("Invalid StackMapTable length");
	}

	private static int skipVerificationTypeInfo(byte[] data, int offset) {
		int tag = readU1(data, offset);
		if (tag >= 0 && tag <= 6) return offset + 1;
		if (tag == 7 || tag == 8) return checkedOffset(data, offset, 3);
		throw new IllegalArgumentException("Invalid verification_type_info tag");
	}

	private static int readU1(byte[] data, int offset) {
		requireRange(data, offset, 1);
		return data[offset] & 0xff;
	}

	private static int readU2(byte[] data, int offset) {
		requireRange(data, offset, 2);
		return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
	}

	private static long readU4(byte[] data, int offset) {
		requireRange(data, offset, 4);
		return ((long)(data[offset] & 0xff) << 24)
				| ((long)(data[offset + 1] & 0xff) << 16)
				| ((long)(data[offset + 2] & 0xff) << 8)
				| (long)(data[offset + 3] & 0xff);
	}

	private static int checkedOffset(byte[] data, int offset, long length) {
		if (length < 0 || offset < 0 || offset > data.length || length > data.length - offset) {
			throw new IllegalArgumentException("Invalid class-file offset");
		}
		return offset + (int)length;
	}

	private static void requireRange(byte[] data, int offset, int length) {
		checkedOffset(data, offset, length);
	}
}
