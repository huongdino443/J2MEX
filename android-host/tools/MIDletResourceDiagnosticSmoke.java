import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Confirms asset failure logs expose stage/type but never exception text. */
public final class MIDletResourceDiagnosticSmoke
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1) throw new IllegalArgumentException("Expected fixture classes directory");
		System.setProperty("freej2me.traceCaughtMidletExceptions", "false");
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, "UTF-8"));
		File temp = Files.createTempDirectory("midlet-resource-diagnostic-").toFile();
		try
		{
			File jar = new File(temp, "resource-diagnostic.jar");
			writeFixtureJar(jar, new File(args[0]));
			MobilePlatform platform = new MobilePlatform(240, 320);
			Mobile.setPlatform(platform);
			platform.dataPath = temp.getCanonicalPath() + File.separator;
			platform.setPainter(() -> { });
			platform.loader = new MIDletLoader(new URL[]{jar.toURI().toURL()});
			platform.loader.start();

			long deadline = System.currentTimeMillis() + 5000;
			while (System.currentTimeMillis() < deadline
					&& !captured.toString("UTF-8").contains("[ResourceDiag] callback="))
				Thread.sleep(20);

			String log = captured.toString("UTF-8");
			if (!log.contains("[ResourceDiag] callback=EnvironmentAssetsS34.run()V")
					|| !log.contains("file=/offline/env-s34/probe.rle")
					|| !log.contains("phase=Tải lỗi")
					|| !log.contains("stage=READ_HEADER")
					|| !log.contains("causeTypes=IOException"))
				throw new AssertionError("Missing safe resource diagnostics:\n" + log);
			if (log.contains("secret-token") || log.contains("must-not-be-logged")
					|| log.contains("[MIDlet-caught]"))
				throw new AssertionError("Sensitive exception text was logged:\n" + log);

			original.println("MIDletResourceDiagnosticSmoke: sanitized asset failure trace passed");
			System.exit(0);
		}
		finally
		{
			System.setOut(original);
		}
	}

	private static void writeFixtureJar(File jar, File classes) throws Exception
	{
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		manifest.getMainAttributes().putValue("MIDlet-Name", "Resource diagnostic probe");
		manifest.getMainAttributes().putValue("MIDlet-Version", "1.0");
		manifest.getMainAttributes().putValue("MIDlet-Vendor", "FreeJ2ME test");
		manifest.getMainAttributes().putValue("MIDlet-1",
				"Resource diagnostic probe,,MIDletResourceDiagnosticFixture");
		String[] names = {
				"MIDletResourceDiagnosticFixture.class",
				"EnvironmentAssetsS34.class",
				"HellMapLoadTrace.class"
		};
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar.toPath()), manifest))
		{
			for (String name : names)
			{
				output.putNextEntry(new JarEntry(name));
				try (InputStream input = Files.newInputStream(new File(classes, name).toPath()))
				{
					byte[] buffer = new byte[4096];
					int length;
					while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
				}
				output.closeEntry();
			}
		}
	}
}
