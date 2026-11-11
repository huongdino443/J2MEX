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
import java.util.Locale;

/** Restricts the managed-memory editor to sessions that have not used J2ME networking. */
public final class MemoryEditorGuard {
    private static boolean networkUsed;
    private static boolean editorOpen;
    private static boolean editorUsed;

    private MemoryEditorGuard() { }

    public static synchronized void resetSession() {
        networkUsed = false;
        editorOpen = false;
        editorUsed = false;
    }

    public static synchronized boolean openEditor() {
        if (networkUsed) return false;
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
        if (connectionName == null) return;
        String value = connectionName.toLowerCase(Locale.ROOT);
        boolean network = value.startsWith("http:")
                || value.startsWith("https:")
                || value.startsWith("ssl:")
                || value.startsWith("socket:")
                || value.startsWith("datagram:")
                || value.startsWith("btspp:")
                || value.startsWith("btl2cap:");
        if (!network) return;
        if (editorOpen)
            throw new IOException("Hãy đóng trình sửa bộ nhớ trước khi dùng kết nối mạng.");
        if (editorUsed)
            throw new IOException("Không thể dùng mạng trong phiên này vì trình sửa bộ nhớ đã được mở.");
        networkUsed = true;
    }
}
