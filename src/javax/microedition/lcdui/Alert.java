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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.recompile.mobile.Mobile;
import org.recompile.mobile.PlatformGraphics;


public class Alert extends Screen
{

	public static final Command DISMISS_COMMAND = new Command("OK", Command.OK, 0);

	public static final int FOREVER = -2;


	private String message;

	private Image image;

	private AlertType type;

	private int timeout = FOREVER;

	private Gauge indicator;

	private Displayable nextScreen = null;

	private Timer timeoutTimer;
	private int scrollLines;

	public Alert(String title)
	{
		setTitle(title);
		message = "";
		addCommand(DISMISS_COMMAND);
		super.setCommandListener(defaultListener);
	}

	public Alert(String title, String alertText, Image alertImage, AlertType alertType)
	{
		setTitle(title);
		message = alertText == null ? "" : alertText;
		setImage(alertImage);
		setType(alertType);
		setTimeout(getDefaultTimeout());
		addCommand(Alert.DISMISS_COMMAND);
		super.setCommandListener(defaultListener);
	}

	public int getDefaultTimeout() { return Alert.FOREVER; }

	public int getTimeout() { return timeout; }

	public void setTimeout(int time)
	{
		if (time < 0 && time != FOREVER) throw new IllegalArgumentException("Invalid alert timeout");
		timeout = time;
	}

	public AlertType getType() { return type; }

	public void setType(AlertType t) { type = t; if (isShown()) renderItems(); }

	public String getString() { return message; }

	public void setString(String text)
	{
		message = text == null ? "" : text;
		if (isShown()) renderItems();
	}

	public Image getImage() { return image; }

	public void setImage(Image img) { image = img; if (isShown()) renderItems(); }

public void setIndicator(Gauge gauge) {
if (indicator != null) indicator.nativeAlert = null;
indicator = gauge;
if (gauge != null) gauge.nativeAlert = this;
if (isShown()) renderItems();
}

	public Gauge getIndicator() { return indicator; }

	public void addCommand(Command cmd)
	{
		if (cmd != null && cmd != DISMISS_COMMAND && getCommands().size() == 1
				&& getCommands().get(0) == DISMISS_COMMAND)
			super.removeCommand(DISMISS_COMMAND);
		super.addCommand(cmd);
	}

	public void removeCommand(Command cmd)
	{
		super.removeCommand(cmd);
		if (getCommands().isEmpty()) super.addCommand(DISMISS_COMMAND);
	}

	public void setCommandListener(CommandListener listener)
	{
		if (listener == null)
		{
			listener = defaultListener;
		}
		super.setCommandListener(listener);
	}

	public CommandListener defaultListener = new CommandListener()
	{
		public void commandAction(Command cmd, Displayable next)
		{
			dismiss();
		}
	};

	public void setNextScreen(Displayable next) { nextScreen = next; }

	public Displayable getNextScreen() { return nextScreen; }

	private void dismiss()
	{
		Display display = Mobile.getDisplay();
		if (display != null && nextScreen != null) display.setCurrent(nextScreen);
	}

	public void showNotify()
	{
		if (timeout <= 0 || timeout == FOREVER || commands.size() > 1) return;
		if (timeoutTimer != null) timeoutTimer.cancel();
		timeoutTimer = new Timer(true);
		timeoutTimer.schedule(new TimerTask()
		{
			public void run()
			{
				Display display = Mobile.getDisplay();
				if (display != null) display.callSerially(new Runnable()
				{
					public void run() { if (isShown()) dismiss(); }
				});
			}
		}, timeout);
	}

	public void hideNotify()
	{
		if (timeoutTimer != null) { timeoutTimer.cancel(); timeoutTimer = null; }
	}

	public void keyPressed(int key)
	{
		if (handleCommandKey(key)) return;
		if (key == Mobile.NOKIA_UP || key == Mobile.KEY_NUM2)
		{
			scrollLines = Math.max(0, scrollLines - 1); renderItems(); return;
		}
		if (key == Mobile.NOKIA_DOWN || key == Mobile.KEY_NUM8)
		{
			scrollLines = Math.min(Math.max(0, wrappedLineCount(message, width - 16) - 1), scrollLines + 1);
			renderItems(); return;
		}
		if (key == Mobile.NOKIA_SOFT3 || key == Mobile.KEY_NUM5) doDefaultCommand();
	}

	public boolean handleBackPressed()
	{
		if (super.handleBackPressed()) return true;
		if (commandlistener == defaultListener) dismiss();
		return true;
	}

	public void pointerPressed(int x, int y) { handlePointerCommand(x, y); }

	public void renderItems()
	{
if (NativeUi.present(this)) return;
		ensurePlatformImage();
		PlatformGraphics gc = platformImage.getGraphics();
		gc.reset();
		gc.setFont(Font.getDefaultFont());
		drawHeader(gc);
		gc.setClip(0, contentTop(), width, Math.max(1, height - contentTop() - 20));
		int y = contentTop() + 8 - scrollLines * 16;
		if (image != null)
		{
			gc.drawImage(image, (width - image.getWidth()) / 2, y, Graphics.LEFT | Graphics.TOP);
			y += image.getHeight() + 6;
		}
		drawWrappedText(gc, message, 8, y, width - 16, 0x000000);
		if (indicator != null && indicator.getMaxValue() > 0)
		{
			int barWidth = width - 24;
			int barY = Math.max(y + 28, height / 2);
			gc.drawRect(12, barY, barWidth, 10);
			int filled = Math.max(0, Math.min(barWidth - 2,
					(int)((barWidth - 2L) * indicator.getValue() / indicator.getMaxValue())));
			gc.fillRect(13, barY + 1, filled, 9);
		}
		gc.setClip(0, 0, width, height);
		drawSoftkeys(gc);
		flushIfCurrent();
	}

	public void notifySetCurrent() { renderItems(); }

}
