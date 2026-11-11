package org.recompile.mobile;

import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public final class DirectGraphicsAlphaSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Image image = Image.createImage(2, 1);
        Graphics graphics = image.getGraphics();
        graphics.setColor(0xFFFFFF);
        graphics.fillRect(0, 0, 2, 1);

        PlatformGraphics direct = (PlatformGraphics) graphics;
        direct.setARGBColor(0x80FF0000);
        graphics.fillRect(0, 0, 1, 1);

        BufferedImage pixels = image.platformImage.getCanvas();
        require(direct.getAlphaComponent() == 0x80, "DirectGraphics alpha getter changed");
        require(pixels.getRGB(0, 0) == 0xFFFF7F7F,
                "setARGBColor must blend the requested alpha over the destination");
        require(pixels.getRGB(1, 0) == 0xFFFFFFFF, "setARGBColor altered an untouched pixel");

        graphics.setColor(0x0000FF);
        require(direct.getAlphaComponent() == 0xFF,
                "ordinary Graphics.setColor must restore opaque alpha");
        System.out.println("DirectGraphicsAlphaSmoke: ARGB blending passed");
    }
}
