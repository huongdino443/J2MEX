import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;

/** Visual/input smoke test, not a substitute for testing a real game. */
public final class DiagnosticMIDlet extends MIDlet {
    private final TestCanvas canvas = new TestCanvas();
    protected void startApp() { Display.getDisplay(this).setCurrent(canvas); canvas.repaint(); }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static final class TestCanvas extends Canvas {
        private int key, x = 120, y = 210, revision;
        TestCanvas() { setFullScreenMode(true); }
        protected void paint(Graphics g) {
            g.setColor(0x142438); g.fillRect(0, 0, getWidth(), getHeight());
            int bar = getWidth() / 3;
            g.setColor(0xee4455); g.fillRect(0, 0, bar, 55);
            g.setColor(0x33bb77); g.fillRect(bar, 0, bar, 55);
            g.setColor(0x4488ff); g.fillRect(bar * 2, 0, getWidth() - bar * 2, 55);
            g.setColor(0xffffff);
            g.drawString("FreeJ2ME Lite", 12, 70, Graphics.TOP | Graphics.LEFT);
            g.drawString("Headless framebuffer OK", 12, 100, Graphics.TOP | Graphics.LEFT);
            g.drawString("Key: " + key + " / update: " + revision, 12, 130, Graphics.TOP | Graphics.LEFT);
            g.drawString("Touch / keypad to test", 12, 160, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xffcc44); g.fillRect(x - 10, y - 10, 20, 20);
        }
        public void keyPressed(int code) {
            key = code; revision++;
            if (code == -1) y -= 10; if (code == -2) y += 10;
            if (code == -3) x -= 10; if (code == -4) x += 10;
            repaint();
        }
        public void keyReleased(int code) { revision++; repaint(); }
        public void pointerPressed(int px, int py) { x = px; y = py; revision++; repaint(); }
        public void pointerDragged(int px, int py) { pointerPressed(px, py); }
    }
}
