import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
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
        if (args.length < 2 || args.length > 6)
            throw new IllegalArgumentException(
                    "JAR, output PNG, and optional width, height, warmup frames, measured frames required");
        int width = args.length > 2 ? positive(args[2], "width") : 240;
        int height = args.length > 3 ? positive(args[3], "height") : 320;
        int warmupFrames = args.length > 4 ? positive(args[4], "warmup frames") : 16;
        int measuredFrames = args.length > 5 ? positive(args[5], "measured frames") : 0;
        MobilePlatform platform = new MobilePlatform(width, height);
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
        Object levelRenderer = field(mcType, "lr").get(mc);
        Method pendingCount = levelRenderer.getClass().getMethod("pendingCount");
        int settleFrames = 0;
        long settleDeadline = System.currentTimeMillis() + 30000;
        while ((int) pendingCount.invoke(levelRenderer) > 0
                && settleFrames < 2048 && System.currentTimeMillis() < settleDeadline) {
            render.invoke(mc, graphics);
            settleFrames++;
        }
        int pending = (int) pendingCount.invoke(levelRenderer);
        if (pending != 0)
            throw new AssertionError("World mesh preparation did not settle: "
                    + pending + " sections remain after " + settleFrames + " frames");
        for (int i = 0; i < warmupFrames; i++) render.invoke(mc, graphics);
        pending = (int) pendingCount.invoke(levelRenderer);
        if (pending != 0)
            throw new AssertionError("World changed during warmup: " + pending + " sections remain");
        long[] renderTimes = new long[measuredFrames];
        for (int i = 0; i < measuredFrames; i++) {
            long started = System.nanoTime();
            render.invoke(mc, graphics);
            renderTimes[i] = System.nanoTime() - started;
        }
        GameCanvas.class.getMethod("flushGraphics").invoke(mc);
        javax.imageio.ImageIO.write(platform.getLCD(), "png", new File(args[1]));
        int[] pixels = new int[width * height];
        platform.copyFrameTo(pixels);
        HashSet<Integer> colors = new HashSet<>();
        for (int y = height / 4; y < height * 3 / 4; y++)
            for (int x = width / 6; x < width * 5 / 6; x++)
                colors.add(pixels[y * width + x] & 0xffffff);
        if (colors.size() < 100)
            throw new AssertionError("World is still nearly flat: " + colors.size() + " central colors");
        File worlds = new File(data, "jsr75-files/games/com.mojang/minecraftWorlds/Unnamed world");
        if (!worlds.isDirectory() || worlds.list() == null || worlds.list().length == 0)
            throw new AssertionError("Original sample did not save its world with a space in the name");
        System.out.println("MinecraftWorldRenderSmoke: original JAR rendered " + width + "x" + height
                + " with " + colors.size() + " central colors after " + settleFrames
                + " mesh-settle frames; screenshot " + args[1]);
        if (renderTimes.length > 0) {
            long total = 0;
            for (long duration : renderTimes) total += duration;
            Arrays.sort(renderTimes);
            double averageMs = total / (double) renderTimes.length / 1_000_000d;
            double medianMs = percentile(renderTimes, 0.50) / 1_000_000d;
            double p95Ms = percentile(renderTimes, 0.95) / 1_000_000d;
            System.out.printf(Locale.ROOT,
                    "Minecraft render timing %dx%d (%d frames): avg %.2f ms / %.1f FPS, median %.2f ms, p95 %.2f ms%n",
                    width, height, renderTimes.length, averageMs, 1000d / averageMs, medianMs, p95Ms);
        }
    }

    private static int positive(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1) throw new IllegalArgumentException(name + " must be positive");
        return parsed;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * percentile) - 1);
        return sorted[index];
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
