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
package javax.microedition.media;

import java.io.InputStream;
import java.io.IOException;

import org.recompile.mobile.PlatformPlayer;
import org.recompile.mobile.AudioBridge;
import org.recompile.mobile.Mobile;

public final class Manager
{

	public static final String TONE_DEVICE_LOCATOR = "device://tone";


	public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException
	{
		//System.out.println("Create Player Stream "+type);
		return new PlatformPlayer(stream, type);
	}

	public static Player createPlayer(String locator) throws MediaException
	{
		System.out.println("Create Player "+locator);
		return new PlatformPlayer(locator);
	}
	
	public static String[] getSupportedContentTypes(String protocol)
	{
if(AudioBridge.isAvailable() && Mobile.sound)
{
return new String[]{"audio/midi", "audio/sp-midi", "audio/x-wav",
"audio/amr", "audio/mpeg", "audio/3gpp", "audio/mp4", "audio/ogg"};
}
return new String[]{"audio/midi", "audio/x-wav", "audio/amr", "audio/mpeg"};
	}
	
	public static String[] getSupportedProtocols(String content_type)
	{
if(AudioBridge.isAvailable() && Mobile.sound) return new String[]{"http", "https"};
return new String[]{};
	}
	
	public static void playTone(int note, int duration, int volume)
	{
if(AudioBridge.isAvailable() && Mobile.sound)
{
AudioBridge.playTone(note, duration, volume);
}
	}

}
