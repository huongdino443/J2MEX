package org.recompile.freej2me.lite;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Locale;

/** App-wide preferences; the runtime process exchanges snapshots through explicit IPC. */
final class AppPreferences {
    private static final String NAME = "app_preferences";
    private static final String FPS = "show_fps";
    private static final String DEBUG_LOGS = "debug_logs_enabled";
    private static final String STATUS_BAR = "status_bar_visible";
    private static final String LOCK_ORIENTATION = "lock_orientation";
    private static final String LANGUAGE = "language";
    static final String ACTION_RUNTIME_SETTINGS_CHANGED =
            "org.recompile.freej2me.lite.RUNTIME_SETTINGS_CHANGED";
    static final String EXTRA_SHOW_FPS = "app_preferences.show_fps";
    static final String EXTRA_DEBUG_LOGS = "app_preferences.debug_logs";
    static final String EXTRA_STATUS_BAR = "app_preferences.status_bar";
    static final String EXTRA_LOCK_ORIENTATION = "app_preferences.lock_orientation";

    private AppPreferences() { }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static void addRuntimeSnapshotExtras(Context context, Intent intent) {
        intent.putExtra(EXTRA_SHOW_FPS, showFps(context));
        intent.putExtra(EXTRA_DEBUG_LOGS, debugLogsEnabled(context));
        intent.putExtra(EXTRA_STATUS_BAR, statusBarVisible(context));
        intent.putExtra(EXTRA_LOCK_ORIENTATION, orientationLocked(context));
    }

    static void syncRuntimeSettingsToMain(Context context, boolean showFps,
            boolean debugLogs, boolean statusBar, boolean lockOrientation) {
        Intent update = new Intent(context, AppPreferencesReceiver.class);
        update.setAction(ACTION_RUNTIME_SETTINGS_CHANGED);
        update.putExtra(EXTRA_SHOW_FPS, showFps);
        update.putExtra(EXTRA_DEBUG_LOGS, debugLogs);
        update.putExtra(EXTRA_STATUS_BAR, statusBar);
        update.putExtra(EXTRA_LOCK_ORIENTATION, lockOrientation);
        context.sendBroadcast(update);
    }

    static boolean persistRuntimeSnapshot(Context context, Intent update) {
        return preferences(context).edit()
                .putBoolean(FPS, update.getBooleanExtra(EXTRA_SHOW_FPS, false))
                .putBoolean(DEBUG_LOGS, update.getBooleanExtra(EXTRA_DEBUG_LOGS, false))
                .putBoolean(STATUS_BAR, update.getBooleanExtra(EXTRA_STATUS_BAR, true))
                .putBoolean(LOCK_ORIENTATION,
                        update.getBooleanExtra(EXTRA_LOCK_ORIENTATION, false))
                .commit();
    }

    static boolean showFps(Context context) {
        return preferences(context).getBoolean(FPS, false);
    }

    static void setShowFps(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(FPS, enabled).apply();
    }

    static boolean debugLogsEnabled(Context context) {
        return preferences(context).getBoolean(DEBUG_LOGS, false);
    }

    static void setDebugLogsEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(DEBUG_LOGS, enabled).apply();
    }

    static boolean statusBarVisible(Context context) {
        return preferences(context).getBoolean(STATUS_BAR, true);
    }

    static void setStatusBarVisible(Context context, boolean visible) {
        preferences(context).edit().putBoolean(STATUS_BAR, visible).apply();
    }

    static boolean orientationLocked(Context context) {
        return preferences(context).getBoolean(LOCK_ORIENTATION, false);
    }

    static void setOrientationLocked(Context context, boolean locked) {
        preferences(context).edit().putBoolean(LOCK_ORIENTATION, locked).apply();
    }

    static String automaticLanguage(Context context) {
        String selected = preferences(context).getString(LANGUAGE, "system");
        if ("vi".equals(selected)) return "Tiếng Việt";
        if ("en".equals(selected)) return "English";
        android.content.res.Configuration configuration =
                context.getResources().getConfiguration();
        Locale locale = Build.VERSION.SDK_INT >= 24
                ? configuration.getLocales().get(0) : configuration.locale;
        if (locale == null) locale = Locale.getDefault();
        return "vi".equalsIgnoreCase(locale.getLanguage()) ? "Tiếng Việt" : "English";
    }

    static int languageChoice(Context context) {
        String selected = preferences(context).getString(LANGUAGE, "system");
        if ("vi".equals(selected)) return 1;
        if ("en".equals(selected)) return 2;
        return 0;
    }

    static void setLanguageChoice(Context context, int choice) {
        String selected = choice == 1 ? "vi" : choice == 2 ? "en" : "system";
        preferences(context).edit().putString(LANGUAGE, selected).apply();
    }
}
