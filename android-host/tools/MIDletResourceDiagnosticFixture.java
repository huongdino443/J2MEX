import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/** Isolated JAR fixture for sanitized optional asset-load diagnostics. */
public final class MIDletResourceDiagnosticFixture extends MIDlet
{
	protected void startApp() throws MIDletStateChangeException
	{
		new Thread(new EnvironmentAssetsS34(), "ResourceDiagnosticFixture").start();
	}

	protected void pauseApp() { }
	protected void destroyApp(boolean unconditional) throws MIDletStateChangeException { }
}
