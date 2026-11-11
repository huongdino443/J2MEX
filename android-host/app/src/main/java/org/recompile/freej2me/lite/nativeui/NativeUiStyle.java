package org.recompile.freej2me.lite.nativeui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ProgressBar;

/** Shared visual tokens for Android's native LCDUI presentation. */
final class NativeUiStyle {
    static final int GROUPED_BACKGROUND = 0xFF0E1826;
    static final int SURFACE = 0xFF192434;
    static final int MENU_SURFACE = 0xFF192434;
    static final int FIELD_SURFACE = 0xFF192434;
    static final int INTERACTIVE_SURFACE = 0xFF192434;
    static final int BUTTON_SURFACE = 0xFF192434;
    static final int LIST_SELECTED_SURFACE = 0xFF2B3548;
    static final int DIVIDER = 0xFF26354A;
    static final int BUTTON_OUTLINE = 0xFF3D4A5E;
    private static final int FILTER_BUTTON_OUTLINE = 0x463D4A5E;
    static final int FIELD_OUTLINE = 0xFF3D4A5E;
    static final int INTERACTIVE_OUTLINE = 0xFF34445B;
    static final int TITLE_DIVIDER_INSET_DP = 8;
    static final int COMMAND_DIVIDER_INSET_DP = 8;
    static final int TITLE_BAR_HEIGHT_DP = 46;
    static final int ACCENT = 0xFFB9C3FF;
    static final int ACCENT_CONTAINER = 0xFF2F313A;
    static final int MENU_OUTLINE = 0xFF3D4A5E;
    static final int MENU_RADIUS_DP = 14;
    static final int COMMAND_BUTTON_HEIGHT_DP = 48;
    static final int COMMAND_MENU_ROW_HEIGHT_DP = 52;
    static final int COMMAND_BUTTON_RADIUS_DP = COMMAND_BUTTON_HEIGHT_DP / 2;
    static final int TEXT_LINE_SPACING_DP = 3;
    static final int COMMAND_MENU_BUTTON_GAP_DP = 24;
    static final int ON_ACCENT_CONTAINER = 0xFFE5E1E9;
    private static final int PRIMARY_TEXT = 0xFFE8EFF7;
    private static final int SECONDARY_TEXT = 0xFF9DAABB;
    private static final int CHOICE_PRIMARY_TEXT = 0xFFE5E1E9;
    private static final int MENU_PRIMARY_TEXT = 0xFFE8EFF7;

    private NativeUiStyle() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int primaryText(Context context) {
        return PRIMARY_TEXT;
    }

    static int secondaryText(Context context) {
        return SECONDARY_TEXT;
    }

    static int choicePrimaryText(Context context) {
        return CHOICE_PRIMARY_TEXT;
    }

    static int menuPrimaryText(Context context) {
        return MENU_PRIMARY_TEXT;
    }

    static int accent(Context context) {
        return ACCENT;
    }

    static Drawable card(Context context, int radiusDp) {
        return shape(context, SURFACE, BUTTON_OUTLINE, radiusDp);
    }

    static Drawable menuSurface(Context context) {
        return shape(context, MENU_SURFACE, MENU_OUTLINE, MENU_RADIUS_DP);
    }

    static Drawable dropdownSurface(Context context) {
        return menuSurface(context);
    }

    static int menuWidth(Context context, CharSequence[] labels,
            int availableWidth, int leftInset) {
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
                context.getResources().getDisplayMetrics()));
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        float widest = 0;
        if (labels != null) for (CharSequence label : labels) {
            if (label != null) widest = Math.max(widest, text.measureText(label.toString()));
        }
        int viewport = availableWidth > 0
                ? availableWidth : context.getResources().getDisplayMetrics().widthPixels;
        int inset = Math.max(0, leftInset);
        int minimum = Math.round(viewport * 0.6f);
        int maximum = Math.max(minimum, viewport - inset * 2);
        int desired = (int)Math.ceil(widest) + dp(context, 38);
        return Math.min(maximum, Math.max(minimum, desired));
    }

    static int menuHeight(Context context, int rowCount, int availableHeight) {
        int rowHeight = dp(context, COMMAND_MENU_ROW_HEIGHT_DP);
        int desired = Math.max(rowHeight, Math.max(0, rowCount) * rowHeight) + dp(context, 2);
        return Math.min(desired, Math.max(rowHeight + dp(context, 2), availableHeight));
    }

    static Drawable spinnerControl(Context context) {
        return new LayerDrawable(new Drawable[]{
                control(context), new DropdownArrowDrawable(context, SECONDARY_TEXT, ACCENT)
        });
    }

    static Drawable control(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                shape(context, FIELD_SURFACE, ACCENT, 8));
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(context, FIELD_SURFACE, FIELD_OUTLINE, 8));
        states.addState(new int[0], shape(context, FIELD_SURFACE, FIELD_OUTLINE, 8));
        return states;
    }

    static Drawable choiceRow(Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                shape(context, LIST_SELECTED_SURFACE, Color.TRANSPARENT, 0));
        states.addState(new int[]{android.R.attr.state_activated},
                shape(context, LIST_SELECTED_SURFACE, Color.TRANSPARENT, 0));
        states.addState(new int[]{android.R.attr.state_selected},
                shape(context, LIST_SELECTED_SURFACE, Color.TRANSPARENT, 0));
        states.addState(new int[]{android.R.attr.state_pressed},
                shape(context, LIST_SELECTED_SURFACE, Color.TRANSPARENT, 0));
        states.addState(new int[0], shape(context, Color.TRANSPARENT, Color.TRANSPARENT, 0));
        return states;
    }

    static Drawable commandMenuRow(Context context) {
        StateListDrawable states = new StateListDrawable();
        int[][] selected = new int[][]{
                {android.R.attr.state_focused},
                {android.R.attr.state_activated},
                {android.R.attr.state_selected},
                {android.R.attr.state_pressed}
        };
        for (int[] state : selected) {
            states.addState(state,
                    shape(context, LIST_SELECTED_SURFACE, Color.TRANSPARENT, 0));
        }
        states.addState(new int[0],
                shape(context, Color.TRANSPARENT, Color.TRANSPARENT, 0));
        return states;
    }

    static Drawable activeListRow(Context context) {
        return shape(context, LIST_SELECTED_SURFACE, Color.TRANSPARENT, 0);
    }

    static void styleProgress(Context context, ProgressBar progress) {
        ColorStateList accent = ColorStateList.valueOf(ACCENT);
        progress.setIndeterminateTintList(accent);
        progress.setProgressTintList(accent);
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(BUTTON_OUTLINE));
    }

    static void styleSlider(Context context, android.widget.SeekBar slider) {
        ColorStateList accent = ColorStateList.valueOf(ACCENT);
        int thumbSafeInset = dp(context, 16);
        slider.setPaddingRelative(thumbSafeInset, slider.getPaddingTop(),
                thumbSafeInset, slider.getPaddingBottom());
        slider.setProgressTintList(accent);
        slider.setThumbTintList(accent);
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(INTERACTIVE_OUTLINE));
    }

    static Drawable interactive(Context context) {
        return ripple(context, INTERACTIVE_SURFACE, INTERACTIVE_OUTLINE, 10);
    }

    static Drawable itemPress(Context context) {
        return ripple(context, Color.TRANSPARENT, Color.TRANSPARENT, 8, 0xAA2B3548);
    }

    static Drawable formButton(Context context, boolean enabled) {
        if (enabled) return ripple(context, ACCENT, FILTER_BUTTON_OUTLINE,
                COMMAND_BUTTON_RADIUS_DP, 0x33B9C3FF);
        return shape(context, ACCENT, FILTER_BUTTON_OUTLINE, COMMAND_BUTTON_RADIUS_DP);
    }

    static void styleFormButton(Context context, TextView button, boolean enabled) {
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(android.view.Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setTextColor(GROUPED_BACKGROUND);
        button.setBackground(formButton(context, enabled));
    }

    private static Drawable buttonBackground(Context context) {
        return ripple(context, BUTTON_SURFACE, FILTER_BUTTON_OUTLINE, 22);
    }

    private static Drawable ripple(Context context, int fill, int stroke, int radiusDp) {
        return ripple(context, fill, stroke, radiusDp, 0x33B9C3FF);
    }

    private static Drawable ripple(Context context, int fill, int stroke, int radiusDp, int rippleColor) {
        Drawable content = shape(context, fill, stroke, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private static GradientDrawable shape(Context context, int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radiusDp));
        if (stroke != Color.TRANSPARENT) {
            shape.setStroke(Math.max(1, dp(context, 1)), stroke);
        }
        return shape;
    }

    private static final class DropdownArrowDrawable extends Drawable {
        private final float halfWidth;
        private final float halfHeight;
        private final float rightInset;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mutedColor;
        private final int focusedColor;
        private boolean focused;

        DropdownArrowDrawable(Context context, int mutedColor, int focusedColor) {
            halfWidth = dp(context, 5);
            halfHeight = dp(context, 3);
            rightInset = dp(context, 18);
            this.mutedColor = mutedColor;
            this.focusedColor = focusedColor;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(mutedColor);
        }

        @Override public void draw(Canvas canvas) {
            float centerX = getBounds().right - rightInset;
            float centerY = getBounds().exactCenterY();
            Path triangle = new Path();
            triangle.moveTo(centerX - halfWidth, centerY - halfHeight / 2f);
            triangle.lineTo(centerX + halfWidth, centerY - halfHeight / 2f);
            triangle.lineTo(centerX, centerY + halfHeight);
            triangle.close();
            paint.setColor(focused ? focusedColor : mutedColor);
            canvas.drawPath(triangle, paint);
        }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean next = false;
            for (int value : state) if (value == android.R.attr.state_focused) {
                next = true;
                break;
            }
            if (focused == next) return false;
            focused = next;
            invalidateSelf();
            return true;
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static void styleButton(Context context, Button button, boolean prominent) {
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setGravity(android.view.Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        button.setBackgroundTintList(null);
        button.setTextColor(PRIMARY_TEXT);
        button.setBackground(buttonBackground(context));
        button.setElevation(0);
        button.setStateListAnimator(null);
    }

    static void styleCommandButton(Context context, Button button, boolean prominent) {
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(android.view.Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setBackgroundTintList(null);
        button.setElevation(0);
        button.setStateListAnimator(null);
        if (prominent) {
            button.setTextColor(GROUPED_BACKGROUND);
            button.setBackground(formButton(context, true));
        } else {
            button.setTextColor(PRIMARY_TEXT);
            button.setBackground(ripple(context, BUTTON_SURFACE, FILTER_BUTTON_OUTLINE,
                    COMMAND_BUTTON_RADIUS_DP));
        }
    }
}
