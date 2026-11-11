package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class HostFiles {
    static final int EXPORT_LOG = 72;
    private static final String INSTALL_VERSION = "v6-runtime-trim-1";
    private static final String HOST_ASSETS_VERSION = "v73-shared-framebuffer-limit";
    static File log(Activity activity) { return new File(activity.getFilesDir(), "latestlog.txt"); }
    private static boolean markerMatches(File marker, String expected) throws IOException {
        return marker.isFile() && new String(readFile(marker), StandardCharsets.UTF_8).equals(expected);
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] bytes = new byte[65536];
        int count;
        while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
    }
    static byte[] readFile(File file) throws IOException {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output); return output.toByteArray();
        }
    }
    static void writeFile(File file, byte[] bytes) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) { output.write(bytes); }
    }
    static void moveFile(File source, File destination) throws IOException {
        if (source.renameTo(destination)) return;
        File staging = new File(destination.getParentFile(), destination.getName() + ".importing");
        if (staging.exists() && !staging.delete()) throw new IOException("Không xóa được file nhập tạm");
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(staging)) { copy(input, output); }
        if (!staging.renameTo(destination)) {
            staging.delete();
            throw new IOException("Không thể hoàn tất việc lưu JAR đã nhập");
        }
        if (!source.delete()) throw new IOException("Đã lưu JAR nhưng không xóa được bản tạm");
    }
    static void mkdir(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Không tạo được thư mục " + directory.getName());
    }
    static void deleteTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (file.exists() && !file.delete()) throw new IOException("Không xóa được " + file.getName());
    }
    static void copyAsset(Activity activity, String name, File destination) throws IOException {
        mkdir(destination.getParentFile());
        try (InputStream input = activity.getAssets().open(name);
             OutputStream output = new FileOutputStream(destination)) { copy(input, output); }
    }
    private static void prepareHostAssets(Activity activity, File root) throws IOException {
        File stamp = new File(root, ".host-assets-version");
        if (markerMatches(stamp, HOST_ASSETS_VERSION)
                && new File(root, "engine.jar").isFile()
                && new File(root, "bridge.jar").isFile()
                && new File(root, "sample.jar").isFile()
                && new File(root, "lcdui-sample.jar").isFile()) return;
        copyAsset(activity, "engine.jar", new File(root, "engine.jar"));
        copyAsset(activity, "bridge.jar", new File(root, "bridge.jar"));
        copyAsset(activity, "sample.jar", new File(root, "sample.jar"));
        copyAsset(activity, "lcdui-sample.jar", new File(root, "lcdui-sample.jar"));
        writeFile(stamp, HOST_ASSETS_VERSION.getBytes(StandardCharsets.UTF_8));
    }

    static boolean isPrepared(Activity activity) throws IOException {
        File root = activity.getFilesDir(), runtime = new File(root, "runtime");
        return markerMatches(new File(root, ".host-assets-version"), HOST_ASSETS_VERSION)
                && new File(root, "engine.jar").isFile()
                && new File(root, "bridge.jar").isFile()
                && new File(root, "sample.jar").isFile()
                && new File(root, "lcdui-sample.jar").isFile()
                && markerMatches(new File(runtime, ".complete"), INSTALL_VERSION)
                && new File(runtime, "lib/aarch64/server/libjvm.so").isFile();
    }

    static synchronized void prepare(Activity activity) throws IOException {
        File root = activity.getFilesDir(), runtime = new File(root, "runtime");
        prepareHostAssets(activity, root);
        File stamp = new File(runtime, ".complete");
        if (markerMatches(stamp, INSTALL_VERSION)
                && new File(runtime, "lib/aarch64/server/libjvm.so").isFile()) return;
        File staging = new File(root, "runtime-installing");
        deleteTree(staging);
        mkdir(staging);
        RuntimeArchive.unpack(activity.getAssets().open("runtime.tar.xz"), staging);
        if (!new File(staging, "lib/aarch64/server/libjvm.so").isFile())
            throw new IOException("Runtime thiếu libjvm.so");
        // Explicit bundled-font mappings avoid a dependency on desktop Linux fontconfig.
        StringBuilder config = new StringBuilder("version=1\nsequence.allfonts=alphabetic\n");
        String[][] families = {
                {"serif", "Lucida Bright", "LucidaBrightRegular.ttf", "Lucida Bright Demibold", "LucidaBrightDemiBold.ttf",
                        "Lucida Bright Italic", "LucidaBrightItalic.ttf", "Lucida Bright Demibold Italic", "LucidaBrightDemiItalic.ttf"},
                {"sansserif", "Lucida Sans", "LucidaSansRegular.ttf", "Lucida Sans Demibold", "LucidaSansDemiBold.ttf",
                        "Lucida Sans", "LucidaSansRegular.ttf", "Lucida Sans Demibold", "LucidaSansDemiBold.ttf"},
                {"monospaced", "Lucida Sans Typewriter", "LucidaTypewriterRegular.ttf", "Lucida Sans Typewriter Bold", "LucidaTypewriterBold.ttf",
                        "Lucida Sans Typewriter", "LucidaTypewriterRegular.ttf", "Lucida Sans Typewriter Bold", "LucidaTypewriterBold.ttf"}
        };
        String[] styles = {"plain", "bold", "italic", "bolditalic"};
        for (String[] family : families) for (int i = 0; i < styles.length; i++) {
            String face = family[1 + i * 2], file = family[2 + i * 2];
            config.append(family[0]).append('.').append(styles[i]).append(".alphabetic=").append(face).append('\n');
            config.append("filename.").append(face.replace(' ', '_')).append('=')
                    .append(runtime.getAbsolutePath()).append("/lib/fonts/").append(file).append('\n');
        }
        for (String alias : new String[]{"dialog", "dialoginput"}) for (String style : styles)
            config.append(alias).append('.').append(style).append(".alphabetic=Lucida Sans\n");
        writeFile(new File(staging, "lib/fontconfig.properties"),
                config.toString().getBytes(StandardCharsets.UTF_8));
        writeFile(new File(staging, ".complete"), INSTALL_VERSION.getBytes(StandardCharsets.UTF_8));
        deleteTree(runtime);
        if (!staging.renameTo(runtime)) throw new IOException("Không hoàn tất cài runtime");
    }

    static void requestLog(Activity activity) {
        if (!AppPreferences.debugLogsEnabled(activity)) {
            android.widget.Toast.makeText(activity, AppLocalization.text(activity,
                    "Hãy bật ghi log trong mục Khác trước khi mở game."),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        File latestLog = log(activity);
        if (!latestLog.isFile() || latestLog.length() == 0) {
            android.widget.Toast.makeText(activity, AppLocalization.text(activity,
                    "Chưa có log lần chạy gần nhất. Hãy bật ghi log rồi mở game."),
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "J2MEX-log.txt");
        activity.startActivityForResult(intent, EXPORT_LOG);
    }
    static void exportLog(Activity activity, Uri destination) throws IOException {
        if (!AppPreferences.debugLogsEnabled(activity))
            throw new IOException("Đang tắt chức năng ghi log gỡ lỗi.");
        File latestLog = log(activity);
        if (!latestLog.isFile() || latestLog.length() == 0)
            throw new IOException("Chưa có log lần chạy gần nhất.");
        try (OutputStream output = activity.getContentResolver().openOutputStream(destination, "wt")) {
            if (output == null) throw new IOException("Không mở được nơi lưu log");
            File gamesRoot = null;
            try { gamesRoot = GameStorage.gamesDirectory(activity); }
            catch (IOException ignored) { }
            SessionLog.export(activity.getFilesDir(), gamesRoot, output);
        }
    }
    static String readAsset(Activity activity, String name) throws IOException {
        try (InputStream input = activity.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output); return output.toString("UTF-8");
        }
    }
}
