package org.recompile.freej2me.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.widget.Toast;

import java.io.File;

/** Shared check, result, download, and install prompts for More and the in-game menu. */
final class AppUpdateUi {
    interface ResultListener {
        void onResult(AppUpdateManager.Release release, String error);
    }

    private AppUpdateUi() {}

    static void check(Activity activity, ResultListener listener) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(AppLocalization.text(activity, "Đang kiểm tra cập nhật…"));
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        AppUpdateManager.check(activity, (release, error) -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            progress.dismiss();
            if (listener != null) listener.onResult(release, error);
            showResult(activity, release, error, listener);
        });
    }

    private static void showResult(Activity activity, AppUpdateManager.Release release,
            String error, ResultListener listener) {
        boolean english = AppLocalization.isEnglish(activity);
        String title = english ? "Updates" : "Cập nhật";
        if (error != null) {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage((english ? "Couldn't check for updates.\n" : "Không kiểm tra được cập nhật.\n")
                            + error)
                    .setPositiveButton(AppLocalization.text(activity, "Kiểm tra lại"),
                            (dialog, which) -> check(activity, listener))
                    .setNegativeButton(AppLocalization.text(activity, "Đóng"), null)
                    .show();
            return;
        }
        if (release == null) {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(AppLocalization.text(activity, "Hiện chưa có bản cập nhật"))
                    .setPositiveButton(AppLocalization.text(activity, "Đóng"), null)
                    .show();
            return;
        }
        StringBuilder message = new StringBuilder()
                .append(AppLocalization.text(activity, "Đã có bản cập nhật mới"))
                .append("\n\n")
                .append(release.name)
                .append(" (").append(release.tag).append(')');
        if (!release.notes.isEmpty()) message.append("\n\n").append(release.notes);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton(AppLocalization.text(activity, "Tải về"),
                        (dialog, which) -> download(activity, release))
                .setNegativeButton(AppLocalization.text(activity, "Đóng"), null)
                .setNeutralButton(AppLocalization.text(activity, "Trang phát hành"),
                        (dialog, which) -> openReleasePage(activity, release))
                .show();
    }

    static void download(Activity activity, AppUpdateManager.Release release) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle(AppLocalization.text(activity, "Cập nhật"));
        progress.setMessage(AppLocalization.text(activity, "Đang tải APK…"));
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setIndeterminate(false);
        progress.setCancelable(false);
        progress.show();
        AppUpdateManager.download(activity, release, new AppUpdateManager.DownloadCallback() {
            @Override public void onProgress(int percent) {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    if (percent < 0) {
                        progress.setIndeterminate(true);
                    } else {
                        progress.setIndeterminate(false);
                        progress.setProgress(percent);
                    }
                }
            }

            @Override public void onComplete(File apk, String error) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                progress.dismiss();
                if (error != null) {
                    new AlertDialog.Builder(activity)
                            .setTitle(AppLocalization.text(activity, "Cập nhật"))
                            .setMessage(AppLocalization.text(activity,
                                    "Không tải được bản cập nhật.") + "\n" + error)
                            .setPositiveButton(AppLocalization.text(activity, "Đóng"), null)
                            .show();
                    return;
                }
                Toast.makeText(activity, AppLocalization.text(activity,
                        "Đã tải APK. Hãy xác nhận trong trình cài đặt Android."),
                        Toast.LENGTH_LONG).show();
                AppUpdateManager.requestInstall(activity, apk);
            }
        });
    }

    private static void openReleasePage(Activity activity, AppUpdateManager.Release release) {
        try {
            activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(release.pageUrl)));
        } catch (Exception error) {
            Toast.makeText(activity, AppLocalization.text(activity,
                    "Không mở được trang phát hành."), Toast.LENGTH_LONG).show();
        }
    }
}
