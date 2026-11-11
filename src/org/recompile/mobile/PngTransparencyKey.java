/*
 * FreeJ2ME compatibility code, licensed under GPL-3.0-or-later.
 * Applies PNG's explicitly encoded grayscale transparency key when ImageIO
 * exposes the keyed pixels as opaque.
 */
package org.recompile.mobile;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

final class PngTransparencyKey {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final int MAX_HEADER_SCAN_BYTES = 1024 * 1024;

    private PngTransparencyKey() { }

    static BufferedImage readImage(InputStream source) throws IOException {
        if (source == null) throw new NullPointerException("source");
        BufferedInputStream buffered = source instanceof BufferedInputStream
                ? (BufferedInputStream) source
                : new BufferedInputStream(source);
        GrayKey key = inspect(buffered);
        BufferedImage image = ImageIO.read(buffered);
        if (image == null || key == null) return image;
        return applyGrayKey(image, key);
    }

    static GrayKey inspect(InputStream source) throws IOException {
        if (source == null) return null;
        BufferedInputStream buffered = source instanceof BufferedInputStream
                ? (BufferedInputStream) source
                : new BufferedInputStream(source);
        buffered.mark(MAX_HEADER_SCAN_BYTES);
        try {
            byte[] signature = new byte[PNG_SIGNATURE.length];
            readFully(buffered, signature);
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (signature[i] != PNG_SIGNATURE[i]) return null;
            }

            int consumed = PNG_SIGNATURE.length;
            int bitDepth = -1;
            int colorType = -1;
            boolean headerSeen = false;
            while (consumed + 8 <= MAX_HEADER_SCAN_BYTES) {
                long chunkLength = readUnsignedInt(buffered);
                byte[] chunkType = new byte[4];
                readFully(buffered, chunkType);
                consumed += 8;
                if (chunkLength > MAX_HEADER_SCAN_BYTES - consumed - 4L) return null;

                if (isChunk(chunkType, "IHDR")) {
                    if (headerSeen || consumed != PNG_SIGNATURE.length + 8
                            || chunkLength != 13) {
                        return null;
                    }
                    byte[] header = new byte[13];
                    readFully(buffered, header);
                    consumed += header.length;
                    skipFully(buffered, 4);
                    consumed += 4;
                    bitDepth = header[8] & 0xff;
                    colorType = header[9] & 0xff;
                    if (colorType != 0 || !isSupportedGrayDepth(bitDepth)) {
                        return null;
                    }
                    headerSeen = true;
                    continue;
                }

                if (!headerSeen) return null;
                if (isChunk(chunkType, "IDAT")) return null;
                if (isChunk(chunkType, "tRNS")) {
                    if (colorType == 0 && chunkLength == 2) {
                        int sample = readUnsignedShort(buffered);
                        long maxSample = (1L << bitDepth) - 1L;
                        return sample <= maxSample ? new GrayKey(bitDepth, sample) : null;
                    }
                    skipFully(buffered, chunkLength + 4L);
                    consumed += (int) chunkLength + 4;
                    continue;
                }

                skipFully(buffered, chunkLength + 4L);
                consumed += (int) chunkLength + 4;
            }
            return null;
        } catch (EOFException exception) {
            return null;
        } finally {
            buffered.reset();
        }
    }

    private static BufferedImage applyGrayKey(BufferedImage image, GrayKey key) {
        Raster raster = image.getRaster();
        int[] sampleSizes = raster.getSampleModel().getSampleSize();
        if (raster.getNumBands() == 0 || sampleSizes.length == 0
                || sampleSizes[0] < key.bitDepth || sampleSizes[0] > 30) {
            return image;
        }

        long pngMax = (1L << key.bitDepth) - 1L;
        long rasterMax = (1L << sampleSizes[0]) - 1L;
        long scaledSample = (key.sample * rasterMax + pngMax / 2L) / pngMax;
        int width = image.getWidth();
        int height = image.getHeight();
        int alphaBand = image.getColorModel().getNumColorComponents();
        boolean directAlphaBand = image.getColorModel().hasAlpha()
                && !image.isAlphaPremultiplied()
                && raster.getNumBands() > alphaBand;
        int changed = 0;

        if (directAlphaBand) {
            WritableRaster writable = image.getRaster();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (isKeySample(raster.getSample(x, y, 0), key, scaledSample)
                            && raster.getSample(x, y, alphaBand) != 0) {
                        writable.setSample(x, y, alphaBand, 0);
                        changed++;
                    }
                }
            }
            if (changed == 0) return image;
            System.out.println("Applied explicit PNG grayscale tRNS transparency to "
                    + changed + " pixels");
            return image;
        }

        BufferedImage transparent = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                if (isKeySample(raster.getSample(x, y, 0), key, scaledSample)
                        && (row[x] >>> 24) != 0) {
                    row[x] &= 0x00ffffff;
                    changed++;
                }
            }
            transparent.setRGB(0, y, width, 1, row, 0, width);
        }
        if (changed == 0) return image;
        System.out.println("Applied explicit PNG grayscale tRNS transparency to "
                + changed + " pixels");
        return transparent;
    }

    static boolean isKeySample(int decodedSample, GrayKey key, long scaledSample) {
        return decodedSample == key.sample || decodedSample == scaledSample;
    }

    private static boolean isSupportedGrayDepth(int depth) {
        return depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16;
    }

    private static boolean isChunk(byte[] chunk, String name) {
        return chunk[0] == name.charAt(0) && chunk[1] == name.charAt(1)
                && chunk[2] == name.charAt(2) && chunk[3] == name.charAt(3);
    }

    private static int readUnsignedShort(InputStream input) throws IOException {
        int high = input.read();
        int low = input.read();
        if (high < 0 || low < 0) throw new EOFException("truncated PNG chunk");
        return (high << 8) | low;
    }

    private static long readUnsignedInt(InputStream input) throws IOException {
        int a = input.read();
        int b = input.read();
        int c = input.read();
        int d = input.read();
        if ((a | b | c | d) < 0) throw new EOFException("truncated PNG chunk");
        return ((long) a << 24) | ((long) b << 16) | ((long) c << 8) | d;
    }

    private static void readFully(InputStream input, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 0) throw new EOFException("truncated PNG header");
            offset += count;
        }
    }

    private static void skipFully(InputStream input, long count) throws IOException {
        byte[] discard = new byte[4096];
        while (count > 0) {
            int read = input.read(discard, 0, (int) Math.min(discard.length, count));
            if (read < 0) throw new EOFException("truncated PNG chunk");
            count -= read;
        }
    }

    static final class GrayKey {
        final int bitDepth;
        final int sample;

        GrayKey(int bitDepth, int sample) {
            this.bitDepth = bitDepth;
            this.sample = sample;
        }
    }
}
