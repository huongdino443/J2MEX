import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

public final class MIDletPlatformRequestSmoke {
    private static final class ProbeMidlet extends MIDlet {
        @Override protected void destroyApp(boolean unconditional)
                throws MIDletStateChangeException { }
        @Override protected void pauseApp() { }
        @Override protected void startApp() throws MIDletStateChangeException { }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        MobilePlatform platform = new MobilePlatform(8, 8);
        Mobile.setPlatform(platform);
        AtomicReference<String> requestedUrl = new AtomicReference<>();
        platform.setExternalUrlHandler(requestedUrl::set);

        boolean mustExit = new ProbeMidlet().platformRequest(
                "https://example.org/path?q=1");
        check(!mustExit, "Opening a browser URL must not require the MIDlet to exit");
        check("https://example.org/path?q=1".equals(requestedUrl.get()),
                "The external URL handler must receive the original URL");

        expectUnsupported("file:///private/data");
        expectUnsupported("javascript:alert(1)");
        System.out.println("MIDlet platformRequest smoke passed");
    }

    private static void expectUnsupported(String url) throws Exception {
        try {
            new ProbeMidlet().platformRequest(url);
            throw new AssertionError("Unsupported URL was accepted: " + url);
        } catch (ConnectionNotFoundException expected) {
            // Unsupported schemes must not be handed to Android.
        }
    }
}
