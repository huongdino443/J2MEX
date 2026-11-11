/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package org.recompile.mobile;


import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.net.URL;
import java.net.URLClassLoader;

import java.lang.ClassLoader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.io.*;
import javax.microedition.midlet.MIDletStateChangeException;

public class MIDletLoader extends URLClassLoader
{
	public String name;
	public String icon;
	private String className;

	public String suitename;

private boolean cloneDisplayIsolation;
private CloneClassSpace cloneClassSpace;
private final GameResourceCompatibility resourceCompatibility;
private final boolean traceEnvironmentAssetFailures;

	private Class<?> mainClass;
	private MIDlet mainInst;

	private HashMap<String, String> properties = new HashMap<String, String>(32);


	public MIDletLoader(URL urls[])
	{
		super(urls);
ManagedMemoryDiscovery.attach(this);
traceEnvironmentAssetFailures = findResource("EnvironmentAssetsS34.class") != null;
if (traceEnvironmentAssetFailures)
	System.out.println("[ResourceDiag] optional asset-loader trace enabled; messages, payloads and credentials are excluded");
		String deviceLocale = Locale.getDefault().toLanguageTag();
		String textEncoding = System.getProperty("file.encoding", "ISO-8859-1");

resourceCompatibility = findResource("fntCrystalSmall40_0.pak") == null ? null
    : GameResourceCompatibility.forClasses(findResource("ew.class"), findResource("ep.class"));
cloneDisplayIsolation = findResource("lib/MIDlet.class") != null
		&& findResource("lib/Display.class") != null;
if (cloneDisplayIsolation) MobilePlatform.resetDisplayOwners();

		try
		{
			System.setProperty("microedition.platform", "j2me");
			System.setProperty("microedition.profiles", "MIDP-2.0");
			System.setProperty("microedition.configuration", "CLDC-1.0");
			System.setProperty("microedition.locale", deviceLocale);
			System.setProperty("microedition.encoding", textEncoding);
			// The Android audio host keeps independent MMAPI Players and mixes them.
			System.setProperty("supports.mixing", "true");
		}
		catch (Exception e)
		{
			System.out.println("Can't add CLDC System Properties");
		}

		try
		{
			loadManifest();
if (cloneDisplayIsolation)
cloneClassSpace = new CloneClassSpace(this, urls, properties, className);

			properties.put("microedition.platform", "j2me");
			properties.put("microedition.profiles", "MIDP-2.0");
			properties.put("microedition.configuration", "CLDC-1.0");
			properties.put("microedition.locale", deviceLocale);
			properties.put("microedition.encoding", textEncoding);
			properties.put("supports.mixing", "true");
		}
		catch (Exception e)
		{
			System.out.println("Can't Read Manifest!");
			return;
		}

	}

	public String getMIDletClassName() { return className; }
	public MIDlet getMainInstance() { return mainInst; }

private static final Set<String> loggedCaughtMidletErrors =
		Collections.synchronizedSet(new HashSet<String>());

public static void logCaughtMidletThrowable(Throwable error, String callback)
{
if(error == null) return;
if ("EnvironmentAssetsS34.run()V".equals(callback)
		|| "EnvironmentAssetsS34.warm()V".equals(callback))
{
	logEnvironmentAssetFailure(error, callback);
	return;
}
String signature = callback + "|" + error.getClass().getName() + "|" + error.getMessage();
if(loggedCaughtMidletErrors.size() >= 64 || !loggedCaughtMidletErrors.add(signature)) return;
System.out.println("[MIDlet-caught] " + callback);
error.printStackTrace(System.out);
}

private static final Set<String> loggedResourceAssetFailures =
		Collections.synchronizedSet(new HashSet<String>());

private static void logEnvironmentAssetFailure(Throwable error, String callback)
{
	try
	{
		MobilePlatform platform = Mobile.getPlatform();
		ClassLoader loader = platform == null ? null : platform.loader;
		String file = diagnosticField(loader, "EnvironmentAssetsS34", "currentFile");
		String phase = diagnosticField(loader, "EnvironmentAssetsS34", "phase");
		String stage = diagnosticMethod(loader, "HellMapLoadTrace", "status");
		String causeTypes = throwableTypes(error);
		String line = "[ResourceDiag] callback=" + callback + " file=" + file
				+ " phase=" + phase + " stage=" + stage + " causeTypes=" + causeTypes;
		if (loggedResourceAssetFailures.size() >= 32
				|| !loggedResourceAssetFailures.add(line)) return;
		System.out.println(line);
	}
	catch (Throwable ignored)
	{
		// Diagnostics must never alter a MIDlet's error handling.
	}
}

private static String diagnosticField(ClassLoader loader, String className, String fieldName)
{
	try
	{
		if (loader == null) return "unavailable";
		Class<?> type = Class.forName(className, false, loader);
		return safeDiagnosticValue(type.getField(fieldName).get(null));
	}
	catch (Throwable ignored) { return "unavailable"; }
}

private static String diagnosticMethod(ClassLoader loader, String className, String methodName)
{
	try
	{
		if (loader == null) return "unavailable";
		Class<?> type = Class.forName(className, false, loader);
		Method method = type.getMethod(methodName);
		return safeDiagnosticValue(method.invoke(null));
	}
	catch (Throwable ignored) { return "unavailable"; }
}

private static String safeDiagnosticValue(Object value)
{
	if (!(value instanceof String)) return "unavailable";
	String text = (String)value;
	if (text.length() > 160) text = text.substring(0, 160);
	StringBuilder safe = new StringBuilder(text.length());
	for (int i = 0; i < text.length(); i++)
	{
		char c = text.charAt(i);
		safe.append(c < 32 || c == 127 ? ' ' : c);
	}
	return safe.length() == 0 ? "empty" : safe.toString();
}

private static String throwableTypes(Throwable error)
{
	StringBuilder types = new StringBuilder();
	Throwable current = error;
	for (int depth = 0; current != null && depth < 5; depth++)
	{
		if (depth > 0) types.append('>');
		String name = current.getClass().getSimpleName();
		types.append(name == null || name.length() == 0
				? current.getClass().getName() : name);
		Throwable cause;
		try { cause = current.getCause(); }
		catch (Throwable ignored) { break; }
		if (cause == current) break;
		current = cause;
	}
	return types.length() == 0 ? "unknown" : types.toString();
}

	public void start() throws MIDletStateChangeException
	{
		Method start = null;

		try
		{
			mainClass = loadClass(className, true);

			Constructor constructor;
			constructor = mainClass.getConstructor();
			constructor.setAccessible(true);

			MIDlet.initAppProperties(properties);
			mainInst = (MIDlet)constructor.newInstance();
		}
		catch (Exception e)
		{
			System.out.println("Problem Constructing " + name + " class: " +className);
			System.out.println("Reason: "+e.getMessage());
			e.printStackTrace();
			System.exit(0);
			return;
		}

		try
		{
			while (start == null)
			{
				try
				{
					start = mainClass.getDeclaredMethod("startApp");
					start.setAccessible(true);
				}
				catch (NoSuchMethodException e)
				{
					mainClass = mainClass.getSuperclass();
					if (mainClass == null || mainClass == MIDlet.class)
					{
						throw e;
					}

					mainClass = loadClass(mainClass.getName(), true);
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("Can't Find startApp Method");
			e.printStackTrace();
			System.exit(0);
			return;
		}

		try
		{
			start.invoke(mainInst);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(0);
		}
	}

	private void loadManifest()
	{
		String resource = "META-INF/MANIFEST.MF";
		URL url = findResource(resource);
		if(url==null)
		{
			resource = "meta-inf/MANIFEST.MF";
			url = findResource(resource);
			if(url==null)
			{
				resource = "META-INF/manifest.fm";
				url = findResource(resource);
				if(url==null)
				{
					resource = "meta-inf/manifest.fm";
					url = findResource(resource);
					if(url==null)
					{
						return;
					}	
				}	
			}
		}

		String line;
		String[] parts;
		int split;
		String key;
		String value;
		try
		{
			InputStream is = url.openStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			
			ArrayList<String> lines = new ArrayList<String>();
			while ((line = br.readLine()) != null) 
			{
				if(line.startsWith(" "))
				{
					line = lines.get(lines.size()-1) + line.trim();
					lines.remove(lines.size()-1);
				}
				lines.add(line);
			}

			for (int i=0; i<lines.size(); i++)
			{
				line = lines.get(i);
				if(line.startsWith("MIDlet-1:"))
				{
					System.out.println(line);
					line = line.substring(9);
					parts = line.split(",");
					if(parts.length == 3)
					{
						name = parts[0].trim();
						icon = parts[1].trim();
						className = parts[2].trim();
						suitename = name;
					}
					//System.out.println("Loading " + name);
				}

				split = line.indexOf(":");
				if(split>0)
				{
					key = line.substring(0, split).trim();
					value = line.substring(split+1).trim();
					properties.put(key, value);
				}
			}
			// for RecordStore, remove illegal chars from name
			suitename = suitename.replace(":","");
		}
		catch (Exception e)
		{
			System.out.println("Can't Read Jar Manifest!");
			e.printStackTrace();
		}
	}


	public InputStream getResourceAsStream(String resource)
	{
		URL url;
		//System.out.println("Loading Resource: " + resource);

		if(resource.startsWith("/"))
		{
			resource = resource.substring(1);
		}

byte[] original;
try
{
url = findResource(resource);
original = readResource(url);
		}
		catch (Exception e)
		{
			System.out.println(resource + " Not Found");
			return super.getResourceAsStream(resource);
		}
return new ByteArrayInputStream(adaptResource(resource, original));
	}


	public URL getResource(String resource)
	{
		if(resource.startsWith("/"))
		{
			resource = resource.substring(1);
		}
		try
		{
			URL url = findResource(resource);
			return url;
		}
		catch (Exception e)
		{
			System.out.println(resource + " Not Found");
			return super.getResource(resource);
		}
	}

	/*
		********  loadClass Modifies Methods with ObjectWeb ASM  ********
		Replaces java.lang.Class.getResourceAsStream calls with calls
		to Mobile.getResourceAsStream which calls
		MIDletLoader.getResourceAsStream(class, string)
	*/

	public InputStream getMIDletResourceAsStream(String resource)
	{
		//System.out.println("Get Resource: "+resource);

		URL url = getResource(resource);

		// Read all bytes, return ByteArrayInputStream //
byte[] original;
try
{
original = readResource(url);
		}
		catch (Exception e)
		{
			return super.getResourceAsStream(resource);
		}
return new ByteArrayInputStream(adaptResource(resource, original));
	}

	public byte[] getMIDletResourceAsByteArray(String resource)
	{
		URL url = getResource(resource);

byte[] original;
		try
		{
original = readResource(url);
		}
		catch (Exception e)
		{
			System.out.println(resource + " Not Found");
			return new byte[0];
		}
return adaptResource(resource, original);
}

private byte[] adaptResource(String resource, byte[] original)
{
return resourceCompatibility == null ? original : resourceCompatibility.adapt(resource, original);
}

private static byte[] readResource(URL url) throws IOException
{
try (InputStream stream = url.openStream())
{
ByteArrayOutputStream output = new ByteArrayOutputStream();
byte[] buffer = new byte[4096];
int length;
while ((length = stream.read(buffer)) != -1) output.write(buffer, 0, length);
return output.toByteArray();
}
	}


	public synchronized Class loadClass(String name) throws ClassNotFoundException
	{
		String resource;
		byte[] code;

		//System.out.println("Load Class "+name);

		if(
			name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("com.nokia") ||
			name.startsWith("com.mascotcapsule") || name.startsWith("com.samsung") || name.startsWith("sun.") ||
			name.startsWith("com.siemens") || name.startsWith("org.recompile")
			)
		{
			return loadClass(name, true);
		}

		Class<?> loaded = findLoadedClass(name);
		if (loaded != null) return loaded;
		try
		{
			resource = name.replace(".", "/") + ".class";
			byte[] original;
			try (InputStream stream = super.getResourceAsStream(resource))
			{
				if (stream == null) throw new ClassNotFoundException(name);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				int length;
				while ((length = stream.read(buffer)) != -1) output.write(buffer, 0, length);
				original = output.toByteArray();
			}
			original = ClassFileCompatibility.adaptMissingStackMapTable(original, name);
			code = adaptDynamicallyLoadedClass(original, name);
			return ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
					name, code, classBytes -> defineClass(name, classBytes, 0, classBytes.length));
		}
		catch (ClassNotFoundException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new ClassNotFoundException("Unable to adapt MIDlet class " + name, e);
		}
	}

	private byte[] adaptDynamicallyLoadedClass(byte[] original, String name) throws Exception
	{
		String bytecodeName = name.replace('.', '/');
		boolean lifecycleAdapter = cloneDisplayIsolation
				&& ("lib/MIDlet".equals(bytecodeName) || "lib/Display".equals(bytecodeName)
				|| cloneClassSpace != null && cloneClassSpace.isFactory(name));
byte[] source = original;
if (containsAscii(original, "<clinit>"))
{
try { source = instrumentMemoryClassInitializer(source, bytecodeName); }
catch (Exception discoveryError)
{
System.out.println("MIDletLoader: memory discovery hook skipped for "
+ name + ": " + discoveryError.getClass().getSimpleName());
}
}
MobilePlatform platform = Mobile.getPlatform();
boolean canvasAdapted = false;
if (platform != null && mayContainCanvasResolutionParser(source))
{
byte[] adapted = CanvasResolutionCompatibility.adapt(source, bytecodeName,
platform.lcdWidth, platform.lcdHeight);
if (adapted != source)
{
source = adapted;
canvasAdapted = true;
}
}

if (lifecycleAdapter) return instrument(new ByteArrayInputStream(source), bytecodeName);

boolean traceCaught = Boolean.getBoolean("freej2me.traceCaughtMidletExceptions")
|| traceEnvironmentAssetClass(bytecodeName);
if (containsTimingCall(original) || canvasAdapted)
{
try
{
byte[] transformed = instrument(new ByteArrayInputStream(source), bytecodeName);
if (traceCaught)
{
try { transformed = instrumentCaughtExceptions(transformed, bytecodeName); }
catch (Exception traceError)
{
System.out.println("MIDletLoader: caught-exception trace skipped for "
+ name + ": " + traceError);
}
}
return transformed;
}
catch (Exception timingError)
{
System.out.println("MIDletLoader: timing/canvas transform skipped for "
+ name + ": " + timingError.getClass().getSimpleName());
return adaptResourceCalls(source, name);
}
}

if (traceCaught)
{
try
{
return instrumentCaughtExceptions(source, bytecodeName);
}
catch (Exception traceError)
{
System.out.println("MIDletLoader: caught-exception trace skipped for "
+ name + ": " + traceError);
return adaptResourceCalls(source, name);
}
}
return adaptResourceCalls(source, name);
	}

	private static boolean containsTimingCall(byte[] bytecode)
	{
		return containsAscii(bytecode, "java/lang/Thread")
				&& containsAscii(bytecode, "sleep")
				|| containsAscii(bytecode, "java/lang/System")
				&& (containsAscii(bytecode, "currentTimeMillis")
						|| containsAscii(bytecode, "nanoTime"))
				|| containsAscii(bytecode, "java/lang/Object")
				&& containsAscii(bytecode, "wait")
				|| containsAscii(bytecode, "java/util/Timer")
				&& (containsAscii(bytecode, "schedule")
						|| containsAscii(bytecode, "scheduleAtFixedRate"));
	}

	private boolean traceEnvironmentAssetClass(String bytecodeName)
	{
		return traceEnvironmentAssetFailures && "EnvironmentAssetsS34".equals(bytecodeName);
	}

	private static boolean mayContainCanvasResolutionParser(byte[] bytecode)
	{
		return containsAscii(bytecode, "javax/microedition/lcdui/Canvas")
				&& containsAscii(bytecode, "setFullScreenMode")
				&& containsAscii(bytecode, "getWidth")
				&& containsAscii(bytecode, "getHeight")
				&& (containsAscii(bytecode, "indexOf") || containsAscii(bytecode, "substring"))
				&& containsAscii(bytecode, "java/lang/Integer")
				&& containsAscii(bytecode, "valueOf");
	}

	protected synchronized Class<?> findClass(String name) throws ClassNotFoundException
	{
		Class<?> loaded = findLoadedClass(name);
		if (loaded != null) return loaded;

		try
		{
			String resource = name.replace(".", "/") + ".class";
			URL url = findResource(resource);
			if (url == null) return super.findClass(name);
			byte[] code;
			try (InputStream stream = url.openStream())
			{
ByteArrayOutputStream output = new ByteArrayOutputStream();
byte[] buffer = new byte[4096];
int length;
while ((length = stream.read(buffer)) != -1) output.write(buffer, 0, length);
byte[] original = ClassFileCompatibility.adaptMissingStackMapTable(output.toByteArray(), name);
code = adaptDynamicallyLoadedClass(original, name);
			}
			return ClassFileCompatibility.defineWithLegacy45CodeAttributeFallback(
					name, code, classBytes -> defineClass(name, classBytes, 0, classBytes.length));
		}
		catch (Exception e)
		{
			throw new ClassNotFoundException("Unable to load MIDlet class " + name, e);
		}
	}


/**
 * Entry and generation loading bypass loadClass(String), so preserve its
 * resource bridge explicitly. Some MIDlets use String.class.getResourceAsStream, which
 * cannot see game assets through the bootstrap loader. Leave all unrelated
 * classes raw: the legacy ASM reader cannot handle every game's classfile.
 */
byte[] adaptResourceCalls(byte[] code, String className) throws Exception
{
if (!containsAscii(code, "java/lang/Class") || !containsAscii(code, "getResourceAsStream"))
return code;
return instrument(new ByteArrayInputStream(code), className.replace('.', '/'));
}

private static boolean containsAscii(byte[] data, String value)
{
outer: for (int i = 0; i <= data.length - value.length(); i++)
{
for (int j = 0; j < value.length(); j++)
if ((data[i + j] & 0xff) != value.charAt(j)) continue outer;
return true;
}
return false;
}

public static Class<?> resolveCloneClass(String name, Object caller)
			throws ClassNotFoundException
	{
		ClassLoader loader = caller.getClass().getClassLoader();
		if (loader instanceof MIDletLoader)
		{
			CloneClassSpace space = ((MIDletLoader)loader).cloneClassSpace;
			if (space != null) return space.resolve(name);
		}
		return Class.forName(name, true, loader);
	}

/* **************************************************************
 * Special Siemens Stuff
 * ************************************************************** */

	public InputStream getMIDletResourceAsSiemensStream(String resource)
	{
		URL url = getResource(resource);

		try
		{
			InputStream stream = url.openStream();

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int count=0;
			byte[] data = new byte[4096];
			while (count!=-1)
			{
				count = stream.read(data);
				if(count!=-1) { buffer.write(data, 0, count); }
			}
			return new SiemensInputStream(buffer.toByteArray());
		}
		catch (Exception e)
		{
			return super.getResourceAsStream(resource);
		}
	}

	private class SiemensInputStream extends InputStream
	{
		private ByteArrayInputStream iostream;

		public SiemensInputStream(byte[] data)
		{
			iostream = new ByteArrayInputStream(data);
		}

		public int read()
		{
			int t = iostream.read();
			if (t == -1) { return 0; }
			return t;
		}
		public int read(byte[] b, int off, int len)
		{
			int t = iostream.read(b, off, len);
			if (t == -1) { return 0; }
			return t;
		}
	}


/* ************************************************************** 
 * Instrumentation
 * ************************************************************** */

	private byte[] instrument(InputStream stream, String bytecodeClassName) throws Exception
	{
ByteArrayOutputStream input = new ByteArrayOutputStream();
byte[] buffer = new byte[8192];
int length;
while((length = stream.read(buffer)) != -1) { input.write(buffer, 0, length); }
byte[] bytecode = input.toByteArray();
bytecode = ClassFileCompatibility.adaptMissingStackMapTable(bytecode, bytecodeClassName.replace('/', '.'));
MobilePlatform platform = Mobile.getPlatform();
if (platform != null)
{
bytecode = CanvasResolutionCompatibility.adapt(bytecode, bytecodeClassName.replace('/', '.'),
platform.lcdWidth, platform.lcdHeight);
}
try
{
return instrument(bytecode, bytecodeClassName, 0);
}
catch(ArrayIndexOutOfBoundsException frameError)
{
// The bundled legacy ASM reader can reject valid Java 6 StackMapTable frames.
// Preserve the fast path for every class it can already adapt, and only retry
// this older class-file version without frames after the frame parser fails.
if(bytecode.length < 8 || (((bytecode[6] & 0xff) << 8) | (bytecode[7] & 0xff)) != Opcodes.V1_6)
{
throw(frameError);
}
System.out.println("MIDletLoader: retrying Java 6 class without StackMapTable frames: " + bytecodeClassName);
return instrument(bytecode, bytecodeClassName, ClassReader.SKIP_FRAMES);
}
}

private byte[] instrumentMemoryClassInitializer(byte[] bytecode, String bytecodeClassName)
throws Exception
{
ClassReader reader = new ClassReader(bytecode);
ClassWriter writer = new ClassWriter(0);
ClassVisitor visitor = new ClassAdapter(writer)
{
public MethodVisitor visitMethod(final int access, final String name, final String desc,
final String signature, final String[] exceptions)
{
MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
if (!"<clinit>".equals(name)) return method;
return new MethodAdapter(method)
{
public void visitInsn(int opcode)
{
if (opcode == Opcodes.RETURN)
{
mv.visitLdcInsn(bytecodeClassName.replace('/', '.'));
mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/recompile/mobile/ManagedMemoryDiscovery",
"classInitialized", "(Ljava/lang/String;)V");
}
super.visitInsn(opcode);
}

public void visitMaxs(int maxStack, int maxLocals)
{
super.visitMaxs(Math.max(maxStack, 1), maxLocals);
}
};
}
};
reader.accept(visitor, 0);
return writer.toByteArray();
}

private byte[] instrument(byte[] bytecode, String bytecodeClassName, int readerFlags) throws Exception
{
ClassReader reader = new ClassReader(bytecode);
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor visitor = new ASMVisitor(writer, bytecodeClassName);
reader.accept(visitor, readerFlags);
		return writer.toByteArray();
	}

	private byte[] instrumentCaughtExceptions(byte[] bytecode, String bytecodeClassName) throws Exception
	{
		try
		{
			return instrument(bytecode, bytecodeClassName, 0);
		}
		catch(ArrayIndexOutOfBoundsException frameError)
		{
			if(bytecode.length < 8
				|| (((bytecode[6] & 0xff) << 8) | (bytecode[7] & 0xff)) != Opcodes.V1_6)
			{
				throw frameError;
			}
			System.out.println("MIDletLoader: retrying Java 6 exception trace without StackMapTable: "
				+ bytecodeClassName);
			return instrument(bytecode, bytecodeClassName, ClassReader.SKIP_FRAMES);
		}
	}

	private class ASMVisitor extends ClassAdapter
	{
		private final String bytecodeClassName;

		public ASMVisitor(ClassVisitor visitor, String bytecodeClassName)
		{
			super(visitor);
			this.bytecodeClassName = bytecodeClassName;
		}

		public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces)
		{
			super.visit(version, access, name, signature, superName, interfaces);
		}

		public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions)
		{
			return new ASMMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions),
					bytecodeClassName, name, desc, access);
		}

		private class ASMMethodVisitor extends MethodAdapter implements Opcodes
		{
			private final String ownerClass;
			private final String methodName;
			private final String methodDescriptor;
			private final boolean staticMethod;
			private final boolean traceCaughtThrowables;
			private final ArrayList<Label> caughtThrowableHandlers = new ArrayList<Label>();
			private boolean insertedThrowableTrace;
			private boolean pendingThrowableTrace;

			public ASMMethodVisitor(MethodVisitor visitor, String ownerClass, String methodName,
					String methodDescriptor, int access)
			{
				super(visitor);
				this.ownerClass = ownerClass;
				this.methodName = methodName;
				this.methodDescriptor = methodDescriptor;
				this.staticMethod = (access & ACC_STATIC) != 0;
				boolean globalCallback = ("run".equals(methodName) && "()V".equals(methodDescriptor))
						|| ("keyPressed".equals(methodName) && "(I)V".equals(methodDescriptor));
				boolean environmentAssetCallback = traceEnvironmentAssetFailures
						&& "EnvironmentAssetsS34".equals(ownerClass)
						&& (("run".equals(methodName) && "()V".equals(methodDescriptor))
						|| ("warm".equals(methodName) && "()V".equals(methodDescriptor)));
				this.traceCaughtThrowables =
						(Boolean.getBoolean("freej2me.traceCaughtMidletExceptions") && globalCallback)
						|| environmentAssetCallback;
			}

			public void visitTryCatchBlock(Label start, Label end, Label handler, String type)
			{
				if(traceCaughtThrowables && "java/lang/Throwable".equals(type))
					caughtThrowableHandlers.add(handler);
				super.visitTryCatchBlock(start, end, handler, type);
			}

			public void visitLabel(Label label)
			{
				super.visitLabel(label);
				if(traceCaughtThrowables && caughtThrowableHandlers.remove(label))
					pendingThrowableTrace = true;
			}

			public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack)
			{
				super.visitFrame(type, nLocal, local, nStack, stack);
				insertPendingThrowableTrace();
			}

			public void visitVarInsn(int opcode, int variable)
			{
				insertPendingThrowableTrace();
				super.visitVarInsn(opcode, variable);
			}

			private void insertPendingThrowableTrace()
			{
				if(!pendingThrowableTrace) return;
				pendingThrowableTrace = false;
				mv.visitInsn(DUP);
				mv.visitLdcInsn(ownerClass.replace('/', '.') + "." + methodName + methodDescriptor);
				mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MIDletLoader",
						"logCaughtMidletThrowable", "(Ljava/lang/Throwable;Ljava/lang/String;)V");
				insertedThrowableTrace = true;
			}

			public void visitCode()
			{
				super.visitCode();
				if (!cloneDisplayIsolation || !"lib/MIDlet".equals(ownerClass)
						|| !"()V".equals(methodDescriptor)) return;

				if ("start".equals(methodName) || "showDisplay".equals(methodName)
						|| "resumeRequest".equals(methodName))
				{
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MobilePlatform",
							"activateDisplayOwner", "(Ljava/lang/Object;)V");
				}
				else if ("notifyPaused".equals(methodName) || "notifyDestroyed".equals(methodName))
				{
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MobilePlatform",
							"deactivateDisplayOwner", "(Ljava/lang/Object;)V");
				}
			}

			public void visitInsn(int opcode)
			{
				insertPendingThrowableTrace();
				if (cloneDisplayIsolation && "lib/MIDlet".equals(ownerClass)
						&& "notifyDestroyed".equals(methodName)
						&& "()V".equals(methodDescriptor) && opcode == RETURN)
				{
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MobilePlatform",
							"destroyDisplayOwner", "(Ljava/lang/Object;)V");
				}
				if (cloneDisplayIsolation && "lib/Display".equals(ownerClass)
						&& "<init>".equals(methodName)
						&& "(Llib/MIDlet;)V".equals(methodDescriptor)
						&& opcode == RETURN)
				{
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ALOAD, 1);
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MobilePlatform",
							"registerDisplayOwner", "(Ljava/lang/Object;Ljava/lang/Object;)V");
				}
				super.visitInsn(opcode);
			}

			public void visitMethodInsn(int opcode, String owner, String name, String desc)
			{
				if (opcode == INVOKESTATIC && "java/lang/Thread".equals(owner)
						&& "sleep".equals(name)
						&& ("(J)V".equals(desc) || "(JI)V".equals(desc)))
				{
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/RuntimeTiming",
							"sleep", desc);
					return;
				}
				if (opcode == INVOKESTATIC && "java/lang/System".equals(owner)
						&& "currentTimeMillis".equals(name) && "()J".equals(desc))
				{
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/RuntimeTiming",
							"currentTimeMillis", "()J");
					return;
				}
				if (opcode == INVOKESTATIC && "java/lang/System".equals(owner)
						&& "nanoTime".equals(name) && "()J".equals(desc))
				{
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/RuntimeTiming",
							"nanoTime", "()J");
					return;
				}
				if (opcode == INVOKEVIRTUAL && "java/lang/Object".equals(owner)
						&& "wait".equals(name)
						&& ("(J)V".equals(desc) || "(JI)V".equals(desc)))
				{
					String helperDesc = "(Ljava/lang/Object;" + desc.substring(1);
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/RuntimeTiming",
							"waitOn", helperDesc);
					return;
				}
				if (opcode == INVOKEVIRTUAL && "java/util/Timer".equals(owner))
				{
					String helper = null;
					String helperDesc = null;
					if ("schedule".equals(name)
							&& "(Ljava/util/TimerTask;J)V".equals(desc))
					{
						helper = "schedule";
						helperDesc = "(Ljava/util/Timer;Ljava/util/TimerTask;J)V";
					}
					else if ("schedule".equals(name)
							&& "(Ljava/util/TimerTask;JJ)V".equals(desc))
					{
						helper = "schedule";
						helperDesc = "(Ljava/util/Timer;Ljava/util/TimerTask;JJ)V";
					}
					else if ("schedule".equals(name)
							&& "(Ljava/util/TimerTask;Ljava/util/Date;)V".equals(desc))
					{
						helper = "schedule";
						helperDesc = "(Ljava/util/Timer;Ljava/util/TimerTask;Ljava/util/Date;)V";
					}
					else if ("schedule".equals(name)
							&& "(Ljava/util/TimerTask;Ljava/util/Date;J)V".equals(desc))
					{
						helper = "schedule";
						helperDesc = "(Ljava/util/Timer;Ljava/util/TimerTask;Ljava/util/Date;J)V";
					}
					else if ("scheduleAtFixedRate".equals(name)
							&& "(Ljava/util/TimerTask;JJ)V".equals(desc))
					{
						helper = "scheduleAtFixedRate";
						helperDesc = "(Ljava/util/Timer;Ljava/util/TimerTask;JJ)V";
					}
					else if ("scheduleAtFixedRate".equals(name)
							&& "(Ljava/util/TimerTask;Ljava/util/Date;J)V".equals(desc))
					{
						helper = "scheduleAtFixedRate";
						helperDesc = "(Ljava/util/Timer;Ljava/util/TimerTask;Ljava/util/Date;J)V";
					}
					if (helper != null)
					{
						mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/RuntimeTiming",
								helper, helperDesc);
						return;
					}
				}
				if (cloneClassSpace != null && !staticMethod
						&& cloneClassSpace.isFactory(ownerClass.replace('/', '.'))
						&& opcode == INVOKESTATIC && "java/lang/Class".equals(owner)
						&& "forName".equals(name)
						&& "(Ljava/lang/String;)Ljava/lang/Class;".equals(desc))
				{
					mv.visitVarInsn(ALOAD, 0);
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MIDletLoader",
							"resolveCloneClass", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Class;");
					return;
				}
				if (cloneDisplayIsolation && "lib/Display".equals(ownerClass)
						&& opcode == INVOKEVIRTUAL
						&& "javax/microedition/lcdui/Display".equals(owner))
				{
					String helper = null;
					String helperDescriptor = null;
					if ("setCurrent".equals(name)
							&& "(Ljavax/microedition/lcdui/Displayable;)V".equals(desc))
					{
						helper = "setCurrentIfOwnerActive";
						helperDescriptor = "(Ljavax/microedition/lcdui/Display;Ljavax/microedition/lcdui/Displayable;Ljava/lang/Object;)V";
					}
					else if ("setCurrent".equals(name)
							&& "(Ljavax/microedition/lcdui/Alert;Ljavax/microedition/lcdui/Displayable;)V".equals(desc))
					{
						helper = "setCurrentIfOwnerActive";
						helperDescriptor = "(Ljavax/microedition/lcdui/Display;Ljavax/microedition/lcdui/Alert;Ljavax/microedition/lcdui/Displayable;Ljava/lang/Object;)V";
					}
					else if ("setCurrentItem".equals(name)
							&& "(Ljavax/microedition/lcdui/Item;)V".equals(desc))
					{
						helper = "setCurrentItemIfOwnerActive";
						helperDescriptor = "(Ljavax/microedition/lcdui/Display;Ljavax/microedition/lcdui/Item;Ljava/lang/Object;)V";
					}
					else if ("callSerially".equals(name) && "(Ljava/lang/Runnable;)V".equals(desc))
					{
						helper = "callSeriallyIfOwnerActive";
						helperDescriptor = "(Ljavax/microedition/lcdui/Display;Ljava/lang/Runnable;Ljava/lang/Object;)V";
					}
					if (helper != null)
					{
						mv.visitVarInsn(ALOAD, 0);
						mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/MobilePlatform",
								helper, helperDescriptor);
						return;
					}
				}

				if(opcode == INVOKEVIRTUAL && name.equals("getResourceAsStream") && owner.equals("java/lang/Class"))
				{
					mv.visitMethodInsn(INVOKESTATIC, "org/recompile/mobile/Mobile", name, "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;");
				}
				else
				{
					mv.visitMethodInsn(opcode, owner, name, desc);
				}
			}

			public void visitMaxs(int maxStack, int maxLocals)
			{
				if(insertedThrowableTrace) maxStack += 2;
				if (cloneDisplayIsolation
						&& ("lib/MIDlet".equals(ownerClass) || "lib/Display".equals(ownerClass)
						|| cloneClassSpace != null && cloneClassSpace.isFactory(ownerClass.replace('/', '.'))))
					maxStack += 4;
				super.visitMaxs(maxStack, maxLocals);
			}
		}
	}
}
