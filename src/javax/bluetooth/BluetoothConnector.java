package javax.bluetooth;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import org.recompile.mobile.BluetoothBridge;

/** Parses JSR-82 URLs and exposes Android-hosted Bluetooth sockets to MIDlets. */
public final class BluetoothConnector {
    private BluetoothConnector() { }

    public static Connection open(String url) throws IOException {
        return open(url, Connector.READ_WRITE);
    }

    public static Connection open(String url, int mode) throws IOException {
        if (url == null) throw new IllegalArgumentException("URL is null");
        int separator = url.indexOf("://");
        if (separator <= 0) throw new IOException("Malformed Bluetooth URL");
        String protocol = url.substring(0, separator).toLowerCase();
        if (!"btspp".equals(protocol) && !"btl2cap".equals(protocol))
            throw new IOException("Unsupported Bluetooth protocol: " + protocol);

        String remainder = url.substring(separator + 3);
        int optionsAt = remainder.indexOf(';');
        String endpoint = optionsAt < 0 ? remainder : remainder.substring(0, optionsAt);
        String options = optionsAt < 0 ? "" : remainder.substring(optionsAt + 1);
        int colon = endpoint.lastIndexOf(':');
        if (colon <= 0 || colon == endpoint.length() - 1) throw new IOException("Bluetooth URL needs host and UUID");
        String host = endpoint.substring(0, colon);
        UUID uuid = new UUID(endpoint.substring(colon + 1), false);
        boolean authenticate = false, encrypt = false;
        String name = "FreeJ2ME";
        if (!options.isEmpty()) {
            for (String option : options.split(";")) {
                int equals = option.indexOf('=');
                if (equals < 0) continue;
                String key = option.substring(0, equals).trim().toLowerCase();
                String value = option.substring(equals + 1).trim();
                if ("authenticate".equals(key)) authenticate = Boolean.parseBoolean(value);
                else if ("encrypt".equals(key)) encrypt = Boolean.parseBoolean(value);
                else if ("name".equals(key) && !value.isEmpty()) name = value;
            }
        }
        boolean secure = authenticate && encrypt;
        if ("localhost".equalsIgnoreCase(host)) {
            int serverId = BluetoothBridge.openServer(protocol, name, uuid.toString(), secure);
            ServiceRecord record = new J2MEServiceRecord(null, uuid, "btl2cap".equals(protocol));
            return "btspp".equals(protocol)
                    ? new StreamNotifier(serverId, record)
                    : new L2capNotifier(serverId, record);
        }

        int connectionId = BluetoothBridge.openClient(protocol, host, uuid.toString(), secure);
        return "btspp".equals(protocol)
                ? new BluetoothStream(connectionId)
                : new BluetoothPacket(connectionId);
    }

    private static final class StreamNotifier implements StreamConnectionNotifier, BluetoothServiceNotifier {
        private final int serverId;
        private final ServiceRecord record;
        private boolean closed;
        StreamNotifier(int serverId, ServiceRecord record) { this.serverId = serverId; this.record = record; }
        public synchronized StreamConnection acceptAndOpen() throws IOException {
            if (closed) throw new IOException("Bluetooth notifier is closed");
            return new BluetoothStream(BluetoothBridge.accept(serverId));
        }
        public ServiceRecord getServiceRecord() { return record; }
        public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            BluetoothBridge.close(serverId);
        }
    }

    private static final class L2capNotifier implements L2CAPConnectionNotifier, BluetoothServiceNotifier {
        private final int serverId;
        private final ServiceRecord record;
        private boolean closed;
        L2capNotifier(int serverId, ServiceRecord record) { this.serverId = serverId; this.record = record; }
        public synchronized L2CAPConnection acceptAndOpen() throws IOException {
            if (closed) throw new IOException("Bluetooth notifier is closed");
            return new BluetoothPacket(BluetoothBridge.accept(serverId));
        }
        public ServiceRecord getServiceRecord() { return record; }
        public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            BluetoothBridge.close(serverId);
        }
    }

    private abstract static class BluetoothConnectionBase implements Connection {
        final int handle;
        private boolean closed;
        BluetoothConnectionBase(int handle) { this.handle = handle; }
        public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            BluetoothBridge.close(handle);
        }
        void ensureOpen() throws IOException {
            if (closed) throw new IOException("Bluetooth connection is closed");
        }
        InputStream input() {
            return new InputStream() {
                public int read() throws IOException {
                    byte[] one = new byte[1];
                    int count = BluetoothBridge.read(handle, one, 1);
                    return count <= 0 ? count : one[0] & 0xff;
                }
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    if (bytes == null) throw new NullPointerException();
                    if (offset < 0 || length < 0 || offset > bytes.length - length)
                        throw new IndexOutOfBoundsException();
                    if (length == 0) return 0;
                    byte[] data = new byte[length];
                    int count = BluetoothBridge.read(handle, data, length);
                    if (count > 0) System.arraycopy(data, 0, bytes, offset, count);
                    return count;
                }
                public int available() throws IOException { return BluetoothBridge.available(handle); }
                public void close() { }
            };
        }
        OutputStream output() {
            return new OutputStream() {
                public void write(int value) throws IOException {
                    byte[] one = {(byte)value};
                    BluetoothBridge.write(handle, one, 0, 1);
                }
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    BluetoothBridge.write(handle, bytes, offset, length);
                }
                public void close() throws IOException { flush(); }
                public void flush() { }
            };
        }
    }

    private static final class BluetoothStream extends BluetoothConnectionBase implements StreamConnection {
        private InputStream input;
        private OutputStream output;
        BluetoothStream(int handle) { super(handle); }
        public synchronized InputStream openInputStream() throws IOException {
            ensureOpen();
            if (input != null) throw new IOException("Bluetooth input stream already opened");
            return input = input();
        }
        public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
        public synchronized OutputStream openOutputStream() throws IOException {
            ensureOpen();
            if (output != null) throw new IOException("Bluetooth output stream already opened");
            return output = output();
        }
        public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }
    }

    private static final class BluetoothPacket extends BluetoothConnectionBase implements L2CAPConnection {
        BluetoothPacket(int handle) { super(handle); }
        public int getTransmitMTU() throws IOException { ensureOpen(); return DEFAULT_MTU; }
        public int getReceiveMTU() throws IOException { ensureOpen(); return DEFAULT_MTU; }
        public void send(byte[] data) throws IOException {
            if (data == null) throw new NullPointerException("data");
            ensureOpen();
            BluetoothBridge.write(handle, data, 0, Math.min(data.length, DEFAULT_MTU));
        }
        public int receive(byte[] data) throws IOException {
            if (data == null) throw new NullPointerException("data");
            ensureOpen();
            if (data.length == 0) return 0;
            return BluetoothBridge.read(handle, data, Math.min(data.length, DEFAULT_MTU));
        }
        public boolean ready() throws IOException { ensureOpen(); return BluetoothBridge.available(handle) > 0; }
    }
}
