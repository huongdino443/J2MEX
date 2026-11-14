import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.recompile.mobile.MIDletLoader;

public final class MIDletManifestPropertySmoke {
    public static void main(String[] args) throws Exception {
        File jarFile = File.createTempFile("midlet-manifest-", ".jar");
        try {
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.putValue("Manifest-Version", "1.0");
            attributes.putValue("MIDlet-1", "Test Game,/icon.png,example.TestMidlet");
            attributes.putValue("MIDlet-Name", "Test Game");
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarFile.toPath()), manifest)) {
                output.flush();
            }
            try (MIDletLoader loader = new MIDletLoader(new URL[]{jarFile.toURI().toURL()})) {
                Field propertiesField = MIDletLoader.class.getDeclaredField("properties");
                propertiesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, String> properties = (Map<String, String>) propertiesField.get(loader);
                String expected = "Test Game,/icon.png,example.TestMidlet";
                if (!expected.equals(properties.get("MIDlet-1"))) {
                    throw new AssertionError("MIDlet-1 was not retained as an app property");
                }
                if (!"example.TestMidlet".equals(loader.getMIDletClassName())) {
                    throw new AssertionError("MIDlet-1 still must identify the entry class");
                }
            }
            System.out.println("MIDletManifestPropertySmoke: MIDlet-1 property and entry class passed");
        } finally {
            Files.deleteIfExists(jarFile.toPath());
        }
    }
}
