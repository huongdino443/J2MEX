package com.mascotcapsule.micro3d.v3;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.awt.image.BufferedImage;

/** Checks Micro3D bitmap decoding and indexed transparency on a game JAR. */
public final class Micro3DTextureSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Micro3DTextureSmoke game.jar");
        }
        int images = 0;
        long pixels = 0;
        boolean sawTransparent = false;
        boolean sawOpaque = false;
        verifyTransparentPaletteKeepsRgb();
        try (JarFile jar = new JarFile(args[0])) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".bmp")) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
                BufferedImage image = Micro3DTextureData.decode(bytes);
                if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new AssertionError("Invalid decoded dimensions: " + entry.getName());
                }
                int[] row = new int[image.getWidth()];
                for (int y = 0; y < image.getHeight(); y++) {
                    image.getRGB(0, y, image.getWidth(), 1, row, 0, image.getWidth());
                    for (int pixel : row) {
                        sawTransparent |= (pixel >>> 24) == 0;
                        sawOpaque |= (pixel >>> 24) == 255;
                    }
                }
                images++;
                pixels += (long) image.getWidth() * image.getHeight();
                System.out.println(entry.getName() + ": " + image.getWidth()
                        + "x" + image.getHeight());
            }
        }
        if (images < 2 || pixels < 10_000 || !sawTransparent || !sawOpaque) {
            throw new AssertionError("Unexpected texture decode result: " + images
                    + " images, " + pixels + " pixels, transparent=" + sawTransparent
                    + ", opaque=" + sawOpaque);
        }
        System.out.println("Micro3DTextureSmoke: " + images
                + " bitmaps, " + pixels + " pixels passed");
    }

    private static void verifyTransparentPaletteKeepsRgb() {
        byte[] bmp = new byte[1082];
        bmp[0] = 'B';
        bmp[1] = 'M';
        writeInt(bmp, 2, bmp.length);
        writeInt(bmp, 10, 1078);
        writeInt(bmp, 14, 40);
        writeInt(bmp, 18, 1);
        writeInt(bmp, 22, 1);
        writeShort(bmp, 26, 1);
        writeShort(bmp, 28, 8);
        writeInt(bmp, 34, 4);
        writeInt(bmp, 46, 256);
        bmp[54] = 0x33;
        bmp[55] = 0x22;
        bmp[56] = 0x11;
        bmp[1078] = 0;

        BufferedImage decoded = Micro3DTextureData.decode(bmp);
        int pixel = decoded.getRGB(0, 0);
        if (pixel != 0x00112233) {
            throw new AssertionError("Transparent palette pixel lost its RGB value: "
                    + Integer.toHexString(pixel));
        }
    }

    private static void writeShort(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }
}
