/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.recompile.mobile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Restricts memory editing after a game uses an external network connection. */
public final class MemoryEditorGuard {
    private static boolean editorOpen;
    private static boolean editorUsed;
    private static int pendingExternalConnections;

    private MemoryEditorGuard() { }

    public static synchronized void resetSession() {
        editorOpen = false;
        editorUsed = false;
        pendingExternalConnections = 0;
    }

    public static synchronized boolean openEditor(boolean networkAvailable) {
        if (pendingExternalConnections > 0) return false;
        if (networkAvailable && !editorUsed) return false;
        editorOpen = true;
        editorUsed = true;
        return true;
    }

    public static synchronized boolean isEditorOpen() {
        return editorOpen;
    }

    public static synchronized void closeEditor() {
        editorOpen = false;
    }

    public static synchronized void checkConnector(String connectionName) throws IOException {
        if (!isExternalConnection(connectionName)) return;
        rejectExternalConnectionWhileEditing();
    }

    /** Reserve an external connection while it is being established. */
    public static synchronized boolean beginConnection(String connectionName) throws IOException {
        if (!isExternalConnection(connectionName)) return false;
        rejectExternalConnectionWhileEditing();
        pendingExternalConnections++;
        return true;
    }

    /** Release a reservation after a connection attempt finishes. */
    public static synchronized void finishConnection(boolean external) {
        if (!external) return;
        if (pendingExternalConnections > 0) pendingExternalConnections--;
    }

    private static boolean isExternalConnection(String connectionName) {
        if (connectionName == null) return false;
        String value = connectionName.trim().toLowerCase(Locale.ROOT);
        int separator = value.indexOf(':');
        if (separator <= 0) return false;
        String scheme = value.substring(0, separator);
        if ("btspp".equals(scheme) || "btl2cap".equals(scheme)) {
            return true;
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)
                && !"ssl".equals(scheme) && !"socket".equals(scheme)) return false;

        String host;
        int port;
        try {
            URI uri = new URI(value);
            host = uri.getHost();
            port = uri.getPort();
        } catch (URISyntaxException error) {
            return false;
        }
        // Invalid URLs and local server-socket declarations are not outgoing connections.
        if (host == null || host.isEmpty() || port < -1 || port > 65535 || port == 0)
            return false;
        if ("socket".equals(scheme) && port < 1) return false;
        return !isLocalHost(host);
    }

    private static void rejectExternalConnectionWhileEditing() throws IOException {
        if (editorOpen)
            throw new IOException(
                    "Hãy đóng trình sửa bộ nhớ trước khi dùng kết nối mạng bên ngoài.");
        if (editorUsed)
            throw new IOException(
                    "Không thể dùng mạng bên ngoài trong phiên này vì trình sửa bộ nhớ đã được mở.");
    }

    private static boolean isLocalHost(String host) {
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]"))
            value = value.substring(1, value.length() - 1);
        int zone = value.indexOf('%');
        if (zone >= 0) value = value.substring(0, zone);
        if ("localhost".equals(value) || value.endsWith(".localhost")
                || value.endsWith(".local") || value.endsWith(".lan")
                || "0.0.0.0".equals(value)) return true;
        if (value.indexOf(':') >= 0) return isLocalIpv6(value);
        return isLocalIpv4(value);
    }

    private static boolean isLocalIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].isEmpty() || parts[index].length() > 3) return false;
            int value = 0;
            for (int character = 0; character < parts[index].length(); character++) {
                char digit = parts[index].charAt(character);
                if (digit < '0' || digit > '9') return false;
                value = value * 10 + digit - '0';
            }
            if (value > 255) return false;
            octets[index] = value;
        }
        int first = octets[0];
        return first == 0 || first == 10 || first == 127
                || (first == 169 && octets[1] == 254)
                || (first == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (first == 192 && octets[1] == 168);
    }

    private static boolean isLocalIpv6(String host) {
        if ("::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) return true;
        int separator = host.indexOf(':');
        String firstWord = separator < 0 ? host : host.substring(0, separator);
        if (firstWord.isEmpty()) return false;
        try {
            int prefix = Integer.parseInt(firstWord, 16);
            return (prefix & 0xfe00) == 0xfc00 || (prefix & 0xffc0) == 0xfe80;
        } catch (NumberFormatException error) {
            return false;
        }
    }
}
