/*
 * Adapted from J2ME Loader's KeyMapperActivity.
 *
 * Copyright 2018-2019 Nikita Shakarun
 * Copyright 2020-2022 Yury Kharchenko
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

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyMappingActivity extends LocalizedActivity {
    static final String EXTRA_MAPPING = "key_mapping.value";
    static final String EXTRA_RESULT = "key_mapping.result";
    private static final String STATE_MAPPING = "key_mapping.state";
    private static final int COLOR_BACKGROUND = Color.rgb(14, 24, 38);
    private static final int COLOR_CARD = Color.rgb(19, 30, 44);
    private static final int COLOR_SURFACE = Color.rgb(25, 36, 52);
    private static final int COLOR_BORDER = Color.rgb(61, 74, 94);
    private static final int COLOR_TEXT = Color.rgb(232, 239, 247);
    private static final int COLOR_MUTED = Color.rgb(157, 170, 187);
    private static final int COLOR_ACCENT = Color.rgb(185, 195, 255);
    private static final int NO_CAPTURE = Integer.MIN_VALUE;

    private static final String[] SECTION_TITLES = {
            "Điều hướng", "Phím mềm", "Bàn phím số"
    };
    private static final int[][] SECTION_TARGETS = {
            {PhysicalKeyMapping.UP, PhysicalKeyMapping.DOWN,
                    PhysicalKeyMapping.LEFT, PhysicalKeyMapping.RIGHT,
                    PhysicalKeyMapping.FIRE},
            {PhysicalKeyMapping.SOFT_LEFT, PhysicalKeyMapping.SOFT_RIGHT,
                    PhysicalKeyMapping.SEND, PhysicalKeyMapping.END},
            {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '*', '#'}
    };

    private SparseIntArray mappings;
    private final Map<Integer, TextView> mappedLabels = new LinkedHashMap<>();
    private TextView captureHint;
    private int captureTarget = NO_CAPTURE;
    private int capturedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int capturedDeviceId = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        String saved = state == null
                ? getIntent().getStringExtra(EXTRA_MAPPING)
                : state.getString(STATE_MAPPING);
        mappings = PhysicalKeyMapping.decode(saved);
        setContentView(createContent());
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int section = 0; section < SECTION_TITLES.length; section++) {
            addSection(content, SECTION_TITLES[section]);
            for (int target : SECTION_TARGETS[section]) addTargetRow(content, target);
        }
        updateMappedLabels();

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        captureHint = bodyText("Chưa chọn hành động.", 13);
        captureHint.setTextColor(COLOR_MUTED);
        captureHint.setPadding(dp(20), dp(4), dp(20), dp(8));
        footer.addView(captureHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(4), dp(16), dp(8));
        footer.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView save = actionButton("Lưu", true);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                0, dp(42), 1f);
        saveParams.setMargins(0, 0, dp(8), 0);
        actions.addView(save, saveParams);
        save.setOnClickListener(view -> saveAndReturn());

        TextView reset = actionButton("Đặt lại", false);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                0, dp(42), 1f);
        resetParams.setMargins(0, 0, dp(8), 0);
        actions.addView(reset, resetParams);
        reset.setOnClickListener(view -> resetMappings());

        TextView cancel = actionButton("Hủy", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(42), 1f));
        cancel.setOnClickListener(view -> finish());
        return root;
    }

    private void addSection(LinearLayout parent, String label) {
        TextView heading = bodyText(label, 12);
        heading.setTextColor(COLOR_ACCENT);
        heading.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        params.bottomMargin = dp(4);
        parent.addView(heading, params);
    }

    private void addTargetRow(LinearLayout parent, int target) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(round(COLOR_CARD, 14, COLOR_BORDER));
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        rowParams.bottomMargin = dp(5);
        parent.addView(row, rowParams);

        TextView action = bodyText(PhysicalKeyMapping.targetLabel(this, target), 15);
        row.addView(action, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView mapped = bodyText("", 13);
        mapped.setTextColor(COLOR_ACCENT);
        mapped.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        mapped.setSingleLine(true);
        mapped.setPadding(dp(6), 0, 0, 0);
        row.addView(mapped, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        mappedLabels.put(target, mapped);
        row.setOnClickListener(view -> beginCapture(target));
    }

    private void beginCapture(int target) {
        captureTarget = target;
        captureHint.setText(AppLocalization.text(this,
                "Đang chờ phím cho: ") + PhysicalKeyMapping.targetLabel(this, target));
        updateMappedLabels();
    }

    private void resetMappings() {
        mappings = PhysicalKeyMapping.defaults();
        captureTarget = NO_CAPTURE;
        captureHint.setText(AppLocalization.text(this, "Đã khôi phục ánh xạ mặc định."));
        updateMappedLabels();
    }

    private void updateMappedLabels() {
        for (Map.Entry<Integer, TextView> entry : mappedLabels.entrySet()) {
            int target = entry.getKey();
            TextView label = entry.getValue();
            label.setText(captureTarget == target
                    ? AppLocalization.text(this, "Nhấn phím…")
                    : PhysicalKeyMapping.mappedKeyLabel(this, mappings, target));
            label.setTextColor(captureTarget == target ? COLOR_ACCENT : COLOR_MUTED);
            label.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void saveAndReturn() {
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT, PhysicalKeyMapping.encode(mappings));
        setResult(RESULT_OK, result);
        finish();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == capturedKeyCode
                && event.getDeviceId() == capturedDeviceId) {
            capturedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            capturedDeviceId = -1;
            return true;
        }
        if (captureTarget != NO_CAPTURE && event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (isProtectedSystemKey(keyCode)) return super.dispatchKeyEvent(event);
            if (event.getRepeatCount() != 0) return true;
            mappings = PhysicalKeyMapping.assign(mappings, keyCode, captureTarget);
            captureHint.setText(AppLocalization.text(this, "Đã gán: ")
                    + PhysicalKeyMapping.keyLabelForCapture(keyCode));
            capturedKeyCode = keyCode;
            capturedDeviceId = event.getDeviceId();
            captureTarget = NO_CAPTURE;
            updateMappedLabels();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isProtectedSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_UNKNOWN
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_MAPPING, PhysicalKeyMapping.encode(mappings));
        super.onSaveInstanceState(outState);
    }

    private TextView bodyText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(AppLocalization.text(this, text));
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView actionButton(String text, boolean primary) {
        TextView button = bodyText(text, 14);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? COLOR_BACKGROUND : COLOR_TEXT);
        button.setBackground(round(primary ? COLOR_ACCENT : COLOR_SURFACE, 24,
                primary ? COLOR_ACCENT : COLOR_BORDER));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
