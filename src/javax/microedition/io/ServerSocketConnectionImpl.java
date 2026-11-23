/*
 * This file is part of FreeJ2ME; licensed under GPL-3.0-or-later.
 */
package javax.microedition.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;

/** MIDP TCP listener for socket://:port URLs. */
final class ServerSocketConnectionImpl implements ServerSocketConnection {
    private final ServerSocket socket;

    static boolean isServerSocketUrl(String name) {
        if (name == null) return false;
        try {
            URI uri = new URI(name);
            String authority = uri.getRawAuthority();
            return "socket".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() == null && authority != null
                    && authority.startsWith(":") && parsePort(uri) >= 0
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (URISyntaxException error) {
            return false;
        }
    }

    ServerSocketConnectionImpl(String name) throws IOException {
        if (!isServerSocketUrl(name))
            throw new ConnectionNotFoundException("Expected socket://:port: " + name);
        URI uri;
        try {
            uri = new URI(name);
        } catch (URISyntaxException error) {
            throw new ConnectionNotFoundException("Invalid server socket URL: " + name);
        }
        int port = parsePort(uri);
        if (port < 0 || port > 65535)
            throw new ConnectionNotFoundException("Invalid server socket port: " + name);
        ServerSocket opened = new ServerSocket();
        try {
            opened.setReuseAddress(true);
            opened.bind(new InetSocketAddress(port), 32);
        } catch (IOException | RuntimeException error) {
            try { opened.close(); } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
        socket = opened;
    }

    private static int parsePort(URI uri) {
        int port = uri.getPort();
        if (port >= 0) return port;
        String authority = uri.getRawAuthority();
        if (uri.getHost() != null || authority == null || !authority.startsWith(":")) return -1;
        try { return Integer.parseInt(authority.substring(1)); }
        catch (NumberFormatException error) { return -1; }
    }

    public StreamConnection acceptAndOpen() throws IOException {
        if (socket.isClosed()) throw new IOException("Server socket is closed");
        return new SocketConnectionImpl(socket.accept(), Connector.READ_WRITE);
    }

    public String getLocalAddress() {
        return LocalNetworkAddress.getAddress(socket.getInetAddress());
    }

    public int getLocalPort() { return socket.getLocalPort(); }

    public void close() throws IOException {
        if (!socket.isClosed()) socket.close();
    }
}
