package org.recompile.freej2me.lite;

import android.content.Context;
import android.os.Environment;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Persistent shared-storage layout for imported games and their private data. */
final class GameStorage {
    private static final String APP_FOLDER = "J2MEX";
    private static final String LOCATION_FILE = ".j2mex-storage-location";
    private static final String ROOT_MARKER = ".j2mex-root";
    private static final String ROOT_MARKER_VALUE = "J2MEX shared game storage v1";
    private static final String PREFERENCES = "j2mex_storage";
    private static final String ROOT_KEY = "root_path";

    private GameStorage() { }

    static synchronized File initialize(Context context) throws IOException {
        File root = root(context);
        ensureDirectory(root);
        ensureDirectory(new File(root, "games"));
        ensureDirectory(new File(root, ".staging"));

        File rootMarker = new File(root, ROOT_MARKER);
        if (!rootMarker.isFile())
            writeAtomic(rootMarker, ROOT_MARKER_VALUE.getBytes(StandardCharsets.UTF_8));

        File defaultRoot = defaultRoot();
        ensureDirectory(defaultRoot);
        File locationFile = new File(defaultRoot, LOCATION_FILE);
        writeAtomic(locationFile, (root.getCanonicalPath() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(ROOT_KEY, root.getCanonicalPath()).apply();
        return root;
    }

    static synchronized File root(Context context) throws IOException {
        File fallback = defaultRoot();
        File locationFile = new File(fallback, LOCATION_FILE);
        String path = hasAtomicFile(locationFile) ? readText(locationFile) : null;
        if (path == null || path.trim().isEmpty()) {
            path = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(ROOT_KEY, null);
        }

        File candidate = path == null || path.trim().isEmpty()
                ? fallback : new File(path.trim());
        File canonical = validatePath(candidate, candidate.equals(fallback));
        if (canonical.exists() && (!canonical.isDirectory()
                || !canonical.canRead() || !canonical.canWrite())) {
            throw new IOException("Thư mục lưu game không đọc/ghi được.");
        }
        if (!canonical.exists() && !canonical.equals(fallback)) {
            throw new IOException("Không tìm thấy thư mục lưu game đã chọn.");
        }
        return canonical;
    }

    static synchronized File setRoot(Context context, File selected) throws IOException {
        File current = initialize(context);
        File target = validatePath(selected, false);
        ensureDirectory(target);
        ensureWritable(target);
        if (current.equals(target)) return current;

        if (directoryHasEntries(new File(current, "games"))) {
            throw new IOException("Thư mục hiện tại còn game hoặc dữ liệu lưu. "
                    + "Không thể đổi vị trí mà không di chuyển dữ liệu.");
        }

        File marker = new File(target, ROOT_MARKER);
        if (marker.isFile() && !ROOT_MARKER_VALUE.equals(readText(marker).trim())) {
            throw new IOException("Thư mục đã được dùng bởi một định dạng J2MEX khác.");
        }
        writeAtomic(marker, ROOT_MARKER_VALUE.getBytes(StandardCharsets.UTF_8));
        ensureDirectory(new File(target, "games"));
        ensureDirectory(new File(target, ".staging"));

        File defaultRoot = defaultRoot();
        ensureDirectory(defaultRoot);
        writeAtomic(new File(defaultRoot, LOCATION_FILE),
                (target.getCanonicalPath() + "\n").getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(ROOT_KEY, target.getCanonicalPath()).apply();
        return target;
    }

    static File gamesDirectory(Context context) throws IOException {
        File directory = new File(root(context), "games");
        ensureDirectory(directory);
        return directory;
    }

    static File stagingDirectory(Context context) throws IOException {
        File directory = new File(root(context), ".staging");
        ensureDirectory(directory);
        return directory;
    }

    static File libraryFile(Context context) throws IOException {
        return new File(root(context), "library.json");
    }

    static void resetGameData(Context context, File jar) throws IOException {
        if (jar == null || !jar.isFile() || !"game.jar".equals(jar.getName())) {
            throw new IOException("Không tìm thấy game để đặt lại dữ liệu.");
        }
        File gamesRoot = gamesDirectory(context).getCanonicalFile();
        File canonicalJar = jar.getCanonicalFile();
        File gameDirectory = canonicalJar.getParentFile();
        if (gameDirectory == null || !gamesRoot.equals(gameDirectory.getParentFile())) {
            throw new IOException("Thư mục game không hợp lệ.");
        }

        File dataDirectory = new File(gameDirectory, "data");
        if (!dataDirectory.exists() && !isSymbolicLink(dataDirectory)) return;
        if (isSymbolicLink(dataDirectory)) {
            throw new IOException("Thư mục dữ liệu game không hợp lệ.");
        }
        File canonicalData = dataDirectory.getCanonicalFile();
        if (!gameDirectory.equals(canonicalData.getParentFile())
                || !"data".equals(canonicalData.getName())) {
            throw new IOException("Thư mục dữ liệu game không hợp lệ.");
        }
        deleteDataTree(dataDirectory);
    }

    static void deleteGameDirectory(Context context, File jar) throws IOException {
        if (jar == null || !"game.jar".equals(jar.getName())) {
            throw new IOException("Không tìm thấy game để gỡ cài đặt.");
        }
        File gamesRoot = gamesDirectory(context).getCanonicalFile();
        File gameDirectory = jar.getAbsoluteFile().getParentFile();
        if (gameDirectory == null || isSymbolicLink(gameDirectory)) {
            throw new IOException("Thư mục game không hợp lệ.");
        }
        File canonicalDirectory = gameDirectory.getCanonicalFile();
        File parent = gameDirectory.getParentFile();
        if (parent == null || !gamesRoot.equals(parent.getCanonicalFile())
                || !gamesRoot.equals(canonicalDirectory.getParentFile())) {
            throw new IOException("Thư mục game không hợp lệ.");
        }
        File storedJar = new File(gameDirectory, "game.jar");
        if (isSymbolicLink(storedJar) || !storedJar.isFile()
                || !storedJar.getCanonicalFile().equals(jar.getCanonicalFile())) {
            throw new IOException("Không thể xác minh game cần gỡ.");
        }
        deleteDataTree(gameDirectory);
        if (gameDirectory.exists() || isSymbolicLink(gameDirectory)) {
            throw new IOException("Không thể xóa toàn bộ thư mục game.");
        }
    }

    static boolean hasAtomicFile(File file) {
        return file.isFile() || new File(file.getPath() + ".bak").isFile();
    }

    static void writeAtomic(File file, byte[] bytes) throws IOException {
        ensureDirectory(file.getParentFile());
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            output.write(bytes);
            output.getFD().sync();
            atomic.finishWrite(output);
        } catch (IOException error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
    }

    private static File defaultRoot() throws IOException {
        return new File(Environment.getExternalStorageDirectory(), APP_FOLDER).getCanonicalFile();
    }

    private static File validatePath(File path, boolean allowMissingDefault) throws IOException {
        if (path == null) throw new IOException("Chưa chọn thư mục lưu game.");
        File external = Environment.getExternalStorageDirectory().getCanonicalFile();
        File canonical = path.getCanonicalFile();
        String externalPath = external.getPath();
        String candidatePath = canonical.getPath();
        if (candidatePath.equals(externalPath)
                || !candidatePath.startsWith(externalPath + File.separator)) {
            throw new IOException("Hãy chọn một thư mục con trong bộ nhớ dùng chung.");
        }
        String relative = candidatePath.substring(externalPath.length() + 1)
                .toLowerCase(java.util.Locale.ROOT);
        if (relative.equals("android") || relative.startsWith("android" + File.separator)) {
            throw new IOException("Không thể dùng thư mục Android làm nơi lưu game.");
        }
        if (!canonical.exists() && !allowMissingDefault) {
            throw new IOException("Thư mục đã chọn không tồn tại.");
        }
        return canonical;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory == null) throw new IOException("Thiếu đường dẫn thư mục.");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Không thể tạo thư mục lưu game.");
        }
    }

    private static void ensureWritable(File directory) throws IOException {
        File probe = File.createTempFile(".j2mex-write-test-", ".tmp", directory);
        if (!probe.delete()) throw new IOException("Không thể ghi vào thư mục đã chọn.");
    }

    private static boolean directoryHasEntries(File directory) {
        File[] entries = directory.listFiles();
        return entries != null && entries.length > 0;
    }

    private static byte[] readBytes(File file) throws IOException {
        try (InputStream input = new AtomicFile(file).openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static void deleteDataTree(File file) throws IOException {
        if (isSymbolicLink(file)) {
            if (!file.delete()) throw new IOException("Không thể xóa dữ liệu game.");
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Không thể đọc dữ liệu game.");
            for (File child : children) deleteDataTree(child);
        }
        if (file.exists() && !file.delete())
            throw new IOException("Không thể xóa dữ liệu game.");
    }

    private static boolean isSymbolicLink(File file) throws IOException {
        try {
            return (Os.lstat(file.getPath()).st_mode & OsConstants.S_IFMT)
                    == OsConstants.S_IFLNK;
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return false;
            throw new IOException("Không thể kiểm tra dữ liệu game.", error);
        }
    }

    private static String readText(File file) throws IOException {
        return new String(readBytes(file), StandardCharsets.UTF_8);
    }
}
