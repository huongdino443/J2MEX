package org.recompile.mobile;

import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public final class SystemFontRenderingSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "expected host system sans-serif and monospace TTF paths");
        System.setProperty("freej2me.android.defaultFont", args[0]);
        System.setProperty("freej2me.android.defaultFallbackFont", args[1]);
        System.setProperty("freej2me.android.monospaceFont", args[1]);
        Font.configureScreenSize(240, 320);

        Font medium = Font.getDefaultFont();
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font large = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE);
        Font monospace = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_MEDIUM);
        require(medium.getPointSize() == 22, "JL-Mod 240x320 medium default should be 22 px");
        require(small.getPointSize() == 18, "JL-Mod 240x320 small default should be 18 px");
        require(large.getPointSize() == 26, "JL-Mod 240x320 large default should be 26 px");
        require(Math.abs(medium.getHeight() - 22) <= 1,
                "v93 AWT metrics should use the original MIDP point size");

        String text = "Tiếng Việt: Đặng, Ắn, phở, ươu, Ệ, ộ";
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            require(medium.platformFont.awtFont.canDisplay(codePoint),
                    "system font cannot display U+" + Integer.toHexString(codePoint));
            offset += Character.charCount(codePoint);
        }
        require(medium.stringWidth(text) > 0, "system font should measure Vietnamese text");
        assertMidpWidthRounding(medium, "Wireless Tanks1.21");
        assertMidpWidthRounding(medium, text);
        require(monospace.platformFont.awtFont.canDisplay(text.charAt(0)),
                "system monospace font should load");
        require(monospace.stringWidth("ii") == monospace.stringWidth("WW"),
                "monospace face should retain fixed-width metrics");
        require(monospace.charWidth('i') == monospace.charWidth('W'),
                "monospace character widths should match");
        Graphics2D monoMetrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                .createGraphics();
        int expectedMonoWidth = monoMetrics.getFontMetrics(monospace.platformFont.awtFont)
                .stringWidth("City Bloxx");
        require(monospace.stringWidth("City Bloxx") == expectedMonoWidth,
                "FACE_MONOSPACE should preserve v92 FontMetrics advances");
        monoMetrics.dispose();

        assertCentered(medium, text);
        assertCentered(medium, "trục và vật liệu xây");
        assertCentered(monospace, "City Bloxx");
        System.out.println("SystemFontRenderingSmoke: local AWT fallback, Vietnamese coverage, "
                + "HCENTER and JL-Mod 240x320 sizes passed");
    }

    private static void assertMidpWidthRounding(Font font, String text) {
        Graphics2D graphics = new PlatformImage(1, 1).getGraphics().getGraphics2D();
        try {
            require(graphics.getFontRenderContext().usesFractionalMetrics(),
                    "MIDP text measurement and drawing must preserve fractional advances");
            TextLayout layout = font.platformFont.textLayout(graphics, text);
            require(font.stringWidth(text) == (int)layout.getAdvance(),
                    "stringWidth should truncate the system-font advance like Turbo");
            char[] chars = text.toCharArray();
            require(font.charsWidth(chars, 0, chars.length) == font.stringWidth(text),
                    "charsWidth should measure the shaped run, not sum rounded glyphs");
            for (char character : new char[]{'W', 'i', ' '}) {
                TextLayout glyph = font.platformFont.textLayout(graphics,
                        Character.toString(character));
                require(font.charWidth(character) == (int)glyph.getAdvance(),
                        "charWidth should truncate the matching system-font advance");
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void assertCentered(Font font, String text) {
        PlatformImage canvas = new PlatformImage(512, 48);
        PlatformGraphics graphics = canvas.getGraphics();
        graphics.setColor(0);
        graphics.fillRect(0, 0, 512, 48);
        graphics.setColor(0x4291D6);
        graphics.setFont(font);
        graphics.drawString(text, 256, 32, Graphics.HCENTER | Graphics.BASELINE);
        BufferedImage image = graphics.getCanvas();
        int rendered = 0;
        int minimumX = image.getWidth(), maximumX = -1;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    rendered++;
                    minimumX = Math.min(minimumX, x);
                    maximumX = Math.max(maximumX, x);
                }
        require(rendered > 100, "font should render visible glyph pixels");
        require(maximumX >= minimumX
                        && Math.abs((minimumX + maximumX) / 2.0 - 256) <= 1.5,
                "HCENTER text should be centered on the requested axis");
    }

}
