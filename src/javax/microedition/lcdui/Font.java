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
import org.recompile.mobile.PlatformFont;

public final class Font
{
	public static final int FACE_MONOSPACE = 32;
	public static final int FACE_PROPORTIONAL = 64;
	public static final int FACE_SYSTEM = 0;

	public static final int FONT_INPUT_TEXT = 1;
	public static final int FONT_STATIC_TEXT = 0;

	public static final int SIZE_LARGE = 16;
	public static final int SIZE_MEDIUM = 0;
	public static final int SIZE_SMALL = 8;

	public static final int STYLE_BOLD = 1;
	public static final int STYLE_ITALIC = 2;
	public static final int STYLE_PLAIN = 0;
	public static final int STYLE_UNDERLINED = 4;

	private static final int[] SCREEN_SIZES = {128, 176, 220, 320};
	private static final int[] FONT_SIZES = {
		9, 13, 15,
		13, 15, 20,
		15, 18, 22,
		18, 22, 26
	};
	private static int screenDimension = 320;

	private int face;
	private int style;
	private int size;

	private static Font defaultFont = new Font(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);

	public PlatformFont platformFont;

	private Font(int fontFace, int fontStyle, int fontSize)
	{
		face = fontFace;
		style = fontStyle;
		size = fontSize;
		platformFont = new PlatformFont(this);
	}

	public static void configureScreenSize(int width, int height)
	{
		screenDimension = Math.max(1, Math.max(width, height));
		defaultFont = new Font(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
	}

	public int charsWidth(char[] ch, int offset, int length)
	{
		if (ch == null) return 0;
		int start = Math.max(0, offset);
		int end = (int)Math.min((long)ch.length, (long)offset + length);
		if (end <= start) return 0;
		return platformFont.stringWidth(new String(ch, start, end - start));
	}

public int charWidth(char ch) { return platformFont.charWidth(ch); }

	public int getBaselinePosition() { return platformFont.getBaselinePosition(); }

	public static Font getDefaultFont() { return defaultFont; }

	public int getFace() { return face; }

	public static Font getFont(int fontSpecifier) { return defaultFont; }

	public static Font getFont(int face, int style, int size) { return new Font(face, style, size); }

	public int getHeight() { return platformFont.getHeight(); }

	public int getSize() { return size; }

	public int getPointSize() { return convertSize(size); }

	public int getStyle() { return style; }

	public boolean isBold() { return (style & STYLE_BOLD) == STYLE_BOLD; }

	public boolean isItalic() { return (style & STYLE_ITALIC) == STYLE_ITALIC; }

	public boolean isPlain() { return style == STYLE_PLAIN; }

	public boolean isUnderlined() { return (style & STYLE_UNDERLINED) == STYLE_UNDERLINED; }

	public int stringWidth(String str)
	{
		return platformFont.stringWidth(str);
	}

	public int substringWidth(String str, int offset, int len)
	{
		return stringWidth(str.substring(offset, offset+len));
	}

	private int convertSize(int size)
	{
		int type;
		switch (size)
		{
			case SIZE_SMALL: type = 0; break;
			case SIZE_MEDIUM: type = 1; break;
			case SIZE_LARGE: type = 2; break;
			default: type = 0; break;
		}
		for (int i = 0; i < SCREEN_SIZES.length; i++)
			if (screenDimension <= SCREEN_SIZES[i]) return FONT_SIZES[i * 3 + type];
		return FONT_SIZES[FONT_SIZES.length - 3 + type];
	}
}
