import java.io.IOException;

/** Public names and diagnostic fields match the optional game asset-loader API. */
public final class EnvironmentAssetsS34 implements Runnable
{
	public static volatile String currentFile = "/offline/env-s34/probe.rle";
	public static volatile String phase = "Tải lỗi";

	public void run()
	{
		try
		{
			throw new IOException("secret-token=must-not-be-logged");
		}
		catch (Throwable expected)
		{
			// The loader should log only file, phase, stage and exception types.
		}
	}
}
