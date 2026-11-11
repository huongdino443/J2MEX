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
package org.recompile.mobile;

import java.net.URL;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import java.awt.event.KeyEvent;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Graphics3D;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;


/*

	Mobile Platform

*/

public class MobilePlatform
{

	private static final Object NO_ACTIVE_DISPLAY_OWNER = new Object();
	private static final IdentityHashMap<Object, Object> displayOwners =
			new IdentityHashMap<Object, Object>();
	private static Object activeDisplayOwner = NO_ACTIVE_DISPLAY_OWNER;

	/**
	 * Tracks the active child MIDlet for JARs that bundle a multi-MIDlet launcher.
	 * Their proxy Display instances share FreeJ2ME's single framebuffer.
	 */
	public static synchronized void resetDisplayOwners()
	{
		displayOwners.clear();
		activeDisplayOwner = NO_ACTIVE_DISPLAY_OWNER;
	}

	public static synchronized void registerDisplayOwner(Object displayProxy, Object owner)
	{
		if (displayProxy != null && owner != null && !CloneClassSpace.isRetired(owner))
			displayOwners.put(displayProxy, owner);
	}

	public static synchronized void activateDisplayOwner(Object owner)
	{
		if (owner != null && !CloneClassSpace.isRetired(owner)) activeDisplayOwner = owner;
	}

	public static synchronized void deactivateDisplayOwner(Object owner)
	{
		if (owner != null && activeDisplayOwner == owner)
			activeDisplayOwner = NO_ACTIVE_DISPLAY_OWNER;
	}

	public static synchronized boolean isDisplayOwnerActive(Object displayProxy)
	{
		Object owner = displayOwners.get(displayProxy);
		return owner != null && activeDisplayOwner == owner && !CloneClassSpace.isRetired(owner);
	}

	public static void destroyDisplayOwner(Object owner)
	{
		CloneClassSpace.retire(owner);
		synchronized (MobilePlatform.class)
		{
			if (activeDisplayOwner == owner) activeDisplayOwner = NO_ACTIVE_DISPLAY_OWNER;
			Iterator<Map.Entry<Object, Object>> entries = displayOwners.entrySet().iterator();
			while (entries.hasNext()) if (entries.next().getValue() == owner) entries.remove();
		}
	}

	public static void setCurrentIfOwnerActive(Display display,
			javax.microedition.lcdui.Displayable next, Object displayProxy)
	{
		if (isDisplayOwnerActive(displayProxy)) display.setCurrent(next);
	}

	public static void setCurrentIfOwnerActive(Display display,
			javax.microedition.lcdui.Alert alert,
			javax.microedition.lcdui.Displayable next, Object displayProxy)
	{
		if (isDisplayOwnerActive(displayProxy)) display.setCurrent(alert, next);
	}

	public static void setCurrentItemIfOwnerActive(Display display,
			javax.microedition.lcdui.Item item, Object displayProxy)
	{
		if (isDisplayOwnerActive(displayProxy)) display.setCurrentItem(item);
	}

	public static void callSeriallyIfOwnerActive(final Display display,
			final Runnable task, final Object displayProxy)
	{
		display.callSerially(new Runnable()
		{
			public void run()
			{
				if (isDisplayOwnerActive(displayProxy)) task.run();
			}
		});
	}

public interface TextInputHandler
{
void request(int requestId, String title, String value, int maxSize, int constraints);
default void request(int requestId, String title, String value, int maxSize, int constraints,
		String[] commands, int defaultCommand)
{
request(requestId, title, value, maxSize, constraints);
}
default void request(int requestId, String title, String value, int maxSize, int constraints,
		String[] commands, int[] commandTypes, int defaultCommand)
{
request(requestId, title, value, maxSize, constraints, commands, defaultCommand);
}
void dismiss();
}

	private PlatformImage lcd;
	private PlatformGraphics gc;
	public int lcdWidth;
	public int lcdHeight;

	public MIDletLoader loader;

	public Runnable painter;

	public String dataPath = "";

	public int keyState = 0;
private final Object gameKeyStateLock = new Object();
private int heldGameKeyState = 0;
private final Map<Integer,Integer> heldGameKeyMasks = new HashMap<Integer,Integer>();

private volatile TextInputHandler textInputHandler;
private volatile Runnable midletExitHandler;
private int textInputRequestId;
private javax.microedition.lcdui.Displayable textInputTarget;
private javax.microedition.lcdui.TextField textInputField;
private javax.microedition.lcdui.Command[] textInputCommands = new javax.microedition.lcdui.Command[0];

	public MobilePlatform(int width, int height)
	{
		lcdWidth = width;
		lcdHeight = height;

		lcd = new PlatformImage(width, height);
		gc = lcd.getGraphics();

		Mobile.setGraphics3D(new Graphics3D());

		painter = new Runnable()
		{
			public void run()
			{
				// Placeholder //
			}
		};
	}

	public synchronized void resizeLCD(int width, int height)
	{
		lcdWidth = width;
		lcdHeight = height;

		lcd = new PlatformImage(width, height);
		gc = lcd.getGraphics();
	}

	public BufferedImage getLCD()
	{
		return lcd.getCanvas();
	}

	public synchronized void copyFrameTo(int[] pixels)
	{
		int required = lcdWidth * lcdHeight;
		if (pixels == null || pixels.length < required)
			throw new IllegalArgumentException("Framebuffer destination is too small");
BufferedImage frame=lcd.getCanvas();
if(frame.getType()==BufferedImage.TYPE_INT_ARGB) {
int[] source=((DataBufferInt)frame.getRaster().getDataBuffer()).getData();
System.arraycopy(source,0,pixels,0,required);
} else frame.getRGB(0,0,lcdWidth,lcdHeight,pixels,0,lcdWidth);
	}

	public void setPainter(Runnable r)
	{
		painter = r;
	}

	public synchronized void clearDisplay()
	{
		gc.setClip(0, 0, lcdWidth, lcdHeight);
		gc.setColor(0x000000);
		gc.fillRect(0, 0, lcdWidth, lcdHeight);
		painter.run();
	}

	public void setMidletExitHandler(Runnable handler)
	{
		midletExitHandler = handler;
	}

	public boolean requestMidletExit()
	{
		Runnable handler = midletExitHandler;
		if (handler == null) return false;
		handler.run();
		return true;
	}

public void keyPressed(int keycode)
	{
		FontInputDiagnostic.input("key-down", keycode, -1, diagnosticDisplayableName());
		updateKeyState(keycode, 1);
		if (Mobile.getDisplay() == null || Mobile.getDisplay().getCurrent() == null) return;
		javax.microedition.lcdui.Displayable current = Mobile.getDisplay().getCurrent();
		if (current.dispatchSoftkey(keycode)) return;

		int volumeDelta = midplayVolumeDelta(current, keycode);
		MidplayVolumeKeys volume = volumeDelta == 0 ? null : MidplayVolumeKeys.capture(current);
		current.keyPressed(keycode);
		if (volume != null && Mobile.getDisplay().getCurrent() == current) volume.finish(volumeDelta > 0);
	}

	private int midplayVolumeDelta(javax.microedition.lcdui.Displayable current, int keycode)
	{
		if (!(current instanceof Canvas) || loader == null
				|| !"midplay.MIDPlay".equals(loader.getMIDletClassName())) return 0;
		if (keycode == Mobile.NOKIA_UP || keycode == Mobile.KEY_NUM2) return 10;
		if (keycode == Mobile.NOKIA_DOWN || keycode == Mobile.KEY_NUM8) return -10;
		return 0;
	}

	public void keyReleased(int keycode)
	{
		FontInputDiagnostic.input("key-up", keycode, -1, diagnosticDisplayableName());
		updateKeyState(keycode, 0);
		if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null)
			Mobile.getDisplay().getCurrent().keyReleased(keycode);
	}

	public void keyRepeated(int keycode)
	{
		FontInputDiagnostic.input("key-repeat", keycode, -1, diagnosticDisplayableName());
		if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null)
			Mobile.getDisplay().getCurrent().keyRepeated(keycode);
	}

	public void pointerDragged(int x, int y)
	{
if (!Canvas.isPointerInputEnabled()) return;
		if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null)
			Mobile.getDisplay().getCurrent().pointerDragged(x, y);
	}

	public void pointerPressed(int x, int y)
	{
if (!Canvas.isPointerInputEnabled()) return;
		FontInputDiagnostic.input("pointer-down", x, y, diagnosticDisplayableName());
		if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null)
			Mobile.getDisplay().getCurrent().pointerPressed(x, y);
	}

	public void pointerReleased(int x, int y)
	{
if (!Canvas.isPointerInputEnabled()) return;
		FontInputDiagnostic.input("pointer-up", x, y, diagnosticDisplayableName());
		if (Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() != null)
			Mobile.getDisplay().getCurrent().pointerReleased(x, y);
	}

public void setTextInputHandler(TextInputHandler handler) { textInputHandler = handler; }

public synchronized void requestTextInput(String title, String value, int maxSize, int constraints)
{
if ((constraints & javax.microedition.lcdui.TextField.UNEDITABLE) != 0) return;
TextInputHandler handler = textInputHandler;
if (handler == null || Mobile.getDisplay() == null) return;
textInputTarget = Mobile.getDisplay().getCurrent();
textInputField = null;
textInputCommands = textInputTarget instanceof javax.microedition.lcdui.TextBox
		? textInputTarget.getCommands().toArray(new javax.microedition.lcdui.Command[0])
		: new javax.microedition.lcdui.Command[0];
if (textInputTarget instanceof javax.microedition.lcdui.Form) {
javax.microedition.lcdui.Item focused = ((javax.microedition.lcdui.Form)textInputTarget).getFocusedItem();
if (focused instanceof javax.microedition.lcdui.TextField)
textInputField = (javax.microedition.lcdui.TextField)focused;
}
String[] labels = new String[textInputCommands.length];
int[] commandTypes = new int[textInputCommands.length];
int defaultCommand = -1;
for (int i = 0; i < textInputCommands.length; i++) {
javax.microedition.lcdui.Command command = textInputCommands[i];
labels[i] = command.getLabel();
commandTypes[i] = command.getCommandType();
if (defaultCommand < 0 && command.getCommandType() == javax.microedition.lcdui.Command.OK)
defaultCommand = i;
}
if (defaultCommand < 0) {
for (int i = 0; i < textInputCommands.length; i++) {
int type = textInputCommands[i].getCommandType();
if (type == javax.microedition.lcdui.Command.SCREEN) {
defaultCommand = i; break;
}
}
}
handler.request(++textInputRequestId, title, value, maxSize, constraints,
		labels, commandTypes, defaultCommand);
}

public synchronized void dismissTextInput()
{
textInputRequestId++;
textInputTarget = null;
textInputField = null;
textInputCommands = new javax.microedition.lcdui.Command[0];
TextInputHandler handler = textInputHandler;
if (handler != null) handler.dismiss();
}

public synchronized void updateTextInput(int requestId, String value)
{
applyTextInput(requestId, value, -1, false);
}

public synchronized void submitTextInput(int requestId, String value, int commandIndex)
{
applyTextInput(requestId, value, commandIndex, true);
}

private void applyTextInput(int requestId, String value, int commandIndex, boolean dispatchCommand)
{
if (requestId != textInputRequestId || textInputTarget == null || Mobile.getDisplay() == null
        || Mobile.getDisplay().getCurrent() != textInputTarget) return;
if (dispatchCommand && (commandIndex < -1 || commandIndex >= textInputCommands.length)) return;
javax.microedition.lcdui.Displayable target = textInputTarget;
javax.microedition.lcdui.TextField field = textInputField;
javax.microedition.lcdui.Command command = dispatchCommand && commandIndex >= 0
		? textInputCommands[commandIndex] : null;
textInputTarget = null;
textInputField = null;
textInputCommands = new javax.microedition.lcdui.Command[0];
if (target instanceof javax.microedition.lcdui.Form && field != null)
((javax.microedition.lcdui.Form)target).applyTextInput(field, value);
else target.updateTextInput(value);
if (command != null) target.dispatchTextInputCommand(command);
}

public synchronized void cancelTextInput(int requestId)
{
if (requestId != textInputRequestId) return;
textInputTarget = null;
textInputField = null;
textInputCommands = new javax.microedition.lcdui.Command[0];
}

public boolean handleBackPressed()
{
if (Mobile.getDisplay() == null || Mobile.getDisplay().getCurrent() == null) return false;
javax.microedition.lcdui.Displayable current = Mobile.getDisplay().getCurrent();
if (current instanceof Canvas) return false;
current.handleBackPressed();
return true;
}

	private void updateKeyState(int key, int val)
	{
int mask=gameKeyMask(key);
if(mask==0) return;
synchronized(gameKeyStateLock) {
if(val==1) {
if(!heldGameKeyMasks.containsKey(key)) {
heldGameKeyMasks.put(key,mask);
heldGameKeyState|=mask;
}
keyState|=mask;
} else if(heldGameKeyMasks.remove(key)!=null) {
heldGameKeyState=0;
for(int heldMask:heldGameKeyMasks.values()) heldGameKeyState|=heldMask;
}
}
}

public int getGameKeyStates()
{
synchronized(gameKeyStateLock) {
int state=keyState|heldGameKeyState;
keyState=0;
return state;
}
}

private int gameKeyMask(int key)
{
int mask=0;
		switch (key)
		{
case Canvas.UP:
case Mobile.NOKIA_UP:
			case Mobile.KEY_NUM2: mask = GameCanvas.UP_PRESSED; break;
case Canvas.LEFT:
case Mobile.NOKIA_LEFT:
			case Mobile.KEY_NUM4: mask = GameCanvas.LEFT_PRESSED; break;
case Canvas.RIGHT:
case Mobile.NOKIA_RIGHT:
			case Mobile.KEY_NUM6: mask = GameCanvas.RIGHT_PRESSED; break;
case Canvas.DOWN:
case Mobile.NOKIA_DOWN:
			case Mobile.KEY_NUM8: mask = GameCanvas.DOWN_PRESSED; break;
case Canvas.FIRE:
case Mobile.NOKIA_FIRE:
			case Mobile.KEY_NUM5: mask = GameCanvas.FIRE_PRESSED; break;
case Canvas.GAME_A:
			case Mobile.KEY_NUM1: mask = GameCanvas.GAME_A_PRESSED; break;
case Canvas.GAME_B:
			case Mobile.KEY_NUM3: mask = GameCanvas.GAME_B_PRESSED; break;
case Canvas.GAME_C:
			case Mobile.KEY_NUM7: mask = GameCanvas.GAME_C_PRESSED; break;
case Canvas.GAME_D:
			case Mobile.KEY_NUM9: mask = GameCanvas.GAME_D_PRESSED; break;
		}
return mask;
	}

	private String diagnosticDisplayableName()
	{
		if (Mobile.getDisplay() == null || Mobile.getDisplay().getCurrent() == null) return null;
		return Mobile.getDisplay().getCurrent().getClass().getName();
	}

/*
	******** Jar Loading ********
*/

	public boolean loadJar(String jarurl)
	{
		try
		{
			URL jar = new URL(jarurl);
			loader = new MIDletLoader(new URL[]{jar});
			return true;
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			e.printStackTrace();
			return false;
		}

	}

	public void runJar()
	{
		try
		{
			loader.start();
		}
		catch (Exception e)
		{
			System.out.println("Error Running Jar");
			e.printStackTrace();
		}
	}

/*
	********* Graphics ********
*/

	public synchronized void flushGraphics(Image img, int x, int y, int width, int height)
	{
		gc.flushGraphics(img, x, y, width, height);

		painter.run();

		//System.gc();
	}

	public synchronized void repaint(Image img, int x, int y, int width, int height)
	{
		gc.flushGraphics(img, x, y, width, height);

		painter.run();

		//System.gc();
	}

}
