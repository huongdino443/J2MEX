package org.recompile.freej2me.lite;

import android.app.Activity;
import android.widget.Toast;

import java.io.File;

/** Shared check, result, download, and install prompts for More and the in-game menu. */
final class AppUpdateUi {
    interface ResultListener {
        void onResult(AppUpdateManager.Release release, String error);
    }

    private AppUpdateUi() {}

    static void check(Activity activity, ResultListener listener) {
        AppUpdatePopup.ProgressPopup progress = AppUpdatePopup.showChecking(activity);
        AppUpdateManager.check(activity, (release, error) -> {
            progress.dismiss();
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (listener != null) listener.onResult(release, error);
            showResult(activity, release, error, listener);
        });
    }

    private static void showResult(Activity activity, AppUpdateManager.Release release,
            String error, ResultListener listener) {
        if (error != null) {
            AppUpdatePopup.showMessage(activity, "Cập nhật",
                    AppLocalization.text(activity, "Không kiểm tra được cập nhật")
                            + "\n\n" + error,
                    true, true,
                    new AppUpdatePopup.Action("Kiểm tra lại", true,
                            () -> check(activity, listener)),
                    new AppUpdatePopup.Action("Đóng", false, null));
            return;
        }
        if (release == null) {
            AppUpdatePopup.showMessage(activity, "Cập nhật",
                    AppLocalization.text(activity, "Hiện chưa có bản cập nhật"),
                    false, true,
                    new AppUpdatePopup.Action("Đóng", true, null));
            return;
        }
        AppUpdatePopup.showManualRelease(activity, release);
    }

    static void showAutomaticPrompt(Activity activity, AppUpdateManager.Release release) {
        AppUpdatePopup.showAutomaticPrompt(activity, release);
    }

    static void download(Activity activity, AppUpdateManager.Release release) {
        AppUpdatePopup.ProgressPopup progress = AppUpdatePopup.showDownloading(activity);
        AppUpdateManager.download(activity, release, new AppUpdateManager.DownloadCallback() {
            @Override public void onProgress(int percent) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                        progress.setProgress(percent);
                });
            }

            @Override public void onComplete(File apk, String error) {
                progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (error != null) {
                    AppUpdatePopup.showMessage(activity, "Cập nhật",
                            AppLocalization.text(activity, "Không tải được bản cập nhật.")
                                    + "\n\n" + error,
                            true, true,
                            new AppUpdatePopup.Action("Đóng", true, null));
                    return;
                }
                Toast.makeText(activity, AppLocalization.text(activity,
                        "Đã tải APK. Hãy xác nhận trong trình cài đặt Android."),
                        Toast.LENGTH_LONG).show();
                AppUpdateManager.requestInstall(activity, apk);
            }
        });
    }

    static void openReleasePage(Activity activity, AppUpdateManager.Release release) {
        try {
            activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(release.pageUrl)));
        } catch (Exception error) {
            Toast.makeText(activity, AppLocalization.text(activity,
                    "Không mở được trang phát hành."), Toast.LENGTH_LONG).show();
        }
    }
}
