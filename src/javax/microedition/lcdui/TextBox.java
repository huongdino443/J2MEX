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

import org.recompile.mobile.Mobile;
import org.recompile.mobile.PlatformGraphics;


public class TextBox extends Screen
{
	private String text;
	private int max;
	private int constraints;
	private String mode;
	private int caretPosition;
	private int scrollLines;


	public TextBox(String Title, String value, int maxSize, int Constraints)
	{
		if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
		super.setTitle(Title);
		max = maxSize;
		text = limit(value == null ? "" : value);
		caretPosition = text.length();
		constraints = Constraints;
	}

	private String limit(String value)
	{
		if (value.length() > max) throw new IllegalArgumentException("Text exceeds maxSize");
		return value;
	}

	public void delete(int offset, int length)
	{
		if (offset < 0 || length < 0 || offset + length > text.length())
			throw new StringIndexOutOfBoundsException();
		text = text.substring(0, offset) + text.substring(offset + length);
		caretPosition = offset;
		render();
	}

	public int getCaretPosition() { return caretPosition; }

	public int getChars(char[] data)
	{
		if (data == null || data.length < text.length()) throw new ArrayIndexOutOfBoundsException();
		text.getChars(0, text.length(), data, 0);
		return text.length();
	}

	public int getConstraints() { return constraints; }

	public int getMaxSize() { return max; }

	public String getString() { return text; }

	public void insert(char[] data, int offset, int length, int position)
	{
		if (data == null || offset < 0 || length < 0 || offset + length > data.length
				|| position < 0 || position > text.length()) throw new StringIndexOutOfBoundsException();
		insert(new String(data, offset, length), position);
	}

	public void insert(String src, int position)
	{
		if (src == null || position < 0 || position > text.length())
			throw new StringIndexOutOfBoundsException();
		int available = Math.max(0, max - text.length());
		if (src.length() > available) throw new IllegalArgumentException("Text exceeds maxSize");
		String inserted = src;
		text = text.substring(0, position) + inserted + text.substring(position);
		caretPosition = position + inserted.length();
		render();
	}

	public void setChars(char[] data, int offset, int length)
	{
		if (data == null) { setString(""); return; }
		if (data == null || offset < 0 || length < 0 || offset + length > data.length)
			throw new StringIndexOutOfBoundsException();
		setString(new String(data, offset, length));
	}

	public void setConstraints(int Constraints) { constraints = Constraints;  }

	public void setInitialInputMode(String characterSubset) { mode = characterSubset; }

	public int setMaxSize(int maxSize)
	{
		if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
		max = maxSize;
		if (text.length() > max) text = text.substring(0, max);
		caretPosition = Math.min(caretPosition, text.length());
		render();
		return max;
	}

	public void setString(String value)
	{
		text = limit(value == null ? "" : value);
		caretPosition = text.length();
		render();
	}

	public void setTicker(Ticker tick) { super.setTicker(tick); }

	public void setTitle(String s) { super.setTitle(s); }

	public int size() { return text.length(); }

	public void keyPressed(int key)
	{
		if (handleCommandKey(key)) return;
		if (key == Mobile.NOKIA_UP || key == Mobile.KEY_NUM2)
		{
			scrollLines = Math.max(0, scrollLines - 1); render(); return;
		}
		if (key == Mobile.NOKIA_DOWN || key == Mobile.KEY_NUM8)
		{
			scrollLines = Math.min(Math.max(0, wrappedLineCount(text, width - 16) - 1), scrollLines + 1);
			render(); return;
		}
		if ((key == Mobile.KEY_NUM5 || key == Mobile.NOKIA_SOFT3) && Mobile.getPlatform() != null)
		{
			if ((constraints & TextField.UNEDITABLE) != 0) return;
			Mobile.getPlatform().requestTextInput(getTitle(), text, max, constraints);
		}
	}

	public void updateTextInput(String value) { setString(value); }

	public void notifySetCurrent() { render(); }

	public void pointerPressed(int x, int y)
	{
		if (!handlePointerCommand(x, y)) keyPressed(Mobile.NOKIA_SOFT3);
	}

	public void renderItems()
	{
		ensurePlatformImage();
		PlatformGraphics gc = platformImage.getGraphics();
		gc.reset();
		gc.setFont(Font.getDefaultFont());
		drawHeader(gc);
		int top = contentTop() + 4;
		int bottom = height - 24;
		gc.setColor(0xFFFFFF);
		gc.fillRect(4, top, width - 8, Math.max(16, bottom - top));
		gc.setColor(0x000000);
		gc.drawRect(4, top, width - 8, Math.max(16, bottom - top));
		gc.setClip(5, top + 1, width - 10, Math.max(16, bottom - top - 1));
		String shownText = (constraints & TextField.PASSWORD) == 0 ? text : text.replaceAll("(?s).", "*");
		if (text.isEmpty()) gc.drawString("Nhập văn bản…", 8, top + 4, Graphics.LEFT);
		else drawWrappedText(gc, shownText, 8, top + 4 - scrollLines * 16, width - 16, 0x000000);
		gc.setClip(0, 0, width, height);
		drawSoftkeys(gc);
		flushIfCurrent();
	}

protected void render()
	{
if (isShown()) super.render();
	}
}
