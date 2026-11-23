package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
/** App-styled popup and progress surfaces for update checks and downloads. */
final class AppUpdatePopup {
    private static final int COLOR_SURFACE = 0xFF192434;
    private static final int COLOR_TEXT = 0xFFE8EFF7;
    private static final int COLOR_MUTED = 0xFF9DAABB;
    private static final int COLOR_ACCENT = 0xFFB9C3FF;
    private static final int COLOR_BORDER = 0xFF3D4A5E;

    private AppUpdatePopup() {}

    static final class Action {
        final String label;
        final boolean accent;
        final Runnable onClick;

        Action(String label, boolean accent, Runnable onClick) {
            this.label = label;
            this.accent = accent;
            this.onClick = onClick;
        }
    }

    static final class ProgressPopup {
        private final PopupWindow popup;
        private final ProgressBar progress;
        private final TextView percentage;

        ProgressPopup(PopupWindow popup, ProgressBar progress, TextView percentage) {
            this.popup = popup;
            this.progress = progress;
            this.percentage = percentage;
        }

        void setProgress(int value) {
            if (progress == null) return;
            if (value < 0) {
                progress.setIndeterminate(true);
                if (percentage != null) percentage.setText("");
            } else {
                progress.setIndeterminate(false);
                progress.setProgress(Math.max(0, Math.min(100, value)));
                if (percentage != null) percentage.setText(value + "%");
            }
        }

        void dismiss() {
            if (popup.isShowing()) popup.dismiss();
        }
    }

    static ProgressPopup showChecking(Activity activity) {
        LinearLayout panel = modalPanel(activity, "Cập nhật");
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(activity, 16), 0, 0);

        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminate(true);
        tintIndeterminate(spinner);
        row.addView(spinner, new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28)));

        TextView label = bodyText(activity, AppLocalization.text(
                activity, "Đang kiểm tra cập nhật…"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(activity, 14);
        row.addView(label, labelParams);
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new ProgressPopup(showModal(activity, panel, false), null, null);
    }

    static ProgressPopup showDownloading(Activity activity) {
        LinearLayout panel = modalPanel(activity, "Cập nhật");
        TextView label = bodyText(activity, AppLocalization.text(activity, "Đang tải APK…"));
        label.setPadding(0, dp(activity, 12), 0, 0);
        panel.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setIndeterminate(true);
        tintProgress(progress);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 6));
        progressParams.topMargin = dp(activity, 20);
        panel.addView(progress, progressParams);

        TextView percentage = bodyText(activity, "");
        percentage.setTextColor(COLOR_MUTED);
        LinearLayout.LayoutParams percentageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentageParams.topMargin = dp(activity, 6);
        panel.addView(percentage, percentageParams);
        return new ProgressPopup(showModal(activity, panel, false), progress, percentage);
    }

    static void showAutomaticPrompt(Activity activity, AppUpdateManager.Release release) {
        String message = releaseMessage(activity, release);
        showMessage(activity, "Cập nhật", message, true, false,
                new Action("Nhắc lại sau", false,
                        () -> AppUpdateManager.remindAutomaticUpdateLater(activity)),
                new Action("Tải về", true, () -> AppUpdateUi.download(activity, release)));
    }

    static void showManualRelease(Activity activity, AppUpdateManager.Release release) {
        String message = releaseMessage(activity, release);
        showMessage(activity, "Cập nhật", message, true, true,
                new Action("Trang phát hành", true,
                        () -> AppUpdateUi.openReleasePage(activity, release)),
                new Action("Đóng", false, null),
                new Action("Tải về", true,
                        () -> AppUpdateUi.download(activity, release)));
    }

    static void showMessage(Activity activity, String title, String message,
            boolean scrollable, boolean dismissOnOutside, Action... actions) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        LinearLayout panel = modalPanel(activity, title);
        addMessageBody(activity, panel, message, scrollable);
        panel.setPadding(dp(activity, AppPopupDimensions.PANEL_HORIZONTAL_PADDING_DP),
                dp(activity, AppPopupDimensions.UPDATE_PANEL_TOP_PADDING_DP),
                dp(activity, AppPopupDimensions.PANEL_HORIZONTAL_PADDING_DP),
                dp(activity, AppPopupDimensions.UPDATE_PANEL_BOTTOM_PADDING_DP));

        ArrayList<TextView> buttons = new ArrayList<>(actions.length);
        if (actions.length > 0) {
            LinearLayout actionRow = new LinearLayout(activity);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity((actions.length == 1 ? Gravity.END : Gravity.START)
                    | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(activity, AppPopupDimensions.UPDATE_FOOTER_GAP_DP);
            panel.addView(actionRow, rowParams);
            if (actions.length == 3) {
                TextView leftButton = actionButton(activity, actions[0].label,
                        actions[0].accent);
                leftButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                actionRow.addView(leftButton, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                buttons.add(leftButton);

                LinearLayout rightActions = new LinearLayout(activity);
                rightActions.setOrientation(LinearLayout.HORIZONTAL);
                rightActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                actionRow.addView(rightActions, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                for (int i = 1; i < actions.length; i++) {
                    TextView button = actionButton(activity, actions[i].label, actions[i].accent);
                    button.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 1) buttonParams.leftMargin = dp(activity, 12);
                    rightActions.addView(button, buttonParams);
                    buttons.add(button);
                }
            } else {
                for (int i = 0; i < actions.length; i++) {
                    Action action = actions[i];
                    TextView button = actionButton(activity, action.label, action.accent);
                    LinearLayout.LayoutParams buttonParams;
                    if (actions.length > 1) {
                        buttonParams = new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        int horizontalGravity = i == 0 ? Gravity.START : Gravity.END;
                        button.setGravity(horizontalGravity | Gravity.CENTER_VERTICAL);
                    } else {
                        buttonParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        button.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    }
                    actionRow.addView(button, buttonParams);
                    buttons.add(button);
                }
            }
        }

        PopupWindow popup = showModal(activity, panel, dismissOnOutside);
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            buttons.get(i).setOnClickListener(view -> {
                popup.dismiss();
                if (action.onClick != null) action.onClick.run();
            });
        }
    }

    private static String releaseMessage(Activity activity, AppUpdateManager.Release release) {
        StringBuilder message = new StringBuilder()
                .append(AppLocalization.text(activity, "Đã có bản cập nhật mới"))
                .append("\n\n")
                .append(release.name)
                .append(" (").append(release.tag).append(')');
        if (!release.notes.isEmpty()) message.append("\n\n").append(release.notes);
        return message.toString();
    }

    private static void addMessageBody(Activity activity, LinearLayout panel,
            String message, boolean scrollable) {
        TextView body = bodyText(activity, AppLocalization.text(activity, message));
        if (scrollable && message.length() > 320) {
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(false);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(body, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int maxHeight = Math.min(dp(activity, AppPopupDimensions.MAX_SCROLL_CONTENT_HEIGHT_DP),
                    activity.getResources().getDisplayMetrics().heightPixels / 2);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
            scrollParams.topMargin = dp(activity, AppPopupDimensions.SECTION_GAP_DP);
            panel.addView(scroll, scrollParams);
            ScrollCueOverlay.install(activity, scroll, 10);
        } else {
            body.setPadding(0, dp(activity, 12), 0, 0);
            panel.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static LinearLayout modalPanel(Activity activity, String title) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, AppPopupDimensions.PANEL_HORIZONTAL_PADDING_DP),
                dp(activity, AppPopupDimensions.UPDATE_PANEL_TOP_PADDING_DP),
                dp(activity, AppPopupDimensions.PANEL_HORIZONTAL_PADDING_DP),
                dp(activity, AppPopupDimensions.PANEL_BOTTOM_PADDING_DP));
        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_SURFACE);
        background.setCornerRadius(dp(activity, AppPopupDimensions.UPDATE_PANEL_CORNER_RADIUS_DP));
        background.setStroke(dp(activity, 1), COLOR_BORDER);
        panel.setBackground(background);
        panel.setElevation(dp(activity, AppPopupDimensions.PANEL_ELEVATION_DP));

        TextView heading = new TextView(activity);
        heading.setText(AppLocalization.text(activity, title));
        heading.setTextColor(COLOR_TEXT);
        heading.setTextSize(18);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private static TextView bodyText(Activity activity, String text) {
        TextView body = new TextView(activity);
        body.setText(text);
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(14);
        body.setLineSpacing(dp(activity, 2), 1f);
        return body;
    }

    private static TextView actionButton(Activity activity, String label, boolean accent) {
        TextView button = new TextView(activity);
        button.setText(AppLocalization.text(activity, label));
        button.setTextColor(accent ? COLOR_ACCENT : COLOR_MUTED);
        button.setTextSize(AppPopupDimensions.UPDATE_ACTION_TEXT_SIZE_SP);
        button.setTypeface(Typeface.create("sans-serif-medium",
                accent ? Typeface.BOLD : Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(activity, AppPopupDimensions.UPDATE_ACTION_PADDING_DP),
                dp(activity, AppPopupDimensions.UPDATE_ACTION_PADDING_DP),
                dp(activity, AppPopupDimensions.UPDATE_ACTION_PADDING_DP),
                dp(activity, AppPopupDimensions.UPDATE_ACTION_PADDING_DP));
        button.setMinHeight(dp(activity, AppPopupDimensions.UPDATE_ACTION_MIN_HEIGHT_DP));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static PopupWindow showModal(Activity activity, View panel, boolean dismissOnOutside) {
        FrameLayout contentRoot = activity.findViewById(android.R.id.content);
        View dim = new View(activity);
        dim.setBackgroundColor(0x80000000);
        dim.setClickable(true);
        contentRoot.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(dp(activity, AppPopupDimensions.MAX_POPUP_WIDTH_DP),
                Math.max(dp(activity, AppPopupDimensions.MIN_POPUP_WIDTH_DP),
                        screenWidth - dp(activity, AppPopupDimensions.SCREEN_EDGE_GUTTER_DP * 2)));
        PopupWindow popup = new PopupWindow(panel, popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(activity, AppPopupDimensions.POPUP_ELEVATION_DP));
        popup.setOutsideTouchable(dismissOnOutside);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (dismissOnOutside) dim.setOnClickListener(view -> popup.dismiss());
        popup.setOnDismissListener(() -> {
            if (dim.getParent() instanceof ViewGroup) {
                ((ViewGroup) dim.getParent()).removeView(dim);
            }
        });
        popup.showAtLocation(contentRoot, Gravity.CENTER, 0, 0);
        return popup;
    }

    private static void tintIndeterminate(ProgressBar progress) {
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setIndeterminateTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }
    }

    private static void tintProgress(ProgressBar progress) {
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
            progress.setIndeterminateTintList(ColorStateList.valueOf(COLOR_ACCENT));
            progress.setProgressBackgroundTintList(
                    ColorStateList.valueOf(0x663D4A5E));
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
