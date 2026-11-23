/*
 * This file is part of FreeJ2ME; licensed under GPL-3.0-or-later.
 */
package javax.microedition.io;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

/** Datagram buffer and MIDP DataInput/DataOutput view. */
final class DatagramImpl implements Datagram {
    static final int MAX_DATAGRAM_LENGTH = 65507;

    private byte[] data;
    private int offset;
    private int length;
    private int readPosition;
    private int writePosition;
    private String address;

    DatagramImpl(int capacity) {
        if (capacity < 0 || capacity > MAX_DATAGRAM_LENGTH)
            throw new IllegalArgumentException("Invalid datagram size: " + capacity);
        data = new byte[capacity];
    }

    DatagramImpl(byte[] buffer, int size) {
        if (buffer == null) throw new NullPointerException("Datagram buffer is null");
        if (size < 0 || size > buffer.length || size > MAX_DATAGRAM_LENGTH)
            throw new IllegalArgumentException("Invalid datagram length: " + size);
        data = buffer;
        length = size;
        writePosition = size;
    }

    static InetSocketAddress parseAddress(String value) throws IOException {
        if (value == null) throw new IOException("Datagram destination is missing");
        try {
            URI uri = new URI(value);
            if (!"datagram".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()))
                throw new IOException("Invalid datagram address: " + value);
            InetAddress host = InetAddress.getByName(uri.getHost());
            return new InetSocketAddress(host, uri.getPort());
        } catch (URISyntaxException error) {
            throw new IOException("Invalid datagram address: " + value, error);
        }
    }

    static String formatAddress(InetAddress host, int port) {
        String address = host.getHostAddress();
        if (address.indexOf(':') >= 0 && address.charAt(0) != '[')
            address = "[" + address + "]";
        return "datagram://" + address + ":" + port;
    }

    byte[] buffer() { return data; }
    int bufferOffset() { return offset; }
    int bufferCapacity() { return data.length - offset; }

    void setReceived(int receivedLength, String senderAddress) {
        length = receivedLength;
        readPosition = 0;
        writePosition = receivedLength;
        address = senderAddress;
    }

    public String getAddress() { return address; }
    public byte[] getData() { return data; }
    public int getLength() { return length; }
    public int getOffset() { return offset; }

    public void reset() {
        length = 0;
        readPosition = 0;
        writePosition = 0;
    }

    public void setAddress(Datagram reference) {
        if (reference == null) throw new NullPointerException("Datagram reference is null");
        address = reference.getAddress();
    }

    public void setAddress(String value) {
        try {
            parseAddress(value);
            address = value;
        } catch (IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
    }

    public void setData(byte[] buffer, int bufferOffset, int dataLength) {
        if (buffer == null) throw new NullPointerException("Datagram buffer is null");
        if (bufferOffset < 0 || dataLength < 0 || dataLength > MAX_DATAGRAM_LENGTH
                || bufferOffset > buffer.length - dataLength)
            throw new IllegalArgumentException("Invalid datagram buffer range");
        data = buffer;
        offset = bufferOffset;
        length = dataLength;
        readPosition = 0;
        writePosition = dataLength;
    }

    public void setLength(int dataLength) {
        if (dataLength < 0 || dataLength > bufferCapacity())
            throw new IllegalArgumentException("Invalid datagram length: " + dataLength);
        length = dataLength;
        readPosition = Math.min(readPosition, length);
        writePosition = dataLength;
    }

    private int readUnsignedByteValue() throws EOFException {
        if (readPosition >= length) throw new EOFException();
        return data[offset + readPosition++] & 0xff;
    }

    private void ensureWritable(int count) throws IOException {
        if (count < 0 || writePosition > MAX_DATAGRAM_LENGTH - count)
            throw new IOException("Datagram exceeds maximum UDP payload");
        int required = writePosition + count;
        if (required <= bufferCapacity()) return;
        int capacity = Math.max(required, Math.max(32, bufferCapacity() * 2));
        capacity = Math.min(capacity, MAX_DATAGRAM_LENGTH);
        byte[] expanded = new byte[capacity];
        System.arraycopy(data, offset, expanded, 0, length);
        data = expanded;
        offset = 0;
    }

    private void writeBytesInternal(byte[] bytes, int sourceOffset, int count)
            throws IOException {
        ensureWritable(count);
        System.arraycopy(bytes, sourceOffset, data, offset + writePosition, count);
        writePosition += count;
        length = Math.max(length, writePosition);
    }

    public boolean readBoolean() throws IOException { return readUnsignedByteValue() != 0; }
    public byte readByte() throws IOException { return (byte) readUnsignedByteValue(); }
    public int readUnsignedByte() throws IOException { return readUnsignedByteValue(); }
    public short readShort() throws IOException { return (short) readUnsignedShort(); }
    public int readUnsignedShort() throws IOException {
        return (readUnsignedByteValue() << 8) | readUnsignedByteValue();
    }
    public char readChar() throws IOException { return (char) readUnsignedShort(); }
    public int readInt() throws IOException {
        return (readUnsignedByteValue() << 24) | (readUnsignedByteValue() << 16)
                | (readUnsignedByteValue() << 8) | readUnsignedByteValue();
    }
    public long readLong() throws IOException {
        return ((long) readInt() << 32) | (readInt() & 0xffffffffL);
    }
    public float readFloat() throws IOException { return Float.intBitsToFloat(readInt()); }
    public double readDouble() throws IOException { return Double.longBitsToDouble(readLong()); }
    public void readFully(byte[] buffer) throws IOException {
        if (buffer == null) throw new NullPointerException("Destination buffer is null");
        readFully(buffer, 0, buffer.length);
    }
    public void readFully(byte[] buffer, int bufferOffset, int count) throws IOException {
        if (buffer == null) throw new NullPointerException("Destination buffer is null");
        if (bufferOffset < 0 || count < 0 || bufferOffset > buffer.length - count)
            throw new IndexOutOfBoundsException();
        if (count > length - readPosition) throw new EOFException();
        System.arraycopy(data, offset + readPosition, buffer, bufferOffset, count);
        readPosition += count;
    }
    public int skipBytes(int count) {
        int skipped = Math.min(Math.max(0, count), length - readPosition);
        readPosition += skipped;
        return skipped;
    }
    public String readLine() throws IOException {
        if (readPosition >= length) return null;
        StringBuilder line = new StringBuilder();
        while (readPosition < length) {
            int value = readUnsignedByteValue();
            if (value == '\n') break;
            if (value == '\r') {
                if (readPosition < length && data[offset + readPosition] == '\n') readPosition++;
                break;
            }
            line.append((char) value);
        }
        return line.toString();
    }
    public String readUTF() throws IOException { return DataInputStream.readUTF(this); }

    public void write(int value) throws IOException {
        ensureWritable(1);
        data[offset + writePosition++] = (byte) value;
        length = Math.max(length, writePosition);
    }
    public void write(byte[] buffer) throws IOException {
        if (buffer == null) throw new NullPointerException("Source buffer is null");
        write(buffer, 0, buffer.length);
    }
    public void write(byte[] buffer, int bufferOffset, int count) throws IOException {
        if (buffer == null) throw new NullPointerException("Source buffer is null");
        if (bufferOffset < 0 || count < 0 || bufferOffset > buffer.length - count)
            throw new IndexOutOfBoundsException();
        writeBytesInternal(buffer, bufferOffset, count);
    }
    public void writeBoolean(boolean value) throws IOException { write(value ? 1 : 0); }
    public void writeByte(int value) throws IOException { write(value); }
    public void writeShort(int value) throws IOException {
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }
    public void writeChar(int value) throws IOException { writeShort(value); }
    public void writeInt(int value) throws IOException {
        write((value >>> 24) & 0xff);
        write((value >>> 16) & 0xff);
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }
    public void writeLong(long value) throws IOException {
        writeInt((int) (value >>> 32));
        writeInt((int) value);
    }
    public void writeFloat(float value) throws IOException {
        writeInt(Float.floatToIntBits(value));
    }
    public void writeDouble(double value) throws IOException {
        writeLong(Double.doubleToLongBits(value));
    }
    public void writeBytes(String value) throws IOException {
        for (int index = 0; index < value.length(); index++) write(value.charAt(index));
    }
    public void writeChars(String value) throws IOException {
        for (int index = 0; index < value.length(); index++) writeChar(value.charAt(index));
    }
    public void writeUTF(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF(value);
        output.flush();
        write(bytes.toByteArray());
    }
}
