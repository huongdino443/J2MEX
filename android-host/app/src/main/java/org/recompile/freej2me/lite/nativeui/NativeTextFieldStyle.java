package org.recompile.freej2me.lite.nativeui;

import android.widget.EditText;

/** Applies the shared LCDUI TextField colors and focused outline to app inputs. */
public final class NativeTextFieldStyle {
    private NativeTextFieldStyle() {}

    public static void apply(EditText field) {
        apply(field, NativeUiStyle.FIELD_SURFACE);
    }

    public static void apply(EditText field, int focusedSurface) {
        if (field == null) return;
        field.setTextColor(NativeUiStyle.primaryText(field.getContext()));
        field.setHintTextColor(NativeUiStyle.secondaryText(field.getContext()));
        field.setBackgroundTintList(null);
        field.setBackground(NativeUiStyle.control(field.getContext(), focusedSurface));
    }
}
