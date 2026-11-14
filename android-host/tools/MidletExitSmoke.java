import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public final class MidletExitSmoke {
    private static int checks;

    public static void main(String[] args) {
        MobilePlatform platform = new MobilePlatform(1, 1);
        Mobile.setPlatform(platform);
        final int[] requests = {0};
        platform.setMidletExitHandler(() -> requests[0]++);
        Fixture midlet = new Fixture();
        Display display = midlet.getDisplay();
        display.setCurrent(new Canvas() {
            protected void paint(Graphics graphics) { }
        });
        if (display.getCurrent() == null) {
            throw new AssertionError("Canvas was not made current");
        }
        checks++;

        display.setCurrent(new Canvas() {
            protected void paint(Graphics graphics) { }
            public void keyPressed(int keyCode) {
                throw new NullPointerException("test MIDlet input failure");
            }
        });
        platform.keyPressed(5);
        if (display.getCurrent() == null) {
            throw new AssertionError("MIDlet input failure stopped the runtime display");
        }
        checks++;

        display.setCurrent(null);
        if (display.getCurrent() != null
                || platform.getLCD().getRGB(0, 0) != 0xFF000000) {
            throw new AssertionError("null current screen did not clear the display");
        }
        checks++;

        midlet.notifyDestroyed();
        if (requests[0] != 1) {
            throw new AssertionError("notifyDestroyed did not request host shutdown exactly once");
        }
        checks++;

        if (platform.requestMidletExit() != true || requests[0] != 2) {
            throw new AssertionError("MIDlet exit handler was not reusable");
        }
        checks++;

        System.out.println("MidletExitSmoke: " + checks + " checks passed");
    }

    private static final class Fixture extends MIDlet {
        protected void destroyApp(boolean unconditional) { }
        protected void pauseApp() { }
        protected void startApp() { }
    }
}
