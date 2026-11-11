package org.recompile.freej2me.lite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.tukaani.xz.XZInputStream;

/** Reader for the normalized, regular-file-only USTAR produced by prepare_runtime.py. */
final class RuntimeArchive {
    static void unpack(InputStream source, File root) throws IOException {
        String prefix = root.getCanonicalPath() + File.separator;
        long total = 0;
        try (InputStream owned = source;
             DataInputStream input = new DataInputStream(new XZInputStream(owned))) {
            byte[] header = new byte[512], buffer = new byte[65536];
            while (true) {
                input.readFully(header);
                boolean empty = true;
                for (byte b : header) if (b != 0) { empty = false; break; }
                if (empty) {
                    // Consume the padding/footer as well, so XZ validates its checksum.
                    while (input.read(buffer) != -1) {}
                    break;
                }
                long checksum = 0;
                for (int i = 0; i < header.length; i++)
                    checksum += i >= 148 && i < 156 ? 32 : header[i] & 255;
                if (checksum != octal(header, 148, 8)) throw new IOException("Runtime TAR checksum mismatch");
                String name = string(header, 0, 100), pathPrefix = string(header, 345, 155);
                if (!pathPrefix.isEmpty()) name = pathPrefix + "/" + name;
                long size = octal(header, 124, 12);
                total += size;
                if (size < 0 || size > 100L * 1024 * 1024 || total > 300L * 1024 * 1024)
                    throw new IOException("Runtime vượt giới hạn dung lượng");
                if (header[156] != 0 && header[156] != '0')
                    throw new IOException("Runtime chứa loại TAR không hỗ trợ");
                File destination = new File(root, name);
                if (name.isEmpty() || !destination.getCanonicalPath().startsWith(prefix))
                    throw new IOException("Runtime chứa đường dẫn không hợp lệ");
                File parent = destination.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Không tạo được thư mục runtime");
                try (OutputStream output = new FileOutputStream(destination)) {
                    long remaining = size;
                    while (remaining > 0) {
                        int n = input.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                        if (n < 0) throw new EOFException("Runtime bị cắt ngắn");
                        output.write(buffer, 0, n); remaining -= n;
                    }
                }
                int padding = (int)((512 - size % 512) % 512);
                input.readFully(header, 0, padding);
                if (name.endsWith(".so"))
                    if (!destination.setExecutable(true, true)) throw new IOException("Không cấp quyền runtime");
            }
        }
    }
    private static String string(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) end++;
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }
    private static long octal(byte[] bytes, int offset, int length) throws IOException {
        String number = string(bytes, offset, length).trim();
        try { return number.isEmpty() ? 0 : Long.parseLong(number, 8); }
        catch (NumberFormatException error) { throw new IOException("Runtime TAR number invalid", error); }
    }
}
