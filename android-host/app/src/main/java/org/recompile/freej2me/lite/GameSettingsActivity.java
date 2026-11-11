package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

public final class GameSettingsActivity extends LocalizedActivity {
    static final String EXTRA_JAR = "game_settings.jar";
    static final String EXTRA_TITLE = "game_settings.title";
    static final String EXTRA_LAUNCH_AFTER_SAVE = "game_settings.launch_after_save";
    private static final int REQUEST_GAMEPAD_EDITOR = 7301;
    private static final int COLOR_BACKGROUND = Color.rgb(14, 24, 38);
    private static final int COLOR_CARD = Color.rgb(19, 30, 44);
    private static final int COLOR_SURFACE = Color.rgb(25, 36, 52);
    private static final int COLOR_ROW_ACTIVE = Color.rgb(35, 46, 62);
    private static final int COLOR_BORDER = Color.rgb(61, 74, 94);
    private static final int COLOR_TEXT = Color.rgb(232, 239, 247);
    private static final int COLOR_MUTED = Color.rgb(157, 170, 187);
    private static final int COLOR_ACCENT = Color.rgb(185, 195, 255);
    private static final int VALUE_BUTTON_WIDTH_DP = 150;
    private static final String[] PRESETS = {
            "240 × 320", "320 × 240", "320 × 480", "352 × 416", "360 × 640", "480 × 800"
    };
    private static final int[][] PRESET_SIZES = {
            {240, 320}, {320, 240}, {320, 480}, {352, 416}, {360, 640}, {480, 800}
    };
    private static final String[] POSITIONS = {"Trên", "Giữa", "Dưới"};
    private static final int REQUEST_KEY_MAPPING = 7302;

    private File jar;
    private String gameTitle;
    private boolean launchAfterSave;
    private GameSettings settings;
    private EditText widthInput;
    private EditText heightInput;
    private ScrollView settingsScroll;
    private Switch reserveGamepadSwitch;
    private Switch canvasOutlineSwitch;
    private Switch touchEnabledSwitch;
    private Switch gamepadEnabledSwitch;
    private Switch bitmapFontSwitch;
    private Switch crtFilterSwitch;
    private TextView positionButton;
    private TextView scaleValue;
    private SeekBar scaleSeekBar;
    private LinearLayout positionRow;
    private LinearLayout scaleRow;
    private TextView gamepadLayoutButton;
    private TextView editTemplateButton;
    private TextView deleteTemplateButton;
    private PopupWindow activePopup;
    private View activePopupDim;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }

        String jarPath = getIntent().getStringExtra(EXTRA_JAR);
        if (jarPath == null || jarPath.isEmpty()) {
            finish();
            return;
        }
        jar = new File(jarPath);
        gameTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (gameTitle == null || gameTitle.trim().isEmpty()) gameTitle = "Thiết lập game";
        launchAfterSave = getIntent().getBooleanExtra(EXTRA_LAUNCH_AFTER_SAVE, false);
        settings = GameSettings.load(this, jar);
        settings.gamepadLayoutId = GamepadTemplateLibrary.resolveId(
                this, settings.gamepadLayoutId);
        restoreTransientSettings(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(8), dp(16), dp(4));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        TextView cancel = actionButton("Hủy", false);
        toolbar.addView(cancel, new LinearLayout.LayoutParams(dp(82), dp(42)));
        TextView title = new TextView(this);
        title.setText(gameTitle);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(8), 0, dp(8), 0);
        toolbar.addView(title, titleParams);
        TextView save = actionButton(launchAfterSave ? "Bắt đầu" : "Lưu", true);
        toolbar.addView(save, new LinearLayout.LayoutParams(dp(82), dp(42)));
        save.setOnClickListener(view -> saveAndReturn());
        cancel.setOnClickListener(view -> finish());

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(settingsScroll, scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(28));
        settingsScroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout displayCard = settingsCard(content);
        addSection(displayCard, "Hiển thị");
        addCanvasSizeControls(displayCard);
        reserveGamepadSwitch = addSwitch(displayCard, "Chừa chỗ cho gamepad",
                settings.reserveGamepadArea);
        reserveGamepadSwitch.setContentDescription(
                "Canvas neo phía trên và không chồng lên gamepad");
        reserveGamepadSwitch.setOnCheckedChangeListener((button, checked) ->
                updateCanvasControlAvailability());
        canvasOutlineSwitch = addSwitch(displayCard, "Đường bao",
                settings.canvasOutlineEnabled);

        positionRow = addValueRow(displayCard, "Vị trí Canvas");
        positionButton = plainValueButton(POSITIONS[settings.canvasPosition]);
        positionRow.addView(positionButton, new LinearLayout.LayoutParams(
                dp(VALUE_BUTTON_WIDTH_DP), dp(44)));
        positionButton.setOnClickListener(view -> showPositionPicker());
        positionRow.setOnClickListener(view -> showPositionPicker());

        scaleRow = new LinearLayout(this);
        scaleRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleRow.setClipChildren(false);
        scaleRow.setClipToPadding(false);
        LinearLayout.LayoutParams scaleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        scaleParams.topMargin = dp(2);
        displayCard.addView(scaleRow, scaleParams);
        TextView scaleLabel = bodyText("Tỷ lệ Canvas", 14);
        scaleRow.addView(scaleLabel, new LinearLayout.LayoutParams(dp(88), dp(48)));
        scaleSeekBar = new SeekBar(this);
        scaleSeekBar.setPadding(0, 0, 0, 0);
        scaleSeekBar.setTranslationY(dp(1));
        scaleSeekBar.setMax(50);
        scaleSeekBar.setProgress(settings.canvasScale - 50);
        scaleSeekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        scaleSeekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        scaleSeekBar.setProgressBackgroundTintList(ColorStateList.valueOf(COLOR_BORDER));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        seekParams.setMargins(dp(2), 0, dp(4), 0);
        scaleRow.addView(scaleSeekBar, seekParams);
        scaleValue = bodyText(settings.canvasScale + "%", 14);
        scaleValue.setGravity(Gravity.CENTER);
        scaleRow.addView(scaleValue, new LinearLayout.LayoutParams(dp(48), dp(48)));
        scaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                scaleValue.setText((progress + 50) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        bitmapFontSwitch = addSwitch(displayCard, "Font bitmap tiếng Việt",
                settings.bitmapFontEnabled);
        crtFilterSwitch = addSwitch(displayCard, "Bộ lọc CRT Grid",
                settings.crtFilterEnabled);

        LinearLayout gamepadCard = settingsCard(content);
        addSection(gamepadCard, "Điều khiển");
        touchEnabledSwitch = addSwitch(gamepadCard, "Chế độ cảm ứng",
                settings.touchEnabled);
        touchEnabledSwitch.setContentDescription(
                "Bật để game nhận và nhận diện sự kiện chạm; không điều khiển việc hiện gamepad ảo.");
        gamepadEnabledSwitch = addSwitch(gamepadCard, "Hiển thị gamepad ảo",
                settings.gamepadEnabled);
        gamepadEnabledSwitch.setContentDescription(
                "Chỉ điều khiển việc hiện gamepad ảo, độc lập với chế độ cảm ứng của game.");
        LinearLayout layoutRow = addValueRow(gamepadCard, "Bố cục");
        gamepadLayoutButton = plainValueButton(
                GamepadTemplateLibrary.get(this, settings.gamepadLayoutId).name);
        layoutRow.addView(gamepadLayoutButton,
                new LinearLayout.LayoutParams(dp(VALUE_BUTTON_WIDTH_DP), dp(44)));
        gamepadLayoutButton.setOnClickListener(view -> showGamepadLayoutPicker());
        layoutRow.setOnClickListener(view -> showGamepadLayoutPicker());

        LinearLayout templateActions = new LinearLayout(this);
        templateActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(4);
        gamepadCard.addView(templateActions, actionsParams);
        TextView createTemplateButton = actionButton("Tạo mẫu", false);
        templateActions.addView(createTemplateButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        editTemplateButton = actionButton("Sửa", false);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(42), .75f);
        editParams.setMargins(dp(6), 0, dp(6), 0);
        templateActions.addView(editTemplateButton, editParams);
        deleteTemplateButton = actionButton("Xóa", false);
        templateActions.addView(deleteTemplateButton, new LinearLayout.LayoutParams(0, dp(42), .75f));
        createTemplateButton.setOnClickListener(view -> openGamepadEditor(null));
        editTemplateButton.setOnClickListener(view -> chooseCustomTemplate(false));
        deleteTemplateButton.setOnClickListener(view -> chooseCustomTemplate(true));
        addKeyMappingRow(gamepadCard);
        updateTemplateActions();
        updateCanvasControlAvailability();
        if (state != null) {
            widthInput.setText(state.getString("game_settings.width_input",
                    widthInput.getText().toString()));
            heightInput.setText(state.getString("game_settings.height_input",
                    heightInput.getText().toString()));
        }
        setContentView(root);
        if (state != null) {
            int scrollY = Math.max(0, state.getInt("game_settings.scroll_y", 0));
            settingsScroll.post(() -> settingsScroll.scrollTo(0, scrollY));
        }
    }

    private void restoreTransientSettings(Bundle state) {
        if (state == null) return;
        settings.canvasPosition = Math.max(0, Math.min(POSITIONS.length - 1,
                state.getInt("game_settings.canvas_position", settings.canvasPosition)));
        settings.canvasScale = Math.max(50, Math.min(100,
                state.getInt("game_settings.canvas_scale", settings.canvasScale)));
        settings.reserveGamepadArea = state.getBoolean(
                "game_settings.reserve_gamepad", settings.reserveGamepadArea);
        settings.canvasOutlineEnabled = state.getBoolean(
                "game_settings.canvas_outline", settings.canvasOutlineEnabled);
        settings.touchEnabled = state.getBoolean(
                "game_settings.touch_enabled", settings.touchEnabled);
        settings.gamepadEnabled = state.getBoolean(
                "game_settings.gamepad_enabled", settings.gamepadEnabled);
        settings.bitmapFontEnabled = state.getBoolean(
                "game_settings.bitmap_font_enabled", settings.bitmapFontEnabled);
        settings.crtFilterEnabled = state.getBoolean(
                "game_settings.crt_filter_enabled", settings.crtFilterEnabled);
        settings.physicalKeyMappings = state.getString(
                "game_settings.physical_key_mappings", settings.physicalKeyMappings);
        String layoutId = state.getString("game_settings.layout_id");
        if (layoutId != null)
            settings.gamepadLayoutId = GamepadTemplateLibrary.resolveId(this, layoutId);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (settings == null) return;
        outState.putString("game_settings.width_input", widthInput == null
                ? Integer.toString(settings.canvasWidth) : widthInput.getText().toString());
        outState.putString("game_settings.height_input", heightInput == null
                ? Integer.toString(settings.canvasHeight) : heightInput.getText().toString());
        outState.putInt("game_settings.canvas_position", settings.canvasPosition);
        outState.putInt("game_settings.canvas_scale", scaleSeekBar == null
                ? settings.canvasScale : scaleSeekBar.getProgress() + 50);
        outState.putBoolean("game_settings.reserve_gamepad", reserveGamepadSwitch == null
                ? settings.reserveGamepadArea : reserveGamepadSwitch.isChecked());
        outState.putBoolean("game_settings.canvas_outline", canvasOutlineSwitch == null
                ? settings.canvasOutlineEnabled : canvasOutlineSwitch.isChecked());
        outState.putBoolean("game_settings.touch_enabled", touchEnabledSwitch == null
                ? settings.touchEnabled : touchEnabledSwitch.isChecked());
        outState.putBoolean("game_settings.gamepad_enabled", gamepadEnabledSwitch == null
                ? settings.gamepadEnabled : gamepadEnabledSwitch.isChecked());
        outState.putBoolean("game_settings.bitmap_font_enabled", bitmapFontSwitch == null
                ? settings.bitmapFontEnabled : bitmapFontSwitch.isChecked());
        outState.putBoolean("game_settings.crt_filter_enabled", crtFilterSwitch == null
                ? settings.crtFilterEnabled : crtFilterSwitch.isChecked());
        outState.putString("game_settings.physical_key_mappings",
                settings.physicalKeyMappings);
        outState.putString("game_settings.layout_id", settings.gamepadLayoutId);
        outState.putInt("game_settings.scroll_y",
                settingsScroll == null ? 0 : settingsScroll.getScrollY());
    }

    private void addCanvasSizeControls(LinearLayout content) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        rowParams.topMargin = dp(8);
        content.addView(row, rowParams);

        widthInput = dimensionInput(settings.canvasWidth);
        row.addView(widthInput, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView swap = bodyText("↔", 22);
        swap.setGravity(Gravity.CENTER);
        swap.setContentDescription(AppLocalization.text(this, "Đổi chiều rộng và chiều cao"));
        row.addView(swap, new LinearLayout.LayoutParams(dp(50), dp(44)));
        swap.setOnClickListener(view -> {
            String width = widthInput.getText().toString();
            widthInput.setText(heightInput.getText().toString());
            heightInput.setText(width);
        });

        heightInput = dimensionInput(settings.canvasHeight);
        row.addView(heightInput, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView preset = bodyText("☰", 22);
        preset.setGravity(Gravity.CENTER);
        preset.setContentDescription(AppLocalization.text(this, "Chọn preset canvas"));
        row.addView(preset, new LinearLayout.LayoutParams(dp(52), dp(44)));
        preset.setOnClickListener(view -> showPresetPicker());
    }

    private EditText dimensionInput(int value) {
        EditText input = new EditText(this);
        input.setText(Integer.toString(value));
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setPadding(dp(8), 0, dp(8), 0);
        input.setBackground(round(COLOR_SURFACE, 12, COLOR_BORDER));
        return input;
    }

    private Switch addSwitch(LinearLayout parent, String label, boolean checked) {
        Switch control = new Switch(this);
        control.setText(AppLocalization.text(this, label));
        control.setTextColor(COLOR_TEXT);
        control.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        control.setChecked(checked);
        control.setPadding(dp(2), 0, dp(2), 0);
        control.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{COLOR_ACCENT, COLOR_MUTED}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xFF5368D8, 0xFF46505D}));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(6);
        parent.addView(control, params);
        return control;
    }

    private LinearLayout addValueRow(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.topMargin = dp(2);
        parent.addView(row, params);
        TextView text = bodyText(label, 15);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void addSection(LinearLayout content, String label) {
        TextView section = bodyText(label, 12);
        section.setTextColor(COLOR_ACCENT);
        section.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = content.getChildCount() == 0 ? 0 : dp(12);
        content.addView(section, params);
    }

    private LinearLayout settingsCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(COLOR_CARD, 18, COLOR_BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        parent.addView(card, params);
        return card;
    }

    private TextView bodyText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(AppLocalization.text(this, text));
        view.setTextColor(COLOR_TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView valueButton(String label) {
        TextView button = bodyText(label, 15);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(COLOR_SURFACE, 12, COLOR_BORDER));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView plainValueButton(String label) {
        TextView button = bodyText(label, 15);
        button.setTextColor(COLOR_ACCENT);
        button.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        button.setPadding(0, 0, dp(4), 0);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = bodyText(label, 14);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? COLOR_BACKGROUND : COLOR_TEXT);
        button.setBackground(round(primary ? COLOR_ACCENT : COLOR_SURFACE, 24,
                primary ? COLOR_ACCENT : COLOR_BORDER));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void showPresetPicker() {
        showChoicePopup("Preset canvas", PRESETS, index -> {
            widthInput.setText(Integer.toString(PRESET_SIZES[index][0]));
            heightInput.setText(Integer.toString(PRESET_SIZES[index][1]));
        });
    }

    private void showGamepadLayoutPicker() {
        java.util.List<GamepadTemplate> templates = GamepadTemplateLibrary.all(this);
        String[] labels = new String[templates.size()];
        for (int index = 0; index < templates.size(); index++) {
            GamepadTemplate template = templates.get(index);
            labels[index] = template.name;
        }
        showChoicePopup("Bố cục gamepad", labels, index -> {
            settings.gamepadLayoutId = templates.get(index).id;
            gamepadLayoutButton.setText(AppLocalization.text(this, templates.get(index).name));
            updateTemplateActions();
        });
    }

    private void chooseCustomTemplate(boolean delete) {
        java.util.List<GamepadTemplate> templates = GamepadTemplateLibrary.custom(this);
        if (templates.isEmpty()) {
            Toast.makeText(this, AppLocalization.text(this, delete
                    ? "Chưa có mẫu tùy chỉnh để xóa."
                    : "Chưa có mẫu tùy chỉnh để sửa."), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[templates.size()];
        for (int index = 0; index < templates.size(); index++)
            labels[index] = templates.get(index).name;
        showChoicePopup(delete ? "Chọn mẫu cần xóa" : "Chọn mẫu cần sửa",
                labels, index -> {
                    GamepadTemplate selected = templates.get(index);
                    if (delete) confirmDeleteTemplate(selected);
                    else openGamepadEditor(selected.id);
                });
    }

    private void confirmDeleteTemplate(GamepadTemplate template) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(16));
        content.setBackground(round(COLOR_SURFACE, 24, COLOR_BORDER));
        TextView title = bodyText("Xóa mẫu gamepad?", 19);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.addView(title);
        TextView message = bodyText("“" + template.name
                + "” sẽ bị xóa. Game đang dùng mẫu này sẽ chuyển về Mẫu 1.", 15);
        message.setTextColor(COLOR_MUTED);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(10);
        content.addView(message, messageParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, dp(52));
        buttonsParams.topMargin = dp(18);
        content.addView(buttons, buttonsParams);
        TextView remove = actionButton("Xóa", true);
        buttons.addView(remove, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView cancel = actionButton("Hủy", false);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        cancelParams.leftMargin = dp(10);
        buttons.addView(cancel, cancelParams);
        PopupWindow popup = showPopup(content);
        remove.setOnClickListener(view -> {
            try {
                GamepadTemplateLibrary.delete(this, template.id);
                if (template.id.equals(settings.gamepadLayoutId))
                    settings.gamepadLayoutId = "builtin:0";
                gamepadLayoutButton.setText(
                        GamepadTemplateLibrary.get(this, settings.gamepadLayoutId).name);
                updateTemplateActions();
                popup.dismiss();
            } catch (Exception error) {
                Toast.makeText(this, AppLocalization.text(this, error.getMessage() == null
                        ? "Không thể xóa mẫu gamepad." : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
        cancel.setOnClickListener(view -> popup.dismiss());
    }

    private void openGamepadEditor(String templateId) {
        Intent intent = new Intent(this, GamepadEditorActivity.class);
        if (templateId == null) {
            intent.putExtra(GamepadEditorActivity.EXTRA_SOURCE_ID, settings.gamepadLayoutId);
        } else {
            intent.putExtra(GamepadEditorActivity.EXTRA_TEMPLATE_ID, templateId);
        }
        startActivityForResult(intent, REQUEST_GAMEPAD_EDITOR);
    }

    private void addKeyMappingRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        rowParams.topMargin = dp(2);
        parent.addView(row, rowParams);

        TextView title = bodyText("Ánh xạ phím", 15);
        row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView editMapping = plainValueButton("Tùy chỉnh");
        row.addView(editMapping, new LinearLayout.LayoutParams(
                dp(VALUE_BUTTON_WIDTH_DP), LinearLayout.LayoutParams.MATCH_PARENT));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> openKeyMappingEditor());
        editMapping.setOnClickListener(view -> openKeyMappingEditor());
    }

    private void openKeyMappingEditor() {
        Intent intent = new Intent(this, KeyMappingActivity.class);
        intent.putExtra(KeyMappingActivity.EXTRA_MAPPING, settings.physicalKeyMappings);
        startActivityForResult(intent, REQUEST_KEY_MAPPING);
    }

    private void updateTemplateActions() {
        if (editTemplateButton == null || deleteTemplateButton == null) return;
        boolean editable = !GamepadTemplateLibrary.custom(this).isEmpty();
        editTemplateButton.setEnabled(editable);
        editTemplateButton.setAlpha(editable ? 1f : .45f);
        deleteTemplateButton.setEnabled(editable);
        deleteTemplateButton.setAlpha(editable ? 1f : .45f);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_KEY_MAPPING) {
            if (resultCode == RESULT_OK && data != null) {
                settings.physicalKeyMappings = data.getStringExtra(
                        KeyMappingActivity.EXTRA_RESULT);
                if (settings.physicalKeyMappings == null) settings.physicalKeyMappings = "";
            }
            return;
        }
        if (requestCode != REQUEST_GAMEPAD_EDITOR || resultCode != RESULT_OK || data == null)
            return;
        String id = data.getStringExtra(GamepadEditorActivity.EXTRA_RESULT_ID);
        if (id == null) return;
        settings.gamepadLayoutId = GamepadTemplateLibrary.resolveId(this, id);
        gamepadLayoutButton.setText(GamepadTemplateLibrary.get(this, settings.gamepadLayoutId).name);
        updateTemplateActions();
    }

    private void showPositionPicker() {
        showChoicePopup("Vị trí Canvas", POSITIONS, index -> {
            settings.canvasPosition = index;
            positionButton.setText(AppLocalization.text(this, POSITIONS[index]));
        });
    }

    private interface ChoiceListener {
        void onSelected(int index);
    }

    private void showChoicePopup(String title, String[] choices, ChoiceListener listener) {
        String translatedTitle = AppLocalization.text(this, title);
        String[] translatedChoices = AppLocalization.texts(this, choices);
        LinearLayout panel = popupPanel(translatedTitle);
        int rowHeight = dp(52);
        int rowGap = dp(2);
        int maxListHeight = Math.max(dp(140),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.62f));
        int contentHeight = rowHeight * choices.length
                + rowGap * Math.max(0, choices.length - 1);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        final PopupWindow[] popupRef = new PopupWindow[1];
        for (int index = 0; index < translatedChoices.length; index++) {
            final int choiceIndex = index;
            TextView option = new TextView(this);
            option.setText(translatedChoices[index]);
            option.setTextColor(COLOR_TEXT);
            option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            option.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setSingleLine(true);
            option.setEllipsize(TextUtils.TruncateAt.END);
            option.setPadding(dp(18), 0, dp(18), 0);
            option.setBackground(choiceRowBackground());
            option.setClickable(true);
            option.setFocusable(false);
            option.setFocusableInTouchMode(false);
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
            if (index < choices.length - 1) optionParams.bottomMargin = rowGap;
            list.addView(option, optionParams);
            option.setOnClickListener(view -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                if (listener != null) listener.onSelected(choiceIndex);
            });
        }

        if (contentHeight > maxListHeight) {
            ScrollView listScroll = new ScrollView(this);
            listScroll.setFillViewport(false);
            listScroll.setVerticalScrollBarEnabled(false);
            listScroll.addView(list, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            panel.addView(listScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight));
        } else {
            panel.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        popupRef[0] = showPopup(panel);
    }

    private StateListDrawable choiceRowBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable active = new GradientDrawable();
        active.setColor(COLOR_ROW_ACTIVE);
        states.addState(new int[]{android.R.attr.state_pressed},
                active);
        states.addState(new int[0], round(Color.TRANSPARENT, 12, Color.TRANSPARENT));
        return states;
    }

    private LinearLayout popupPanel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(14));
        panel.setBackground(round(COLOR_SURFACE, 20, COLOR_BORDER));
        TextView heading = bodyText(title, 18);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.bottomMargin = dp(14);
        panel.addView(heading, headingParams);
        return panel;
    }

    private PopupWindow showPopup(View panel) {
        if (activePopup != null) activePopup.dismiss();
        FrameLayout contentRoot = findViewById(android.R.id.content);
        View dim = new View(this);
        dim.setBackgroundColor(0x80000000);
        dim.setClickable(true);
        contentRoot.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(dp(400), screenWidth - dp(32));
        PopupWindow popup = new PopupWindow(panel, popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(10));
        popup.setOutsideTouchable(true);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dim.setOnClickListener(view -> popup.dismiss());
        activePopup = popup;
        activePopupDim = dim;
        popup.setOnDismissListener(() -> {
            if (dim.getParent() instanceof ViewGroup) {
                ((ViewGroup) dim.getParent()).removeView(dim);
            }
            if (activePopup == popup) {
                activePopup = null;
                activePopupDim = null;
            }
        });
        popup.showAtLocation(contentRoot, Gravity.CENTER, 0, 0);
        return popup;
    }

    private void updateCanvasControlAvailability() {
        if (reserveGamepadSwitch == null || positionRow == null || scaleRow == null) return;
        boolean enabled = !reserveGamepadSwitch.isChecked();
        positionRow.setEnabled(enabled);
        positionButton.setEnabled(enabled);
        scaleRow.setEnabled(enabled);
        scaleSeekBar.setEnabled(enabled);
        positionRow.setAlpha(enabled ? 1f : 0.45f);
        scaleRow.setAlpha(enabled ? 1f : 0.45f);
    }

    private void saveAndReturn() {
        int width;
        int height;
        try {
            width = Integer.parseInt(widthInput.getText().toString().trim());
            height = Integer.parseInt(heightInput.getText().toString().trim());
        } catch (NumberFormatException error) {
            Toast.makeText(this, AppLocalization.text(this,
                    "Nhập chiều rộng và chiều cao Canvas hợp lệ."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (width < GameSettings.MIN_CANVAS_SIZE || width > GameSettings.MAX_CANVAS_SIZE
                || height < GameSettings.MIN_CANVAS_SIZE
                || height > GameSettings.MAX_CANVAS_SIZE) {
            Toast.makeText(this, AppLocalization.text(this,
                    "Kích thước Canvas phải từ 64 đến 2048."),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        settings.canvasWidth = width;
        settings.canvasHeight = height;
        settings.canvasScale = scaleSeekBar.getProgress() + 50;
        settings.reserveGamepadArea = reserveGamepadSwitch.isChecked();
        settings.canvasOutlineEnabled = canvasOutlineSwitch.isChecked();
        settings.touchEnabled = touchEnabledSwitch.isChecked();
        settings.gamepadEnabled = gamepadEnabledSwitch.isChecked();
        settings.bitmapFontEnabled = bitmapFontSwitch.isChecked();
        settings.crtFilterEnabled = crtFilterSwitch.isChecked();
        if (!settings.save(this, jar)) {
            Toast.makeText(this, AppLocalization.text(this, "Không thể lưu cài đặt game."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_JAR, jar.getAbsolutePath());
        result.putExtra(EXTRA_TITLE, gameTitle);
        result.putExtra(EXTRA_LAUNCH_AFTER_SAVE, launchAfterSave);
        setResult(RESULT_OK, result);
        finish();
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

    @Override protected void onDestroy() {
        if (activePopup != null) activePopup.dismiss();
        if (activePopupDim != null && activePopupDim.getParent() instanceof ViewGroup) {
            ((ViewGroup) activePopupDim.getParent()).removeView(activePopupDim);
            activePopupDim = null;
        }
        super.onDestroy();
    }
}
