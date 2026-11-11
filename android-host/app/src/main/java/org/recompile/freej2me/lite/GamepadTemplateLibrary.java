package org.recompile.freej2me.lite;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class GamepadTemplateLibrary {
    private static final String TAG = "J2MEXGamepads";
    private static final String PREFS = "j2mex_gamepad_templates";
    private static final String ITEMS = "items";
    private static final String[] BUILTIN_NAMES = {
            "Mẫu 1", "Mẫu 2", "Mẫu 3 (Joystick)"
    };
    // Keep the original joystick ID stable so existing game settings still use it.
    private static final int[] BUILTIN_INDICES = {0, 1, 3};
    private static final String[] EDITOR_LABELS = {
            "L", "R", "↑", "←", "→", "↓", "F",
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#",
            "Joystick"
    };
    private static final int[] EDITOR_CODES = {
            -6, -7, -1, -3, -4, -2, -5,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 42, 48, 35,
            GamepadTemplate.JOYSTICK_CODE
    };
    private static final String[] PRESET1_LABELS = {
            "L", "R", "↑", "←", "F", "→", "↓",
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"
    };
    private static final int[] PRESET1_CODES = {
            -6, -7, -1, -3, -5, -4, -2,
            49, 50, 51, 52, 53, 54, 55, 56, 57, 42, 48, 35
    };

    private GamepadTemplateLibrary() { }

    static List<GamepadTemplate> all(Context context) {
        List<GamepadTemplate> result = new ArrayList<>();
        for (int position = 0; position < BUILTIN_NAMES.length; position++) {
            int builtinIndex = BUILTIN_INDICES[position];
            GamepadTemplate item = new GamepadTemplate();
            item.id = "builtin:" + builtinIndex;
            item.name = BUILTIN_NAMES[position];
            item.builtinIndex = builtinIndex;
            result.add(item);
        }
        result.addAll(custom(context));
        return result;
    }

    static List<GamepadTemplate> custom(Context context) {
        List<GamepadTemplate> result = new ArrayList<>();
        JSONArray source = stored(context);
        for (int index = 0; index < source.length(); index++) {
            JSONObject item = source.optJSONObject(index);
            if (item == null) continue;
            GamepadTemplate parsed = parse(item);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    static GamepadTemplate get(Context context, String id) {
        for (GamepadTemplate template : all(context)) {
            if (template.id.equals(id)) return template;
        }
        GamepadTemplate fallback = new GamepadTemplate();
        fallback.id = "builtin:0";
        fallback.name = BUILTIN_NAMES[0];
        fallback.builtinIndex = 0;
        return fallback;
    }

    static boolean isCustom(String id) {
        return id != null && id.startsWith("custom:");
    }

    static String resolveId(Context context, String id) {
        for (GamepadTemplate template : all(context)) {
            if (template.id.equals(id)) return id;
        }
        return "builtin:0";
    }

    static String[] labels(Context context) {
        List<GamepadTemplate> templates = all(context);
        String[] labels = new String[templates.size()];
        for (int index = 0; index < templates.size(); index++)
            labels[index] = templates.get(index).name;
        return labels;
    }

    static String[] customIds(Context context) {
        List<GamepadTemplate> templates = custom(context);
        String[] ids = new String[templates.size()];
        for (int index = 0; index < templates.size(); index++)
            ids[index] = templates.get(index).id;
        return ids;
    }

    static GamepadTemplate createDraft(Context context) {
        int[] display = displaySize(context);
        GamepadTemplate template = new GamepadTemplate();
        template.portrait.screenWidth = Math.min(display[0], display[1]);
        template.portrait.screenHeight = Math.max(display[0], display[1]);
        template.landscape.screenWidth = template.portrait.screenHeight;
        template.landscape.screenHeight = template.portrait.screenWidth;
        int[] portraitGrid = gridDimensions(context,
                template.portrait.screenWidth, template.portrait.screenHeight);
        template.portrait.columns = portraitGrid[0];
        template.portrait.rows = portraitGrid[1];
        template.landscape.columns = portraitGrid[1];
        template.landscape.rows = portraitGrid[0];
        template.portrait.keys.addAll(defaultEditorKeys());
        template.landscape.keys.addAll(defaultEditorKeys());
        anchorSample1(template.portrait, false);
        anchorSample1(template.landscape, true);
        return template;
    }

    static String saveNew(Context context, GamepadTemplate template) throws Exception {
        JSONArray items = stored(context);
        GamepadTemplate copy = template.copy();
        copy.id = "custom:" + UUID.randomUUID();
        copy.name = nextName(items);
        items.put(serialize(copy));
        writeItems(context, items);
        return copy.id;
    }

    static void update(Context context, String id, GamepadTemplate template) throws Exception {
        if (!isCustom(id)) throw new IllegalArgumentException("Chỉ có thể sửa mẫu tùy chỉnh.");
        JSONArray items = stored(context);
        boolean replaced = false;
        for (int index = 0; index < items.length(); index++) {
            JSONObject old = items.optJSONObject(index);
            if (old != null && id.equals(old.optString("id"))) {
                GamepadTemplate copy = template.copy();
                copy.id = id;
                copy.name = old.optString("name", "Mẫu tùy chỉnh");
                items.put(index, serialize(copy));
                replaced = true;
                break;
            }
        }
        if (!replaced) throw new IllegalArgumentException("Không tìm thấy mẫu gamepad cần sửa.");
        writeItems(context, items);
    }

    static void delete(Context context, String id) throws Exception {
        if (!isCustom(id)) throw new IllegalArgumentException("Không thể xóa mẫu dựng sẵn.");
        JSONArray oldItems = stored(context);
        JSONArray nextItems = new JSONArray();
        boolean removed = false;
        for (int index = 0; index < oldItems.length(); index++) {
            JSONObject item = oldItems.optJSONObject(index);
            if (item != null && id.equals(item.optString("id"))) removed = true;
            else if (item != null) nextItems.put(item);
        }
        if (!removed) throw new IllegalArgumentException("Mẫu không còn tồn tại.");
        writeItems(context, nextItems);
        clearDeletedReferences(context, id);
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int[] displaySize(Context context) {
        int width = 0;
        int height = 0;
        try {
            Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point point = new Point();
            // Match the Activity content area: status and navigation bars remain visible.
            display.getSize(point);
            width = point.x;
            height = point.y;
        } catch (Exception ignored) { }
        if (width <= 0 || height <= 0) {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        return new int[]{width, height};
    }

    static int[] realDisplaySize(Context context) {
        int width = 0;
        int height = 0;
        try {
            Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point point = new Point();
            try {
                Display.class.getMethod("getRealSize", Point.class).invoke(display, point);
            } catch (Exception ignored) {
                display.getSize(point);
            }
            width = point.x;
            height = point.y;
        } catch (Exception ignored) { }
        if (width <= 0 || height <= 0) {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        return new int[]{width, height};
    }

    static int gridMargin(Context context, int dimension) {
        return Math.max(dp(context, 4), Math.round(dimension * .03f));
    }

    static int gridGap(Context context, int minDimension) {
        return Math.max(dp(context, 4), Math.round(minDimension * .012f));
    }

    static int[] gridDimensions(Context context, int width, int height) {
        int marginX = gridMargin(context, width);
        int marginY = gridMargin(context, height);
        float availableWidth = Math.max(6, width - marginX * 2f);
        float availableHeight = Math.max(6, height - marginY * 2f);
        float cell = Math.min(availableWidth / 6f, availableHeight / 6f);
        return new int[]{
                Math.max(6, Math.round(availableWidth / cell + .0001f)),
                Math.max(6, Math.round(availableHeight / cell + .0001f))
        };
    }

    static float gridCell(Context context, int width, int height, int columns, int rows) {
        return Math.min(Math.max(1, width - gridMargin(context, width) * 2f) / Math.max(1, columns),
                Math.max(1, height - gridMargin(context, height) * 2f) / Math.max(1, rows));
    }

    static boolean free(GamepadTemplate.Layout layout, int targetIndex, float x, float y) {
        GamepadTemplate.Key target = layout.keys.get(targetIndex);
        if (x < 0 || y < 0 || x + target.width() > layout.columns
                || y + target.height() > layout.rows) return false;
        for (int index = 0; index < layout.keys.size(); index++) {
            if (index == targetIndex) continue;
            GamepadTemplate.Key other = layout.keys.get(index);
            if (other.visible && x < other.x + other.width()
                    && x + target.width() > other.x && y < other.y + other.height()
                    && y + target.height() > other.y) return false;
        }
        return true;
    }

    static void anchorSample1(GamepadTemplate.Layout layout, boolean landscape) {
        boolean[][] occupied = new boolean[Math.max(1, layout.rows)][Math.max(1, layout.columns)];
        int startLeft = layout.columns < 8 || landscape ? 0 : 1;
        int startRight = layout.columns < 8 || landscape
                ? layout.columns - 3 : layout.columns - 4;
        int[] padX = {0, 2, 1, 0, 2, 1, 1};
        int[] padY = {0, 0, 1, 2, 2, 3, 2};
        int baseY = Math.max(0, layout.rows - 4);
        for (int index = 0; index < layout.keys.size(); index++) {
            GamepadTemplate.Key key = layout.keys.get(index);
            if (key.code == GamepadTemplate.JOYSTICK_CODE) continue;
            int targetX = index < 7 ? padX[index] + startLeft
                    : ((index - 7) % 3) + startRight;
            int targetY = (index < 7 ? padY[index] : (index - 7) / 3) + baseY;
            int minX = index < 7 ? startLeft : startRight;
            int maxX = index < 7
                    ? Math.min(layout.columns - key.width(), startLeft + 3 - key.width())
                    : Math.min(layout.columns - key.width(), startRight + 3 - key.width());
            int maxY = Math.max(0, layout.rows - key.height());
            int chosenX = Math.max(minX, Math.min(maxX, targetX));
            int chosenY = Math.max(0, Math.min(maxY, targetY));
            long bestDistance = Long.MAX_VALUE;
            for (int y = 0; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!freeAgainst(occupied, x, y, key.width(), key.height())) continue;
                    long dx = x - targetX;
                    long dy = y - targetY;
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        chosenX = x;
                        chosenY = y;
                    }
                }
            }
            key.x = chosenX;
            key.y = chosenY;
            for (int y = chosenY; y < chosenY + key.height(); y++)
                for (int x = chosenX; x < chosenX + key.width(); x++)
                    if (y >= 0 && y < occupied.length && x >= 0 && x < occupied[y].length)
                        occupied[y][x] = true;
        }
    }

    private static boolean freeAgainst(boolean[][] occupied, int x, int y, int width, int height) {
        if (x < 0 || y < 0 || y + height > occupied.length) return false;
        for (int row = y; row < y + height; row++) {
            if (x + width > occupied[row].length) return false;
            for (int col = x; col < x + width; col++)
                if (occupied[row][col]) return false;
        }
        return true;
    }

    private static List<GamepadTemplate.Key> defaultEditorKeys() {
        List<GamepadTemplate.Key> result = new ArrayList<>();
        for (int index = 0; index < EDITOR_CODES.length; index++) {
            GamepadTemplate.Key key = new GamepadTemplate.Key(
                    EDITOR_LABELS[index], EDITOR_CODES[index], 0, 0, 0);
            key.visible = index != EDITOR_CODES.length - 1;
            result.add(key);
        }
        return result;
    }

    private static JSONArray stored(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(ITEMS, "[]"));
        } catch (Exception error) {
            Log.e(TAG, "Invalid saved gamepad template list", error);
            return new JSONArray();
        }
    }

    private static void writeItems(Context context, JSONArray items) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ITEMS, items.toString()).commit())
            throw new IllegalStateException("Không thể lưu danh sách mẫu gamepad.");
    }

    private static JSONObject serialize(GamepadTemplate template) throws Exception {
        JSONObject result = new JSONObject();
        result.put("id", template.id);
        result.put("name", template.name);
        result.put("coordinateMode", "screen-aspect-v5");
        writeLayout(result, "portrait", template.portrait);
        writeLayout(result, "landscape", template.landscape);
        return result;
    }

    private static void writeLayout(JSONObject target, String prefix,
            GamepadTemplate.Layout layout) throws Exception {
        target.put(prefix + "Columns", layout.columns);
        target.put(prefix + "Rows", layout.rows);
        target.put(prefix + "ScreenWidth", layout.screenWidth);
        target.put(prefix + "ScreenHeight", layout.screenHeight);
        JSONArray keys = new JSONArray();
        for (GamepadTemplate.Key key : layout.keys) {
            JSONObject item = new JSONObject();
            item.put("label", key.label);
            item.put("code", key.code);
            item.put("x", key.x / Math.max(1, layout.columns));
            item.put("y", key.y / Math.max(1, layout.rows));
            item.put("size", key.size);
            item.put("w", key.width() / (float) Math.max(1, layout.columns));
            item.put("h", key.height() / (float) Math.max(1, layout.rows));
            item.put("visible", key.visible);
            keys.put(item);
        }
        target.put(prefix, keys);
    }

    private static GamepadTemplate parse(JSONObject source) {
        try {
            GamepadTemplate result = new GamepadTemplate();
            result.id = source.getString("id");
            result.name = source.optString("name", "Mẫu tùy chỉnh");
            if (source.optJSONArray("portrait") != null) {
                result.portrait = parseLayout(source, "portrait");
                result.landscape = parseLayout(source, "landscape");
            } else {
                // Migrate templates saved by the previous J2MEX editor.
                result.portrait = parseLegacyLayout(source);
                result.landscape = result.portrait.copy();
                result.landscape.columns = Math.max(6, result.landscape.columns);
                result.landscape.rows = Math.max(6, result.landscape.rows);
            }
            return result;
        } catch (Exception error) {
            Log.e(TAG, "Skipping invalid custom gamepad template", error);
            return null;
        }
    }

    private static GamepadTemplate.Layout parseLayout(JSONObject source, String prefix)
            throws Exception {
        GamepadTemplate.Layout layout = new GamepadTemplate.Layout();
        layout.columns = Math.max(6, source.optInt(prefix + "Columns", 6));
        layout.rows = Math.max(6, source.optInt(prefix + "Rows", 6));
        layout.screenWidth = source.optInt(prefix + "ScreenWidth", 0);
        layout.screenHeight = source.optInt(prefix + "ScreenHeight", 0);
        JSONArray keys = source.getJSONArray(prefix);
        for (int index = 0; index < keys.length(); index++) {
            JSONObject item = keys.optJSONObject(index);
            if (item == null) continue;
            float x = (float) (item.optDouble("x", 0) * layout.columns);
            float y = (float) (item.optDouble("y", 0) * layout.rows);
            GamepadTemplate.Key key = new GamepadTemplate.Key(
                    item.optString("label", "?"), item.optInt("code"), x, y,
                    item.optInt("size", 0));
            key.visible = item.optBoolean("visible", true);
            layout.keys.add(key);
        }
        ensureKeys(layout);
        return layout;
    }

    private static GamepadTemplate.Layout parseLegacyLayout(JSONObject source) {
        GamepadTemplate.Layout layout = new GamepadTemplate.Layout();
        layout.columns = 10;
        layout.rows = 10;
        JSONArray keys = source.optJSONArray("keys");
        if (keys != null) {
            for (int index = 0; index < keys.length(); index++) {
                JSONObject item = keys.optJSONObject(index);
                if (item == null) continue;
                int code = item.optInt("code");
                float w = (float) item.optDouble("width", .12);
                float h = (float) item.optDouble("height", .16);
                int size = w >= .19f || h >= .24f ? 1 : 0;
                GamepadTemplate.Key key = new GamepadTemplate.Key(
                        item.optString("label", "?"), code,
                        clamp(item.optDouble("x", 0), 0, .9f) * layout.columns,
                        clamp(item.optDouble("y", 0), 0, .9f) * layout.rows, size);
                key.visible = item.optBoolean("visible", true);
                layout.keys.add(key);
            }
        }
        ensureKeys(layout);
        return layout;
    }

    private static void ensureKeys(GamepadTemplate.Layout layout) {
        for (int index = 0; index < EDITOR_CODES.length; index++) {
            int code = EDITOR_CODES[index];
            boolean found = false;
            for (GamepadTemplate.Key key : layout.keys)
                if (key.code == code) { found = true; break; }
            if (!found) {
                GamepadTemplate.Key key = new GamepadTemplate.Key(
                        EDITOR_LABELS[index], code, 0, 0, 0);
                key.visible = code != GamepadTemplate.JOYSTICK_CODE;
                layout.keys.add(key);
            }
        }
    }

    private static String nextName(JSONArray items) {
        int next = 4;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            String name = item.optString("name", "");
            if (name.startsWith("Mẫu ")) {
                try { next = Math.max(next, Integer.parseInt(name.substring(4)) + 1); }
                catch (NumberFormatException ignored) { }
            }
        }
        return "Mẫu " + next;
    }

    private static float clamp(double value, float min, float max) {
        return (float) Math.max(min, Math.min(max, value));
    }

    private static void clearDeletedReferences(Context context, String id) {
        File preferencesDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = preferencesDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (!name.startsWith("game_settings_") || !name.endsWith(".xml")) continue;
            SharedPreferences preferences = context.getSharedPreferences(
                    name.substring(0, name.length() - 4), Context.MODE_PRIVATE);
            if (id.equals(preferences.getString("gamepad_layout_id", "builtin:0"))) {
                preferences.edit().putString("gamepad_layout_id", "builtin:0").commit();
            }
        }
    }
}
