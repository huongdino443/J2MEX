/*
 * FreeJ2ME compatibility code, licensed under GPL-3.0-or-later.
 * Contains no bundled game assets or modified game bytecode.
 */
package org.recompile.mobile;

import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.*;

/** Narrow, fingerprinted resource adapters. Never writes to the user's JAR. */
final class GameResourceCompatibility {
    private static final String DECODER_SHA =
        "5f238e7b024deb6463096792e042a823e067613ec48123132e897009e6be09e8";
    private static final String PACK_READER_SHA =
        "99ec2dda59eb48ace0ed6397731d39f87b17ce2adc81fe01a8e174a1612c28a8";
    private static final String FONT_SHA =
        "dd699b3ae649f2c7c8c8331c1faf142be914f6d4dcc5618a98d46ea96094a031";
    private static final String FONT_PATH = "fntCrystalSmall40_0.pak";
    private byte[] cachedFont;

    static GameResourceCompatibility forClasses(URL decoder, URL packReader) {
        if (decoder == null || packReader == null) return null;
        try {
            if (DECODER_SHA.equals(hash(decoder)) && PACK_READER_SHA.equals(hash(packReader)))
                return new GameResourceCompatibility();
        } catch (IOException error) {
            System.out.println("Unable to check Majesty resource profile: " + error);
        }
        return null;
    }

    synchronized byte[] adapt(String path, byte[] original) {
        if (path.startsWith("/")) path = path.substring(1);
        if (!FONT_PATH.equals(path) || !FONT_SHA.equals(hash(original))) return original;
        if (cachedFont == null) {
            try {
                cachedFont = normalizePack(original);
                System.out.println("Compatibility: Majesty PNG filters normalized for "
                    + FONT_PATH + "; 11 images; original JAR unchanged");
            } catch (IOException error) {
                // Known profile failures must be visible, not silently replayed raw.
                throw new IllegalStateException("Majesty PNG compatibility failed", error);
            }
        }
        // A caller of the byte-array API may modify its private result.
        return cachedFont.clone();
    }

    private static String hash(URL url) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = url.openStream()) {
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return hex(digest.digest());
    }

    private static String hash(byte[] bytes) { return hex(digest().digest(bytes)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static byte[] normalizePack(byte[] original) throws IOException {
        Reader reader = new Reader(original);
        byte[] header = reader.take(8);
        if (header[0] != 'H' || header[1] != 'G' || header[2] != 'I'
                || header[3] != 'M' || header[7] != 9) throw new IOException("Unsupported pack");
        int palettes = reader.varint();
        if (palettes > 128) throw new IOException("Too many palettes");
        int[] paletteSizes = new int[palettes];
        for (int i = 0; i < palettes; i++) {
            byte[] palette = reader.block();
            paletteSizes[i] = (palette.length - 4) / 3;
            reader.block(); // Preserve alpha bytes verbatim in the prefix below.
        }
        int count = reader.varint();
        if (count > 65535) throw new IOException("Too many images");
        byte[] indices = reader.take(count);
        int[] widths = new int[count], heights = new int[count];
        for (int i = 0; i < count; i++) {
            widths[i] = reader.varint();
            heights[i] = reader.varint();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(original, 0, reader.position);
        int changed = 0;
        for (int i = 0; i < count; i++) {
            int blockStart = reader.position;
            byte[] payload = reader.block();
            int palette = indices[i] & 255;
            if (palette >= 128) {
                output.write(original, blockStart, reader.position - blockStart);
                continue;
            }
            if (palette >= palettes) throw new IOException("Palette out of range");
            int width = widths[i], height = heights[i];
            long expected = ((long)width + 1) * height;
            if (width <= 0 || height <= 0 || expected > 1024 * 1024)
                throw new IOException("Invalid scanline size");
            byte[] raw = inflate(payload, (int)expected);
            byte[] pixels = unfilter(raw, width, height);
            boolean needsRepair = false;
            for (int y = 0; y < height; y++) needsRepair |= raw[y * (width + 1)] != 0;
            for (byte pixel : pixels)
                if ((pixel & 255) >= paletteSizes[palette]) throw new IOException("Invalid pixel");
            if (!needsRepair) {
                output.write(original, blockStart, reader.position - blockStart);
                continue;
            }
            byte[] normalized = new byte[raw.length];
            for (int y = 0; y < height; y++)
                System.arraycopy(pixels, y * width, normalized, y * (width + 1) + 1, width);
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(9);
            try {
                try (DeflaterOutputStream zip = new DeflaterOutputStream(compressed, deflater)) {
                    zip.write(normalized);
                }
            } finally { deflater.end(); }
            byte[] data = compressed.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(new byte[]{'I', 'D', 'A', 'T'});
            crc.update(data);
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            chunk.write(data);
            new DataOutputStream(chunk).writeInt((int)crc.getValue());
            byte[] repaired = chunk.toByteArray();
            if (!Arrays.equals(pixels, unfilter(inflate(repaired, raw.length), width, height)))
                throw new IOException("Pixel roundtrip failed");
            writeVarint(output, repaired.length);
            output.write(repaired);
            changed++;
        }
        output.write(original, reader.position, original.length - reader.position);
        if (changed != 11) throw new IOException("Unexpected image count: " + changed);
        return output.toByteArray();
    }

    private static byte[] inflate(byte[] payload, int expected) throws IOException {
        if (payload.length < 4) throw new IOException("Truncated image chunk");
        CRC32 crc = new CRC32();
        crc.update(new byte[]{'I', 'D', 'A', 'T'});
        crc.update(payload, 0, payload.length - 4);
        DataInputStream stored = new DataInputStream(new ByteArrayInputStream(payload,
            payload.length - 4, 4));
        if ((int)crc.getValue() != stored.readInt()) throw new IOException("Invalid IDAT CRC");
        byte[] result = new byte[expected];
        try (DataInputStream input = new DataInputStream(new InflaterInputStream(
                new ByteArrayInputStream(payload, 0, payload.length - 4)))) {
            input.readFully(result);
            if (input.read() != -1) throw new IOException("Unexpected inflated data");
        }
        return result;
    }

    private static byte[] unfilter(byte[] raw, int width, int height) throws IOException {
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            int mode = raw[y * (width + 1)] & 255;
            if (mode > 4) throw new IOException("Invalid PNG filter");
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int left = x == 0 ? 0 : pixels[index - 1] & 255;
                int up = y == 0 ? 0 : pixels[index - width] & 255;
                int corner = x == 0 || y == 0 ? 0 : pixels[index - width - 1] & 255;
                int prediction = mode == 0 ? 0 : mode == 1 ? left : mode == 2 ? up
                    : mode == 3 ? (left + up) / 2 : paeth(left, up, corner);
                pixels[index] = (byte)((raw[y * (width + 1) + 1 + x] & 255) + prediction);
            }
        }
        return pixels;
    }

    private static int paeth(int left, int up, int corner) {
        int estimate = left + up - corner;
        int a = Math.abs(estimate - left), b = Math.abs(estimate - up), c = Math.abs(estimate - corner);
        return a <= b && a <= c ? left : b <= c ? up : corner;
    }
    private static void writeVarint(OutputStream output, int value) throws IOException {
        while (value >= 128) { output.write((value & 127) | 128); value >>>= 7; }
        output.write(value);
    }
    private static final class Reader {
        private final byte[] data;
        private int position;
        Reader(byte[] data) { this.data = data; }
        byte[] take(int length) throws IOException {
            if (length < 0 || length > data.length - position) throw new IOException("Truncated pack");
            byte[] result = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return result;
        }
        int varint() throws IOException {
            int value = 0;
            for (int shift = 0; shift <= 28; shift += 7) {
                int next = take(1)[0] & 255;
                if (shift == 28 && next > 7) throw new IOException("Invalid integer");
                value |= (next & 127) << shift;
                if (next < 128) return value;
            }
            throw new IOException("Invalid integer");
        }
        byte[] block() throws IOException { return take(varint()); }
    }
}
