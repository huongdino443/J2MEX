package org.recompile.freej2me.lite.nativeui;

import android.text.InputType;

/** Native MIDP constraints mapping; game bytecode is never loaded in ART. */
public final class NativeInputTypes {
    private NativeInputTypes() { }

    public static boolean isPassword(int constraints) {
        return (constraints & 0x10000) != 0;
    }

    public static int of(int constraints) {
        int kind = constraints & 0xFFFF;
        int type;
        switch (kind) {
            case 2: type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED; break;
            case 5: type = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL; break;
            case 3: type = InputType.TYPE_CLASS_PHONE; break;
            case 1: type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; break;
            case 4: type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI; break;
            default: type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        if (isPassword(constraints)) {
            if (kind == 2 || kind == 5) type |= InputType.TYPE_NUMBER_VARIATION_PASSWORD;
            else type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            if (kind != 2 && kind != 5) type |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        }
        if ((constraints & (0x40000 | 0x80000)) != 0 && kind != 2 && kind != 5) type |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if ((constraints & 0x100000) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        if ((constraints & 0x200000) != 0) type |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        return type;
    }
}
