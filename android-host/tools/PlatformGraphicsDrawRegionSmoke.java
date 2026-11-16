package org.recompile.mobile;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.microedition.lcdui.Graphics;

public final class PlatformGraphicsDrawRegionSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        PlatformImage source = new PlatformImage(5, 4);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (x + y) % 3 == 0 ? 0x00 : (x + y) % 3 == 1 ? 0x80 : 0xFF;
                source.getCanvas().setRGB(x, y,
                        (alpha << 24) | ((x * 47) << 16) | ((y * 61) << 8) | (x + y));
            }
        }

        int[][] draws = {{2, 3}, {-1, 2}, {6, 1}};
        for (int[] destination : draws) {
            PlatformImage actual = new PlatformImage(8, 7);
            PlatformImage expected = new PlatformImage(8, 7);
            Graphics2D referenceGraphics = expected.getCanvas().createGraphics();
            try {
                referenceGraphics.drawImage(
                        source.getCanvas().getSubimage(1, 1, 3, 2),
                        destination[0], destination[1], null);
            } finally {
                referenceGraphics.dispose();
            }

            actual.getGraphics().drawRegion(source, 1, 1, 3, 2, 0,
                    destination[0], destination[1], Graphics.LEFT | Graphics.TOP);
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    require(actual.getCanvas().getRGB(x, y)
                                    == expected.getCanvas().getRGB(x, y),
                            "source-crop draw differs at " + x + "," + y
                                    + " for destination " + destination[0] + "," + destination[1]);
                }
            }
        }

        PlatformImage invalid = new PlatformImage(8, 7);
        PlatformImage before = new PlatformImage(8, 7);
        invalid.getGraphics().drawRegion(source, 4, 3, 2, 2, 0, 0, 0,
                Graphics.LEFT | Graphics.TOP);
        for (int y = 0; y < invalid.getHeight(); y++)
            for (int x = 0; x < invalid.getWidth(); x++)
                require(invalid.getCanvas().getRGB(x, y) == before.getCanvas().getRGB(x, y),
                        "invalid source region should not modify the destination");

        System.out.println("PlatformGraphicsDrawRegionSmoke: source cropping matches subimage rendering");
    }
}
