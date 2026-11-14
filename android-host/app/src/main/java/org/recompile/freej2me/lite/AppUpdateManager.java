package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks GitHub Releases and downloads the signed APK for Android's package installer. */
final class AppUpdateManager {
    private static final String RELEASE_API =
            "https://api.github.com/repos/huongdino443/J2MEX/releases?per_page=20";
    private static final String PREFS = "app_update";
    private static final String PENDING_INSTALL = "pending_install_path";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long MAX_APK_BYTES = 256L * 1024L * 1024L;
    private static final int INSTALL_PERMISSION_REQUEST = 4087;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppUpdateManager() {}

    interface CheckCallback {
        void onComplete(Release release, String error);
    }

    interface DownloadCallback {
        void onProgress(int percent);
        void onComplete(File apk, String error);
    }

    static final class Release {
        final String tag;
        final String name;
        final String notes;
        final String pageUrl;
        final String apkUrl;
        final String sha256;

        Release(String tag, String name, String notes, String pageUrl,
                String apkUrl, String sha256) {
            this.tag = tag;
            this.name = name;
            this.notes = notes;
            this.pageUrl = pageUrl;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }

    static void check(Context context, CheckCallback callback) {
        Context application = context.getApplicationContext();
        WORKER.execute(() -> {
            Release release = null;
            String error = null;
            try {
                release = fetchLatest(application);
            } catch (Exception failure) {
                error = failure.getMessage();
                if (error == null || error.trim().isEmpty()) error = failure.toString();
            }
            Release finalRelease = release;
            String finalError = error;
            MAIN.post(() -> callback.onComplete(finalRelease, finalError));
        });
    }

    private static Release fetchLatest(Context context) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "J2MEX-Android-Updater");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("GitHub trả về HTTP " + status + ".");
            }
            JSONArray releases = new JSONArray(readText(connection.getInputStream(), 1_000_000));
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            String currentName = packageInfo.versionName == null ? "" : packageInfo.versionName;
            Release newest = null;
            for (int releaseIndex = 0; releaseIndex < releases.length(); releaseIndex++) {
                JSONObject json = releases.optJSONObject(releaseIndex);
                if (json == null) continue;
                String tag = json.optString("tag_name", "").trim();
                String page = json.optString("html_url", "").trim();
                if (!tag.matches("(?i)v?[0-9]+(?:\\.[0-9]+){0,3}") || page.isEmpty()
                        || ReleaseVersionPolicy.compare(tag, currentName) <= 0) {
                    continue;
                }
                JSONArray assets = json.optJSONArray("assets");
                if (assets == null) continue;
                JSONObject apk = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject candidate = assets.optJSONObject(i);
                    if (candidate == null) continue;
                    String name = candidate.optString("name", "");
                    if (name.toLowerCase(Locale.ROOT).endsWith(".apk")
                            && candidate.optString("browser_download_url", "").startsWith("https://")) {
                        apk = candidate;
                        break;
                    }
                }
                if (apk == null || (newest != null
                        && ReleaseVersionPolicy.compare(tag, newest.tag) <= 0)) {
                    continue;
                }
                String assetUrl = apk.optString("browser_download_url", "");
                String digest = apk.optString("digest", "");
                if (digest.startsWith("sha256:")) digest = digest.substring("sha256:".length());
                newest = new Release(tag, json.optString("name", tag),
                        json.optString("body", "").trim(), page, assetUrl, digest);
            }
            return newest;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static void download(Context context, Release release, DownloadCallback callback) {
        Context application = context.getApplicationContext();
        WORKER.execute(() -> {
            File apk = null;
            String error = null;
            try {
                apk = downloadApk(application, release, callback);
            } catch (Exception failure) {
                error = failure.getMessage();
                if (error == null || error.trim().isEmpty()) error = failure.toString();
            }
            File finalApk = apk;
            String finalError = error;
            MAIN.post(() -> callback.onComplete(finalApk, finalError));
        });
    }

    private static File downloadApk(Context context, Release release,
            DownloadCallback callback) throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Không tạo được thư mục tải cập nhật.");
        }
        String safeTag = release.tag.replaceAll("[^A-Za-z0-9._-]", "_");
        File temporary = new File(directory, "J2MEX-" + safeTag + ".apk.part");
        File result = new File(directory, "J2MEX-" + safeTag + ".apk");
        HttpURLConnection connection = null;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        MAIN.post(() -> callback.onProgress(-1));
        try {
            connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "J2MEX-Android-Updater");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Không tải được APK (HTTP " + status + ").");
            }
            long expected = connection.getContentLengthLong();
            if (expected > MAX_APK_BYTES) throw new IllegalStateException("APK vượt giới hạn tải.");
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(
                         new FileOutputStream(temporary, false))) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                int lastPercent = -1;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_APK_BYTES) {
                        throw new IllegalStateException("APK vượt giới hạn tải.");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    if (expected > 0) {
                        int percent = (int) Math.min(100, total * 100L / expected);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            int progress = percent;
                            MAIN.post(() -> callback.onProgress(progress));
                        }
                    }
                }
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
        if (total == 0) throw new IllegalStateException("Tệp APK tải về đang trống.");
        String actualDigest = toHex(digest.digest());
        if (!release.sha256.isEmpty() && !actualDigest.equalsIgnoreCase(release.sha256)) {
            temporary.delete();
            throw new SecurityException("Mã SHA-256 của APK không khớp với thông tin GitHub.");
        }
        PackageInfo downloaded = context.getPackageManager().getPackageArchiveInfo(
                temporary.getAbsolutePath(), 0);
        if (downloaded == null || !context.getPackageName().equals(downloaded.packageName)) {
            temporary.delete();
            throw new SecurityException("APK tải về không phải ứng dụng J2MEX.");
        }
        if (downloaded.versionName == null
                || ReleaseVersionPolicy.compare(downloaded.versionName, release.tag) < 0) {
            temporary.delete();
            throw new SecurityException("Phiên bản trong APK không khớp bản phát hành.");
        }
        if (downloadedVersionCode(downloaded) <= currentVersionCode(context)) {
            temporary.delete();
            throw new IllegalStateException(
                    "Bản APK này không có mã phiên bản cao hơn bản đang cài.");
        }
        if (result.exists() && !result.delete()) {
            throw new IllegalStateException("Không thay thế được tệp APK cũ.");
        }
        if (!temporary.renameTo(result)) {
            throw new IllegalStateException("Không thể hoàn tất lưu tệp APK.");
        }
        return result;
    }

    private static String readText(InputStream input, int maxBytes) throws Exception {
        try (InputStream in = input;
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (output.size() + count > maxBytes) {
                    throw new IllegalStateException("Phản hồi cập nhật quá lớn.");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static long currentVersionCode(Context context) throws PackageManager.NameNotFoundException {
        return packageVersionCode(context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0));
    }

    private static long downloadedVersionCode(PackageInfo info) {
        return packageVersionCode(info);
    }

    private static long packageVersionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
        return info.versionCode;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    static void requestInstall(Activity activity, File apk) {
        if (activity == null || apk == null || !apk.isFile()) return;
        if (!canInstallFromThisSource(activity)) {
            preferences(activity).edit().putString(PENDING_INSTALL, apk.getAbsolutePath()).apply();
            try {
                Intent settings = Build.VERSION.SDK_INT >= 26
                        ? new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()))
                        : new Intent(Settings.ACTION_SECURITY_SETTINGS);
                activity.startActivityForResult(settings, INSTALL_PERMISSION_REQUEST);
            } catch (Exception error) {
                preferences(activity).edit().remove(PENDING_INSTALL).apply();
                Toast.makeText(activity, AppLocalization.text(activity,
                        "Không mở được quyền cài ứng dụng không rõ nguồn gốc."),
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        launchInstaller(activity, apk);
    }

    static void resumePendingInstall(Activity activity) {
        if (activity == null) return;
        SharedPreferences prefs = preferences(activity);
        String path = prefs.getString(PENDING_INSTALL, null);
        if (path == null) return;
        if (!canInstallFromThisSource(activity)) {
            prefs.edit().remove(PENDING_INSTALL).apply();
            Toast.makeText(activity, AppLocalization.text(activity,
                    "Chưa được cấp quyền cài ứng dụng không rõ nguồn gốc."),
                    Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().remove(PENDING_INSTALL).apply();
        launchInstaller(activity, new File(path));
    }

    private static boolean canInstallFromThisSource(Activity activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            return activity.getPackageManager().canRequestPackageInstalls();
        }
        try {
            return android.provider.Settings.Secure.getInt(activity.getContentResolver(),
                    android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void launchInstaller(Activity activity, File apk) {
        if (!apk.isFile()) {
            Toast.makeText(activity, AppLocalization.text(activity,
                    "Không tìm thấy APK đã tải."), Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(activity.getPackageName() + ".updates")
                .appendPath("apk")
                .appendPath(apk.getName())
                .build();
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            activity.startActivity(install);
        } catch (Exception error) {
            Toast.makeText(activity, AppLocalization.text(activity,
                    "Không mở được trình cài đặt Android: ") + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isInstallPermissionRequest(int requestCode) {
        return requestCode == INSTALL_PERMISSION_REQUEST;
    }
}
