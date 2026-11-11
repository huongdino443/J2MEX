import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.microedition.lcdui.*;
import org.recompile.mobile.*;

/** Bounded reproduction of a lazy player initializer requesting repaint. */
public final class CanvasReentrancySmoke {
    private static int checks;
    private static class ReentrantCanvas extends Canvas {
        int depth, maxDepth, constructions, paints;
        boolean initialized;
        final CountDownLatch deferred = new CountDownLatch(1);
        protected synchronized void paint(Graphics g) {
            depth++;
            maxDepth = Math.max(maxDepth, depth);
            paints++;
            try {
                if (!initialized) {
                    constructions++;
                    // Bound the old failure instead of exhausting JVM stacks/threads.
                    if (constructions < 8) {
                        repaint();
                        repaint(1, 1, 2, 2);
                        serviceRepaints();
                    }
                    initialized = true;
                }
                g.setColor(0x123456);
                g.fillRect(0, 0, getWidth(), getHeight());
            } finally { depth--; }
        }
        public void repaint() {
            super.repaint();
            if (paints > 1) deferred.countDown();
        }
    }
    public static void main(String[] args) throws Exception {
        MobilePlatform platform = new MobilePlatform(24, 32);
        Mobile.setPlatform(platform);
        Display display = new Display();
        AtomicInteger frames = new AtomicInteger();
        platform.setPainter(frames::incrementAndGet);
        ReentrantCanvas canvas = new ReentrantCanvas();
        display.setCurrent(canvas);
        require(canvas.maxDepth == 1, "paint must not recurse; depth=" + canvas.maxDepth);
        require(canvas.constructions == 1, "lazy player must initialize only once");
        require(canvas.deferred.await(2, TimeUnit.SECONDS), "nested repaint delivered later");
        require(canvas.maxDepth == 1, "deferred partial/full repaint stays nonrecursive");
        require(frames.get() > 0, "visible frames published");
        require((platform.getLCD().getRGB(0, 0) & 0xffffff) == 0x123456, "pixel path unchanged");
        int before = canvas.paints;
        canvas.repaint();
        require(canvas.paints == before + 1, "ordinary repaint remains synchronous");
        canvas.repaint(0, 0, 4, 4);
        require(canvas.paints == before + 2, "ordinary partial repaint remains synchronous");
        int publishedBeforeService = frames.get();
        canvas.serviceRepaints();
        require(frames.get() == publishedBeforeService,
                "serviceRepaints must not republish an already completed synchronous repaint");
        display.setCurrent(new Form("Other"));
        before = frames.get();
        canvas.repaint();
        canvas.repaint(0, 0, 4, 4);
        canvas.serviceRepaints();
        require(frames.get() == before, "hidden Canvas cannot publish");
        require(display.getCurrent() != canvas, "deferred repaint cannot reactivate hidden Canvas");

        display.setCurrent(canvas);
        Canvas.setFrameRateLimit(60);
        canvas.repaint();
        long secondFrameStarted = System.nanoTime();
        canvas.repaint();
        long pacedIntervalNanos = System.nanoTime() - secondFrameStarted;
        require(pacedIntervalNanos >= 12_000_000L,
                "Android frame pacing must limit rapid repaints to at most 60 fps; interval="
                        + pacedIntervalNanos + "ns");
        Canvas.setFrameRateLimit(0);
        System.out.println("CanvasReentrancySmoke: " + checks
                + " checks passed; repaint pacing, no duplicate serviceRepaints publish, bounded lazy initialization, pixels and visibility");
    }
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
