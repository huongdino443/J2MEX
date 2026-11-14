/*
This file is part of FreeJ2ME.

FreeJ2ME is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

FreeJ2ME is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package com.mascotcapsule.micro3d.v3;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Decodes the bitmap and image data accepted by Micro3D textures. */
final class Micro3DTextureData {
    private Micro3DTextureData() { }

    static BufferedImage decode(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("texture");
        }
        try {
            if (bytes.length >= 54 && bytes[0] == 'B' && bytes[1] == 'M') {
                int dibSize = readInt(bytes, 14);
                if (dibSize == 12 || dibSize >= 40) {
                    int bpp = dibSize == 12 ? readUShort(bytes, 24) : readUShort(bytes, 28);
                    if (bpp == 8) {
                        return decodeIndexedBitmap(bytes, dibSize);
                    }
                }
            }
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) {
                throw new IOException("unsupported image format");
            }
            return decoded;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Cannot decode Micro3D texture: " + e.getMessage(), e);
        }
    }

    private static BufferedImage decodeIndexedBitmap(byte[] bytes, int dibSize) throws IOException {
        int pixelOffset = readInt(bytes, 10);
        int width;
        int height;
        boolean topDown = false;
        int paletteOffset = 14 + dibSize;
        int paletteStride;
        int paletteCount;
        if (dibSize == 12) {
            width = readUShort(bytes, 18);
            height = readUShort(bytes, 20);
            paletteStride = 3;
            paletteCount = 256;
        } else {
            width = readInt(bytes, 18);
            int signedHeight = readInt(bytes, 22);
            if (signedHeight == Integer.MIN_VALUE) {
                throw new IOException("invalid bitmap height");
            }
            topDown = signedHeight < 0;
            height = Math.abs(signedHeight);
            int compression = readInt(bytes, 30);
            if (compression != 0) {
                throw new IOException("compressed indexed bitmap is unsupported");
            }
            int colorsUsed = readInt(bytes, 46);
            paletteStride = 4;
            paletteCount = colorsUsed == 0 ? 256 : colorsUsed;
        }
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                || (long) width * height > 16_777_216L) {
            throw new IOException("invalid bitmap dimensions");
        }
        if (paletteCount < 1 || paletteCount > 256 || pixelOffset < 0) {
            throw new IOException("invalid bitmap palette");
        }
        requireRange(bytes, paletteOffset, paletteCount * paletteStride);
        int[] palette = new int[paletteCount];
        for (int i = 0; i < paletteCount; i++) {
            int at = paletteOffset + i * paletteStride;
            int blue = bytes[at] & 255;
            int green = bytes[at + 1] & 255;
            int red = bytes[at + 2] & 255;
            palette[i] = 0xff000000 | (red << 16) | (green << 8) | blue;
        }
        int rowStride = (width + 3) & ~3;
        requireRange(bytes, pixelOffset, rowStride * height);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int sourceY = 0; sourceY < height; sourceY++) {
            int targetY = topDown ? sourceY : height - 1 - sourceY;
            int source = pixelOffset + sourceY * rowStride;
            for (int x = 0; x < width; x++) {
                int index = bytes[source + x] & 255;
                if (index >= palette.length) {
                    throw new IOException("bitmap palette index out of range");
                }
                row[x] = index == 0 ? palette[index] & 0x00ffffff : palette[index];
            }
            result.setRGB(0, targetY, width, 1, row, 0, width);
        }
        return result;
    }

    private static int readUShort(byte[] data, int offset) throws IOException {
        requireRange(data, offset, 2);
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
    }

    private static int readInt(byte[] data, int offset) throws IOException {
        requireRange(data, offset, 4);
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | (data[offset + 3] << 24);
    }

    private static void requireRange(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || (long) offset + length > data.length) {
            throw new IOException("bitmap data is truncated");
        }
    }
}
