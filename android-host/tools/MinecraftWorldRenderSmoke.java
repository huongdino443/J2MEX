import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Runs the original sample offline against temporary data, then captures its actual world renderer. */
public final class MinecraftWorldRenderSmoke {
    public static void main(String[] args) {
        try {
            run(args);
            System.exit(0); // The sample owns persistent game/LAN threads.
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("JAR and output PNG required");
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        platform.setPainter(() -> {});
        File data = Files.createTempDirectory("minecraft-m3g-smoke-").toFile();
        platform.dataPath = data.getCanonicalPath() + File.separator;
        // This is a rendering test, not a test of the runner's desktop sound device.
        org.recompile.mobile.AudioBridge.configure(new File(data, "audio"),
                (action, id, text, extra, value, number, flags) -> {});
        MIDletLoader loader = new MIDletLoader(new URL[]{new File(args[0]).toURI().toURL()});
        platform.loader = loader;
        loader.start();
        Class<?> mcType = loader.loadClass("MC");
        Object mc = mcType.getField("instance").get(null);
        Field initialized = field(mcType, "initDone");
        long deadline = System.currentTimeMillis() + 30000;
        while (!initialized.getBoolean(mc) && System.currentTimeMillis() < deadline) Thread.sleep(20);
        if (!initialized.getBoolean(mc)) throw new AssertionError("Original sample did not initialize");
        // Pause the game thread before calling its actual world-generation and render methods.
        field(mcType, "running").setBoolean(mc, false);
        Thread thread = (Thread) field(mcType, "thread").get(mc);
        thread.join(5000);
        if (thread.isAlive()) throw new AssertionError("Game thread did not stop for deterministic render test");
        mcType.getMethod("createWorld", String.class, long.class, int.class)
                .invoke(mc, "Unnamed world", 12345L, 1);
        Method getGraphics = GameCanvas.class.getDeclaredMethod("getGraphics");
        getGraphics.setAccessible(true);
        Graphics graphics = (Graphics) getGraphics.invoke(mc);
        Method render = mcType.getDeclaredMethod("render", Graphics.class);
        render.setAccessible(true);
        for (int i = 0; i < 16; i++) render.invoke(mc, graphics);
        GameCanvas.class.getMethod("flushGraphics").invoke(mc);
        javax.imageio.ImageIO.write(platform.getLCD(), "png", new File(args[1]));
        int[] pixels = new int[240 * 320];
        platform.copyFrameTo(pixels);
        HashSet<Integer> colors = new HashSet<>();
        for (int y = 70; y < 260; y++)
            for (int x = 40; x < 200; x++) colors.add(pixels[y * 240 + x] & 0xffffff);
        if (colors.size() < 100)
            throw new AssertionError("World is still nearly flat: " + colors.size() + " central colors");
        File worlds = new File(data, "jsr75-files/games/com.mojang/minecraftWorlds/Unnamed world");
        if (!worlds.isDirectory() || worlds.list() == null || worlds.list().length == 0)
            throw new AssertionError("Original sample did not save its world with a space in the name");
        System.out.println("MinecraftWorldRenderSmoke: original JAR generated/saved a world and rendered "
                + colors.size() + " central colors; screenshot " + args[1]);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
