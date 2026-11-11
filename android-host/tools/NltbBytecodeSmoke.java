import java.io.File;
import java.net.URL;
import org.recompile.mobile.MIDletLoader;

public final class NltbBytecodeSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the original NLTB JAR");
        URL jar = new File(args[0]).toURI().toURL();
        MIDletLoader loader = new MIDletLoader(new URL[]{jar});
        Class<?> game = loader.loadClass("code.main.GameMidlet");
        Class<?> failing = loader.loadClass("ds");

        require(game.getClassLoader() == loader, "MIDlet loaded by FreeJ2ME classloader");
        require(failing.getClassLoader() == loader, "problematic class transformed by FreeJ2ME");
        require(game.getDeclaredMethods().length > 0, "MIDlet passes JVM bytecode verification");
        require(failing.getDeclaredMethods().length > 0, "transformed class passes JVM bytecode verification");
        System.out.println("NltbBytecodeSmoke: GameMidlet and ds transformed and verified; original JAR, no DEX");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
