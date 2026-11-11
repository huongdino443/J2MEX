import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.Permission;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.lcdui.*;
import org.recompile.mobile.*;

/** Exercise actual MIDPlay playback Canvas with an empty playlist and blocked network. */
public final class MidplayScreenSmoke {
    private static int checks;
    public static void main(String[] args) {
        try { test(args[0]); System.exit(0); }
        catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void test(String path) throws Exception {
        final AtomicInteger startedThreads = new AtomicInteger();
        final AtomicInteger networkAttempts = new AtomicInteger();
        // Keep a baseline recursion from consuming hundreds of native threads.
        System.setSecurityManager(new SecurityManager() {
            public void checkPermission(Permission permission) { }
            public void checkConnect(String host, int port) {
                networkAttempts.incrementAndGet();
                throw new SecurityException("Offline screen test");
            }
            public void checkAccess(Thread thread) {
                if (Thread.currentThread() == thread) return;
                if (startedThreads.incrementAndGet() > 24)
                    throw new SecurityException("Unbounded player initialization");
            }
        });
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        platform.dataPath = Files.createTempDirectory("midplay-rms-").toString() + File.separator;
        AtomicInteger frames = new AtomicInteger();
        platform.setPainter(frames::incrementAndGet);
        MIDletLoader loader = new MIDletLoader(new URL[]{new File(path).toURI().toURL()});
        platform.loader = loader;
        Object midlet = loader.loadClass("midplay.MIDPlay").getConstructor().newInstance();
        Class<?> aa = loader.loadClass("aa"), de = loader.loadClass("de"), bp = loader.loadClass("bp");
        Object playlist = aa.getConstructor().newInstance();
        Object navigation = de.getConstructor(javax.microedition.midlet.MIDlet.class).newInstance(midlet);
        Canvas screen = (Canvas)bp.getConstructor(String.class, aa, int.class, long.class, de)
                .newInstance("Offline playback test", playlist, 0, 0L, navigation);
        Display display = Mobile.getDisplay();
        display.setCurrent(screen);
        Thread.sleep(150);
        int before = startedThreads.get();
        require(before < 24, "actual playback UI initializes a bounded number of workers: " + before);
        for (int i = 0; i < 50; i++) {
            screen.repaint();
            screen.repaint(0, 0, 32, 32);
            screen.serviceRepaints();
        }
        require(display.getCurrent() == screen, "playback Canvas remains current");
        require(frames.get() >= 100, "actual playback UI renders repeated frames");
        require(startedThreads.get() == before, "repaint does not recreate player workers");
        Method controller = bp.getMethod("m");
        Object first = controller.invoke(screen);
        for (int i = 0; i < 20; i++)
            require(controller.invoke(screen) == first, "actual lazy controller identity remains stable");

        File audioCache = new File("build/midplay-key-audio").getCanonicalFile();
        delete(audioCache);
        require(audioCache.mkdirs(), "create fake Android audio transport cache");
        AudioBridge.configure(audioCache, (action, id, text, extra, value, number, flags) -> { });
        Player volumePlayer = Manager.createPlayer("https://midplay.invalid/smoke-track.mp3");
        volumePlayer.realize(); volumePlayer.prefetch();
        VolumeControl volume = (VolumeControl)volumePlayer.getControl("VolumeControl");
        require(volume != null, "MIDPlay-style locator player exposes VolumeControl");
        volume.setLevel(50);
        Field playerField = first.getClass().getDeclaredField("b");
        playerField.setAccessible(true); playerField.set(first, volumePlayer);
        Field appLevel = first.getClass().getDeclaredField("c");
        appLevel.setAccessible(true); appLevel.setInt(first, 50);
        Field streamBusy = first.getClass().getDeclaredField("E");
        streamBusy.setAccessible(true); streamBusy.setBoolean(first, false);
        Field volumeIndicator = bp.getDeclaredField("f");
        volumeIndicator.setAccessible(true);
        volumePlayer.start();

        platform.keyPressed(Mobile.NOKIA_UP);
        require(volume.getLevel() == 60, "MIDPlay app handles dpad-up VolumeControl itself");
        platform.keyReleased(Mobile.NOKIA_UP);
        streamBusy.setBoolean(first, true);
        volumeIndicator.setBoolean(screen, false);
        platform.keyPressed(Mobile.NOKIA_UP);
        require(volume.getLevel() == 70, "busy progressive stream uses dpad-up volume fallback");
        require(appLevel.getInt(first) == 70, "busy-key fallback updates MIDPlay's own stored level");
        require(volumeIndicator.getBoolean(screen), "busy-key fallback opens MIDPlay's actual volume indicator");
        platform.keyReleased(Mobile.NOKIA_UP);
        platform.keyPressed(Mobile.KEY_NUM8);
        require(volume.getLevel() == 60, "2/8-compatible down input lowers active player volume");
        require(appLevel.getInt(first) == 60, "down key keeps MIDPlay controller and VolumeControl synchronized");
        platform.keyReleased(Mobile.KEY_NUM8);
        platform.keyPressed(Mobile.KEY_NUM2);
        require(volume.getLevel() == 70, "2/8-compatible up input raises active player volume");
        platform.keyReleased(Mobile.KEY_NUM2);
        volumePlayer.close();
        delete(audioCache);

        display.setCurrent(new Form("Returned"));
        require(!screen.isShown(), "returning from playback hides Canvas");
        require(System.getSecurityManager() != null, "all external networking blocked for UI-only test");
        System.out.println("MidplayScreenSmoke: " + checks
                + " checks passed; real playback Canvas, stable controller/workers, frames/navigation; "
                + networkAttempts.get() + " network attempts blocked; no audible playback verification");
    }
    private static void delete(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
