/*
 * This file is part of FreeJ2ME; licensed under GPL-3.0-or-later.
 */
package javax.microedition.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** MIDP {@code ssl://} client connection with certificate and host verification. */
final class SecureConnectionImpl extends SocketConnectionImpl implements SecureConnection {
    private final SecurityInfo securityInfo;

    SecureConnectionImpl(String name, int mode, boolean timeouts) throws IOException {
        this(openSocket(name, timeouts), mode);
    }

    private SecureConnectionImpl(SSLSocket socket, int mode) {
        super(socket, mode);
        securityInfo = securityInfo(socket.getSession());
    }

    public SecurityInfo getSecurityInfo() {
        return securityInfo;
    }

    private static SSLSocket openSocket(String name, boolean timeouts) throws IOException {
        final URI uri;
        try {
            uri = new URI(name);
        } catch (URISyntaxException error) {
            throw new ConnectionNotFoundException("Invalid SSL URL: " + name);
        }

        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        if (!"ssl".equalsIgnoreCase(uri.getScheme()) || host == null || host.length() == 0
                || port < 1 || port > 65535 || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new ConnectionNotFoundException("Expected ssl://host[:port]: " + name);
        }

        Socket transport = new Socket();
        SSLSocket secure = null;
        try {
            transport.connect(new InetSocketAddress(host, port), timeouts ? 15000 : 0);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            secure = (SSLSocket) factory.createSocket(transport, host, port, true);

            SSLParameters parameters = secure.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secure.setSSLParameters(parameters);
            if (timeouts) secure.setSoTimeout(15000);
            secure.startHandshake();
            if (timeouts) secure.setSoTimeout(0);
            System.out.println("[MIDP SSL] " + host + ":" + port + " connected using "
                    + secure.getSession().getProtocol() + " "
                    + secure.getSession().getCipherSuite());
            return secure;
        } catch (IOException | RuntimeException error) {
            System.out.println("[MIDP SSL] " + host + ":" + port + " failed: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            try {
                if (secure != null) secure.close();
                else transport.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    private static SecurityInfo securityInfo(SSLSession session) {
        final X509Certificate serverCertificate = peerCertificate(session);
        return new SecurityInfo() {
            public String getCipherSuite() {
                return session.getCipherSuite();
            }

            public String getProtocolName() {
                return "TLS";
            }

            public String getProtocolVersion() {
                return session.getProtocol();
            }

            public javax.microedition.pki.Certificate getServerCertificate() {
                if (serverCertificate == null) return null;
                return new javax.microedition.pki.Certificate() {
                    public String getSubject() {
                        return serverCertificate.getSubjectX500Principal().getName();
                    }

                    public String getIssuer() {
                        return serverCertificate.getIssuerX500Principal().getName();
                    }

                    public String getType() {
                        return serverCertificate.getType();
                    }

                    public String getVersion() {
                        return Integer.toString(serverCertificate.getVersion());
                    }

                    public String getSigAlgName() {
                        return serverCertificate.getSigAlgName();
                    }

                    public long getNotBefore() {
                        return serverCertificate.getNotBefore().getTime();
                    }

                    public long getNotAfter() {
                        return serverCertificate.getNotAfter().getTime();
                    }

                    public String getSerialNumber() {
                        return serverCertificate.getSerialNumber().toString(16);
                    }
                };
            }
        };
    }

    private static X509Certificate peerCertificate(SSLSession session) {
        try {
            java.security.cert.Certificate[] certificates = session.getPeerCertificates();
            return certificates.length > 0 && certificates[0] instanceof X509Certificate
                    ? (X509Certificate) certificates[0] : null;
        } catch (SSLPeerUnverifiedException error) {
            return null;
        }
    }
}
