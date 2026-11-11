import java.io.*;
import java.net.URL;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.*;
import org.recompile.mobile.*;

/** Exercises only static resource helpers, never MIDlet constructor/startApp. */
public final class EntryResourceSmoke {
    private static int checks;
    private static final class Loader extends MIDletLoader {
        Loader(URL[] urls) { super(urls); }
        Class<?> entry(String name) throws ClassNotFoundException {
            // Exactly the protected two-argument route used by MIDletLoader.start.
            return super.loadClass(name, true);
        }
    }
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        Map<String, byte[]> expected = new LinkedHashMap<String, byte[]>();
        String entry;
        try (JarFile jar = new JarFile(file)) {
            entry = jar.getManifest().getMainAttributes().getValue("MIDlet-1").split(",")[2].trim();
            for (String path : new String[]{"/mfont/number", "/mfont/tahoma_7",
                    "/mfont/tahoma_7b", "/mfont/tahoma_8b"}) {
                JarEntry resource = jar.getJarEntry(path.substring(1));
                require(resource != null, "font data exists in JAR: " + path);
                try (InputStream input = jar.getInputStream(resource)) {
                    expected.put(path, read(input));
                }
            }
        }
        MobilePlatform platform = new MobilePlatform(24, 32);
        Mobile.setPlatform(platform);
        URL[] urls = {file.toURI().toURL()};
        for (boolean startupRoute : new boolean[]{false, true}) {
            Loader loader = new Loader(urls);
            platform.loader = loader;
            Class<?> main = startupRoute ? loader.entry(entry) : loader.loadClass(entry);
            Method helper = main.getMethod("d", String.class);
            for (Map.Entry<String, byte[]> resource : expected.entrySet()) {
                try (InputStream input = (InputStream)helper.invoke(null, resource.getKey())) {
                    require(input != null, "helper returns stream");
                    require(Arrays.equals(read(input), resource.getValue()),
                            (startupRoute ? "startup" : "public") + " loader preserves font bytes");
                }
            }
            require(loader.loadClass(entry) == main, "public loader retains entry class identity");
            require(loader.entry(entry) == main, "startup loader retains entry class identity");
            require(loader.getMIDletResourceAsStream("/nonexistent-entry-resource-test") == null,
                    "missing resource remains null");
        }
        System.out.println("EntryResourceSmoke: " + checks
                + " checks passed; actual HaiTac font bytes via entry/public loader; no constructor, startApp or network");
    }
    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        return output.toByteArray();
    }
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
