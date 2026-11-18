package org.recompile.freej2me.lite;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

/** Lets app backgrounds extend behind display cutouts while keeping content inset. */
final class CutoutSafeArea {
    private CutoutSafeArea() { }

    static void configureWindow(Window window) {
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if (attributes.layoutInDisplayCutoutMode != mode) {
                attributes.layoutInDisplayCutoutMode = mode;
                window.setAttributes(attributes);
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    static void install(View contentRoot) {
        final int baseLeft = contentRoot.getPaddingLeft();
        final int baseTop = contentRoot.getPaddingTop();
        final int baseRight = contentRoot.getPaddingRight();
        final int baseBottom = contentRoot.getPaddingBottom();
        contentRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            int safeLeft = 0;
            int safeTop = 0;
            int safeRight = 0;
            int safeBottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    safeLeft = cutout.getSafeInsetLeft();
                    safeTop = cutout.getSafeInsetTop();
                    safeRight = cutout.getSafeInsetRight();
                    safeBottom = cutout.getSafeInsetBottom();
                }
            }
            int statusBarInset = insets.getStableInsetTop();
            if (statusBarInset <= 0) statusBarInset = insets.getSystemWindowInsetTop();
            int contentTop = Math.max(Math.max(0, statusBarInset), safeTop);
            int left = baseLeft + safeLeft;
            int top = baseTop + contentTop;
            int right = baseRight + safeRight;
            int bottom = baseBottom + safeBottom;
            if (view.getPaddingLeft() != left || view.getPaddingTop() != top
                    || view.getPaddingRight() != right || view.getPaddingBottom() != bottom) {
                view.setPadding(left, top, right, bottom);
            }
            return insets;
        });
    }
}
