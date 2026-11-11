package org.recompile.mobile;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.recompile.freej2me.protocol.BluetoothProtocol;

/**
 * Request/response boundary for Android APIs that the embedded Java SE VM
 * cannot call directly. Every wait has a deadline so a dismissed system
 * dialog cannot strand a MIDlet worker forever.
 */
public final class BluetoothBridge {
    public interface Transport {
        void send(int id, int operation, int flags, int number, String value,
                String extra, byte[] data) throws IOException;
    }

    public static final class Reply {
        public final boolean accepted;
        public final String detail;
        public final int number;
        public final byte[] data;
        Reply(boolean accepted, String detail, int number, byte[] data) {
            this.accepted = accepted;
            this.detail = detail == null ? "" : detail;
            this.number = number;
            this.data = data == null ? new byte[0] : data;
        }
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, CompletableFuture<Reply>> PENDING =
            new ConcurrentHashMap<Integer, CompletableFuture<Reply>>();
    private static final ExecutorService EVENTS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BluetoothEvents");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Transport transport;

    private BluetoothBridge() { }

    public static void setTransport(Transport value) { transport = value; }
    public static boolean hasTransport() { return transport != null; }

    public static Reply request(int operation, int flags, String value) {
        return request(operation, flags, 0, value, "", new byte[0], 75000L);
    }

    public static Reply request(int operation, int flags, int number, String value,
            String extra, byte[] data) {
        return request(operation, flags, number, value, extra, data, 75000L);
    }

    public static Reply request(int operation, int flags, int number, String value,
            String extra, byte[] data, long timeoutMillis) {
        Transport current = transport;
        if (current == null) {
            System.out.println("[bluetooth] host transport unavailable operation=" + operation
                    + " bridgeLoader=" + BluetoothBridge.class.getClassLoader());
            return new Reply(false, "Bluetooth host is unavailable", 0, null);
        }

        int id = NEXT_ID.incrementAndGet();
        CompletableFuture<Reply> result = new CompletableFuture<Reply>();
        PENDING.put(id, result);
        try {
            current.send(id, operation, flags, number, value == null ? "" : value,
                    extra == null ? "" : extra, data == null ? new byte[0] : data);
            return timeoutMillis <= 0 ? result.get() : result.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new Reply(false, "Bluetooth request interrupted", 0, null);
        } catch (Exception error) {
            return new Reply(false, "Bluetooth request failed: " + error, 0, null);
        } finally {
            PENDING.remove(id);
        }
    }

    private static Reply requestChecked(int operation, int flags, int number, String value,
            String extra, byte[] data, long timeoutMillis) throws IOException {
        Reply reply = request(operation, flags, number, value, extra, data, timeoutMillis);
        if (!reply.accepted) throw new IOException(reply.detail.length() == 0
                ? "Bluetooth operation failed" : reply.detail);
        return reply;
    }

    public static int openServer(String protocol, String serviceName, String uuid, boolean secure)
            throws IOException {
        Reply reply = requestChecked(BluetoothProtocol.OPEN_SERVER,
                BluetoothProtocol.NEED_CONNECT | BluetoothProtocol.NEED_ADVERTISE,
                secure ? 1 : 0, protocol + ":" + serviceName, uuid, null, 75000L);
        return reply.number;
    }

    public static int openClient(String protocol, String address, String uuid, boolean secure)
            throws IOException {
        int protocolId = "btl2cap".equals(protocol) ? 2 : 1;
        Reply reply = requestChecked(BluetoothProtocol.OPEN_CLIENT,
                BluetoothProtocol.NEED_CONNECT, protocolId | (secure ? 0x100 : 0),
                address, uuid, null, 75000L);
        return reply.number;
    }

    public static int accept(int serverId) throws IOException {
        return requestChecked(BluetoothProtocol.ACCEPT, BluetoothProtocol.NEED_CONNECT,
                serverId, "", "", null, 0L).number;
    }

    public static int read(int connectionId, byte[] data, int length) throws IOException {
        if (data == null) throw new NullPointerException("data");
        if (length < 0 || length > data.length) throw new IndexOutOfBoundsException();
        Reply reply = requestChecked(BluetoothProtocol.READ, BluetoothProtocol.NEED_CONNECT,
                connectionId, "", Integer.toString(length), new byte[0], 0L);
        int count = Math.min(length, reply.data.length);
        System.arraycopy(reply.data, 0, data, 0, count);
        return reply.number;
    }

    public static void write(int connectionId, byte[] data, int offset, int length) throws IOException {
        if (data == null) throw new NullPointerException("data");
        if (offset < 0 || length < 0 || offset > data.length - length)
            throw new IndexOutOfBoundsException();
        byte[] payload = new byte[length];
        System.arraycopy(data, offset, payload, 0, length);
        requestChecked(BluetoothProtocol.WRITE, BluetoothProtocol.NEED_CONNECT,
                connectionId, "", "", payload, 75000L);
    }

    public static int available(int connectionId) throws IOException {
        return requestChecked(BluetoothProtocol.AVAILABLE, BluetoothProtocol.NEED_CONNECT,
                connectionId, "", "", null, 75000L).number;
    }

    public static void close(int handle) throws IOException {
        requestChecked(BluetoothProtocol.CLOSE, 0,
                handle, "", "", null, 75000L);
    }

    public static void complete(int id, boolean accepted, int number, String detail, byte[] data) {
        CompletableFuture<Reply> result = PENDING.get(id);
        if (result != null) result.complete(new Reply(accepted, detail, number, data));
    }

    public static void dispatchEvent(final int event, final String address, final String name,
            final int deviceClass, final int result) {
        EVENTS.execute(() -> {
            if (event == BluetoothProtocol.DEVICE_FOUND) {
                javax.bluetooth.DiscoveryAgent.deviceFound(address, name, deviceClass);
            } else if (event == BluetoothProtocol.INQUIRY_FINISHED) {
                javax.bluetooth.DiscoveryAgent.inquiryFinished(result);
            } else if (event == BluetoothProtocol.UUIDS_FOUND) {
                javax.bluetooth.DiscoveryAgent.servicesFound(address, name);
            }
        });
    }
}
