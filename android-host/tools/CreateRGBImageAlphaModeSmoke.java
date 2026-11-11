package org.recompile.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

public final class CreateRGBImageAlphaModeSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[] source = { 0x00123456, 0x80123456 };

        BufferedImage opaque = Image.createRGBImage(source, 2, 1, false)
                .platformImage.getCanvas();
        require(opaque.getRGB(0, 0) == 0xFF123456,
                "processAlpha=false must ignore a zero source alpha");
        require(opaque.getRGB(1, 0) == 0xFF123456,
                "processAlpha=false must ignore a partial source alpha");

        BufferedImage alpha = Image.createRGBImage(source, 2, 1, true)
                .platformImage.getCanvas();
        require((alpha.getRGB(0, 0) >>> 24) == 0,
                "processAlpha=true must preserve transparent pixels");
        require(alpha.getRGB(1, 0) == 0x80123456,
                "processAlpha=true must preserve partial alpha");

        Image mutable = Image.createImage(2, 1);
        require(mutable.isMutable(), "createImage(width,height) must be mutable");
        require(!Image.createImage(mutable).isMutable(),
                "createImage(Image) must return an immutable image copy");
        require(!Image.createImage(mutable, 0, 0, 1, 1, Sprite.TRANS_NONE).isMutable(),
                "createImage(subregion) must return an immutable image");
        require(!Image.createRGBImage(source, 2, 1, true).isMutable(),
                "createRGBImage must return an immutable image");
        byte[] png = pngFixture();
        require(!Image.createImage(png, 0, png.length).isMutable(),
                "createImage(byte[]) must return an immutable image");
        System.out.println("CreateRGBImageAlphaModeSmoke: processAlpha semantics passed");
    }

    private static byte[] pngFixture() {
        try {
            BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            source.setRGB(0, 0, 0x80123456);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(source, "png", output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new AssertionError("could not create PNG fixture", error);
        }
    }
}
