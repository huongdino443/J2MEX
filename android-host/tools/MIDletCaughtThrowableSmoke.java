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

/** Verifies opt-in tracing of Throwable handlers in MIDlet run callbacks. */
public final class MIDletCaughtThrowableSmoke
{
	public static void main(String[] args) throws Exception
	{
		if(args.length != 1) throw new IllegalArgumentException("Expected fixture class file");
		System.setProperty("freej2me.traceCaughtMidletExceptions", "true");
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, "UTF-8"));
		File temp = Files.createTempDirectory("midlet-caught-trace-").toFile();
		try
		{
			File jar = new File(temp, "caught-trace.jar");
			writeFixtureJar(jar, new File(args[0]));
			MobilePlatform platform = new MobilePlatform(240, 320);
			Mobile.setPlatform(platform);
			platform.dataPath = temp.getCanonicalPath() + File.separator;
			platform.setPainter(() -> { });
			platform.loader = new MIDletLoader(new URL[]{jar.toURI().toURL()});
			platform.loader.start();

			long deadline = System.currentTimeMillis() + 5000;
			while(System.currentTimeMillis() < deadline
				&& !captured.toString("UTF-8").contains("[MIDlet-caught]"))
			{
				Thread.sleep(20);
			}
			String log = captured.toString("UTF-8");
			if(!log.contains("[MIDlet-caught] MIDletCaughtThrowableFixture.run()V")
				|| !log.contains("NullPointerException: trace-probe"))
			{
				throw new AssertionError("Caught Throwable was not logged:\n" + log);
			}
			original.println("MIDletCaughtThrowableSmoke: caught run exception trace passed");
			System.exit(0);
		}
		finally
		{
			System.setOut(original);
		}
	}

	private static void writeFixtureJar(File jar, File fixtureClass) throws Exception
	{
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
		manifest.getMainAttributes().putValue("MIDlet-Name", "Caught trace probe");
		manifest.getMainAttributes().putValue("MIDlet-Version", "1.0");
		manifest.getMainAttributes().putValue("MIDlet-Vendor", "FreeJ2ME test");
		manifest.getMainAttributes().putValue("MIDlet-1",
			"Trace probe,,MIDletCaughtThrowableFixture");
		try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar.toPath()), manifest);
			InputStream input = Files.newInputStream(fixtureClass.toPath()))
		{
			output.putNextEntry(new JarEntry("MIDletCaughtThrowableFixture.class"));
			byte[] buffer = new byte[4096];
			int length;
			while((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
			output.closeEntry();
		}
	}
}
