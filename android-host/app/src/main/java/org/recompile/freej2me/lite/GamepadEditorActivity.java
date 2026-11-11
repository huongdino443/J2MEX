package org.recompile.freej2me.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.List;

public final class GamepadEditorActivity extends LocalizedActivity {
    static final String EXTRA_SOURCE_ID = "gamepad_editor.source_id";
    static final String EXTRA_TEMPLATE_ID = "gamepad_editor.template_id";
    static final String EXTRA_RESULT_ID = "gamepad_editor.result_id";

    private static final int BG = 0xFF000000;
    private static final int TOOL_BG = -15263715;
    private static final int TOOL_SELECTED = 0xFFB9C3FF;
    private static final int TOOL_STROKE = -10065806;
    private static final int TOOL_SELECTED_STROKE = 0xFFB9C3FF;
    private static final int TOOL_TEXT = -986894;
    private static final int TOOL_TEXT_ON_ACCENT = 0xFF0E1826;
    private static final String[] VISIBILITY_LABELS = {
            "L", "R", "↑", "←", "→", "↓", "F",
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#",
            "Joystick"
    };

    private GamepadTemplate template;
    private GamepadTemplate.Layout current;
    private String editingId;
    private String savedName;
    private FrameLayout root;
    private Board board;
    private LinearLayout toolPanel;
    private Button gridTool;
    private Button saveTool;
    private ToolHub toolHub;
    private boolean landscape;
    private boolean gridEnabled = true;
    private boolean toolMenuOpen;
    private int selected = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        editingId = getIntent().getStringExtra(EXTRA_TEMPLATE_ID);
        if (editingId == null || editingId.isEmpty()) {
            template = GamepadTemplateLibrary.createDraft(this);
        } else {
            template = GamepadTemplateLibrary.get(this, editingId);
            if (!template.isCustom()) {
                Toast.makeText(this, AppLocalization.text(this,
                        "Không thể sửa mẫu dựng sẵn."), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            savedName = template.name;
        }
        landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        current = template.layout(landscape);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        board = new Board(this);
        root.addView(board, new FrameLayout.LayoutParams(-1, -1));
        buildFloatingTools();
        setContentView(root);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        boolean nextLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (nextLandscape != landscape) {
            landscape = nextLandscape;
            current = template.layout(landscape);
            selected = -1;
            buildFloatingTools();
            if (board != null) board.invalidate();
        }
    }

    private void buildFloatingTools() {
        if (root == null) return;
        if (toolPanel != null) root.removeView(toolPanel);
        if (toolHub != null) root.removeView(toolHub);
        toolMenuOpen = false;

        toolPanel = buildToolPanel();
        toolPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(232), dp(120), Gravity.TOP | Gravity.LEFT);
        root.addView(toolPanel, panelParams);

        toolHub = new ToolHub();
        toolHub.setContentDescription(AppLocalization.text(this, "Công cụ chỉnh bố cục"));
        toolHub.setFocusable(true);
        toolHub.setElevation(dp(6));
        toolHub.setBackgroundDrawable(hubBackground());
        root.addView(toolHub, new FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.TOP | Gravity.LEFT));
        toolHub.setOnTouchListener(this::onToolHubTouch);
        toolHub.setOnClickListener(view -> toggleToolMenu());
        root.post(this::restoreToolHubPosition);
    }

    private LinearLayout buildToolPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(0, 0, 0, 0);
        panel.setClipToPadding(false);
        panel.setClickable(false);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setGravity(Gravity.CENTER);
        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setGravity(Gravity.CENTER);
        panel.addView(firstRow, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(secondRow, new LinearLayout.LayoutParams(-1, 0, 1));

        gridTool = addTool(firstRow, "Lưới", 0);
        addTool(firstRow, "Đổi kích thước", 1);
        addTool(secondRow, "Ẩn nút", 2);
        saveTool = addTool(secondRow, savedName == null ? "Lưu" : "Đã lưu", 3);
        return panel;
    }

    private Button addTool(LinearLayout parent, String label, int action) {
        Button button = new Button(this);
        button.setText(AppLocalization.text(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTextColor(TOOL_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setTransformationMethod(null);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setEllipsize(null);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackgroundDrawable(toolBackground());
        button.setFocusable(true);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setSelected(action == 0 && gridEnabled);
        if (action == 0 && gridEnabled) button.setTextColor(TOOL_TEXT_ON_ACCENT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        parent.addView(button, params);
        button.setOnClickListener(view -> {
            runToolAction(action);
        });
        return button;
    }

    private void runToolAction(int action) {
        if (action == 0) {
            gridEnabled = !gridEnabled;
            if (gridTool != null) {
                gridTool.setSelected(gridEnabled);
                gridTool.setTextColor(gridEnabled ? TOOL_TEXT_ON_ACCENT : TOOL_TEXT);
            }
            if (gridEnabled) {
                snapAll(current);
                markUnsaved();
            }
            board.invalidate();
        } else if (action == 1) {
            cycleSize();
        } else if (action == 2) {
            showVisibilityPicker();
        } else {
            saveTemplate();
        }
    }

    private boolean onToolHubTouch(View view, MotionEvent event) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                view.setPressed(true);
                view.setTag(new float[]{event.getRawX(), event.getRawY(),
                        params.leftMargin, params.topMargin, 0});
                return true;
            case MotionEvent.ACTION_MOVE: {
                float[] start = (float[]) view.getTag();
                if (start == null) return true;
                float dx = event.getRawX() - start[0];
                float dy = event.getRawY() - start[1];
                if (start[4] == 0 && Math.hypot(dx, dy)
                        > ViewConfiguration.get(this).getScaledTouchSlop()) start[4] = 1;
                if (start[4] != 0) {
                    setToolHubPosition(Math.round(start[2] + dx),
                            Math.round(start[3] + dy));
                    if (toolMenuOpen) positionToolPanel();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float[] start = (float[]) view.getTag();
                view.setPressed(false);
                view.setTag(null);
                if (start != null && start[4] != 0) {
                    saveToolHubPosition();
                } else {
                    view.performClick();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                view.setPressed(false);
                view.setTag(null);
                return true;
            default:
                return true;
        }
    }

    private void toggleToolMenu() {
        if (toolMenuOpen) hideToolMenu(true);
        else showToolMenu();
    }

    private void showToolMenu() {
        if (toolPanel == null || toolHub == null) return;
        toolMenuOpen = true;
        toolHub.setContentDescription(AppLocalization.text(this, "Đóng công cụ"));
        toolHub.setSelected(true);
        toolHub.invalidate();
        positionToolPanel();
        toolPanel.setVisibility(View.VISIBLE);
        toolPanel.setAlpha(0f);
        toolPanel.setScaleX(.94f);
        toolPanel.setScaleY(.94f);
        toolPanel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(160).start();
    }

    private void hideToolMenu(boolean animate) {
        if (!toolMenuOpen) return;
        toolMenuOpen = false;
        if (toolHub != null) {
            toolHub.setContentDescription(AppLocalization.text(this, "Mở công cụ"));
            toolHub.setSelected(false);
            toolHub.invalidate();
        }
        if (toolPanel == null) return;
        toolPanel.animate().cancel();
        if (!animate) {
            toolPanel.setVisibility(View.GONE);
            toolPanel.setAlpha(1f);
            toolPanel.setScaleX(1f);
            toolPanel.setScaleY(1f);
            return;
        }
        toolPanel.animate().alpha(0f).scaleX(.94f).scaleY(.94f)
                .setDuration(120).withEndAction(() -> {
                    if (!toolMenuOpen && toolPanel != null) {
                        toolPanel.setVisibility(View.GONE);
                        toolPanel.setAlpha(1f);
                        toolPanel.setScaleX(1f);
                        toolPanel.setScaleY(1f);
                    }
                }).start();
    }

    private void positionToolPanel() {
        if (root == null || toolHub == null || toolPanel == null
                || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        FrameLayout.LayoutParams hubParams =
                (FrameLayout.LayoutParams) toolHub.getLayoutParams();
        FrameLayout.LayoutParams panelParams =
                (FrameLayout.LayoutParams) toolPanel.getLayoutParams();
        int gap = dp(8);
        int margin = dp(8);
        int panelWidth = panelParams.width;
        int panelHeight = panelParams.height;
        int hubWidth = toolHub.getWidth() > 0 ? toolHub.getWidth() : dp(56);
        int hubHeight = toolHub.getHeight() > 0 ? toolHub.getHeight() : dp(56);
        int maxX = Math.max(margin, root.getWidth() - panelWidth - margin);
        int maxY = Math.max(margin, root.getHeight() - panelHeight - margin);
        int[][] candidates = {
                {hubParams.leftMargin + hubWidth + gap, hubParams.topMargin},
                {hubParams.leftMargin - panelWidth - gap, hubParams.topMargin},
                {hubParams.leftMargin, hubParams.topMargin + hubHeight + gap},
                {hubParams.leftMargin, hubParams.topMargin - panelHeight - gap}
        };
        int bestX = margin;
        int bestY = margin;
        long bestScore = Long.MAX_VALUE;
        for (int[] candidate : candidates) {
            int x = Math.max(margin, Math.min(maxX, candidate[0]));
            int y = Math.max(margin, Math.min(maxY, candidate[1]));
            int overlapWidth = Math.max(0, Math.min(x + panelWidth,
                    hubParams.leftMargin + hubWidth) - Math.max(x, hubParams.leftMargin));
            int overlapHeight = Math.max(0, Math.min(y + panelHeight,
                    hubParams.topMargin + hubHeight) - Math.max(y, hubParams.topMargin));
            long overlap = (long) overlapWidth * overlapHeight;
            long displacement = Math.abs(x - candidate[0]) + Math.abs(y - candidate[1]);
            long score = overlap * 10000L + displacement * 10L
                    + Math.abs(x - hubParams.leftMargin)
                    + Math.abs(y - hubParams.topMargin);
            if (score < bestScore) {
                bestScore = score;
                bestX = x;
                bestY = y;
            }
        }
        panelParams.leftMargin = bestX;
        panelParams.topMargin = bestY;
        toolPanel.setLayoutParams(panelParams);
    }

    private void restoreToolHubPosition() {
        if (root == null || toolHub == null || root.getWidth() <= 0 || root.getHeight() <= 0)
            return;
        String orientation = landscape ? "landscape" : "portrait";
        android.content.SharedPreferences prefs = getSharedPreferences(
                "gamepad_editor_tools", MODE_PRIVATE);
        int margin = dp(12);
        int maxX = Math.max(0, root.getWidth() - dp(56) - margin);
        int maxY = Math.max(0, root.getHeight() - dp(56) - margin);
        float xRatio = prefs.getFloat(orientation + "_x", 1f);
        float yRatio = prefs.getFloat(orientation + "_y", 0f);
        setToolHubPosition(Math.round(maxX * xRatio), Math.round(maxY * yRatio));
        if (toolMenuOpen) positionToolPanel();
    }

    private void setToolHubPosition(int x, int y) {
        if (root == null || toolHub == null || root.getWidth() <= 0 || root.getHeight() <= 0)
            return;
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) toolHub.getLayoutParams();
        int width = toolHub.getWidth() > 0 ? toolHub.getWidth() : dp(56);
        int height = toolHub.getHeight() > 0 ? toolHub.getHeight() : dp(56);
        int margin = dp(8);
        params.leftMargin = Math.max(margin, Math.min(root.getWidth() - width - margin, x));
        params.topMargin = Math.max(margin, Math.min(root.getHeight() - height - margin, y));
        toolHub.setLayoutParams(params);
    }

    private void saveToolHubPosition() {
        if (root == null || toolHub == null || root.getWidth() <= 0 || root.getHeight() <= 0)
            return;
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) toolHub.getLayoutParams();
        int maxX = Math.max(1, root.getWidth() - toolHub.getWidth() - dp(12));
        int maxY = Math.max(1, root.getHeight() - toolHub.getHeight() - dp(12));
        String orientation = landscape ? "landscape" : "portrait";
        getSharedPreferences("gamepad_editor_tools", MODE_PRIVATE).edit()
                .putFloat(orientation + "_x", Math.max(0f, Math.min(1f,
                        params.leftMargin / (float) maxX)))
                .putFloat(orientation + "_y", Math.max(0f, Math.min(1f,
                        params.topMargin / (float) maxY)))
                .apply();
    }

    private StateListDrawable toolBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                drawable(-13947847, TOOL_SELECTED_STROKE, dp(9)));
        states.addState(new int[]{android.R.attr.state_focused},
                drawable(-13947847, TOOL_SELECTED_STROKE, dp(9)));
        states.addState(new int[]{android.R.attr.state_selected},
                drawable(TOOL_SELECTED, TOOL_SELECTED_STROKE, dp(9)));
        states.addState(new int[0], drawable(TOOL_BG, TOOL_STROKE, dp(9)));
        return states;
    }

    private StateListDrawable hubBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                drawable(TOOL_SELECTED, TOOL_SELECTED_STROKE, dp(28)));
        states.addState(new int[]{android.R.attr.state_selected},
                drawable(TOOL_SELECTED, TOOL_SELECTED_STROKE, dp(28)));
        states.addState(new int[]{android.R.attr.state_focused},
                drawable(TOOL_SELECTED, TOOL_SELECTED_STROKE, dp(28)));
        states.addState(new int[0], drawable(TOOL_BG, TOOL_STROKE, dp(28)));
        return states;
    }

    private GradientDrawable drawable(int fill, int stroke) {
        return drawable(fill, stroke, dp(9));
    }

    private GradientDrawable drawable(int fill, int stroke, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(radius);
        result.setStroke(dp(1), stroke);
        return result;
    }

    private final class ToolHub extends View {
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ToolHub() {
            super(GamepadEditorActivity.this);
            setClickable(true);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            iconPaint.setColor(TOOL_TEXT);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeWidth(dp(2));
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            if (toolMenuOpen) {
                float offset = dp(7);
                iconPaint.setColor(TOOL_TEXT_ON_ACCENT);
                canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset,
                        iconPaint);
                canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset,
                        iconPaint);
            } else {
                iconPaint.setStyle(Paint.Style.FILL);
                float size = dp(7);
                float gap = dp(4);
                for (int row = -1; row <= 1; row += 2) {
                    for (int column = -1; column <= 1; column += 2) {
                        float centerX = cx + column * (size + gap) / 2f;
                        float centerY = cy + row * (size + gap) / 2f;
                        canvas.drawRoundRect(centerX - size / 2f, centerY - size / 2f,
                                centerX + size / 2f, centerY + size / 2f,
                                dp(1.5f), dp(1.5f), iconPaint);
                    }
                }
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private void showVisibilityPicker() {
        boolean[] checked = new boolean[current.keys.size()];
        String[] labels = new String[current.keys.size()];
        for (int i = 0; i < current.keys.size(); i++) {
            GamepadTemplate.Key key = current.keys.get(i);
            labels[i] = key.code == GamepadTemplate.JOYSTICK_CODE
                    ? "Joystick" : key.label;
            checked[i] = key.visible;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(AppLocalization.text(this, "Các nút"))
                .setMultiChoiceItems(labels, checked, (d, index, value) -> {
                    if (!setKeyVisible(current, index, value)) {
            Toast.makeText(this, AppLocalization.text(this,
                    "Cần vùng trống 3×3 để hiện joystick."),
                                Toast.LENGTH_SHORT).show();
                    }
                    for (int i = 0; i < current.keys.size(); i++)
                        ((AlertDialog) d).getListView().setItemChecked(
                                i, current.keys.get(i).visible);
                    if (selected >= 0 && !current.keys.get(selected).visible) selected = -1;
                    markUnsaved();
                    board.invalidate();
                })
                .setPositiveButton(AppLocalization.text(this, "Xong"),
                        (d, which) -> board.invalidate())
                .create();
        dialog.show();
    }

    private boolean setKeyVisible(GamepadTemplate.Layout layout, int index, boolean visible) {
        GamepadTemplate.Key key = layout.keys.get(index);
        boolean[] previous = new boolean[layout.keys.size()];
        for (int i = 0; i < layout.keys.size(); i++)
            previous[i] = layout.keys.get(i).visible;
        key.visible = visible;
        if (key.code == GamepadTemplate.JOYSTICK_CODE && visible) {
            for (GamepadTemplate.Key other : layout.keys)
                if (isDirection(other.code)) other.visible = false;
            if (!GamepadTemplateLibrary.free(layout, index, key.x, key.y)) {
                boolean placed = false;
                for (int y = 0; y <= layout.rows - 3 && !placed; y++) {
                    for (int x = 0; x <= layout.columns - 3; x++) {
                        if (GamepadTemplateLibrary.free(layout, index, x, y)) {
                            key.x = x;
                            key.y = y;
                            placed = true;
                            break;
                        }
                    }
                }
                if (!placed) {
                    for (int i = 0; i < layout.keys.size(); i++)
                        layout.keys.get(i).visible = previous[i];
                    return false;
                }
            }
        } else if (isDirection(key.code) && visible) {
            GamepadTemplate.Key joystick = findKey(layout, GamepadTemplate.JOYSTICK_CODE);
            if (joystick != null) joystick.visible = false;
        }
        return true;
    }

    private static boolean isDirection(int code) {
        return code == -1 || code == -2 || code == -3 || code == -4;
    }

    private static GamepadTemplate.Key findKey(GamepadTemplate.Layout layout, int code) {
        for (GamepadTemplate.Key key : layout.keys) if (key.code == code) return key;
        return null;
    }

    private void cycleSize() {
        if (selected < 0 || selected >= current.keys.size()) {
            Toast.makeText(this, AppLocalization.text(this,
                    "Chạm vào một nút trước."), Toast.LENGTH_SHORT).show();
            return;
        }
        GamepadTemplate.Key key = current.keys.get(selected);
        if (key.code == GamepadTemplate.JOYSTICK_CODE) return;
        int oldSize = key.size;
        key.size = (key.size + 1) % 3;
        if (!gridEnabled || GamepadTemplateLibrary.free(current, selected, key.x, key.y)) {
            markUnsaved();
            if (gridEnabled) snapAll(current);
        } else {
            key.size = oldSize;
            Toast.makeText(this, AppLocalization.text(this,
                    "Không đủ chỗ cho cỡ tiếp theo."),
                    Toast.LENGTH_SHORT).show();
        }
        board.invalidate();
    }

    private void saveTemplate() {
        if (savedName != null && (editingId == null || editingId.isEmpty())) {
            Toast.makeText(this, AppLocalization.text(this, "Đã lưu " + savedName),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            String resultId;
            if (editingId == null || editingId.isEmpty()) {
                resultId = GamepadTemplateLibrary.saveNew(this, template);
                savedName = GamepadTemplateLibrary.get(this, resultId).name;
                editingId = resultId;
            } else {
                GamepadTemplateLibrary.update(this, editingId, template);
                resultId = editingId;
                savedName = template.name;
            }
            saveTool.setText(AppLocalization.text(this, "Đã lưu"));
            Intent result = new Intent();
            result.putExtra(EXTRA_RESULT_ID, resultId);
            setResult(RESULT_OK, result);
            Toast.makeText(this, AppLocalization.text(this, "Đã lưu " + savedName),
                    Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception error) {
            Toast.makeText(this, AppLocalization.text(this, error.getMessage() == null
                    ? "Không thể lưu mẫu gamepad." : error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void markUnsaved() {
        if (savedName == null) return;
        savedName = null;
        if (saveTool != null) saveTool.setText(AppLocalization.text(this, "Lưu"));
    }

    private void snapAll(GamepadTemplate.Layout layout) {
        for (int index = 0; index < layout.keys.size(); index++) {
            GamepadTemplate.Key key = layout.keys.get(index);
            snapOne(layout, index, Math.round(key.x * 2), Math.round(key.y * 2));
        }
    }

    private void snapOne(GamepadTemplate.Layout layout, int index, int targetX, int targetY) {
        GamepadTemplate.Key key = layout.keys.get(index);
        int maxX = Math.max(0, layout.columns - key.width()) * 2;
        int maxY = Math.max(0, layout.rows - key.height()) * 2;
        long best = Long.MAX_VALUE;
        int bestX = Math.round(key.x * 2);
        int bestY = Math.round(key.y * 2);
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                if (!GamepadTemplateLibrary.free(layout, index, x / 2f, y / 2f)) continue;
                long dx = x - targetX;
                long dy = y - targetY;
                long distance = dx * dx + dy * dy;
                if (distance < best) {
                    best = distance;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (best != Long.MAX_VALUE) {
            key.x = bestX / 2f;
            key.y = bestY / 2f;
        }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private final class Board extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float gridX;
        private float gridY;
        private float cell;
        private float gridWidth;
        private float gridHeight;
        private int dragging = -1;
        private float screenWidth;
        private float screenHeight;
        private int portraitScreenWidth;
        private int portraitScreenHeight;

        Board(Activity context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (current == null || getWidth() <= 0 || getHeight() <= 0) return;
            prepareGeometry();
            canvas.drawColor(BG);
            if (gridEnabled) {
                paint.setColor(1430077770);
                paint.setStrokeWidth(dp(1));
                for (int i = 0; i <= current.columns * 2; i++) {
                    float x = gridX + cell * i / 2f;
                    canvas.drawLine(x, gridY, x, gridY + gridHeight, paint);
                }
                for (int i = 0; i <= current.rows * 2; i++) {
                    float y = gridY + cell * i / 2f;
                    canvas.drawLine(gridX, y, gridX + gridWidth, y, paint);
                }
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(-10327688);
            canvas.drawRect(gridX, gridY, gridX + gridWidth, gridY + gridHeight, paint);
            drawKeys(canvas);
        }

        private void prepareGeometry() {
            int[] display = GamepadTemplateLibrary.displaySize(GamepadEditorActivity.this);
            if (!landscape || portraitScreenWidth <= 0 || portraitScreenHeight <= 0) {
                if (!landscape) {
                    portraitScreenWidth = Math.min(display[0], display[1]);
                    portraitScreenHeight = Math.max(display[0], display[1]);
                } else {
                    GamepadTemplate.Layout portrait = template.portrait;
                    portraitScreenWidth = portrait.screenWidth > 0
                            ? portrait.screenWidth : Math.min(display[0], display[1]);
                    portraitScreenHeight = portrait.screenHeight > 0
                            ? portrait.screenHeight : Math.max(display[0], display[1]);
                }
            }
            screenWidth = landscape ? portraitScreenHeight : portraitScreenWidth;
            screenHeight = landscape ? portraitScreenWidth : portraitScreenHeight;
            current.screenWidth = Math.round(screenWidth);
            current.screenHeight = Math.round(screenHeight);
            int[] portraitDimensions = GamepadTemplateLibrary.gridDimensions(
                    GamepadEditorActivity.this, portraitScreenWidth, portraitScreenHeight);
            int[] dimensions = landscape
                    ? new int[]{portraitDimensions[1], portraitDimensions[0]}
                    : portraitDimensions;
            int oldColumns = Math.max(1, current.columns);
            int oldRows = Math.max(1, current.rows);
            if (oldColumns != dimensions[0] || oldRows != dimensions[1]) {
                for (GamepadTemplate.Key key : current.keys) {
                    key.x = key.x * dimensions[0] / oldColumns;
                    key.y = key.y * dimensions[1] / oldRows;
                    key.x = Math.max(0, Math.min(dimensions[0] - key.width(), key.x));
                    key.y = Math.max(0, Math.min(dimensions[1] - key.height(), key.y));
                }
                markUnsaved();
            }
            current.columns = dimensions[0];
            current.rows = dimensions[1];
            float screenCell = GamepadTemplateLibrary.gridCell(GamepadEditorActivity.this,
                    Math.round(screenWidth), Math.round(screenHeight),
                    current.columns, current.rows);
            float availableWidth = Math.max(1, getWidth());
            float availableHeight = Math.max(1, getHeight());
            float scale = landscape ? 1f : Math.min(1f, Math.min(
                    availableWidth / screenWidth, availableHeight / screenHeight));
            cell = screenCell * scale;
            gridWidth = current.columns * cell;
            gridHeight = current.rows * cell;
            float scaledScreenWidth = screenWidth * scale;
            float scaledScreenHeight = screenHeight * scale;
            int[] realDisplay = GamepadTemplateLibrary.realDisplaySize(
                    GamepadEditorActivity.this);
            int[] boardLocation = new int[2];
            getLocationOnScreen(boardLocation);
            float screenLeft = (realDisplay[0] - scaledScreenWidth) / 2f;
            float screenTop = (realDisplay[1] - scaledScreenHeight) / 2f;
            float marginX = GamepadTemplateLibrary.gridMargin(
                    GamepadEditorActivity.this, Math.round(screenWidth)) * scale;
            float marginY = GamepadTemplateLibrary.gridMargin(
                    GamepadEditorActivity.this, Math.round(screenHeight)) * scale;
            gridX = screenLeft + marginX
                    + (scaledScreenWidth - marginX * 2 - gridWidth) / 2f - boardLocation[0];
            gridY = screenTop + marginY
                    + (scaledScreenHeight - marginY * 2 - gridHeight) / 2f - boardLocation[1];
        }

        private void drawKeys(Canvas canvas) {
            float gap = GamepadTemplateLibrary.gridGap(GamepadEditorActivity.this,
                    Math.round(Math.min(screenWidth, screenHeight))) * Math.min(1f,
                    cell / Math.max(1f, GamepadTemplateLibrary.gridCell(GamepadEditorActivity.this,
                            Math.round(screenWidth), Math.round(screenHeight),
                            current.columns, current.rows)));
            float drawingCell = Math.max(1, cell - gap);
            for (int index = 0; index < current.keys.size(); index++) {
                GamepadTemplate.Key key = current.keys.get(index);
                if (!key.visible || key.code == GamepadTemplate.JOYSTICK_CODE) continue;
                float inset = (cell - drawingCell) / 2f;
                float left = gridX + key.x * cell + inset;
                float top = gridY + key.y * cell + inset;
                float width = key.width() * cell - (cell - drawingCell);
                float height = key.height() * cell - (cell - drawingCell);
                RectF rect = new RectF(left, top, left + width, top + height);
                boolean isSelected = index == selected;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(TOOL_BG);
                if (key.size == 2) canvas.drawCircle(rect.centerX(), rect.centerY(),
                        Math.min(width, height) / 2f, paint);
                else canvas.drawRoundRect(rect, cell * .22f, cell * .22f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(isSelected ? 2 : 1));
                paint.setColor(isSelected ? TOOL_SELECTED_STROKE : TOOL_STROKE);
                if (key.size == 2) canvas.drawCircle(rect.centerX(), rect.centerY(),
                        Math.min(width, height) / 2f, paint);
                else canvas.drawRoundRect(rect, cell * .22f, cell * .22f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(-986894);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.max(dp(14), cell * .34f));
                canvas.drawText(key.label, rect.centerX(),
                        rect.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
            }
            drawJoystick(canvas);
        }

        private void drawJoystick(Canvas canvas) {
            for (GamepadTemplate.Key key : current.keys) {
                if (!key.visible || key.code != GamepadTemplate.JOYSTICK_CODE) continue;
                float centerX = gridX + (key.x + 1.5f) * cell;
                float centerY = gridY + (key.y + 1.5f) * cell;
                boolean isSelected = current.keys.indexOf(key) == selected;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x552E333B);
                canvas.drawCircle(centerX, centerY, cell, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1));
                paint.setColor(TOOL_STROKE);
                canvas.drawCircle(centerX, centerY, cell, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(-986894);
                canvas.drawCircle(centerX, centerY, cell * .4f, paint);
                if (isSelected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(2));
                    paint.setColor(TOOL_SELECTED_STROKE);
                    canvas.drawRect(gridX + key.x * cell, gridY + key.y * cell,
                            gridX + (key.x + 3) * cell, gridY + (key.y + 3) * cell, paint);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            prepareGeometry();
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                dragging = hit(event.getX(), event.getY());
                selected = dragging;
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && dragging >= 0) {
                GamepadTemplate.Key key = current.keys.get(dragging);
                float x = ((event.getX() - gridX) / cell) - key.width() / 2f;
                float y = ((event.getY() - gridY) / cell) - key.height() / 2f;
                if (gridEnabled) snapOne(current, dragging,
                        Math.round((x + x)), Math.round(y + y));
                else {
                    key.x = Math.max(0, Math.min(current.columns - key.width(), x));
                    key.y = Math.max(0, Math.min(current.rows - key.height(), y));
                }
                markUnsaved();
                invalidate();
                return true;
            }
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    && dragging >= 0) {
                dragging = -1;
                return true;
            }
            return true;
        }

        private int hit(float x, float y) {
            for (int index = current.keys.size() - 1; index >= 0; index--) {
                GamepadTemplate.Key key = current.keys.get(index);
                if (!key.visible) continue;
                float left = gridX + key.x * cell;
                float top = gridY + key.y * cell;
                if (x >= left && x <= left + key.width() * cell
                        && y >= top && y <= top + key.height() * cell) return index;
            }
            return -1;
        }
    }
}
