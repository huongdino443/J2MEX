/*
 * Adapted from J2ME Loader's KeyMapper and KeyMapperActivity.
 *
 * Copyright 2018-2019 Nikita Shakarun
 * Copyright 2020-2022 Yury Kharchenko
 * Copyright 2023 Cansor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.recompile.freej2me.lite;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

final class PhysicalKeyMapping {
    static final int UP = -1;
    static final int DOWN = -2;
    static final int LEFT = -3;
    static final int RIGHT = -4;
    static final int FIRE = -5;
    static final int SOFT_LEFT = -6;
    static final int SOFT_RIGHT = -7;
    static final int SEND = -10;
    static final int END = -11;

    private static final int[] TARGETS = {
            UP, DOWN, LEFT, RIGHT, FIRE, SOFT_LEFT, SOFT_RIGHT, SEND, END,
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '*', '#'
    };

    private PhysicalKeyMapping() { }

    static SparseIntArray defaults() {
        SparseIntArray map = new SparseIntArray();
        map.put(KeyEvent.KEYCODE_DPAD_UP, UP);
        map.put(KeyEvent.KEYCODE_DPAD_DOWN, DOWN);
        map.put(KeyEvent.KEYCODE_DPAD_LEFT, LEFT);
        map.put(KeyEvent.KEYCODE_DPAD_RIGHT, RIGHT);
        map.put(KeyEvent.KEYCODE_DPAD_CENTER, FIRE);
        map.put(KeyEvent.KEYCODE_ENTER, FIRE);
        map.put(KeyEvent.KEYCODE_SOFT_LEFT, SOFT_LEFT);
        map.put(KeyEvent.KEYCODE_SOFT_RIGHT, SOFT_RIGHT);
        map.put(KeyEvent.KEYCODE_CALL, SEND);
        map.put(KeyEvent.KEYCODE_ENDCALL, END);
        map.put(KeyEvent.KEYCODE_STAR, '*');
        map.put(KeyEvent.KEYCODE_POUND, '#');
        for (int digit = 0; digit <= 9; digit++) {
            map.put(KeyEvent.KEYCODE_0 + digit, '0' + digit);
            map.put(KeyEvent.KEYCODE_NUMPAD_0 + digit, '0' + digit);
        }
        return map;
    }

    static SparseIntArray decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return defaults();
        SparseIntArray result = new SparseIntArray();
        String[] pairs = encoded.split(",");
        for (String pair : pairs) {
            String[] values = pair.split(":", 2);
            if (values.length != 2) continue;
            try {
                int androidKey = Integer.parseInt(values[0]);
                int midpKey = Integer.parseInt(values[1]);
                if (androidKey >= 0 && isTarget(midpKey)) result.put(androidKey, midpKey);
            } catch (NumberFormatException ignored) { }
        }
        return result.size() == 0 ? defaults() : result;
    }

    static String encode(SparseIntArray map) {
        if (map == null || equal(map, defaults())) return "";
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < map.size(); index++) {
            if (encoded.length() > 0) encoded.append(',');
            encoded.append(map.keyAt(index)).append(':').append(map.valueAt(index));
        }
        return encoded.toString();
    }

    static SparseIntArray assign(SparseIntArray current, int androidKey, int midpKey) {
        SparseIntArray updated = copy(current);
        for (int index = updated.size() - 1; index >= 0; index--) {
            if (updated.keyAt(index) == androidKey || updated.valueAt(index) == midpKey)
                updated.removeAt(index);
        }
        updated.put(androidKey, midpKey);
        return updated;
    }

    static int codeForEvent(SparseIntArray map, KeyEvent event) {
        if (event == null) return 0;
        if (!event.isShiftPressed() && map != null) {
            int index = map.indexOfKey(event.getKeyCode());
            if (index >= 0) return map.valueAt(index);
        }
        int unicode = event.getUnicodeChar();
        return unicode & ~KeyCharacterMap.COMBINING_ACCENT_MASK;
    }

    static String targetLabel(Context context, int target) {
        switch (target) {
            case UP: return AppLocalization.text(context, "Lên");
            case DOWN: return AppLocalization.text(context, "Xuống");
            case LEFT: return AppLocalization.text(context, "Trái");
            case RIGHT: return AppLocalization.text(context, "Phải");
            case FIRE: return AppLocalization.text(context, "Chọn");
            case SOFT_LEFT: return AppLocalization.text(context, "Phím mềm trái");
            case SOFT_RIGHT: return AppLocalization.text(context, "Phím mềm phải");
            case SEND: return AppLocalization.text(context, "Gọi");
            case END: return AppLocalization.text(context, "Kết thúc");
            default: return String.valueOf((char) target);
        }
    }

    static String mappedKeyLabel(Context context, SparseIntArray map, int target) {
        StringBuilder label = new StringBuilder();
        if (map != null) {
            for (int index = 0; index < map.size(); index++) {
                if (map.valueAt(index) != target) continue;
                if (label.length() > 0) label.append(" · ");
                label.append(keyLabel(map.keyAt(index)));
            }
        }
        return label.length() == 0
                ? AppLocalization.text(context, "Chưa gán") : label.toString();
    }

    static String keyLabelForCapture(int keyCode) {
        return keyLabel(keyCode);
    }

    static boolean isTarget(int value) {
        for (int target : TARGETS) if (value == target) return true;
        return false;
    }

    private static boolean equal(SparseIntArray first, SparseIntArray second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (first.keyAt(index) != second.keyAt(index)
                    || first.valueAt(index) != second.valueAt(index)) return false;
        }
        return true;
    }

    private static SparseIntArray copy(SparseIntArray source) {
        SparseIntArray result = new SparseIntArray();
        if (source != null) {
            for (int index = 0; index < source.size(); index++)
                result.put(source.keyAt(index), source.valueAt(index));
        }
        return result;
    }

    private static String keyLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
            case KeyEvent.KEYCODE_DPAD_CENTER: return "D-pad";
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_SOFT_LEFT: return "Soft Left";
            case KeyEvent.KEYCODE_SOFT_RIGHT: return "Soft Right";
            case KeyEvent.KEYCODE_CALL: return "Call";
            case KeyEvent.KEYCODE_ENDCALL: return "End";
            case KeyEvent.KEYCODE_STAR: return "*";
            case KeyEvent.KEYCODE_POUND: return "#";
            default:
                String name = KeyEvent.keyCodeToString(keyCode);
                if (name.startsWith("KEYCODE_")) name = name.substring(8);
                return name.replace('_', ' ');
        }
    }
}
