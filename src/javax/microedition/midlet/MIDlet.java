/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package javax.microedition.midlet;

import java.util.HashMap;
import java.net.URI;
import java.net.URISyntaxException;

import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import javax.microedition.pki.*;
import javax.microedition.rms.*;
import org.recompile.mobile.Mobile;

public abstract class MIDlet
{

	public static HashMap<String, String> properties;

	private Display display = new Display();

	protected MIDlet()
	{
		System.out.println("Create MIDlet");
	}


	public final int checkPermission(String permission)
	{
		// 0 - denied; 1 - allowed; -1 unknown
		System.out.println("checkPermission: "+permission);
		return -1;
	}

	protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

	public String getAppProperty(String key)
	{
		return properties.get(key);
	}

	public static void initAppProperties(HashMap<String, String> initProperties)
	{
		properties = initProperties;
	}

	public final void notifyDestroyed()
	{
		System.out.println("MIDlet sent Destroyed Notification");
		if (Boolean.getBoolean("freej2me.startup.diagnostics"))
		{
			StackTraceElement[] trace = new Throwable().getStackTrace();
			for (int i = 1; i < trace.length && i < 7; i++)
				System.out.println("[midlet-exit] " + trace[i]);
		}
		org.recompile.mobile.MobilePlatform platform = Mobile.getPlatform();
		if (platform != null && platform.requestMidletExit()) return;
		System.exit(0);
	}

	public final void notifyPaused() { }

	protected abstract void pauseApp();

	public final boolean platformRequest(String URL) throws ConnectionNotFoundException
	{
		if (URL == null || URL.length() == 0 || URL.length() > 8192)
			throw new ConnectionNotFoundException("Invalid external URL");
		try
		{
			URI uri = new URI(URL);
			String scheme = uri.getScheme();
			if (scheme == null || uri.getHost() == null
					|| (!"http".equalsIgnoreCase(scheme)
							&& !"https".equalsIgnoreCase(scheme)))
				throw new ConnectionNotFoundException("Only HTTP and HTTPS URLs can be opened externally");
		}
		catch (URISyntaxException invalidUrl)
		{
			throw new ConnectionNotFoundException("Invalid external URL");
		}

		org.recompile.mobile.MobilePlatform platform = Mobile.getPlatform();
		if (platform == null) throw new ConnectionNotFoundException("Android URL handler is unavailable");
		try
		{
			platform.openExternalUrl(URL);
		}
		catch (java.io.IOException error)
		{
			throw new ConnectionNotFoundException("Could not send URL to Android");
		}
		return false;
	}

	public final void resumeRequest() { }

	protected abstract void startApp() throws MIDletStateChangeException;

	public Display getDisplay() { return display; }

}
