import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/** Isolated from the smoke-test class path so the MIDlet loader must define it. */
public final class MIDletCaughtThrowableFixture extends MIDlet implements Runnable
{
	protected void startApp() throws MIDletStateChangeException
	{
		new Thread(this, "CaughtThrowableFixture").start();
	}

	public void run()
	{
		try
		{
			throw new NullPointerException("trace-probe");
		}
		catch(Throwable expected)
		{
			// The MIDlet loader should report this before the game swallows it.
		}
	}

	protected void pauseApp() { }
	protected void destroyApp(boolean unconditional) throws MIDletStateChangeException { }
}
