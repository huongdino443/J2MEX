import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/** Forces linkage of the Bluetooth classes referenced by Tic-Tac-Doodle. */
public final class BluetoothJarLinkageSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected game JAR path");
        URL jar = new File(args[0]).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar},
                BluetoothJarLinkageSmoke.class.getClassLoader())) {
            for (String name : Arrays.asList("k", "n", "l")) {
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredFields();
                type.getDeclaredConstructors();
                type.getDeclaredMethods();
            }
        }
        System.out.println("BluetoothJarLinkageSmoke: Tic-Tac-Doodle Bluetooth listeners and API descriptors link");
    }
}
