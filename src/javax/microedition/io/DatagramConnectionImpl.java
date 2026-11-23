/*
 * This file is part of FreeJ2ME; licensed under GPL-3.0-or-later.
 */
package javax.microedition.io;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.recompile.mobile.MemoryEditorGuard;

/** MIDP UDP datagram connection, including wildcard-bound discovery sockets. */
final class DatagramConnectionImpl implements UDPDatagramConnection {
    private final DatagramSocket socket;
    private final InetSocketAddress defaultAddress;
    private final int mode;

    DatagramConnectionImpl(String name, int mode) throws IOException {
        this.mode = mode;
        URI uri;
        try {
            uri = new URI(name);
        } catch (URISyntaxException error) {
            throw new ConnectionNotFoundException("Invalid datagram URL: " + name);
        }
        String authority = uri.getRawAuthority();
        String host = uri.getHost();
        int port = uri.getPort();
        if (port < 0 && host == null && authority != null && authority.startsWith(":")) {
            try { port = Integer.parseInt(authority.substring(1)); }
            catch (NumberFormatException error) {
                throw new ConnectionNotFoundException("Invalid datagram port: " + name);
            }
        }
        if (!"datagram".equalsIgnoreCase(uri.getScheme())
                || authority == null || authority.isEmpty()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                || port < 0 || port > 65535 || (host != null && port == 0)
                || (host == null && !authority.startsWith(":")))
            throw new ConnectionNotFoundException("Expected datagram://:port or datagram://host:port");

        InetSocketAddress remote = null;
        boolean bindLocal = host == null;
        if (host != null) {
            InetAddress address = InetAddress.getByName(host);
            bindLocal = address.isAnyLocalAddress();
            if (!bindLocal) remote = new InetSocketAddress(address, port);
        }
        DatagramSocket opened = new DatagramSocket(null);
        try {
            opened.setBroadcast(true);
            opened.bind(new InetSocketAddress(bindLocal ? port : 0));
            if (remote != null) opened.connect(remote);
        } catch (IOException | RuntimeException error) {
            opened.close();
            throw error;
        }
        socket = opened;
        defaultAddress = remote;
    }

    private void ensureOpen() throws IOException {
        if (socket.isClosed()) throw new IOException("Datagram connection is closed");
    }

    public int getMaximumLength() { return DatagramImpl.MAX_DATAGRAM_LENGTH; }
    public int getNominalLength() { return 1024; }
    public String getLocalAddress() {
        return LocalNetworkAddress.getAddress(socket.getLocalAddress());
    }
    public int getLocalPort() { return socket.getLocalPort(); }

    public Datagram newDatagram(byte[] buffer, int size) {
        return new DatagramImpl(buffer, size);
    }
    public Datagram newDatagram(byte[] buffer, int size, String address) {
        Datagram datagram = new DatagramImpl(buffer, size);
        datagram.setAddress(address);
        return datagram;
    }
    public Datagram newDatagram(int size) { return new DatagramImpl(size); }
    public Datagram newDatagram(int size, String address) {
        Datagram datagram = new DatagramImpl(size);
        datagram.setAddress(address);
        return datagram;
    }

    public void receive(Datagram datagram) throws IOException {
        ensureOpen();
        if ((mode & Connector.READ) == 0)
            throw new IOException("Datagram connection is not open for reading");
        if (datagram == null) throw new NullPointerException("Datagram is null");
        byte[] buffer = datagram.getData();
        int offset = datagram.getOffset();
        int capacity = buffer == null ? 0 : buffer.length - offset;
        if (capacity <= 0) throw new IOException("Datagram has no receive buffer");
        DatagramPacket packet = new DatagramPacket(buffer, offset, capacity);
        socket.receive(packet);
        String sender = DatagramImpl.formatAddress(packet.getAddress(), packet.getPort());
        if (datagram instanceof DatagramImpl)
            ((DatagramImpl) datagram).setReceived(packet.getLength(), sender);
        else {
            datagram.setLength(packet.getLength());
            datagram.setAddress(sender);
        }
    }

    public void send(Datagram datagram) throws IOException {
        ensureOpen();
        if ((mode & Connector.WRITE) == 0)
            throw new IOException("Datagram connection is not open for writing");
        if (datagram == null) throw new NullPointerException("Datagram is null");
        int length = datagram.getLength();
        byte[] buffer = datagram.getData();
        int offset = datagram.getOffset();
        if (buffer == null || offset < 0 || length < 0 || offset > buffer.length - length
                || length > DatagramImpl.MAX_DATAGRAM_LENGTH)
            throw new IOException("Invalid datagram payload");
        String targetUrl = datagram.getAddress();
        InetSocketAddress target = targetUrl == null ? defaultAddress
                : DatagramImpl.parseAddress(targetUrl);
        if (target == null) throw new IOException("Datagram destination is missing");
        if (socket.isConnected() && !target.equals(socket.getRemoteSocketAddress()))
            throw new IOException("Datagram destination differs from the connected peer");

        boolean external = MemoryEditorGuard.beginConnection(targetUrl == null
                ? DatagramImpl.formatAddress(target.getAddress(), target.getPort()) : targetUrl);
        try {
            socket.send(new DatagramPacket(buffer, offset, length, target));
        } finally {
            MemoryEditorGuard.finishConnection(external);
        }
    }

    public void close() throws IOException {
        if (!socket.isClosed()) socket.close();
    }
}
