import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import org.recompile.mobile.MobilePlatform;

/** Ensures the framebuffer sender can only observe complete, synchronized flushes. */
public final class FramebufferSnapshotSmoke {
    private static final int WIDTH = 96;
    private static final int HEIGHT = 64;
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    public static void main(String[] args) throws Exception {
        MobilePlatform platform = new MobilePlatform(WIDTH, HEIGHT);
        Image red = solid(0xFF0000);
        Image blue = solid(0x0000FF);
        platform.flushGraphics(red, 0, 0, WIDTH, HEIGHT);

        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int index = 0; index < 3000; index++)
                    platform.flushGraphics((index & 1) == 0 ? blue : red, 0, 0, WIDTH, HEIGHT);
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                done.set(true);
            }
        }, "framebuffer-smoke-writer");
        Thread reader = new Thread(() -> {
            try {
                int[] pixels = new int[WIDTH * HEIGHT];
                start.await();
                int snapshots = 0;
                while (!done.get() || snapshots < 200) {
                    platform.copyFrameTo(pixels);
                    int frameColor = pixels[0];
                    if (frameColor != RED && frameColor != BLUE)
                        throw new AssertionError("Unexpected framebuffer color: " + Integer.toHexString(frameColor));
                    for (int pixel : pixels) {
                        if (pixel != frameColor)
                            throw new AssertionError("Snapshot mixed pixels from different frames");
                    }
                    snapshots++;
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "framebuffer-smoke-reader");

        writer.start();
        reader.start();
        start.countDown();
        writer.join();
        reader.join();
        if (failure.get() != null) throw new AssertionError("Framebuffer snapshot failed", failure.get());
        System.out.println("FramebufferSnapshotSmoke: 1 check passed; snapshots contain complete frames");
    }

    private static Image solid(int color) {
        Image image = Image.createImage(WIDTH, HEIGHT);
        Graphics graphics = image.getGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        return image;
    }
}
