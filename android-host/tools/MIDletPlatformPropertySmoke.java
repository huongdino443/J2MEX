import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.recompile.mobile.MIDletLoader;

public final class MIDletPlatformPropertySmoke {
    public static void main(String[] args) throws Exception {
        File nokiaJar = createJar("Nokia*,SonyEricsson*");
        File otherJar = createJar("SonyEricsson*");
        File genericJar = createJar(null);
        try {
            assertPlatform(nokiaJar, "Nokia6233/05.10");
            assertPlatform(otherJar, "j2me");
            assertPlatform(genericJar, "j2me");
            System.out.println("MIDletPlatformPropertySmoke: Nokia allowlist and generic fallback passed");
        } finally {
            Files.deleteIfExists(nokiaJar.toPath());
            Files.deleteIfExists(otherJar.toPath());
            Files.deleteIfExists(genericJar.toPath());
        }
    }

    private static File createJar(String nokiaPlatforms) throws Exception {
        File jar = File.createTempFile("midlet-platform-", ".jar");
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("MIDlet-Name", "Platform Probe");
        attributes.putValue("MIDlet-1", "Platform Probe,,ProbeMidlet");
        if (nokiaPlatforms != null) attributes.putValue("Nokia-Platform", nokiaPlatforms);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar.toPath()), manifest)) {
            output.flush();
        }
        return jar;
    }

    private static void assertPlatform(File jar, String expected) throws Exception {
        try (MIDletLoader loader = new MIDletLoader(new URL[]{jar.toURI().toURL()})) {
            if (!expected.equals(System.getProperty("microedition.platform")))
                throw new AssertionError("System property expected " + expected + " got "
                        + System.getProperty("microedition.platform"));
            Field field = MIDletLoader.class.getDeclaredField("properties");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> properties = (Map<String, String>) field.get(loader);
            if (!expected.equals(properties.get("microedition.platform")))
                throw new AssertionError("MIDlet app property expected " + expected + " got "
                        + properties.get("microedition.platform"));
        }
    }
}
