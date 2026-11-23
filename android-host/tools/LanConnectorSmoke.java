import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.UDPDatagramConnection;

/** Loopback tests for the MIDP UDP and server-socket APIs used by Near Transfer. */
public final class LanConnectorSmoke {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        testDatagrams();
        testServerSocket();
        System.out.println("LanConnectorSmoke: " + checks
                + " checks passed; MIDP UDP discovery packets and inbound TCP");
    }

    private static void testDatagrams() throws Exception {
        UDPDatagramConnection receiver = (UDPDatagramConnection)
                Connector.open("datagram://:0", Connector.READ_WRITE);
        UDPDatagramConnection sender = (UDPDatagramConnection)
                Connector.open("datagram://:0", Connector.READ_WRITE);
        try {
            check(receiver.getLocalPort() > 0 && sender.getLocalPort() > 0,
                    "wildcard datagram URLs bind ephemeral UDP ports");
            check(receiver.getLocalAddress() != null && !receiver.getLocalAddress().isEmpty(),
                    "bound UDP sockets report a local interface address");

            byte[] discover = "NWS1|DISCOVER|45321|smoke".getBytes("UTF-8");
            Datagram outgoing = sender.newDatagram(discover, discover.length,
                    "datagram://127.0.0.1:" + receiver.getLocalPort());
            sender.send(outgoing);
            Datagram incoming = receiver.newDatagram(1024);
            receiver.receive(incoming);
            check(incoming.getLength() == discover.length,
                    "UDP receive records the exact packet length");
            check(Arrays.equals(discover, Arrays.copyOfRange(incoming.getData(),
                    incoming.getOffset(), incoming.getOffset() + incoming.getLength())),
                    "UDP payload preserves the Near Transfer discovery packet");
            check(incoming.getAddress().equals(
                    "datagram://127.0.0.1:" + sender.getLocalPort()),
                    "received datagram exposes its sender URL");

            byte[] peer = "NWS1|PEER|45321|smoke".getBytes("UTF-8");
            Datagram response = receiver.newDatagram(peer, peer.length);
            response.setAddress(incoming);
            receiver.send(response);
            Datagram reply = sender.newDatagram(1024);
            sender.receive(reply);
            check(Arrays.equals(peer, Arrays.copyOfRange(reply.getData(),
                    reply.getOffset(), reply.getOffset() + reply.getLength())),
                    "peer response can reuse the received datagram address");

            Datagram encoded = sender.newDatagram(64);
            encoded.writeUTF("NWS1");
            encoded.writeInt(45321);
            Datagram decoded = sender.newDatagram(
                    Arrays.copyOf(encoded.getData(), encoded.getLength()), encoded.getLength());
            check("NWS1".equals(decoded.readUTF()) && decoded.readInt() == 45321,
                    "datagram DataInput/DataOutput uses Java ME binary encoding");
        } finally {
            receiver.close();
            sender.close();
        }
    }

    private static void testServerSocket() throws Exception {
        final ServerSocketConnection listener = (ServerSocketConnection)
                Connector.open("socket://:0", Connector.READ_WRITE);
        final AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        Thread acceptor = new Thread(() -> {
            try {
                StreamConnection accepted = listener.acceptAndOpen();
                try {
                    DataInputStream input = accepted.openDataInputStream();
                    DataOutputStream output = accepted.openDataOutputStream();
                    int value = input.readInt();
                    output.writeInt(value + 1);
                    output.flush();
                    output.close();
                    input.close();
                } finally {
                    accepted.close();
                }
            } catch (Throwable error) {
                serverFailure.set(error);
            }
        }, "NearTransferLocalTcpServer");
        acceptor.setDaemon(true);
        acceptor.start();

        try {
            check(listener.getLocalPort() > 0, "socket://:port opens a TCP listener");
            check(listener.getLocalAddress() != null && !listener.getLocalAddress().isEmpty(),
                    "TCP listener reports a local interface address");
            SocketConnection client = (SocketConnection) Connector.open(
                    "socket://127.0.0.1:" + listener.getLocalPort());
            try {
                DataInputStream input = client.openDataInputStream();
                DataOutputStream output = client.openDataOutputStream();
                output.writeInt(45321);
                output.flush();
                check(input.readInt() == 45322, "accepted TCP connection exchanges binary data");
                output.close();
                input.close();
            } finally {
                client.close();
            }
            acceptor.join(3000);
            check(!acceptor.isAlive(), "server accept worker finishes after one peer");
            if (serverFailure.get() != null)
                throw new AssertionError("TCP listener worker failed", serverFailure.get());
        } finally {
            listener.close();
        }
    }
}
