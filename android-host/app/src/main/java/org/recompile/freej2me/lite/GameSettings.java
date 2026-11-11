package org.recompile.freej2me.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;

final class GameSettings {
    static final int DEFAULT_WIDTH = 240;
    static final int DEFAULT_HEIGHT = 320;
    static final int MIN_CANVAS_SIZE = 64;
    static final int MAX_CANVAS_SIZE = 2048;

    int canvasWidth = DEFAULT_WIDTH;
    int canvasHeight = DEFAULT_HEIGHT;
    int canvasPosition;
    int canvasScale = 100;
    boolean reserveGamepadArea = true;
    boolean canvasOutlineEnabled = true;
    boolean touchEnabled = true;
    boolean gamepadEnabled = true;
    boolean bitmapFontEnabled = true;
    boolean crtFilterEnabled = true;
    String gamepadLayoutId = "builtin:0";
    String physicalKeyMappings = "";

    static GameSettings load(Context context, File jar) {
        if (jar == null) return new GameSettings();
        Properties preferences = readPreferences(context, jar);
        GameSettings settings = new GameSettings();
        settings.canvasWidth = boundedInt(preferences.getProperty("canvas_width"),
                DEFAULT_WIDTH, MIN_CANVAS_SIZE, MAX_CANVAS_SIZE);
        settings.canvasHeight = boundedInt(preferences.getProperty("canvas_height"),
                DEFAULT_HEIGHT, MIN_CANVAS_SIZE, MAX_CANVAS_SIZE);
        settings.canvasPosition = positionIndex(
                preferences.getProperty("canvas_position", "top"));
        settings.canvasScale = boundedInt(preferences.getProperty("canvas_scale", "100"),
                100, 50, 100);
        settings.reserveGamepadArea = booleanProperty(
                preferences, "reserve_gamepad_area", true);
        settings.canvasOutlineEnabled = booleanProperty(
                preferences, "canvas_outline_enabled", true);
        settings.touchEnabled = booleanProperty(preferences, "touch_enabled", true);
        settings.gamepadEnabled = booleanProperty(preferences, "gamepad_enabled", true);
        settings.bitmapFontEnabled = booleanProperty(preferences, "bitmap_font_enabled", true);
        settings.crtFilterEnabled = booleanProperty(preferences, "crt_filter_enabled", true);
        settings.gamepadLayoutId = preferences.getProperty("gamepad_layout_id", "builtin:0");
        settings.physicalKeyMappings = preferences.getProperty("physical_key_mappings", "");
        return settings;
    }

    boolean save(Context context, File jar) {
        if (jar == null) return false;
        Properties values = readPreferences(context, jar);
        values.setProperty("canvas_width", Integer.toString(canvasWidth));
        values.setProperty("canvas_height", Integer.toString(canvasHeight));
        values.setProperty("canvas_position", positionName(canvasPosition));
        values.setProperty("canvas_scale", Integer.toString(canvasScale));
        values.setProperty("reserve_gamepad_area", Boolean.toString(reserveGamepadArea));
        values.setProperty("canvas_outline_enabled", Boolean.toString(canvasOutlineEnabled));
        values.setProperty("touch_enabled", Boolean.toString(touchEnabled));
        values.setProperty("gamepad_enabled", Boolean.toString(gamepadEnabled));
        values.setProperty("bitmap_font_enabled", Boolean.toString(bitmapFontEnabled));
        values.setProperty("crt_filter_enabled", Boolean.toString(crtFilterEnabled));
        values.setProperty("gamepad_layout_id", gamepadLayoutId);
        if (physicalKeyMappings == null || physicalKeyMappings.isEmpty())
            values.remove("physical_key_mappings");
        else values.setProperty("physical_key_mappings", physicalKeyMappings);
        return writePreferences(jar, values);
    }

    static boolean hasLaunched(Context context, File jar) {
        if (jar == null) return false;
        return booleanProperty(readPreferences(context, jar), "launched_once", false);
    }

    static boolean markLaunched(Context context, File jar) {
        if (jar == null) return false;
        Properties values = readPreferences(context, jar);
        values.setProperty("launched_once", "true");
        return writePreferences(jar, values);
    }

    private static Properties readPreferences(Context context, File jar) {
        Properties values = new Properties();
        File file = settingsFile(jar);
        if (GameStorage.hasAtomicFile(file)) {
            try (InputStream input = new AtomicFile(file).openRead()) {
                values.load(input);
                return values;
            } catch (IOException error) {
                throw new IllegalStateException("Could not read saved game settings", error);
            }
        }

        SharedPreferences legacy = preferences(context, legacyJar(context, jar));
        Map<String, ?> oldValues = legacy.getAll();
        for (Map.Entry<String, ?> entry : oldValues.entrySet()) {
            Object value = entry.getValue();
            if (value != null) values.setProperty(entry.getKey(), String.valueOf(value));
        }
        if (!values.isEmpty()) writePreferences(jar, values);
        return values;
    }

    private static File settingsFile(File jar) {
        if (jar == null || jar.getParentFile() == null)
            throw new IllegalArgumentException("A game JAR path is required");
        return new File(jar.getParentFile(), "settings.properties");
    }

    private static File legacyJar(Context context, File jar) {
        if (jar == null || jar.getParentFile() == null) return jar;
        return new File(new File(new File(context.getFilesDir(), "games"),
                jar.getParentFile().getName()), "game.jar");
    }

    private static boolean writePreferences(File jar, Properties values) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            values.store(output, "J2MEX per-game settings");
            GameStorage.writeAtomic(settingsFile(jar), output.toByteArray());
            return true;
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private static boolean booleanProperty(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static SharedPreferences preferences(Context context, File jar) {
        return context.getSharedPreferences("game_settings_" + keyFor(jar),
                Context.MODE_PRIVATE);
    }

    private static String keyFor(File jar) {
        String path;
        try {
            path = jar == null ? "default" : jar.getCanonicalPath();
        } catch (IOException error) {
            path = jar == null ? "default" : jar.getAbsolutePath();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(path.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                key.append(Character.forDigit((value >>> 4) & 0xf, 16));
                key.append(Character.forDigit(value & 0xf, 16));
            }
            return key.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static int boundedInt(String value, int fallback, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(minimum, Math.min(maximum, parsed));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static String positionName(int position) {
        switch (position) {
            case 1: return "center";
            case 2: return "bottom";
            default: return "top";
        }
    }

    private static int positionIndex(String position) {
        if ("center".equals(position)) return 1;
        if ("bottom".equals(position)) return 2;
        return 0;
    }
}
