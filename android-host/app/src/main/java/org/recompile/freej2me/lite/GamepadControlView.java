package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * J2ME gamepad overlay ported from the PhoneME Turbo layout and touch rules.
 * The view covers the complete game viewport so custom geometry uses the
 * Activity content area's dimensions; touches outside keys pass to the game.
 */
final class GamepadControlView extends View {
    interface KeySink {
        void onKey(int code, boolean down);
    }

    private static final int KEY_BG = 0x9626262E;
    private static final int KEY_PRESSED_BG = 0xDCB9C3FF;
    private static final int KEY_STROKE = 0x96DCE1EB;
    private static final int JOYSTICK_IDLE = -4408128;
    private static final int[] JOYSTICK_DIRECTIONS = {-1, -4, -2, -3};

    private static final class KeySpec {
        final String label;
        final int code;
        KeySpec(String label, int code) {
            this.label = label;
            this.code = code;
        }
    }

    private static final class PlacedKey {
        final KeySpec key;
        final RectF bounds;
        final boolean circular;
        final boolean visible;
        PlacedKey(KeySpec key, float left, float top, float width, float height,
                boolean circular) {
            this(key, left, top, width, height, circular, true);
        }
        PlacedKey(KeySpec key, float left, float top, float width, float height,
                boolean circular, boolean visible) {
            this.key = key;
            this.bounds = new RectF(left, top, left + width, top + height);
            this.circular = circular;
            this.visible = visible;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Integer> pointerKeys = new HashMap<>();
    private final int[] keyCounts = new int[24];
    private final List<PlacedKey> placedKeys = new ArrayList<>();
    private KeySink sink;
    private GamepadTemplate template;
    private boolean isLandscape;

    private boolean joystickEnabled;
    private int joystickPointerId = -1;
    private int joystickSector = -1;
    private int joystickMask;
    private int joystickUpIndex = -1;
    private int joystickRightIndex = -1;
    private int joystickDownIndex = -1;
    private int joystickLeftIndex = -1;
    private float joystickCenterX;
    private float joystickCenterY;
    private float joystickStepX;
    private float joystickStepY;
    private float joystickRadius;
    private float joystickMaxTravel;
    private float joystickThumbMaxTravel;
    private float joystickThumbRadius;
    private float joystickThumbX;
    private float joystickThumbY;
    private float joystickBaseRadius;
    private float joystickBaseCenterX;
    private float joystickBaseCenterY;
    private int customJoystickIndex = -1;
    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;
    private int coordinateOriginTopInset;

    GamepadControlView(Context context, GamepadTemplate template, KeySink sink) {
        super(context);
        this.sink = sink;
        this.template = template == null
                ? GamepadTemplateLibrary.get(context, "builtin:0") : template.copy();
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    void setTemplate(GamepadTemplate value) {
        releaseAll();
        template = value == null
                ? GamepadTemplateLibrary.get(getContext(), "builtin:0") : value.copy();
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        rebuildLayout(getWidth(), getHeight());
        invalidate();
    }

    void setKeySink(KeySink value) {
        sink = value;
    }

    void setCoordinateOriginTopInset(int inset) {
        int next = Math.max(0, inset);
        if (coordinateOriginTopInset == next) return;
        coordinateOriginTopInset = next;
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        rebuildLayout(getWidth(), getHeight());
        invalidate();
    }

    void cancelActiveTouches() {
        releaseAll();
        invalidate();
    }

    int reservedHeight(int width, int height) {
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        rebuildLayout(width, height);
        if (placedKeys.isEmpty()) return 0;
        float top = height;
        for (PlacedKey key : placedKeys)
            if (key.visible && key.key.code != Integer.MIN_VALUE)
                top = Math.min(top, key.bounds.top);
        return Math.max(0, Math.min(height, height - (int) Math.floor(top)));
    }

    int[] reservedSideWidths(int width, int height) {
        lastLayoutWidth = -1;
        lastLayoutHeight = -1;
        rebuildLayout(width, height);
        List<PlacedKey> active = new ArrayList<>();
        List<Float> sizes = new ArrayList<>();
        for (PlacedKey key : placedKeys) {
            if (!key.visible || key.key.code == Integer.MIN_VALUE) continue;
            active.add(key);
            sizes.add(Math.min(key.bounds.width(), key.bounds.height()));
        }
        if (active.isEmpty()) return new int[]{0, 0};

        Collections.sort(sizes);
        float typicalSize = sizes.get(sizes.size() / 2);
        float joinDistance = Math.max(dp(12), typicalSize * .55f);
        int[] parents = new int[active.size()];
        for (int index = 0; index < parents.length; index++) parents[index] = index;
        for (int left = 0; left < active.size(); left++) {
            RectF a = active.get(left).bounds;
            for (int right = left + 1; right < active.size(); right++) {
                RectF b = active.get(right).bounds;
                float gapX = Math.max(0f, Math.max(a.left - b.right, b.left - a.right));
                float gapY = Math.max(0f, Math.max(a.top - b.bottom, b.top - a.bottom));
                if (Math.hypot(gapX, gapY) <= joinDistance)
                    union(parents, left, right);
            }
        }

        Map<Integer, RectF> clusters = new HashMap<>();
        for (int index = 0; index < active.size(); index++) {
            int cluster = findRoot(parents, index);
            RectF bounds = active.get(index).bounds;
            RectF combined = clusters.get(cluster);
            if (combined == null) clusters.put(cluster, new RectF(bounds));
            else combined.union(bounds);
        }

        float leftEdge = 0f;
        float rightEdge = width;
        for (RectF cluster : clusters.values()) {
            float center = cluster.centerX();
            if (center <= width * .42f) leftEdge = Math.max(leftEdge, cluster.right);
            else if (center >= width * .58f) rightEdge = Math.min(rightEdge, cluster.left);
        }
        int leftInset = Math.max(0, Math.min(width, (int) Math.ceil(leftEdge)));
        int rightInset = Math.max(0, Math.min(width, width - (int) Math.floor(rightEdge)));
        int minCanvasWidth = Math.min(width, Math.max(dp(96), Math.round(width * .22f)));
        int maxReserved = Math.max(0, width - minCanvasWidth);
        if (leftInset + rightInset > maxReserved && leftInset + rightInset > 0) {
            float factor = maxReserved / (float) (leftInset + rightInset);
            leftInset = Math.round(leftInset * factor);
            rightInset = maxReserved - leftInset;
        }
        return new int[]{leftInset, rightInset};
    }

    private static int findRoot(int[] parents, int index) {
        while (parents[index] != index) {
            parents[index] = parents[parents[index]];
            index = parents[index];
        }
        return index;
    }

    private static void union(int[] parents, int first, int second) {
        int firstRoot = findRoot(parents, first);
        int secondRoot = findRoot(parents, second);
        if (firstRoot != secondRoot) parents[secondRoot] = firstRoot;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) releaseAll();
        rebuildLayout(width, height);
    }

    private void rebuildLayout(int width, int height) {
        if (width == lastLayoutWidth && height == lastLayoutHeight
                && !placedKeys.isEmpty() && template != null) return;
        releaseAll();
        lastLayoutWidth = width;
        lastLayoutHeight = height;
        placedKeys.clear();
        resetJoystickGeometry();
        if (width <= 0 || height <= 0 || template == null) return;
        isLandscape = width > height;
        if (template.builtinIndex >= 0) layoutBuiltin(width, height, template.builtinIndex);
        else layoutCustom(width, height, template.layout(isLandscape));
        setupJoystick();
    }

    private void layoutBuiltin(int width, int height, int mode) {
        List<KeySpec> specs = builtInSpecs(mode);
        for (KeySpec key : specs) placedKeys.add(new PlacedKey(key, 0, 0, 0, 0, false));
        int bottom = Math.max(0, height - Math.round(height * .02f));
        if (mode == 2) {
            layoutTopShoulders(width, height, bottom);
        } else if (mode == 1 || mode == 3) {
            layoutTemplateTwo(width, height, bottom);
        } else {
            layoutClustered(width, height, bottom, isLandscape);
        }
        if (mode == 1 || mode == 3) setCircular(10);
        if (mode == 3) {
            // The fourth PhoneME preset replaces the center of its D-pad with
            // the joystick and hides the four directional labels.
            setCircular(4);
            setJoystickForPresetThree();
        }
    }

    private List<KeySpec> builtInSpecs(int mode) {
        List<KeySpec> specs = new ArrayList<>();
        if (mode == 0) {
            String[] labels = {"L", "R", "↑", "←", "F", "→", "↓",
                    "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
            int[] codes = {-6, -7, -1, -3, -5, -4, -2,
                    49, 50, 51, 52, 53, 54, 55, 56, 57, 42, 48, 35};
            for (int i = 0; i < codes.length; i++) specs.add(new KeySpec(labels[i], codes[i]));
        } else if (mode == 2) {
            String[] labels = {"L", "R", "↑", "←", "↓", "→", "0", "*", "#", "Ok"};
            int[] codes = {-6, -7, -1, -3, -2, -4, 48, 42, 35, -5};
            for (int i = 0; i < codes.length; i++) specs.add(new KeySpec(labels[i], codes[i]));
        } else {
            String[] labels = {"L", "R", "↑", "←", "F", "→", "↓",
                    "1", "3", "7", "5", "9", "*", "0", "#"};
            int[] codes = {-6, -7, -1, -3, -5, -4, -2,
                    49, 51, 55, 53, 57, 42, 48, 35};
            if (mode == 3) {
                labels[10] = "F";
                codes[10] = -5;
            }
            for (int i = 0; i < codes.length; i++) specs.add(new KeySpec(labels[i], codes[i]));
        }
        return specs;
    }

    private void layoutClustered(int width, int height, int bottom, boolean wide) {
        int[] grid = builtInGridMetrics(width, height);
        int keyWidth = grid[0];
        int keyHeight = grid[0];
        int gap = grid[1];
        int groupWidth = keyWidth * 3 + gap * 2;
        int side = Math.max(gap, Math.round(width * .04f));
        int rightX = Math.max(side + groupWidth + gap, width - side - groupWidth);
        int bottomRow = Math.max(keyHeight, bottom - dp(8));
        int startY = bottomRow - keyHeight * 4 - gap * 3;
        int row1Y = startY + keyHeight + gap;
        int row2Y = row1Y + keyHeight + gap;
        int padX = side;
        if (!wide) {
            int available = width - groupWidth - gap - (keyHeight * 3 + gap * 2);
            padX = Math.max(0, available) / 2;
            rightX = padX + groupWidth + gap;
        }
        put(0, padX, startY, keyWidth, keyHeight, false);
        put(1, padX + 2 * (keyWidth + gap), startY, keyWidth, keyHeight, false);
        put(2, padX + keyWidth + gap, row1Y, keyWidth, keyHeight, false);
        put(3, padX, row2Y, keyWidth, keyHeight, false);
        put(4, padX + keyWidth + gap, row2Y, keyWidth, keyHeight, false);
        put(5, padX + 2 * (keyWidth + gap), row2Y, keyWidth, keyHeight, false);
        put(6, padX + keyWidth + gap, row1Y + 2 * (keyHeight + gap), keyWidth, keyHeight, false);
        for (int index = 0; index < 12; index++) {
            int keyIndex = index + 7;
            int column = index % 3;
            int row = index / 3;
            put(keyIndex, rightX + column * (keyWidth + gap),
                    startY + row * (keyHeight + gap), keyWidth, keyHeight, false);
        }
    }

    private void layoutTemplateTwo(int width, int height, int bottom) {
        int[] grid = builtInGridMetrics(width, height);
        int previewKeySize = grid[0];
        int gap = grid[1];
        int keyWidth = Math.max(1, Math.min(previewKeySize, (width - gap * 7) / 6));
        int bottomEdge = Math.max(dp(48), bottom - Math.max(dp(8), (gap + 1) / 2));
        int keyHeight = Math.max(1, Math.min(previewKeySize, (bottomEdge - gap * 4) / 4));
        int groupWidth = keyWidth * 3 + gap * 2;
        int leftX;
        int rightX;
        if (width > height * 1.15f) {
            leftX = Math.min(Math.max(gap, Math.round(width * .04f)),
                    Math.max(gap, (width - groupWidth * 2 - gap) / 2));
            rightX = width - leftX - groupWidth;
        } else {
            leftX = Math.max(gap, (width - groupWidth * 2 - gap) / 2);
            rightX = leftX + groupWidth + gap;
        }
        int topY = Math.max(gap, bottomEdge - keyHeight * 4 - gap * 3);
        int stepX = keyWidth + gap;
        int stepY = keyHeight + gap;
        int pairWidth = keyWidth * 2 + gap;
        int pairHeight = keyHeight * 2 + gap;
        int circle = Math.min(pairWidth, pairHeight);
        float circleX = rightX + stepX + (pairWidth - circle) / 2f;
        float circleY = topY + stepY * 2 + (pairHeight - circle) / 2f;
        put(0, leftX, topY, keyWidth * 2 + gap, keyHeight, false);
        put(1, rightX + stepX, topY, keyWidth * 2 + gap, keyHeight, false);
        put(2, leftX + stepX, topY + stepY, keyWidth, keyHeight, false);
        put(3, leftX, topY + stepY * 2, keyWidth, keyHeight, false);
        put(4, leftX + stepX, topY + stepY * 2, keyWidth, keyHeight, false);
        put(5, leftX + stepX * 2, topY + stepY * 2, keyWidth, keyHeight, false);
        put(6, leftX + stepX, topY + stepY * 3, keyWidth, keyHeight, false);
        put(7, rightX + stepX, topY + stepY, keyWidth, keyHeight, false);
        put(8, rightX + stepX * 2, topY + stepY, keyWidth, keyHeight, false);
        put(9, rightX, topY + stepY * 2, keyWidth, keyHeight, false);
        // Match the custom editor's shared grid-cell sizing. The circular key
        // occupies a 2x2 cell area and remains centered if the available game
        // viewport forces rectangular keys on an unusual screen shape.
        put(10, circleX, circleY, circle, circle, true);
        put(11, rightX, topY + stepY * 3, keyWidth, keyHeight, false);
        put(12, leftX + stepX * 2, topY, keyWidth, keyHeight, false);
        put(13, rightX, topY + stepY, keyWidth, keyHeight, false);
        put(14, rightX, topY, keyWidth, keyHeight, false);
    }

    private int[] builtInGridMetrics(int width, int height) {
        int[] display = GamepadTemplateLibrary.displaySize(getContext());
        int portraitWidth = Math.min(display[0], display[1]);
        int portraitHeight = Math.max(display[0], display[1]);
        int[] portraitGrid = GamepadTemplateLibrary.gridDimensions(
                getContext(), portraitWidth, portraitHeight);
        boolean landscape = width > height;
        int columns = landscape ? portraitGrid[1] : portraitGrid[0];
        int rows = landscape ? portraitGrid[0] : portraitGrid[1];
        int realWidth = landscape ? portraitHeight : portraitWidth;
        int realHeight = landscape ? portraitWidth : portraitHeight;
        realWidth = Math.max(realWidth, width);
        realHeight = Math.max(realHeight, height);
        float cell = GamepadTemplateLibrary.gridCell(
                getContext(), realWidth, realHeight, columns, rows);
        int gap = GamepadTemplateLibrary.gridGap(
                getContext(), Math.min(realWidth, realHeight));
        return new int[]{Math.max(1, Math.round(cell - gap)), gap};
    }

    private void layoutTopShoulders(int width, int height, int bottom) {
        int min = Math.min(width, height);
        int keyWidth = Math.max(dp(48), Math.round(.14f * min));
        int keyHeight = Math.max(dp(48), Math.round(.13f * min));
        int gap = Math.max(dp(4), Math.round(.012f * min));
        int margin = Math.max(gap, Math.round(width * .03f));
        int fitWidth = (width - margin * 2 - gap * 5) / 5;
        if (fitWidth > 0 && fitWidth < keyWidth) keyWidth = fitWidth;
        int topShouldersY = height - bottom + gap;
        int bottomRowY = bottom - gap - keyHeight;
        int middleRowY = bottomRowY - gap - keyHeight;
        int leftCenterX = margin + keyWidth + gap;
        int dpadRightX = leftCenterX + keyWidth + gap;
        int actionRightX = width - margin - keyWidth;
        int actionLeftX = actionRightX - keyWidth - gap;
        put(0, margin, topShouldersY, 2 * keyWidth + gap, keyHeight, false);
        put(1, width - margin - (2 * keyWidth + gap), topShouldersY,
                2 * keyWidth + gap, keyHeight, false);
        put(2, leftCenterX, middleRowY, keyWidth, keyHeight, false);
        put(3, margin, bottomRowY, keyWidth, keyHeight, false);
        put(4, leftCenterX, bottomRowY, keyWidth, keyHeight, false);
        put(5, dpadRightX, bottomRowY, keyWidth, keyHeight, false);
        put(6, actionLeftX, middleRowY, keyWidth, keyHeight, false);
        put(7, actionRightX, middleRowY, keyWidth, keyHeight, false);
        put(8, actionLeftX, bottomRowY, keyWidth, keyHeight, false);
        put(9, actionRightX, bottomRowY, keyWidth, keyHeight, false);
    }

    private void layoutCustom(int width, int height, GamepadTemplate.Layout layout) {
        if (layout == null || layout.keys.isEmpty()) return;
        int[] display = GamepadTemplateLibrary.displaySize(getContext());
        int realWidth;
        int realHeight;
        if (width <= height) {
            realWidth = Math.min(display[0], display[1]);
            realHeight = Math.max(display[0], display[1]);
        } else {
            realWidth = Math.max(display[0], display[1]);
            realHeight = Math.min(display[0], display[1]);
        }
        realWidth = Math.max(realWidth, width);
        realHeight = Math.max(realHeight, height);
        float cell = GamepadTemplateLibrary.gridCell(getContext(), realWidth, realHeight,
                layout.columns, layout.rows);
        int min = Math.min(realWidth, realHeight);
        float gap = GamepadTemplateLibrary.gridGap(getContext(), min);
        int[] location = new int[2];
        View locationView = this;
        if (!isLaidOut() && getParent() instanceof View)
            locationView = (View) getParent();
        locationView.getLocationOnScreen(location);
        if (getContext() instanceof Activity) {
            View content = ((Activity) getContext()).findViewById(android.R.id.content);
            if (content != null) {
                int[] contentLocation = new int[2];
                content.getLocationOnScreen(contentLocation);
                location[0] -= contentLocation[0];
                location[1] -= contentLocation[1];
            }
        }
        location[1] -= coordinateOriginTopInset;
        float gridX = (realWidth - layout.columns * cell) / 2f - location[0];
        float gridY = (realHeight - layout.rows * cell) / 2f - location[1];
        for (GamepadTemplate.Key key : layout.keys) {
            float left = Math.round(gridX + key.x * cell) + Math.max(0, Math.round(gap / 2f));
            float top = Math.round(gridY + key.y * cell) + Math.max(0, Math.round(gap / 2f));
            float w = Math.max(1, Math.round(key.width() * cell - gap));
            float h = Math.max(1, Math.round(key.height() * cell - gap));
            boolean circle = key.size == 2 && key.code != GamepadTemplate.JOYSTICK_CODE;
            placedKeys.add(new PlacedKey(new KeySpec(key.label, key.code),
                    left, top, w, h, circle, key.visible));
        }
    }

    private void put(int index, float left, float top, float width, float height, boolean circle) {
        if (index < 0 || index >= placedKeys.size()) return;
        PlacedKey current = placedKeys.get(index);
        placedKeys.set(index, new PlacedKey(current.key, left, top, width, height, circle));
    }

    private void setCircular(int index) {
        if (index < 0 || index >= placedKeys.size()) return;
        PlacedKey old = placedKeys.get(index);
        placedKeys.set(index, new PlacedKey(old.key, old.bounds.left, old.bounds.top,
                old.bounds.width(), old.bounds.height(), true));
    }

    private void setJoystickForPresetThree() {
        if (placedKeys.size() <= 6) return;
        PlacedKey center = placedKeys.get(4);
        PlacedKey left = placedKeys.get(3);
        PlacedKey up = placedKeys.get(2);
        joystickCenterX = center.bounds.centerX();
        joystickCenterY = center.bounds.centerY();
        joystickStepX = Math.abs(joystickCenterX - left.bounds.centerX());
        joystickStepY = Math.abs(joystickCenterY - up.bounds.centerY());
        joystickRadius = Math.min(joystickStepX, joystickStepY);
        initializeJoystickTravel(false);
        setJoystickDirectionIndexes();
        for (int i : new int[]{2, 3, 5, 6}) hide(i);
        setCircular(4);
        PlacedKey base = placedKeys.get(4);
        float halfW = joystickStepX * 1.5f;
        float halfH = joystickStepY * 1.5f;
        placedKeys.set(4, new PlacedKey(base.key, joystickCenterX - halfW,
                joystickCenterY - halfH, halfW * 2f, halfH * 2f, true));
        joystickEnabled = true;
    }

    private void setupJoystick() {
        if (template.builtinIndex == 3) {
            if (placedKeys.size() > 4) {
                joystickBaseCenterX = joystickCenterX;
                joystickBaseCenterY = joystickCenterY;
                joystickBaseRadius = joystickRadius;
            }
            return;
        }
        if (template.builtinIndex >= 0) return;
        GamepadTemplate.Layout layout = template.layout(isLandscape);
        if (layout == null) return;
        customJoystickIndex = -1;
        for (int index = 0; index < placedKeys.size(); index++) {
            if (placedKeys.get(index).key.code == GamepadTemplate.JOYSTICK_CODE) {
                customJoystickIndex = index;
                break;
            }
        }
        if (customJoystickIndex < 0) return;
        PlacedKey joystick = placedKeys.get(customJoystickIndex);
        if (!joystick.visible) return;
        joystickCenterX = joystick.bounds.centerX();
        joystickCenterY = joystick.bounds.centerY();
        joystickStepX = joystick.bounds.width() / 3f;
        joystickStepY = joystick.bounds.height() / 3f;
        joystickRadius = Math.min(joystickStepX, joystickStepY);
        initializeJoystickTravel(true);
        joystickBaseCenterX = joystickCenterX;
        joystickBaseCenterY = joystickCenterY;
        joystickBaseRadius = joystickRadius;
        joystickEnabled = joystickStepX > 0 && joystickStepY > 0;
        for (int index = 0; index < placedKeys.size(); index++) {
            int code = placedKeys.get(index).key.code;
            if (code == JOYSTICK_DIRECTIONS[0]) joystickUpIndex = index;
            if (code == JOYSTICK_DIRECTIONS[1]) joystickRightIndex = index;
            if (code == JOYSTICK_DIRECTIONS[2]) joystickDownIndex = index;
            if (code == JOYSTICK_DIRECTIONS[3]) joystickLeftIndex = index;
        }
        if (joystickEnabled) {
            if (joystickUpIndex >= 0) hide(joystickUpIndex);
            if (joystickRightIndex >= 0) hide(joystickRightIndex);
            if (joystickDownIndex >= 0) hide(joystickDownIndex);
            if (joystickLeftIndex >= 0) hide(joystickLeftIndex);
        }
    }

    private void initializeJoystickTravel(boolean custom) {
        int thumbSize = Math.round(joystickRadius * .8f);
        thumbSize = Math.max(custom ? 3 : 1, thumbSize);
        joystickThumbRadius = thumbSize / 2f;
        joystickMaxTravel = joystickRadius * 1.5f - joystickThumbRadius;
        joystickThumbMaxTravel = joystickRadius * 1.25f - joystickThumbRadius;
        joystickThumbX = joystickCenterX;
        joystickThumbY = joystickCenterY;
    }

    private void resetJoystickGeometry() {
        joystickEnabled = false;
        joystickPointerId = -1;
        joystickSector = -1;
        joystickMask = 0;
        joystickUpIndex = joystickRightIndex = joystickDownIndex = joystickLeftIndex = -1;
        joystickCenterX = joystickCenterY = 0;
        joystickStepX = joystickStepY = joystickRadius = 0;
        joystickMaxTravel = joystickThumbMaxTravel = joystickThumbRadius = 0;
        joystickBaseRadius = 0;
        joystickBaseCenterX = joystickBaseCenterY = 0;
        customJoystickIndex = -1;
    }

    private void hide(int index) {
        if (index < 0 || index >= placedKeys.size()) return;
        PlacedKey key = placedKeys.get(index);
        placedKeys.set(index, new PlacedKey(key.key, key.bounds.left, key.bounds.top,
                key.bounds.width(), key.bounds.height(), key.circular, false));
    }

    private void setJoystickDirectionIndexes() {
        for (int index = 0; index < placedKeys.size(); index++) {
            int code = placedKeys.get(index).key.code;
            if (code == JOYSTICK_DIRECTIONS[0]) joystickUpIndex = index;
            if (code == JOYSTICK_DIRECTIONS[1]) joystickRightIndex = index;
            if (code == JOYSTICK_DIRECTIONS[2]) joystickDownIndex = index;
            if (code == JOYSTICK_DIRECTIONS[3]) joystickLeftIndex = index;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (int index = 0; index < placedKeys.size(); index++) {
            PlacedKey key = placedKeys.get(index);
            if (!key.visible || key.key.code == Integer.MIN_VALUE) continue;
            if (key.key.code == GamepadTemplate.JOYSTICK_CODE) continue;
            if (template.builtinIndex == 3 && index == 4) continue;
            drawButton(canvas, index, key);
        }
        if (joystickEnabled && joystickBaseRadius > 0) drawJoystick(canvas);
    }

    private void drawButton(Canvas canvas, int index, PlacedKey key) {
        RectF bounds = key.bounds;
        boolean pressed = keyCounts[index] > 0;
        int color = pressed ? KEY_PRESSED_BG : KEY_BG;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        if (key.circular) canvas.drawOval(bounds, paint);
        else canvas.drawRoundRect(bounds, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1), 1));
        paint.setColor(KEY_STROKE);
        if (key.circular) canvas.drawOval(bounds, paint);
        else canvas.drawRoundRect(bounds, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? 0xFF0E1826 : 0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        float sizeSp = Math.max(13f, Math.min(22f,
                Math.min(bounds.width(), bounds.height()) * .3f / Math.max(1f, dp(1))));
        paint.setTextSize(sizeSp * getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(key.key.label, bounds.centerX(), baseline, paint);
    }

    private void drawJoystick(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(KEY_BG);
        canvas.drawCircle(joystickBaseCenterX, joystickBaseCenterY, joystickBaseRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, Math.round(joystickBaseRadius / 45f)));
        paint.setColor(-7170918);
        canvas.drawCircle(joystickBaseCenterX, joystickBaseCenterY, joystickBaseRadius, paint);
        float thumbX = joystickPointerId < 0 ? joystickBaseCenterX : joystickThumbX;
        float thumbY = joystickPointerId < 0 ? joystickBaseCenterY : joystickThumbY;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(joystickPointerId < 0 ? JOYSTICK_IDLE : KEY_PRESSED_BG);
        canvas.drawCircle(thumbX, thumbY, joystickThumbRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(-7170918);
        canvas.drawCircle(thumbX, thumbY, joystickThumbRadius, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            int pointerId = event.getPointerId(index);
            float x = event.getX(index);
            float y = event.getY(index);
            if (beginJoystick(pointerId, x, y)) return true;
            int keyIndex = hitKey(x, y);
            if (keyIndex >= 0) {
                updatePointer(pointerId, keyIndex);
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) return false;
            pointerKeys.put(pointerId, -1);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int index = 0; index < event.getPointerCount(); index++) {
                int pointerId = event.getPointerId(index);
                float x = event.getX(index);
                float y = event.getY(index);
                if (joystickPointerId == pointerId) updateJoystick(x, y);
                else updatePointer(pointerId, hitKeyForMove(pointerId, x, y));
            }
            invalidate();
            return !pointerKeys.isEmpty() || joystickPointerId >= 0;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int index = event.getActionIndex();
            int pointerId = event.getPointerId(index);
            if (joystickPointerId == pointerId) endJoystick();
            else {
                updatePointer(pointerId, -1);
                pointerKeys.remove(pointerId);
            }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            invalidate();
            return true;
        }
        return !pointerKeys.isEmpty() || joystickPointerId >= 0;
    }

    private boolean beginJoystick(int pointerId, float x, float y) {
        if (!joystickEnabled || joystickPointerId >= 0 || !hitJoystick(x, y)) return false;
        joystickPointerId = pointerId;
        updateJoystick(x, y);
        return true;
    }

    private boolean hitJoystick(float x, float y) {
        float halfX = joystickStepX * 1.5f;
        float halfY = joystickStepY * 1.5f;
        return joystickEnabled && joystickStepX > 0 && joystickStepY > 0
                && x >= joystickCenterX - halfX && x < joystickCenterX + halfX
                && y >= joystickCenterY - halfY && y < joystickCenterY + halfY;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joystickCenterX;
        float dy = y - joystickCenterY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (joystickMaxTravel > 0) {
            if (distance > joystickMaxTravel) {
                float scale = joystickMaxTravel / distance;
                dx *= scale;
                dy *= scale;
                distance = joystickMaxTravel;
            }
        float thumbDx = dx;
        float thumbDy = dy;
        float thumbDistance = (float) Math.sqrt(thumbDx * thumbDx + thumbDy * thumbDy);
        if (thumbDistance > joystickThumbMaxTravel && thumbDistance > 0) {
            float thumbScale = joystickThumbMaxTravel / thumbDistance;
            thumbDx *= thumbScale;
            thumbDy *= thumbScale;
        }
        joystickThumbX = joystickCenterX + thumbDx;
        joystickThumbY = joystickCenterY + thumbDy;
            float pressure = distance / joystickMaxTravel;
            updateJoystickDirections(dx, dy, pressure);
        } else {
            joystickThumbX = joystickCenterX;
            joystickThumbY = joystickCenterY;
            applyJoystickMask(0);
            joystickSector = -1;
        }
    }

    private void updateJoystickDirections(float dx, float dy, float pressure) {
        float normalized = pressure > .15f ? (pressure - .15f) / .85f : 0f;
        if (joystickMask == 0) {
            if (normalized < .5f) {
                applyJoystickMask(0);
                joystickSector = -1;
                return;
            }
        } else if (normalized < .35f) {
            applyJoystickMask(0);
            joystickSector = -1;
            return;
        }
        float degrees = (float) Math.toDegrees(Math.atan2(-dy, dx));
        if (degrees < 0) degrees += 360f;
        int candidate = ((int) ((degrees + 22.5f) / 45f)) & 7;
        int chosen = candidate;
        if (joystickMask != 0 && joystickSector >= 0) {
            float difference = Math.abs(degrees - joystickSector * 45f);
            if (difference > 180f) difference = 360f - difference;
            if (difference <= 30f) chosen = joystickSector;
        }
        joystickSector = chosen;
        applyJoystickMask(maskForSector(chosen));
    }

    private static int maskForSector(int sector) {
        switch (sector) {
            case 0: return 2;
            case 1: return 3;
            case 2: return 1;
            case 3: return 9;
            case 4: return 8;
            case 5: return 12;
            case 6: return 4;
            case 7: return 6;
            default: return 0;
        }
    }

    private void applyJoystickMask(int next) {
        int[] indices = {joystickUpIndex, joystickRightIndex,
                joystickDownIndex, joystickLeftIndex};
        for (int bit = 0; bit < 4; bit++) {
            int flag = 1 << bit;
            if ((joystickMask & flag) == (next & flag)) continue;
            int keyIndex = indices[bit];
            if (keyIndex < 0) continue;
            if ((next & flag) != 0) pressIndex(keyIndex);
            else releaseIndex(keyIndex);
        }
        joystickMask = next;
    }

    private void endJoystick() {
        if (joystickPointerId >= 0) updateJoystick(joystickCenterX, joystickCenterY);
        joystickPointerId = -1;
        joystickSector = -1;
        applyJoystickMask(0);
    }

    private int hitKey(float x, float y) {
        for (int index = placedKeys.size() - 1; index >= 0; index--) {
            PlacedKey key = placedKeys.get(index);
            if (!key.visible || key.key.code == Integer.MIN_VALUE
                    || key.key.code == GamepadTemplate.JOYSTICK_CODE) continue;
            if (template.builtinIndex == 3 && index == 4) continue;
            if (key.bounds.contains(x, y)) return index;
        }
        return -1;
    }

    private int hitKeyForMove(int pointerId, float x, float y) {
        if (joystickEnabled && hitJoystick(x, y)) return -1;
        int rawHit = hitKey(x, y);
        int hit = rawHit;
        if (hit >= 0 && isTapOnly(placedKeys.get(hit).key.code)) hit = -1;
        if (hit >= 0) return hit;
        Integer current = pointerKeys.get(pointerId);
        if (current != null && current >= 0 && current < placedKeys.size()) {
            int code = placedKeys.get(current).key.code;
            if (isTapOnly(code)) return rawHit == current ? current : -1;
            return current;
        }
        return -1;
    }

    private void updatePointer(int pointerId, int keyIndex) {
        Integer old = pointerKeys.get(pointerId);
        int oldIndex = old == null ? -1 : old;
        if (oldIndex == keyIndex) return;
        if (oldIndex >= 0) releaseIndex(oldIndex);
        if (keyIndex >= 0) {
            pointerKeys.put(pointerId, keyIndex);
            pressIndex(keyIndex);
        } else if (pointerKeys.containsKey(pointerId)) pointerKeys.put(pointerId, -1);
    }

    private void pressIndex(int index) {
        if (index < 0 || index >= keyCounts.length || index >= placedKeys.size()) return;
        if (keyCounts[index]++ == 0 && sink != null)
            sink.onKey(placedKeys.get(index).key.code, true);
        invalidate();
    }

    private void releaseIndex(int index) {
        if (index < 0 || index >= keyCounts.length || index >= placedKeys.size()
                || keyCounts[index] <= 0) return;
        if (--keyCounts[index] == 0 && sink != null)
            sink.onKey(placedKeys.get(index).key.code, false);
        invalidate();
    }

    private static boolean isTapOnly(int code) {
        return code != -1 && code != -2 && code != -3 && code != -4;
    }

    private void releaseAll() {
        for (int index = 0; index < keyCounts.length; index++) {
            while (keyCounts[index] > 0) releaseIndex(index);
        }
        pointerKeys.clear();
        if (joystickPointerId >= 0) {
            joystickPointerId = -1;
            joystickSector = -1;
            applyJoystickMask(0);
        }
    }

    @Override protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
