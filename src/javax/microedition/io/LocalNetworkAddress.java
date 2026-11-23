/*
 * This file is part of FreeJ2ME; licensed under GPL-3.0-or-later.
 */
package javax.microedition.io;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;

/** Finds a useful LAN address when a socket is bound to the wildcard address. */
final class LocalNetworkAddress {
    private LocalNetworkAddress() { }

    static String getAddress(InetAddress boundAddress) {
        if (boundAddress != null && !boundAddress.isAnyLocalAddress())
            return boundAddress.getHostAddress();

        InetAddress best = null;
        int bestScore = Integer.MIN_VALUE;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                try {
                    if (!network.isUp() || network.isLoopback()) continue;
                } catch (SocketException unavailable) {
                    continue;
                }

                String name = network.getName().toLowerCase(Locale.ROOT);
                int interfaceScore = name.startsWith("wlan") || name.contains("wifi") ? 100
                        : name.startsWith("ap") ? 80
                        : name.startsWith("eth") ? 60 : 0;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress candidate = addresses.nextElement();
                    if (!(candidate instanceof Inet4Address)
                            || candidate.isAnyLocalAddress()
                            || candidate.isLoopbackAddress()
                            || candidate.isLinkLocalAddress()) continue;
                    int score = interfaceScore + (candidate.isSiteLocalAddress() ? 100 : 0);
                    if (score > bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        } catch (SocketException unavailable) {
            // The caller still receives the socket's bound address below.
        }
        if (best != null) return best.getHostAddress();
        return boundAddress == null ? "0.0.0.0" : boundAddress.getHostAddress();
    }
}
