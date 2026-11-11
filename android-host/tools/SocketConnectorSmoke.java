import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.microedition.io.*;
import org.recompile.mobile.MIDletLoader;

/** Offline TCP integration tests; optional real JAR wrapper uses loopback only. */
public final class SocketConnectorSmoke {
    private static int checks;
    private static final int HELLO = 0x12345678;
    private interface Action { void run() throws Exception; }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void io(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected IOException"); }
        catch (IOException expected) { checks++; }
    }
    private static void badOption(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        final Set<Socket> peers = Collections.synchronizedSet(new HashSet<Socket>());
        final ServerSocket server = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        Thread acceptor = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    final Socket peer = server.accept();
                    peers.add(peer);
                    Thread worker = new Thread(() -> {
                        try (Socket socket = peer) {
                            socket.setSoTimeout(3000);
                            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                            output.writeInt(HELLO);
                            output.flush();
                            InputStream input = socket.getInputStream();
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = input.read(buffer)) != -1) {
                                output.write(buffer, 0, length);
                                output.flush();
                            }
                        } catch (IOException clientClosed) {
                            // Negative tests deliberately abort sockets.
                        } finally { peers.remove(peer); }
                    }, "LocalTcpPeer");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException stopped) { if (server.isClosed()) return; }
            }
        }, "LocalTcpServer");
        acceptor.setDaemon(true);
        acceptor.start();
        String url = "socket://127.0.0.1:" + server.getLocalPort();
        try {
            final SocketConnection connection = (SocketConnection)Connector.open(url);
            check(connection.getAddress().equals("127.0.0.1"), "remote address");
            check(connection.getLocalAddress().equals("127.0.0.1"), "local address");
            check(connection.getPort() == server.getLocalPort(), "remote port");
            check(connection.getLocalPort() > 0, "local port");
            connection.setSocketOption(SocketConnection.DELAY, 0);
            check(connection.getSocketOption(SocketConnection.DELAY) == 0, "TCP_NODELAY inversion");
            connection.setSocketOption(SocketConnection.DELAY, 1);
            check(connection.getSocketOption(SocketConnection.DELAY) == 1, "Nagle enabled");
            connection.setSocketOption(SocketConnection.KEEPALIVE, 1);
            check(connection.getSocketOption(SocketConnection.KEEPALIVE) == 1, "keepalive enabled");
            connection.setSocketOption(SocketConnection.KEEPALIVE, 0);
            check(connection.getSocketOption(SocketConnection.KEEPALIVE) == 0, "keepalive disabled");
            connection.setSocketOption(SocketConnection.LINGER, 2);
            check(connection.getSocketOption(SocketConnection.LINGER) == 2, "linger seconds");
            connection.setSocketOption(SocketConnection.LINGER, 0);
            check(connection.getSocketOption(SocketConnection.LINGER) == 0, "linger disabled");
            connection.setSocketOption(SocketConnection.RCVBUF, 8192);
            connection.setSocketOption(SocketConnection.SNDBUF, 8192);
            check(connection.getSocketOption(SocketConnection.RCVBUF) > 0, "receive buffer");
            check(connection.getSocketOption(SocketConnection.SNDBUF) > 0, "send buffer");
            badOption(() -> connection.setSocketOption(SocketConnection.DELAY, 2));
            badOption(() -> connection.setSocketOption(SocketConnection.KEEPALIVE, -1));
            badOption(() -> connection.setSocketOption(SocketConnection.RCVBUF, 0));
            badOption(() -> connection.setSocketOption(SocketConnection.SNDBUF, 0));
            badOption(() -> connection.setSocketOption(SocketConnection.LINGER, 65536));
            badOption(() -> connection.getSocketOption((byte)99));
            DataInputStream input = connection.openDataInputStream();
            DataOutputStream output = connection.openDataOutputStream();
            check(input.readInt() == HELLO, "real inbound binary data");
            io(() -> connection.openInputStream());
            io(() -> connection.openOutputStream());
            output.writeInt(0x89abcdef);
            output.writeUTF("Xin chào — socket");
            output.write(new byte[]{0, (byte)128, (byte)255});
            output.flush();
            check(input.readInt() == 0x89abcdef, "binary int roundtrip");
            check(input.readUTF().equals("Xin chào — socket"), "Unicode data stream roundtrip");
            check(input.readUnsignedByte() == 0 && input.readUnsignedByte() == 128
                    && input.readUnsignedByte() == 255, "unsigned binary roundtrip");
            connection.close();
            connection.close();
            io(() -> connection.getPort());
            io(() -> connection.openInputStream());
            output.writeInt(21);
            output.flush();
            check(input.readInt() == 21, "existing streams survive connection-handle close");
            output.close();
            output.close();
            check(input.read() == -1, "peer EOF is minus one, not fabricated zero");
            input.close();
            input.close();
            io(() -> input.read());
            io(() -> output.writeByte(1));

            SocketConnection readOnly = (SocketConnection)Connector.open(url, Connector.READ);
            io(() -> readOnly.openOutputStream());
            try (DataInputStream stream = readOnly.openDataInputStream()) {
                check(stream.readInt() == HELLO, "READ mode inbound data");
            } finally { readOnly.close(); }
            SocketConnection writeOnly = (SocketConnection)Connector.open(url, Connector.WRITE, false);
            io(() -> writeOnly.openInputStream());
            writeOnly.openOutputStream().close();
            writeOnly.close();
            try (DataInputStream stream = Connector.openDataInputStream(url)) {
                check(stream.readInt() == HELLO, "Connector data-input convenience is real");
            }
            try (InputStream stream = Connector.openInputStream(url)) {
                check(new DataInputStream(stream).readInt() == HELLO, "Connector raw-input convenience is real");
            }
            try (DataOutputStream stream = Connector.openDataOutputStream(url)) {
                stream.writeInt(42);
            }
            try (OutputStream stream = Connector.openOutputStream(url)) { stream.write(new byte[16]); }
            checks += 2;
            for (String malformed : new String[]{"socket://localhost", "socket://:0",
                    "socket://localhost:0", "socket://localhost:65536", "socket://localhost:1/a",
                    "socket://user@localhost:1", "socket://localhost:1?q=1",
                    "socket://localhost:1#fragment", "socket://bad host:1"}) {
                try { Connector.open(malformed); throw new AssertionError("Accepted " + malformed); }
                catch (ConnectionNotFoundException expected) { checks++; }
            }
            badOption(() -> Connector.open(url, 0));
            badOption(() -> Connector.open(null));
            badOption(() -> Connector.openInputStream(null));
            int refusedPort;
            try (ServerSocket unavailable = new ServerSocket(0)) { refusedPort = unavailable.getLocalPort(); }
            try {
                Connector.open("socket://127.0.0.1:" + refusedPort);
                throw new AssertionError("Expected refused connection");
            } catch (ConnectionNotFoundException wrong) {
                throw new AssertionError("A TCP refusal must not be reported as unsupported URL", wrong);
            } catch (IOException expected) { checks++; }

            final SocketConnection blocked = (SocketConnection)Connector.open(url);
            final DataInputStream waiting = blocked.openDataInputStream();
            check(waiting.readInt() == HELLO, "blocking-read fixture ready");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Integer> result = executor.submit(() -> {
                    try { return waiting.read(); } catch (IOException closed) { return -2; }
                });
                try {
                    result.get(50, TimeUnit.MILLISECONDS);
                    throw new AssertionError("Expected a pending read before closing");
                } catch (TimeoutException expected) { checks++; }
                blocked.close();
                waiting.close();
                check(result.get(2, TimeUnit.SECONDS) < 0, "closing stream unblocks pending read");
            } finally { waiting.close(); blocked.close(); executor.shutdownNow(); }

            if (args.length > 0) {
                MIDletLoader loader = new MIDletLoader(new URL[]{new File(args[0]).toURI().toURL()});
                Class<?> wrapperClass = loader.loadClass("cl");
                Object wrapper = wrapperClass.getConstructor(String.class, int.class)
                        .newInstance("127.0.0.1", server.getLocalPort());
                DataInputStream gameInput = (DataInputStream)wrapperClass.getMethod("c").invoke(wrapper);
                DataOutputStream gameOutput = (DataOutputStream)wrapperClass.getMethod("b").invoke(wrapper);
                check(gameInput != null && gameOutput != null, "actual KnightOnline wrapper opens streams");
                try {
                    check(gameInput.readInt() == HELLO, "actual JAR wrapper receives TCP data");
                    gameOutput.writeUTF("KnightOnline local test");
                    gameOutput.flush();
                    check(gameInput.readUTF().equals("KnightOnline local test"), "actual JAR wrapper TCP roundtrip");
                } finally {
                    gameInput.close(); gameOutput.close();
                    wrapperClass.getMethod("a").invoke(wrapper);
                }
            }
            System.out.println("SocketConnectorSmoke: " + checks
                    + " checks passed; loopback TCP, streams, options, modes, EOF, close, failures"
                    + (args.length > 0 ? ", real KnightOnline wrapper; no remote server" : ""));
        } finally {
            server.close();
            synchronized (peers) {
                for (Socket peer : peers) try { peer.close(); } catch (IOException ignored) { }
            }
        }
    }
}
