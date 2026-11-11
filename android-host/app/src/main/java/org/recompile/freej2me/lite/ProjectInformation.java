package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.IOException;

final class ProjectInformation {
    private static final String VIETNAMESE_NOTICE_MARKER = "BẢN TIẾNG VIỆT";
    private static final String VIETNAMESE_NOTICE_START =
            "J2MEX — ứng dụng Android hỗ trợ chạy trò chơi Java ME";

    private ProjectInformation() { }

    static String introduction(Activity activity) throws IOException {
        boolean english = AppLocalization.isEnglish(activity);
        String notice = HostFiles.readAsset(activity, "NOTICE.txt");
        int vietnameseMarker = notice.indexOf(VIETNAMESE_NOTICE_MARKER);
        int vietnameseStart = notice.indexOf(VIETNAMESE_NOTICE_START, vietnameseMarker);
        if (vietnameseMarker < 0 || vietnameseStart < 0) {
            throw new IOException("Không tìm thấy phần thông báo giấy phép.");
        }
        int englishEnd = notice.lastIndexOf("\n────────────────", vietnameseMarker);
        String selectedNotice = english
                ? notice.substring(0, englishEnd >= 0 ? englishEnd : vietnameseMarker).trim()
                : notice.substring(vietnameseStart).trim();

        String version = "unknown";
        try {
            PackageInfo packageInfo = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            if (packageInfo.versionName != null) version = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException ignored) { }

        String overview = english
                ? "J2MEX " + version
                        + "\n\nJ2MEX is an Android application for running Java ME games. "
                        + "It brings together a game library, per-game settings, "
                        + "customizable touch controls, and tools for managing game files "
                        + "and saved data."
                        + "\n\nCopyright for the original source code, interface design, "
                        + "and original modifications belongs to Trần Văn Hưởng. Components "
                        + "and code adapted from related projects remain subject to their "
                        + "respective licenses."
                : "J2MEX " + version
                        + "\n\nJ2MEX là ứng dụng Android hỗ trợ chạy trò chơi Java ME. "
                        + "Ứng dụng tích hợp thư viện game, thiết lập riêng cho từng game, "
                        + "điều khiển cảm ứng tùy chỉnh và công cụ quản lý tệp, dữ liệu game."
                        + "\n\nBản quyền đối với mã nguồn, thiết kế giao diện và các chỉnh "
                        + "sửa nguyên gốc thuộc về tác giả Trần Văn Hưởng. Các thành phần "
                        + "và mã được chuyển thể từ các dự án liên quan vẫn tuân theo giấy "
                        + "phép tương ứng.";
        String noticeHeading = english ? "Project, licensing, and verification"
                : "Thông tin dự án, giấy phép và xác minh";
        return overview + "\n\n" + noticeHeading + "\n\n" + selectedNotice;
    }
}
