import java.io.File;
import java.net.URL;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;
import org.recompile.mobile.MIDletLoader;

public final class MIDletLoaderTransformSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass a clone-style JAR path");

        URL jar = new File(args[0]).toURI().toURL();
        MIDletLoader loader = new MIDletLoader(new URL[]{jar});
        Class<?> childMidlet = Class.forName("lib.MIDlet", false, loader);
        Class<?> childDisplay = Class.forName("lib.Display", false, loader);
        Class.forName("quyetdaik.a", false, loader).getDeclaredMethods();
        require(childMidlet.getClassLoader() == loader, "child MIDlet is loaded from JAR");
        require(childDisplay.getClassLoader() == loader, "child Display is loaded from JAR");
        require(MIDlet.class.isAssignableFrom(Class.forName("quyetdaik.quyetdaik", false, loader)),
                "launcher remains a standard MIDlet");
        childDisplay.getDeclaredConstructor(childMidlet);
        childDisplay.getDeclaredMethod("setCurrent", Displayable.class);
        Class<?> childAlert = Class.forName("lib.Alert", false, loader);
        childDisplay.getDeclaredMethod("setCurrent", childAlert, Displayable.class);

        System.out.println("MIDletLoaderTransformSmoke: clone JAR classes verified without starting the game");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
