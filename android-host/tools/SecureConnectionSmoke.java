package org.recompile.mobile;

import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.SecureConnection;
import javax.microedition.io.SecurityInfo;

public final class SecureConnectionSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Pass one ssl://host[:port] URL");
        }

        Connection connection = Connector.open(args[0], Connector.READ_WRITE, true);
        try {
            require(connection instanceof SecureConnection,
                    "ssl:// must return a MIDP SecureConnection");
            SecureConnection secure = (SecureConnection) connection;
            SecurityInfo security = secure.getSecurityInfo();
            require(security != null, "TLS negotiation must provide SecurityInfo");
            require(security.getCipherSuite() != null && !security.getCipherSuite().isEmpty(),
                    "TLS cipher suite must be available");
            require("TLS".equals(security.getProtocolName()),
                    "protocol name must be TLS");
            require(security.getProtocolVersion() != null,
                    "negotiated TLS version must be available");
            require(security.getServerCertificate() != null,
                    "verified peer certificate must be available");
            System.out.println("SecureConnectionSmoke: verified " + security.getProtocolVersion()
                    + " " + security.getCipherSuite());
        } finally {
            connection.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
