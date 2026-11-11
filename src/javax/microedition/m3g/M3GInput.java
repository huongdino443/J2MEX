package javax.microedition.m3g;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bounded little-endian input for the JSR-184 interchange format. */
final class M3GInput {
    static final int MAX_BYTES = 64 * 1024 * 1024;
    final byte[] bytes;
    final int end;
    int position;

    M3GInput(byte[] bytes, int offset, int length) throws IOException {
        if (length < 0 || offset < 0 || offset > bytes.length - length)
            throw new IOException("Truncated M3G data");
        this.bytes = bytes; position = offset; end = offset + length;
    }
    int left() { return end - position; }
    void need(int size) throws IOException {
        if (size < 0 || size > left()) throw new IOException("Truncated M3G object at " + position);
    }
    int u8() throws IOException { need(1); return bytes[position++] & 255; }
    boolean bool() throws IOException {
        int value = u8();
        if (value > 1) throw new IOException("Invalid M3G boolean");
        return value != 0;
    }
    int u16() throws IOException { return u8() | u8() << 8; }
    int i32() throws IOException { return u16() | u16() << 16; }
    int count() throws IOException {
        int value = i32();
        if (value < 0 || value > 1_000_000) throw new IOException("Invalid/excessive M3G count");
        return value;
    }
    float f32() throws IOException {
        float value = Float.intBitsToFloat(i32());
        if (Float.isNaN(value) || Float.isInfinite(value)) throw new IOException("Non-finite M3G number");
        return value;
    }
    float[] floats(int size) throws IOException {
        if (size < 0 || size > left() / 4) throw new IOException("Truncated M3G float array");
        float[] values = new float[size];
        for (int i = 0; i < size; i++) values[i] = f32();
        return values;
    }
    byte[] data() throws IOException {
        int size = count(); need(size);
        byte[] result = java.util.Arrays.copyOfRange(bytes, position, position + size);
        position += size;
        return result;
    }
    String string() throws IOException {
        int start = position;
        while (u8() != 0) if (position - start > 65536) throw new IOException("M3G string too long");
        return new String(bytes, start, position - start - 1, StandardCharsets.UTF_8);
    }
    int rgb() throws IOException { return u8() << 16 | u8() << 8 | u8(); }
    int rgba() throws IOException { int color = rgb(); return u8() << 24 | color; }
    M3GInput slice(int size) throws IOException {
        need(size); M3GInput result = new M3GInput(bytes, position, size); position += size; return result;
    }
}
