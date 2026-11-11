package org.recompile.freej2me.lite;

import java.util.ArrayList;
import java.util.List;

final class GamepadTemplate {
    static final int JOYSTICK_CODE = -1000;

    static final class Key {
        String label;
        int code;
        int size;
        float x;
        float y;
        boolean visible = true;

        Key(String label, int code, float x, float y, int size) {
            this.label = label;
            this.code = code;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        int width() {
            return code == JOYSTICK_CODE ? 3 : (size == 0 ? 1 : 2);
        }

        int height() {
            return code == JOYSTICK_CODE ? 3 : (size == 2 ? 2 : 1);
        }

        Key copy() {
            Key result = new Key(label, code, x, y, size);
            result.visible = visible;
            return result;
        }
    }

    static final class Layout {
        int columns = 6;
        int rows = 6;
        int screenWidth;
        int screenHeight;
        final List<Key> keys = new ArrayList<>();

        Layout copy() {
            Layout result = new Layout();
            result.columns = columns;
            result.rows = rows;
            result.screenWidth = screenWidth;
            result.screenHeight = screenHeight;
            for (Key key : keys) result.keys.add(key.copy());
            return result;
        }
    }

    String id;
    String name;
    int builtinIndex = -1;
    Layout portrait = new Layout();
    Layout landscape = new Layout();

    boolean isCustom() {
        return builtinIndex < 0;
    }

    Layout layout(boolean isLandscape) {
        return isLandscape ? landscape : portrait;
    }

    GamepadTemplate copy() {
        GamepadTemplate result = new GamepadTemplate();
        result.id = id;
        result.name = name;
        result.builtinIndex = builtinIndex;
        result.portrait = portrait.copy();
        result.landscape = landscape.copy();
        return result;
    }
}
