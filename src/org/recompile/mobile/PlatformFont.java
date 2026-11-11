/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTIBILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
*/
package org.recompile.mobile;

import javax.microedition.lcdui.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class PlatformFont {
	private Graphics2D gc;
	private static final Map<String, java.awt.Font> loadedSystemFonts =
			new HashMap<String, java.awt.Font>();
	private final int face;
	private final int style;
	private final int pointSize;
	private final java.awt.Font originalAwtFont;
	private final java.awt.Font fallbackAwtFont;
	private final Graphics2D monospaceMetricsGc;
	private int ascentOffset;

	public java.awt.Font awtFont;

	public PlatformFont(Font font) {
		face = font.getFace();
		style = font.getStyle();
		pointSize = font.getPointSize();
		originalAwtFont = createOriginalAwtFont(font);
		awtFont = originalAwtFont;
		fallbackAwtFont = createFallbackAwtFont(font);
		gc = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
		gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		gc.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		gc.setFont(awtFont);
		if (face == Font.FACE_MONOSPACE) {
			monospaceMetricsGc = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
					.createGraphics();
			monospaceMetricsGc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			monospaceMetricsGc.setFont(awtFont);
		} else {
			monospaceMetricsGc = null;
		}
		ascentOffset = gc.getFontMetrics(awtFont).getAscent()
				- gc.getFontMetrics(originalAwtFont).getAscent();
		System.out.println("MIDP font face=" + face + " pointSize=" + pointSize
				+ " -> " + systemFontProperty(face) + "="
				+ System.getProperty(systemFontProperty(face), "not provided")
				+ "; AWT=" + awtFont.getFontName());
	}

	private static java.awt.Font createOriginalAwtFont(Font font) {
		int style = font.getStyle() & (java.awt.Font.BOLD | java.awt.Font.ITALIC);
		int pointSize = font.getPointSize();
		java.awt.Font base = loadSystemFont(font.getFace());
		if (base == null && font.getFace() == Font.FACE_MONOSPACE)
			base = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, pointSize);
		if (base == null)
			base = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, pointSize);
		java.awt.Font initial = base.deriveFont(style, (float)pointSize);
		java.awt.Font adjusted = fitLineHeight(initial, pointSize);
		return applyUnderline(adjusted, font.getStyle());
	}

	private static java.awt.Font createFallbackAwtFont(Font font) {
		if (font.getFace() == Font.FACE_MONOSPACE) return null;
		String path = System.getProperty("freej2me.android.defaultFallbackFont", "").trim();
		String primaryPath = System.getProperty("freej2me.android.defaultFont", "").trim();
		if (path.isEmpty() || path.equals(primaryPath)) return null;
		java.awt.Font base;
		try {
			base = loadFontFile(path);
		} catch (IllegalStateException error) {
			System.out.println("Configured fallback font unavailable: " + error.getMessage());
			return null;
		}
		java.awt.Font fallback = fitLineHeight(base.deriveFont(
				font.getStyle() & (java.awt.Font.BOLD | java.awt.Font.ITALIC),
				(float)font.getPointSize()), font.getPointSize());
		return applyUnderline(fallback, font.getStyle());
	}

	private static java.awt.Font fitLineHeight(java.awt.Font font, int pointSize) {
		Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		float lineHeight = font.getLineMetrics("Hg", graphics.getFontRenderContext()).getHeight();
		graphics.dispose();
		float adjustedSize = lineHeight <= 0f
				? pointSize : pointSize * (float)pointSize / lineHeight;
		return font.deriveFont(adjustedSize);
	}

	private static java.awt.Font applyUnderline(java.awt.Font font, int midpStyle) {
		if ((midpStyle & Font.STYLE_UNDERLINED) == 0) return font;
		Map<TextAttribute, Object> attributes = new Hashtable<TextAttribute, Object>(1);
		attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
		return font.deriveFont(attributes);
	}

	private static String systemFontProperty(int face) {
		return face == Font.FACE_MONOSPACE
				? "freej2me.android.monospaceFont" : "freej2me.android.defaultFont";
	}

	private static synchronized java.awt.Font loadSystemFont(int face) {
		String path = System.getProperty(systemFontProperty(face));
		if (path == null || path.trim().isEmpty()) return null;
		return loadFontFile(path);
	}

	private static synchronized java.awt.Font loadFontFile(String path) {
		java.awt.Font loaded = loadedSystemFonts.get(path);
		if (loaded != null) return loaded;
		try (FileInputStream input = new FileInputStream(path)) {
			loaded = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, input);
			loadedSystemFonts.put(path, loaded);
			System.out.println("Configured AWT font loaded: " + loaded.getFontName() + " from " + path);
			return loaded;
		} catch (IOException | FontFormatException error) {
			throw new IllegalStateException("Cannot load configured AWT font: " + path, error);
		}
	}

	public int stringWidth(String text) {
		if (text == null || text.length() == 0) return 0;
		if (AndroidFontBridge.isConnected())
			return (int)AndroidFontBridge.measure(face, style, pointSize, text);
		if (face == Font.FACE_MONOSPACE)
			return monospaceMetricsGc.getFontMetrics(awtFont).stringWidth(text);
		return (int)stringAdvance(text);
	}

	public int charWidth(char character) {
		return stringWidth(Character.toString(character));
	}

	public int charsWidth(char[] characters, int offset, int length) {
		if (characters == null || length <= 0) return 0;
		int start = Math.max(0, offset);
		int end = (int)Math.min((long)characters.length, (long)offset + length);
		if (end <= start) return 0;
		return stringWidth(new String(characters, start, end - start));
	}

	public float stringAdvance(String text) {
		if (text == null || text.length() == 0) return 0f;
		if (AndroidFontBridge.isConnected())
			return AndroidFontBridge.measure(face, style, pointSize, text);
		return textLayout(gc, text).getAdvance();
	}

	public boolean hasAndroidFontBridge() { return AndroidFontBridge.isConnected(); }
	public boolean isMonospaceFace() { return face == Font.FACE_MONOSPACE; }

	public AndroidFontBridge.RenderedText renderAndroidText(String text) {
		return AndroidFontBridge.render(face, style, pointSize, text);
	}

	public int getHeight() {
		AndroidFontBridge.FontMetrics metrics = AndroidFontBridge.metrics(face, style, pointSize);
		return metrics == null ? gc.getFontMetrics().getHeight() : metrics.height();
	}

	public int getBaselinePosition() {
		AndroidFontBridge.FontMetrics metrics = AndroidFontBridge.metrics(face, style, pointSize);
		return metrics == null ? gc.getFontMetrics().getAscent() : metrics.ascent;
	}

	public int getAscentOffset() { return ascentOffset; }

	public TextLayout textLayout(Graphics2D target, String text) {
		if (fallbackAwtFont == null || text == null || text.isEmpty())
			return new TextLayout(text, awtFont, target.getFontRenderContext());
		AttributedString attributed = new AttributedString(text);
		int runStart = 0;
		int firstCodePoint = Character.codePointAt(text, 0);
		java.awt.Font runFont = fontForCodePoint(firstCodePoint);
		for (int index = Character.charCount(firstCodePoint); index < text.length();) {
			int codePoint = Character.codePointAt(text, index);
			java.awt.Font nextFont = fontForCodePoint(codePoint);
			if (nextFont != runFont) {
				attributed.addAttribute(TextAttribute.FONT, runFont, runStart, index);
				runStart = index;
				runFont = nextFont;
			}
			index += Character.charCount(codePoint);
		}
		attributed.addAttribute(TextAttribute.FONT, runFont, runStart, text.length());
		return new TextLayout(attributed.getIterator(), target.getFontRenderContext());
	}

	private java.awt.Font fontForCodePoint(int codePoint) {
		if (awtFont.canDisplay(codePoint) || fallbackAwtFont == null) return awtFont;
		if (fallbackAwtFont.canDisplay(codePoint)) return fallbackAwtFont;
		return awtFont;
	}

	public void drawString(Graphics2D target, String text, int x, int baselineY) {
		if (text == null || text.length() == 0) return;
		target.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (face == Font.FACE_MONOSPACE) {
			target.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
					RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
			target.setFont(awtFont);
			target.drawString(text, x, baselineY);
			return;
		}
		target.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		textLayout(target, text).draw(target, x, baselineY);
	}
}
