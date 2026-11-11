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
package javax.microedition.lcdui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;

import javax.microedition.lcdui.Image;

import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

import org.recompile.mobile.Mobile;

public class Display
{
	public static final int LIST_ELEMENT = 1;
	public static final int CHOICE_GROUP_ELEMENT = 2;
	public static final int ALERT = 3;
	public static final int COLOR_BACKGROUND = 0;
	public static final int COLOR_FOREGROUND = 1;
	public static final int COLOR_HIGHLIGHTED_BACKGROUND = 2;
	public static final int COLOR_HIGHLIGHTED_FOREGROUND = 3;
	public static final int COLOR_BORDER = 4;
	public static final int COLOR_HIGHLIGHTED_BORDER = 5;


	private volatile Displayable current;

	private static Display display;

	public Vector<Runnable> serialCalls;

	private Timer timer;

	private SerialCallTimerTask timertask;

	public Display()
	{
		display = this;

		Mobile.setDisplay(this);

		serialCalls = new Vector<Runnable>(16);
		timer = new Timer("FreeJ2ME-LCDUI", true);
		timertask = new SerialCallTimerTask();
		timer.schedule(timertask, 0, 17);
	}

	public void callSerially(Runnable r)
	{
		if (r == null) throw new NullPointerException("runnable");
		synchronized (serialCalls) { serialCalls.add(r); }
	}
	private class SerialCallTimerTask extends TimerTask
	{
		public void run()
		{
			Runnable next = null;
			synchronized (serialCalls)
			{
				if (!serialCalls.isEmpty()) next = serialCalls.remove(0);
			}
			if (next != null) try { next.run(); } catch (Throwable error) { error.printStackTrace(); }
		}
	}

	public boolean flashBacklight(int duration) { return true; }

	public int getBestImageHeight(int imageType)
	{
		switch(imageType)
		{
			case LIST_ELEMENT:
			case CHOICE_GROUP_ELEMENT: return choiceImageSide();
			case ALERT: return Mobile.getPlatform().lcdHeight;
		}
		return Mobile.getPlatform().lcdHeight;
	}

	public int getBestImageWidth(int imageType)
	{
		if (imageType == LIST_ELEMENT || imageType == CHOICE_GROUP_ELEMENT)
			return choiceImageSide();
		return Mobile.getPlatform().lcdWidth;
	}

	private int choiceImageSide()
	{
		return Math.max(1, Math.min(32, Math.min(Mobile.getPlatform().lcdWidth,
				Mobile.getPlatform().lcdHeight)));
	}

	public int getBorderStyle(boolean highlighted) { return 0; }

	public int getColor(int colorSpecifier)
	{
		switch(colorSpecifier)
		{
			case COLOR_BACKGROUND: return 0;
			case COLOR_FOREGROUND: return 0xFFFFFF;
			case COLOR_HIGHLIGHTED_BACKGROUND: return 0xFFFFFF;
			case COLOR_HIGHLIGHTED_FOREGROUND: return 0;
			case COLOR_BORDER: return 0x808080;
			case COLOR_HIGHLIGHTED_BORDER: return 0xFFFFFF;
		}
		return 0;
	}

	public Displayable getCurrent() { return current; }

	public static Display getDisplay(MIDlet m) { return display; }

	public boolean isColor() { return true; }

	public int numAlphaLevels() { return 256; }

	public int numColors() { return 16777216; }

	public void setCurrent(Displayable next)
	{
		if (next == null)
		{
			// J2ME Loader accepts a null current screen; some MIDlets do this
			// immediately before destroyApp() and notifyDestroyed().
			Displayable old = current;
			if (old != null) old.hideNotify();
			if (Mobile.getPlatform() != null)
			{
				Mobile.getPlatform().dismissTextInput();
			}
			current = null;
			NativeUi.transition(null);
			if (Mobile.getPlatform() != null) Mobile.getPlatform().clearDisplay();
			return;
		}
		try
		{
			Displayable old = current;
			if (old == next)
			{
				next.notifySetCurrent();
				return;
			}
			if (next instanceof Alert && ((Alert)next).getNextScreen() == null)
				((Alert)next).setNextScreen(old);
			if (old != null) old.hideNotify();
			if (Mobile.getPlatform() != null) Mobile.getPlatform().dismissTextInput();
			current = next;
NativeUi.transition(next);
			next.ensurePlatformImage();
			next.showNotify();
			next.notifySetCurrent();
if (!NativeUi.supports(next) && !NativeUi.usesNativeTextBox(next))
Mobile.getPlatform().flushGraphics(next.platformImage, 0, 0, next.width, next.height);
			if (next instanceof TextBox && (((TextBox)next).getConstraints() & TextField.UNEDITABLE) == 0)
			{
				TextBox box = (TextBox)next;
				Mobile.getPlatform().requestTextInput(box.getTitle(), box.getString(),
						box.getMaxSize(), box.getConstraints());
			}
		}
		catch (Throwable e)
		{
			System.out.println("Problem with setCurrent(next)");
			e.printStackTrace();
		}
	}

	public void setCurrent(Alert alert, Displayable next)
	{
		try
		{
			alert.setNextScreen(next);
			setCurrent(alert);
		}
		catch (Exception e)
		{
			System.out.println("Problem with setCurrent(alert, next)");
			e.printStackTrace();
		}
	}

	public void setCurrentItem(Item item)
	{
		if (item == null) throw new NullPointerException("item");
		Form owner = item.getOwner();
		if (owner != null)
		{
			if (current != owner) setCurrent(owner);
			owner.focusItem(item);
		}
	}

	public boolean handleBackPressed()
	{
		return current != null && current.handleBackPressed();
	}

	public boolean vibrate(int duration)
	{
		//System.out.println("Vibrate");
		return true;
	}

}
