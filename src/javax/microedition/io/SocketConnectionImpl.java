/*
 * This file is part of FreeJ2ME; licensed under GPL-3.0-or-later.
 */
package javax.microedition.io;

import java.io.*;
import java.net.*;

/** Outbound MIDP TCP connection. Handles and streams own separate references. */
class SocketConnectionImpl implements SocketConnection {
    protected final Socket socket;
    private final int mode;
    private boolean closed;
    private boolean inputOpened, outputOpened;
    private int references = 1;

    SocketConnectionImpl(Socket socket, int mode) {
        this.socket = socket;
        this.mode = mode;
    }

    SocketConnectionImpl(String name, int mode, boolean timeouts) throws IOException {
        URI uri;
        try { uri = new URI(name); }
        catch (URISyntaxException error) {
            throw new ConnectionNotFoundException("Invalid socket URL: " + name);
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (!"socket".equalsIgnoreCase(uri.getScheme()) || host == null || host.length() == 0
                || port < 1 || port > 65535 || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new ConnectionNotFoundException("Expected socket://host:port: " + name);
        }
        this.mode = mode;
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeouts ? 15000 : 0);
            // Do not impose a read deadline: online games may legitimately idle.
        } catch (IOException | RuntimeException error) {
            try { socket.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("Socket connection handle is closed");
    }

    private synchronized void release() throws IOException {
        if (--references == 0) socket.close();
    }

    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        release();
    }

    public synchronized InputStream openInputStream() throws IOException {
        ensureOpen();
        if ((mode & Connector.READ) == 0) throw new IOException("Socket is not open for reading");
        if (inputOpened) throw new IOException("Socket input stream already opened");
        final InputStream input = socket.getInputStream();
        inputOpened = true;
        references++;
        return new InputStream() {
            private volatile boolean ended;
            private void check() throws IOException {
                if (ended) throw new IOException("Socket input stream is closed");
            }
            public int read() throws IOException { check(); return input.read(); }
            public int read(byte[] bytes, int offset, int length) throws IOException {
                check(); return input.read(bytes, offset, length);
            }
            public int available() throws IOException { check(); return input.available(); }
            public long skip(long count) throws IOException { check(); return input.skip(count); }
            public synchronized void close() throws IOException {
                if (ended) return;
                ended = true;
                try {
                    if (!socket.isClosed() && !socket.isInputShutdown()) socket.shutdownInput();
                } finally { release(); }
            }
        };
    }

    public DataInputStream openDataInputStream() throws IOException {
        return new DataInputStream(openInputStream());
    }

    public synchronized OutputStream openOutputStream() throws IOException {
        ensureOpen();
        if ((mode & Connector.WRITE) == 0) throw new IOException("Socket is not open for writing");
        if (outputOpened) throw new IOException("Socket output stream already opened");
        final OutputStream output = socket.getOutputStream();
        outputOpened = true;
        references++;
        return new OutputStream() {
            private volatile boolean ended;
            private void check() throws IOException {
                if (ended) throw new IOException("Socket output stream is closed");
            }
            public void write(int value) throws IOException { check(); output.write(value); }
            public void write(byte[] bytes, int offset, int length) throws IOException {
                check(); output.write(bytes, offset, length);
            }
            public void flush() throws IOException { check(); output.flush(); }
            public synchronized void close() throws IOException {
                if (ended) return;
                ended = true;
                try {
                    if (!socket.isClosed() && !socket.isOutputShutdown()) socket.shutdownOutput();
                } finally { release(); }
            }
        };
    }

    public DataOutputStream openDataOutputStream() throws IOException {
        return new DataOutputStream(openOutputStream());
    }

    public synchronized String getAddress() throws IOException { ensureOpen(); return socket.getInetAddress().getHostAddress(); }
    public synchronized String getLocalAddress() throws IOException { ensureOpen(); return socket.getLocalAddress().getHostAddress(); }
    public synchronized int getPort() throws IOException { ensureOpen(); return socket.getPort(); }
    public synchronized int getLocalPort() throws IOException { ensureOpen(); return socket.getLocalPort(); }

    public synchronized int getSocketOption(byte option) throws IOException {
        ensureOpen();
        switch (option) {
            case DELAY: return socket.getTcpNoDelay() ? 0 : 1;
            case KEEPALIVE: return socket.getKeepAlive() ? 1 : 0;
            case LINGER: return Math.max(0, socket.getSoLinger());
            case RCVBUF: return socket.getReceiveBufferSize();
            case SNDBUF: return socket.getSendBufferSize();
            default: throw new IllegalArgumentException("Unknown socket option: " + option);
        }
    }

    public synchronized void setSocketOption(byte option, int value) throws IOException {
        ensureOpen();
        if (value < 0) throw new IllegalArgumentException("Negative socket option value");
        switch (option) {
            case DELAY:
                if (value > 1) throw new IllegalArgumentException("DELAY must be 0 or 1");
                socket.setTcpNoDelay(value == 0); break;
            case KEEPALIVE:
                if (value > 1) throw new IllegalArgumentException("KEEPALIVE must be 0 or 1");
                socket.setKeepAlive(value != 0); break;
            case LINGER:
                if (value > 65535) throw new IllegalArgumentException("LINGER exceeds 65535 seconds");
                socket.setSoLinger(value != 0, value); break;
            case RCVBUF:
                if (value == 0) throw new IllegalArgumentException("RCVBUF must be positive");
                socket.setReceiveBufferSize(value); break;
            case SNDBUF:
                if (value == 0) throw new IllegalArgumentException("SNDBUF must be positive");
                socket.setSendBufferSize(value); break;
            default: throw new IllegalArgumentException("Unknown socket option: " + option);
        }
    }
}
