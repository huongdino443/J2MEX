package org.recompile.mobile;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Verifies PNG's explicit grayscale tRNS key without guessing font resources. */
public final class PngTransparencySmoke {
    public static void main(String[] args) throws Exception {
        testOpaqueWhiteRemainsOpaque();
        int[] scanned = args.length == 0 ? new int[] {0, 0} : scanJar(args[0]);
        if (args.length > 0 && (scanned[0] == 0 || scanned[1] == 0)) {
            throw new AssertionError("sample JAR had no verified grayscale tRNS pixels");
        }
        System.out.println("PngTransparencySmoke: explicit tRNS and opaque-image checks passed; "
                + scanned[0] + " PNG resources checked, " + scanned[1]
                + " grayscale-key images verified");
    }

    private static void testOpaqueWhiteRemainsOpaque() throws Exception {
        BufferedImage opaque = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        opaque.setRGB(0, 0, 0xffffffff);
        opaque.setRGB(1, 0, 0xff000000);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(opaque, "png", bytes)) throw new AssertionError("PNG writer unavailable");

        BufferedImage decoded = PngTransparencyKey.readImage(
                new ByteArrayInputStream(bytes.toByteArray()));
        if (decoded.getRGB(0, 0) != 0xffffffff || decoded.getRGB(1, 0) != 0xff000000) {
            throw new AssertionError("an opaque white image was changed without a tRNS key");
        }
    }

    private static int[] scanJar(String jarPath) throws Exception {
        int pngs = 0;
        int verified = 0;
        try (ZipFile zip = new ZipFile(jarPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".png")) {
                    continue;
                }
                pngs++;
                PngTransparencyKey.GrayKey key;
                try (InputStream stream = zip.getInputStream(entry)) {
                    key = PngTransparencyKey.inspect(stream);
                }
                if (key == null) continue;

                BufferedImage original;
                try (InputStream stream = zip.getInputStream(entry)) {
                    original = ImageIO.read(stream);
                }
                BufferedImage corrected;
                try (InputStream stream = zip.getInputStream(entry)) {
                    corrected = PngTransparencyKey.readImage(stream);
                }
                if (original == null || corrected == null
                        || original.getWidth() != corrected.getWidth()
                        || original.getHeight() != corrected.getHeight()) {
                    throw new AssertionError("PNG decode failed: " + entry.getName());
                }
                verifyOnlyTheDeclaredKeyChanged(original, corrected, key, entry.getName());
                verified++;
            }
        }
        return new int[] {pngs, verified};
    }

    private static void verifyOnlyTheDeclaredKeyChanged(BufferedImage original,
            BufferedImage corrected, PngTransparencyKey.GrayKey key, String name) {
        Raster raster = original.getRaster();
        int sampleBits = raster.getSampleModel().getSampleSize(0);
        if (sampleBits < key.bitDepth || sampleBits > 30) return;
        long pngMax = (1L << key.bitDepth) - 1L;
        long rasterMax = (1L << sampleBits) - 1L;
        long scaled = (key.sample * rasterMax + pngMax / 2L) / pngMax;
        int matchingSamples = 0;
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int before = original.getRGB(x, y);
                int after = corrected.getRGB(x, y);
                boolean keyPixel = PngTransparencyKey.isKeySample(
                        raster.getSample(x, y, 0), key, scaled);
                if (keyPixel) {
                    matchingSamples++;
                    if ((after >>> 24) != 0 || (after & 0x00ffffff) != (before & 0x00ffffff)) {
                        throw new AssertionError("declared tRNS sample was not preserved: " + name);
                    }
                } else if (after != before) {
                    throw new AssertionError("a non-key pixel changed: " + name);
                }
            }
        }
        if (matchingSamples == 0) throw new AssertionError("tRNS sample not present: " + name);
    }
}
