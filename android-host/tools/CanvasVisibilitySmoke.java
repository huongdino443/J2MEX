import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Reproduces clone loops that call repaint + serviceRepaints on hidden canvases. */
public final class CanvasVisibilitySmoke {
    private static int checks;

    private static final class CloneCanvas extends Canvas {
        final int color;
        int keys, touches, drags, releases;
        CloneCanvas(int color) { this.color = color; }
        protected void paint(Graphics graphics) {
            graphics.setColor(color);
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
        public void keyPressed(int key) { keys++; }
        public void pointerPressed(int x, int y) { touches++; }
        public void pointerDragged(int x, int y) { drags++; }
        public void pointerReleased(int x, int y) { releases++; }
    }

    private static final class BufferedCanvas extends GameCanvas {
        BufferedCanvas() { super(false); }
        void fill(int color) {
            Graphics graphics = getGraphics();
            graphics.setColor(color);
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        MobilePlatform platform = new MobilePlatform(24, 32);
        Mobile.setPlatform(platform);
        AtomicInteger frames = new AtomicInteger();
        platform.setPainter(frames::incrementAndGet);
        Display display = new Display();
        CloneCanvas[] clones = new CloneCanvas[5];
        for (int i = 0; i < clones.length; i++)
            clones[i] = new CloneCanvas(0x101010 * (i + 1));

        for (int selected = 0; selected < clones.length; selected++) {
            CloneCanvas active = clones[selected];
            display.setCurrent(active);
            int before = frames.get();
            active.repaint();
            active.repaint(0, 0, 8, 8);
            active.serviceRepaints();
            require(frames.get() == before + 2,
                    "serviceRepaints does not republish completed synchronous repaints");
            for (CloneCanvas hidden : clones) {
                if (hidden == active) continue;
                before = frames.get();
                hidden.repaint();
                hidden.repaint(0, 0, 8, 8);
                require(frames.get() == before, "hidden repaint does not publish");
                hidden.serviceRepaints();
                require(display.getCurrent() == active, "Display.current remains the selected clone");
                require(frames.get() == before,
                        "Hidden Canvas serviceRepaints must not publish a frame");
                require(pixel(platform) == active.color, "hidden clone cannot overwrite selected pixels");
            }
            platform.keyPressed(49);
            platform.pointerPressed(1, 1);
            require(active.keys == 1 && active.touches == 1, "input stays with selected clone");
        }

        CloneCanvas touchProbe = clones[0];
        display.setCurrent(touchProbe);
        Canvas.setPointerInputEnabled(false);
        require(!touchProbe.hasPointerEvents() && !touchProbe.hasPointerMotionEvents(),
                "disabled touch mode reports no pointer capability");
        int pressesBeforeDisabledInput = touchProbe.touches;
        int dragsBeforeDisabledInput = touchProbe.drags;
        int releasesBeforeDisabledInput = touchProbe.releases;
        platform.pointerPressed(2, 2);
        platform.pointerDragged(3, 3);
        platform.pointerReleased(4, 4);
        require(touchProbe.touches == pressesBeforeDisabledInput
                        && touchProbe.drags == dragsBeforeDisabledInput
                        && touchProbe.releases == releasesBeforeDisabledInput,
                "disabled touch mode suppresses pointer callbacks");
        Canvas.setPointerInputEnabled(true);
        require(touchProbe.hasPointerEvents() && touchProbe.hasPointerMotionEvents(),
                "enabled touch mode reports pointer and motion capability");
        platform.pointerPressed(5, 5);
        require(touchProbe.touches == pressesBeforeDisabledInput + 1,
                "enabled touch mode delivers pointer input");

        // All five game loops remain alive, but only the selected one may publish.
        display.setCurrent(clones[4]);
        int before = frames.get();
        Thread[] loops = new Thread[5];
        for (int i = 0; i < loops.length; i++) {
            final CloneCanvas clone = clones[i];
            loops[i] = new Thread(() -> {
                for (int frame = 0; frame < 100; frame++) {
                    clone.repaint();
                    clone.serviceRepaints();
                }
            }, "clone-render-" + i);
            loops[i].start();
        }
        for (Thread loop : loops) {
            loop.join(5000);
            require(!loop.isAlive(), "clone render loop finishes");
        }
        require(frames.get() == before + 100,
                "only active clone repaint publishes during concurrent loops");
        require(pixel(platform) == clones[4].color, "concurrent hidden loops cannot replace active image");

        BufferedCanvas game = new BufferedCanvas();
        game.fill(0x00AA77);
        before = frames.get();
        game.flushGraphics();
        game.flushGraphics(0, 0, 8, 8);
        require(frames.get() == before, "hidden GameCanvas flushes do not publish");
        display.setCurrent(game);
        before = frames.get();
        Canvas.setFrameRateLimit(60);
        game.flushGraphics();
        long secondFlushStartedAt = System.nanoTime();
        game.flushGraphics(0, 0, 8, 8);
        require(frames.get() == before + 2, "visible GameCanvas retains full and regional flush");
        require(System.nanoTime() - secondFlushStartedAt >= 12_000_000L,
                "GameCanvas flushes obey the configured frame limit");
        Canvas.setFrameRateLimit(0);
        require(pixel(platform) == 0x00AA77, "visible GameCanvas buffer reaches display");
        display.setCurrent(new Form("Launcher"));
        before = frames.get();
        game.flushGraphics();
        game.flushGraphics(0, 0, 8, 8);
        game.serviceRepaints();
        for (CloneCanvas clone : clones) clone.serviceRepaints();
        require(frames.get() == before, "background canvases cannot draw over launcher/Form");
        System.out.println("CanvasVisibilitySmoke: " + checks
                + " checks passed; 5 concurrent clone loops, pixels/input, Canvas/GameCanvas visibility");
    }

    private static int pixel(MobilePlatform platform) {
        return platform.getLCD().getRGB(1, 1) & 0xFFFFFF;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
