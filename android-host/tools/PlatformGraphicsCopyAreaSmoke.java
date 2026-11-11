package org.recompile.mobile;

import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;

public final class PlatformGraphicsCopyAreaSmoke {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void setRow(BufferedImage image, int[] pixels) {
        image.setRGB(0, 0, pixels.length, 1, pixels, 0, pixels.length);
    }

    private static void requireRow(BufferedImage image, int[] expected, String message) {
        for (int x = 0; x < expected.length; x++) {
            require(image.getRGB(x, 0) == expected[x],
                    message + " at x=" + x + ": expected "
                            + Integer.toHexString(expected[x]) + " got "
                            + Integer.toHexString(image.getRGB(x, 0)));
        }
    }

    public static void main(String[] args) {
        Mobile.setPlatform(new MobilePlatform(8, 2));
        PlatformImage image = new PlatformImage(8, 2);
        PlatformGraphics graphics = image.getGraphics();
        BufferedImage pixels = image.getCanvas();
        int anchor = Graphics.TOP | Graphics.LEFT;

        int[] row = {
                0xff110000, 0xff220000, 0xff330000, 0xff440000,
                0xff550000, 0xff660000, 0xff770000, 0xff880000
        };
        setRow(pixels, row);
        graphics.copyArea(0, 0, 6, 1, 1, 0, anchor);
        requireRow(pixels, new int[] {
                row[0], row[0], row[1], row[2], row[3], row[4], row[5], row[7]
        }, "Overlapping copy to the right must use a source snapshot");

        setRow(pixels, row);
        graphics.copyArea(1, 0, 6, 1, 0, 0, anchor);
        requireRow(pixels, new int[] {
                row[1], row[2], row[3], row[4], row[5], row[6], row[6], row[7]
        }, "Overlapping copy to the left must use a source snapshot");

        int[] alphaRow = {
                0xffaa0000, 0x00123456, 0xff00bb00, 0xff0000cc,
                0xffdddddd, 0xffeeeeee, 0xff123456, 0xff654321
        };
        setRow(pixels, alphaRow);
        graphics.copyArea(0, 0, 3, 1, 1, 0, anchor);
        requireRow(pixels, new int[] {
                alphaRow[0], 0xffaa0000, 0xff123456, 0xff00bb00,
                alphaRow[4], alphaRow[5], alphaRow[6], alphaRow[7]
        }, "Copied pixels must replace destination pixels without alpha blending");

        setRow(pixels, row);
        graphics.setClip(2, 0, 2, 1);
        graphics.copyArea(0, 0, 4, 1, 1, 0, anchor);
        requireRow(pixels, new int[] {
                row[0], row[1], row[1], row[2], row[4], row[5], row[6], row[7]
        }, "Copy must honor the destination clip");

        graphics.reset();
        setRow(pixels, row);
        graphics.translate(1, 0);
        graphics.copyArea(0, 0, 2, 1, 0, 0, anchor);
        requireRow(pixels, new int[] {
                row[0], row[0], row[1], row[3], row[4], row[5], row[6], row[7]
        }, "Copy destination must honor Graphics translation");

        System.out.println("PlatformGraphicsCopyAreaSmoke: " + checks
                + " checks passed; overlapping scroll, opaque copy, clipping and translation");
    }
}
