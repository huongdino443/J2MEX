/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2018 Nikita Shakarun
 * Copyright 2020-2023 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0.
 * See http://www.apache.org/licenses/LICENSE-2.0
 * Distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Modified for FreeJ2ME Lite: Android Form layout port based on J2ME Loader
 * Form.getScreenView; Items are detached wire models rather than local MIDP
 * objects. Adds ordered state collection and in-place view updates for IPC.
 */
package org.recompile.freej2me.lite.nativeui;

import org.recompile.freej2me.lite.AppLocalization;
import android.content.Context;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.recompile.freej2me.lite.R;
import org.recompile.freej2me.protocol.NativeScreen;

final class NativeFormContent extends ScrollView {
    interface ItemActions { void activate(int id); }
    private final LinearLayout layout;
    private final LinearLayout surface;
    private final List<Binding> bindings = new ArrayList<>();
    private final Runnable changed;
    private final ItemActions itemAction;
    private boolean updating;

    NativeFormContent(Context context, Runnable changed, ItemActions itemAction) {
        super(context);
        this.changed = changed; this.itemAction = itemAction;
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(NativeUiStyle.dp(context, 12), NativeUiStyle.dp(context, 6),
                NativeUiStyle.dp(context, 12), NativeUiStyle.dp(context, 6));
        layout.setFocusableInTouchMode(true);
        layout.setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
        addView(layout);
        surface = new LinearLayout(context);
        surface.setOrientation(LinearLayout.VERTICAL);
        surface.setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
        layout.addView(surface, new LinearLayout.LayoutParams(-1, -2));
        setFillViewport(true);
    }
    void update(List<NativeScreen.Entry> entries, boolean preserveInput) {
        updating = true;
        try {
            boolean rebuild = entries.size() != bindings.size();
            for (int i = 0; !rebuild && i < entries.size(); i++)
                rebuild = bindings.get(i).entry.id != entries.get(i).id
                        || bindings.get(i).entry.kind != entries.get(i).kind
                        || bindings.get(i).entry.interactive != entries.get(i).interactive
                        || appearanceChanged(bindings.get(i).entry, entries.get(i))
                        || layoutChanged(bindings.get(i).entry, entries.get(i));
            if (rebuild) {
                bindings.clear(); surface.removeAllViews();
                for (int i = 0; i < entries.size(); i++) {
                    NativeScreen.Entry entry = entries.get(i);
                    Binding binding = new Binding(entry);
                    bindings.add(binding);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                    if (entry.kind != NativeScreen.SPACER) {
                        params.bottomMargin = NativeUiStyle.dp(getContext(), 4);
                    }
                    surface.addView(binding.container, params);
                }
                surface.setVisibility(entries.isEmpty() ? GONE : VISIBLE);
                layout.requestFocus();
            }
            for (int i = 0; i < entries.size(); i++) bindings.get(i).update(entries.get(i), preserveInput && !rebuild);
        } finally { updating = false; }
    }
    List<NativeScreen.State> states() {
        List<NativeScreen.State> states = new ArrayList<>();
        for (Binding binding : bindings) {
            NativeScreen.State state = binding.state();
            if (state != null) states.add(state);
        }
        return states;
    }
    void focus(int id) {
        for (Binding binding : bindings)
            if (binding.entry.id == id && !binding.container.hasFocus()) binding.container.requestFocus();
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setLineSpacing(NativeUiStyle.dp(getContext(),
                NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
        return view;
    }
    private void localChange() { if (!updating) changed.run(); }

    private static boolean appearanceChanged(NativeScreen.Entry before, NativeScreen.Entry after) {
        return (before.kind == NativeScreen.STRING || before.kind == NativeScreen.IMAGE)
                && before.value != after.value;
    }

    private static boolean layoutChanged(NativeScreen.Entry before, NativeScreen.Entry after) {
        return (before.kind == NativeScreen.IMAGE || before.kind == NativeScreen.DATE_FIELD)
                && before.constraints != after.constraints;
    }

    private static boolean isButtonItem(NativeScreen.Entry entry) {
        return entry.kind == NativeScreen.STRING
                && entry.value == NativeScreen.APPEARANCE_BUTTON;
    }

    private static boolean isImageButton(NativeScreen.Entry entry) {
        return entry.kind == NativeScreen.IMAGE
                && entry.value == NativeScreen.APPEARANCE_BUTTON;
    }

    private static boolean isHyperlinkItem(NativeScreen.Entry entry) {
        return (entry.kind == NativeScreen.STRING || entry.kind == NativeScreen.IMAGE)
                && entry.value == NativeScreen.APPEARANCE_HYPERLINK;
    }

    private static final class PopupChoiceRow extends LinearLayout {
        final TextView label;
        final FrameLayout preview;
        final ImageView image;
        final View color;

        PopupChoiceRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);

            label = new TextView(context);
            label.setTextColor(NativeUiStyle.primaryText(context));
            label.setTextSize(16);
            label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label, new LayoutParams(0, -1, 1f));

            preview = new FrameLayout(context);
            image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.addView(image, new FrameLayout.LayoutParams(
                    NativeUiStyle.dp(context, 32), NativeUiStyle.dp(context, 32), Gravity.CENTER));
            color = new View(context);
            preview.addView(color, new FrameLayout.LayoutParams(
                    NativeUiStyle.dp(context, 26), NativeUiStyle.dp(context, 20), Gravity.CENTER));
            LayoutParams previewParams = new LayoutParams(
                    NativeUiStyle.dp(context, 32), NativeUiStyle.dp(context, 32));
            previewParams.leftMargin = NativeUiStyle.dp(context, 12);
            addView(preview, previewParams);
        }

        void bind(Context context, NativeScreen.Entry option, int position,
                int count, int selectedPosition) {
            setPadding(NativeUiStyle.dp(context, 18), NativeUiStyle.dp(context, 10),
                    NativeUiStyle.dp(context, 18), NativeUiStyle.dp(context, 10));
            setMinimumHeight(NativeUiStyle.dp(context,
                    NativeUiStyle.COMMAND_MENU_ROW_HEIGHT_DP));
            label.setText(option.text);

            setBackground(NativeUiStyle.commandMenuRow(context));
            setSelected(position == selectedPosition);

            android.graphics.drawable.Drawable optionImage =
                    NativeChoiceAdapter.icon(context, option.image, 28);
            if (optionImage != null) {
                image.setImageDrawable(optionImage);
                image.setVisibility(VISIBLE);
                color.setVisibility(GONE);
                preview.setVisibility(VISIBLE);
                return;
            }

            Integer previewColor = choicePreviewColor(option.text);
            if (previewColor != null) {
                GradientDrawable swatch = new GradientDrawable();
                swatch.setColor(previewColor);
                swatch.setCornerRadius(NativeUiStyle.dp(context, 5));
                swatch.setStroke(NativeUiStyle.dp(context, 1), NativeUiStyle.FIELD_OUTLINE);
                color.setBackground(swatch);
                color.setVisibility(VISIBLE);
                image.setVisibility(GONE);
                preview.setVisibility(VISIBLE);
            } else {
                image.setVisibility(GONE);
                color.setVisibility(GONE);
                preview.setVisibility(GONE);
            }
        }
    }

    private static Integer choicePreviewColor(String label) {
        if (label == null) return null;
        String value = label.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (value.startsWith("#")) {
            try { return Color.parseColor(value); } catch (IllegalArgumentException ignored) { }
        }
        switch (value) {
            case "purple": return 0xFF8055A6;
            case "blue": return 0xFF3978D4;
            case "green": return 0xFF378B4A;
            case "red": return 0xFFD84949;
            case "orange": return 0xFFE58B35;
            case "pink": return 0xFFE16A9A;
            case "teal": return 0xFF278D89;
            case "indigo": return 0xFF5962C9;
            case "yellow": return 0xFFE0C247;
            case "black": return 0xFF111114;
            case "white": case "light": return 0xFFF1F1F1;
            case "dark": return 0xFF303036;
            case "gray": case "grey": return 0xFF888888;
            case "brown": return 0xFF895A3C;
            case "cyan": case "aqua": return 0xFF28B5C5;
            case "magenta": case "fuchsia": return 0xFFD83A9D;
            case "lime": return 0xFF83B83D;
            case "maroon": return 0xFF800000;
            case "navy": return 0xFF142B63;
            case "olive": return 0xFF808000;
            case "silver": return 0xFFC0C0C0;
            default:
                try { return Color.parseColor(value); }
                catch (IllegalArgumentException ignored) { return null; }
        }
    }

    private final class Binding {
        NativeScreen.Entry entry;
        final LinearLayout container;
        final TextView label;
        TextView body;
        ImageView image;
        FrameLayout imageFrame;
        LinearLayout imageButtonContent;
        TextView dateControl;
        Long dateMillis;
        EditText editor;
        LinearLayout choices;
        TextView popup;
        PopupWindow choicePopup;
        SeekBar slider;
        ProgressBar progress;
        int choiceCount = -1, choiceType = -1, popupSelection = -1, inputConstraints = -1;

        Binding(NativeScreen.Entry entry) {
            this.entry = entry;
            container = new LinearLayout(getContext()); container.setOrientation(LinearLayout.VERTICAL);
            int horizontalInset = NativeUiStyle.dp(getContext(), 4);
            int verticalInset = NativeUiStyle.dp(getContext(),
                    isButtonItem(entry) || isImageButton(entry) ? 2 : 4);
            container.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset);
            label = text("", 12);
            label.setTextColor(NativeUiStyle.ACCENT);
            label.setTypeface(Typeface.MONOSPACE);
            label.setPadding(0, 0, 0,
                    NativeUiStyle.dp(getContext(), 4));
            container.addView(label);
            switch (entry.kind) {
                case NativeScreen.STRING:
                    body = text("", 16);
                    body.setTextColor(NativeUiStyle.primaryText(getContext()));
                    if (isButtonItem(entry)) {
                        body.setTextSize(15);
                        body.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                        body.setGravity(Gravity.CENTER);
                        body.setSingleLine(true);
                        body.setEllipsize(TextUtils.TruncateAt.END);
                        body.setPadding(NativeUiStyle.dp(getContext(), 16), 0,
                                NativeUiStyle.dp(getContext(), 16), 0);
                        body.setMinHeight(NativeUiStyle.dp(getContext(),
                                NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP));
                        container.addView(body, new LinearLayout.LayoutParams(-1,
                                NativeUiStyle.dp(getContext(), NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP)));
                    } else {
                        container.addView(body);
                    }
                    break;
                case NativeScreen.DATE_FIELD:
                    dateControl = text("", 16);
                    dateControl.setSingleLine(true);
                    dateControl.setEllipsize(TextUtils.TruncateAt.END);
                    dateControl.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    dateControl.setPadding(NativeUiStyle.dp(getContext(), 12), 0,
                            NativeUiStyle.dp(getContext(), 40), 0);
                    dateControl.setBackground(NativeUiStyle.spinnerControl(getContext()));
                    dateControl.setMinHeight(NativeUiStyle.dp(getContext(),
                            NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP));
                    dateControl.setFocusable(true);
                    dateControl.setOnClickListener(view -> showDatePicker());
                    container.addView(dateControl, new LinearLayout.LayoutParams(-1,
                            NativeUiStyle.dp(getContext(), NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP)));
                    break;
                case NativeScreen.TEXT:
                    editor = new EditText(getContext());
                    editor.setTextSize(16);
                    editor.setTextColor(NativeUiStyle.primaryText(getContext()));
                    editor.setHintTextColor(NativeUiStyle.secondaryText(getContext()));
                    editor.setBackgroundTintList(null);
                    editor.setBackground(NativeUiStyle.control(getContext()));
                    editor.setSingleLine(true);
                    editor.setMaxLines(1);
                    editor.setHorizontallyScrolling(true);
                    editor.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    editor.setPadding(NativeUiStyle.dp(getContext(), 12), 0,
                            NativeUiStyle.dp(getContext(), 12), 0);
                    container.addView(editor, new LinearLayout.LayoutParams(-1,
                            NativeUiStyle.dp(getContext(), NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP)));
                    editor.addTextChangedListener(new TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                        public void onTextChanged(CharSequence s, int start, int before, int count) { }
                        public void afterTextChanged(Editable s) { localChange(); }
                    });
                    break;
                case NativeScreen.CHOICE:
                    choices = new LinearLayout(getContext()); choices.setOrientation(LinearLayout.VERTICAL);
                    choices.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    choices.setPadding(0, 0, 0, 0);
                    container.addView(choices);
                    break;
                case NativeScreen.GAUGE:
                    if (entry.interactive && entry.maximum > 0) {
                        slider = new SeekBar(getContext());
                        NativeUiStyle.styleSlider(getContext(), slider);
                        container.addView(slider, new LinearLayout.LayoutParams(-1, -2));
                        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                            public void onProgressChanged(SeekBar bar, int value, boolean user) { if (user) localChange(); }
                            public void onStartTrackingTouch(SeekBar bar) { }
                            public void onStopTrackingTouch(SeekBar bar) { localChange(); }
                        });
                    } else {
                        progress = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
                        NativeUiStyle.styleProgress(getContext(), progress);
                        container.addView(progress);
                    }
                    break;
                case NativeScreen.IMAGE:
                    imageFrame = new FrameLayout(getContext());
                    image = new ImageView(getContext());
                    image.setAdjustViewBounds(false);
                    image.setMaxWidth(NativeUiStyle.dp(getContext(), 240));
                    image.setMaxHeight(NativeUiStyle.dp(getContext(), 240));
                    image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    if (isImageButton(entry)) {
                        int iconSize = NativeUiStyle.dp(getContext(), 32);
                        imageFrame.addView(image, new FrameLayout.LayoutParams(
                                iconSize, iconSize, Gravity.CENTER));
                        imageButtonContent = new LinearLayout(getContext());
                        imageButtonContent.setOrientation(LinearLayout.HORIZONTAL);
                        imageButtonContent.setGravity(Gravity.CENTER);
                        imageButtonContent.setPadding(NativeUiStyle.dp(getContext(), 18), 0,
                                NativeUiStyle.dp(getContext(), 18), 0);
                        imageButtonContent.addView(imageFrame, new LinearLayout.LayoutParams(
                                iconSize, iconSize));
                        LinearLayout.LayoutParams imageButtonParams =
                                new LinearLayout.LayoutParams(-2, NativeUiStyle.dp(getContext(),
                                        NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP));
                        imageButtonParams.gravity = Gravity.CENTER_HORIZONTAL;
                        container.addView(imageButtonContent, imageButtonParams);
                    } else {
                        imageFrame.addView(image,
                                new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                        container.addView(imageFrame, new LinearLayout.LayoutParams(-1, -2));
                        body = text("", 16);
                        body.setTextColor(NativeUiStyle.primaryText(getContext()));
                        container.addView(body);
                    }
                    break;
                case NativeScreen.CUSTOM:
                    imageFrame = new FrameLayout(getContext());
                    image = new ImageView(getContext());
                    image.setAdjustViewBounds(false);
                    image.setMaxWidth(NativeUiStyle.dp(getContext(), 240));
                    image.setMaxHeight(NativeUiStyle.dp(getContext(), 240));
                    image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    imageFrame.addView(image, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                    container.addView(imageFrame, new LinearLayout.LayoutParams(-1, -2));
                    body = text("", 16);
                    body.setTextColor(NativeUiStyle.primaryText(getContext()));
                    container.addView(body);
                    break;
                case NativeScreen.SPACER:
                    container.setMinimumHeight(NativeChoiceAdapter.dp(getContext(), Math.min(160, entry.value)));
                    break;
                default:
                    body = text("", 16);
                    body.setTextColor(NativeUiStyle.primaryText(getContext()));
                    container.addView(body);
                    if (entry.kind == NativeScreen.UNSUPPORTED) body.setTextColor(0xFFFF8A80);
            }
            container.setOnClickListener(view -> {
                if (this.entry.kind == NativeScreen.DATE_FIELD) {
                    showDatePicker();
                } else if (this.entry.actionId != 0
                        && !isButtonItem(this.entry) && !isImageButton(this.entry)) {
                    itemAction.activate(this.entry.id);
                }
            });
        }
        void update(NativeScreen.Entry next, boolean preserveInput) {
            NativeScreen.Entry previous = entry; entry = next;
            boolean buttonItem = isButtonItem(next);
            boolean imageButton = isImageButton(next);
            boolean hyperlink = isHyperlinkItem(next);
            boolean containerAction = next.actionId != 0 && !buttonItem && !imageButton;
            container.setClickable(containerAction || next.kind == NativeScreen.DATE_FIELD);
            container.setFocusable(containerAction || next.kind == NativeScreen.DATE_FIELD);
            if (containerAction) {
                container.setBackground(NativeUiStyle.itemPress(getContext()));
            } else {
                container.setBackground(null);
            }
            label.setText(next.label); label.setVisibility(next.label == null || next.label.isEmpty() ? GONE : VISIBLE);
            if (body != null) body.setText(next.text);
            if (buttonItem && body != null) {
                NativeUiStyle.styleFormButton(getContext(), body, next.actionId != 0);
                body.setClickable(next.actionId != 0);
                body.setFocusable(next.actionId != 0);
                body.setOnClickListener(next.actionId == 0 ? null
                        : view -> itemAction.activate(entry.id));
            } else if (imageButtonContent != null) {
                imageButtonContent.setBackground(NativeUiStyle.formButton(
                        getContext(), next.actionId != 0));
                imageButtonContent.setClickable(next.actionId != 0);
                imageButtonContent.setFocusable(next.actionId != 0);
                imageButtonContent.setOnClickListener(next.actionId == 0 ? null
                        : view -> itemAction.activate(entry.id));
            } else if (hyperlink && body != null) {
                body.setTextColor(NativeUiStyle.accent(getContext()));
                body.setPaintFlags(body.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            } else if (body != null && next.kind == NativeScreen.STRING) {
                body.setTextColor(NativeUiStyle.primaryText(getContext()));
                body.setPaintFlags(body.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            }
            if (imageFrame != null) {
                if (!Arrays.equals(previous.image, next.image) || image.getDrawable() == null) {
                    android.graphics.Bitmap bitmap = NativeChoiceAdapter.bitmap(next.image, 1024);
                    image.setImageBitmap(bitmap);
                    image.setContentDescription(next.text);
                    if (bitmap != null) {
                        FrameLayout.LayoutParams imageParams =
                                (FrameLayout.LayoutParams)image.getLayoutParams();
                        if (next.kind == NativeScreen.IMAGE) {
                            int iconSize = NativeUiStyle.dp(getContext(), 32);
                            imageParams.width = iconSize;
                            imageParams.height = iconSize;
                            image.setScaleType(ImageView.ScaleType.FIT_XY);
                        } else {
                            float scale = Math.min(1f, 240f
                                    / Math.max(bitmap.getWidth(), bitmap.getHeight()));
                            imageParams.width = NativeUiStyle.dp(getContext(),
                                    Math.max(1, Math.round(bitmap.getWidth() * scale)));
                            imageParams.height = NativeUiStyle.dp(getContext(),
                                    Math.max(1, Math.round(bitmap.getHeight() * scale)));
                            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        }
                        image.setLayoutParams(imageParams);
                    }
                }
                boolean hasImage = next.image != null && next.image.length > 0
                        && image.getDrawable() != null;
                imageFrame.setVisibility(hasImage ? VISIBLE : GONE);
                if (body != null) {
                    body.setVisibility(!hasImage || next.text == null || next.text.isEmpty()
                            ? VISIBLE : GONE);
                }
                int alignment = next.constraints & 0x0F;
                int gravity = imageButton ? Gravity.CENTER
                        : alignment == NativeScreen.ITEM_LAYOUT_RIGHT ? Gravity.END
                        : alignment == NativeScreen.ITEM_LAYOUT_CENTER
                                ? Gravity.CENTER_HORIZONTAL : Gravity.START;
                FrameLayout.LayoutParams imageParams =
                        (FrameLayout.LayoutParams)image.getLayoutParams();
                imageParams.gravity = gravity | Gravity.CENTER_VERTICAL;
                image.setLayoutParams(imageParams);
                if (body != null) {
                    body.setGravity(gravity | Gravity.TOP);
                    if (hyperlink) {
                        body.setTextColor(NativeUiStyle.accent(getContext()));
                        body.setPaintFlags(body.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    } else {
                        body.setTextColor(NativeUiStyle.secondaryText(getContext()));
                        body.setPaintFlags(body.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
                    }
                }
            }
            if (dateControl != null) {
                if (!preserveInput || !Objects.equals(previous.text, next.text)) {
                    dateMillis = parseDate(next.text);
                }
                dateControl.setText(formatDate(next.constraints, dateMillis));
            }
            if (editor != null) {
                if (inputConstraints != next.constraints) {
                    editor.setInputType(NativeInputTypes.of(next.constraints));
                    editor.setSingleLine(true);
                    editor.setMaxLines(1);
                    editor.setHorizontallyScrolling(true);
                    inputConstraints = next.constraints;
                }
                editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(1, next.maxSize))});
                editor.setEnabled((next.constraints & 0x20000) == 0);
                if (!preserveInput && !editor.getText().toString().equals(next.text)) {
                    editor.setText(next.text);
                    editor.setSelection(Math.max(0, Math.min(next.caret, editor.length())));
                }
            }
            if (choices != null) updateChoices(next, preserveInput);
            if (slider != null) {
                slider.setMax(Math.max(1, next.maximum));
                if (!preserveInput) slider.setProgress(next.value);
            }
            if (progress != null) {
                progress.setIndeterminate(next.maximum <= 0);
                progress.setMax(Math.max(1, next.maximum)); progress.setProgress(next.value);
            }
        }
        void updateChoices(NativeScreen.Entry next, boolean preserveInput) {
            boolean rebuild = choiceCount != next.children.size() || choiceType != next.choiceType;
            boolean[] previousFlags = new boolean[choiceCount < 0 ? 0 : choiceCount];
            if (preserveInput && !rebuild) previousFlags = state().flags;
            if (choicePopup != null && choicePopup.isShowing()) choicePopup.dismiss();
            if (rebuild) {
                choices.removeAllViews(); popup = null;
                choiceCount = next.children.size(); choiceType = next.choiceType;
                if (choiceType == NativeScreen.POPUP) {
                    popup = new TextView(getContext());
                    popup.setTextSize(16);
                    popup.setTextColor(NativeUiStyle.primaryText(getContext()));
                    popup.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                    popup.setSingleLine(true);
                    popup.setEllipsize(TextUtils.TruncateAt.END);
                    popup.setPadding(NativeUiStyle.dp(getContext(), 12), 0,
                            NativeUiStyle.dp(getContext(), 40), 0);
                    popup.setBackground(NativeUiStyle.spinnerControl(getContext()));
                    popup.setMinimumHeight(NativeUiStyle.dp(getContext(),
                            NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP));
                    popup.setFocusable(true);
                    popup.setClickable(true);
                    popup.setOnClickListener(view -> showPopupChoices());
                    choices.addView(popup, new LinearLayout.LayoutParams(-1,
                            NativeUiStyle.dp(getContext(), NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP)));
                } else {
                    for (int i = 0; i < choiceCount; i++) {
                        final int index = i;
                        CompoundButton button = choiceType == NativeScreen.MULTIPLE
                                ? new CheckBox(getContext()) : new RadioButton(getContext());
                        button.setTextSize(16);
                        button.setTextColor(NativeUiStyle.choicePrimaryText(getContext()));
                        button.setLineSpacing(NativeUiStyle.dp(getContext(),
                                NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
                        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                        button.setBackground(NativeUiStyle.choiceRow(getContext()));
                        button.setFocusable(true);
                        button.setMinHeight(NativeUiStyle.dp(getContext(),
                                NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP));
                        button.setPadding(NativeUiStyle.dp(getContext(), 8), 0,
                                NativeUiStyle.dp(getContext(), 8), 0);
                        choices.addView(button, new LinearLayout.LayoutParams(-1,
                                NativeUiStyle.dp(getContext(), NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP)));
                        button.setOnCheckedChangeListener((view, checked) -> {
                            if (updating) return;
                            if (checked && choiceType != NativeScreen.MULTIPLE) {
                                updating = true;
                                for (int j = 0; j < choices.getChildCount(); j++)
                                    if (j != index) ((CompoundButton)choices.getChildAt(j)).setChecked(false);
                                updating = false;
                            }
                            localChange();
                        });
                    }
                }
            }
            if (popup != null) {
                int selected = 0;
                for (int i = 0; i < next.children.size(); i++) {
                    if ((!preserveInput || rebuild ? next.children.get(i).selected : previousFlags[i])) selected = i;
                }
                popupSelection = selected;
                popup.setText(next.children.isEmpty() ? "" : next.children.get(selected).text);
            } else {
                for (int i = 0; i < next.children.size(); i++) {
                    NativeScreen.Entry child = next.children.get(i);
                    CompoundButton button = (CompoundButton)choices.getChildAt(i);
                    button.setText(child.text);
                    button.setCompoundDrawables(null, null, NativeChoiceAdapter.icon(getContext(), child.image, 28), null);
                    if (!preserveInput || rebuild) button.setChecked(child.selected);
                }
            }
        }

        private void showPopupChoices() {
            if (popup == null || entry.children.isEmpty()) return;
            if (choicePopup != null && choicePopup.isShowing()) {
                choicePopup.dismiss();
                return;
            }

            final int[] focus = {
                    Math.max(0, Math.min(popupSelection, entry.children.size() - 1))
            };
            ListView options = new ListView(getContext());
            options.setBackground(NativeUiStyle.menuSurface(getContext()));
            options.setClipToOutline(true);
            options.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            options.setPadding(NativeUiStyle.dp(getContext(), 1),
                    NativeUiStyle.dp(getContext(), 1), NativeUiStyle.dp(getContext(), 1),
                    NativeUiStyle.dp(getContext(), 1));
            options.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            options.setDividerHeight(0);
            options.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            options.setVerticalScrollBarEnabled(false);

            BaseAdapter adapter = new BaseAdapter() {
                @Override public int getCount() { return entry.children.size(); }
                @Override public NativeScreen.Entry getItem(int position) {
                    return entry.children.get(position);
                }
                @Override public long getItemId(int position) { return getItem(position).id; }
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    PopupChoiceRow row = recycled instanceof PopupChoiceRow
                            ? (PopupChoiceRow)recycled : new PopupChoiceRow(getContext());
                    row.bind(getContext(), getItem(position), position,
                            entry.children.size(), focus[0]);
                    return row;
                }
            };
            options.setAdapter(adapter);
            options.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(AdapterView<?> parent) { }
                @Override public void onItemSelected(AdapterView<?> parent, View view,
                        int position, long id) {
                    if (position >= 0 && focus[0] != position) {
                        focus[0] = position;
                        adapter.notifyDataSetChanged();
                    }
                }
            });
            options.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < entry.children.size()) {
                    boolean changedSelection = popupSelection != position;
                    popupSelection = position;
                    popup.setText(entry.children.get(position).text);
                    if (changedSelection) localChange();
                }
                if (choicePopup != null) choicePopup.dismiss();
            });

            CharSequence[] labels = new CharSequence[entry.children.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = entry.children.get(i).text;
            android.graphics.Rect visibleFrame = new android.graphics.Rect();
            popup.getWindowVisibleDisplayFrame(visibleFrame);
            int[] anchorLocation = new int[2];
            popup.getLocationOnScreen(anchorLocation);
            int[] anchorWindowLocation = new int[2];
            popup.getLocationInWindow(anchorWindowLocation);
            int leftInset = Math.max(NativeUiStyle.dp(getContext(), 8),
                    anchorLocation[0] - visibleFrame.left);
            int width = NativeUiStyle.menuWidth(
                    getContext(), labels, visibleFrame.width(), leftInset);
            int anchorTop = anchorLocation[1];
            int anchorBottom = anchorTop + popup.getHeight();
            int spaceAbove = anchorTop - visibleFrame.top;
            int spaceBelow = visibleFrame.bottom - anchorBottom;
            int popupGap = NativeUiStyle.dp(getContext(), 6);
            int screenEdgeInset = NativeUiStyle.dp(getContext(), 8);
            int availableHeight = Math.max(0, Math.max(spaceAbove, spaceBelow)
                    - popupGap - screenEdgeInset);
            int height = NativeUiStyle.menuHeight(getContext(), entry.children.size(),
                    availableHeight);
            int requiredSpace = height + popupGap + screenEdgeInset;
            boolean fitsBelow = spaceBelow >= requiredSpace;
            boolean fitsAbove = spaceAbove >= requiredSpace;
            boolean openAbove = !fitsBelow && (fitsAbove || spaceAbove > spaceBelow);
            int popupScreenTop = openAbove
                    ? anchorTop - popupGap - height
                    : anchorBottom + popupGap;
            int windowScreenLeft = anchorLocation[0] - anchorWindowLocation[0];
            int windowScreenTop = anchorLocation[1] - anchorWindowLocation[1];
            int popupWindowLeft = visibleFrame.left + leftInset - windowScreenLeft;
            int popupWindowTop = popupScreenTop - windowScreenTop;

            PopupWindow window = new PopupWindow(options, width, height, true);
            choicePopup = window;
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setAnimationStyle(openAbove
                    ? R.style.NativeChoiceDropdownAnimationUp
                    : R.style.NativeChoiceDropdownAnimationDown);
            window.setElevation(NativeUiStyle.dp(getContext(), 8));
            window.setOutsideTouchable(true);
            window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            window.setClippingEnabled(true);
            window.setOnDismissListener(() -> {
                if (choicePopup == window) choicePopup = null;
            });
            options.setSelection(focus[0]);
            window.showAtLocation(popup, Gravity.TOP | Gravity.LEFT,
                    popupWindowLeft, popupWindowTop);
            options.requestFocus();
            options.setSelection(focus[0]);
        }

        private Long parseDate(String value) {
            if (value == null || value.length() == 0) return null;
            try { return Long.valueOf(value); }
            catch (NumberFormatException ignored) { return null; }
        }
        private String formatDate(int mode, Long millis) {
            if (millis == null) {
                return mode == NativeScreen.DATE_MODE_TIME ? "Chọn giờ" : "Chọn ngày";
            }
            Date date = new Date(millis);
            String dateText = java.text.DateFormat.getDateInstance(
                    java.text.DateFormat.MEDIUM, AppLocalization.locale(getContext())).format(date);
            String timeText = java.text.DateFormat.getTimeInstance(
                    java.text.DateFormat.SHORT, AppLocalization.locale(getContext())).format(date);
            if (mode == NativeScreen.DATE_MODE_TIME) return timeText;
            if (mode == NativeScreen.DATE_MODE_DATE_TIME) return dateText + " • " + timeText;
            return dateText;
        }
        private void showDatePicker() {
            Calendar initial = Calendar.getInstance();
            if (dateMillis != null) initial.setTimeInMillis(dateMillis);
            if (entry.constraints == NativeScreen.DATE_MODE_TIME) {
                showTimePicker(initial);
                return;
            }
            DatePickerDialog dialog = new DatePickerDialog(getContext(),
                    (picker, year, month, day) -> {
                        Calendar selected = (Calendar)initial.clone();
                        selected.set(Calendar.YEAR, year);
                        selected.set(Calendar.MONTH, month);
                        selected.set(Calendar.DAY_OF_MONTH, day);
                        if (entry.constraints == NativeScreen.DATE_MODE_DATE_TIME) {
                            showTimePicker(selected);
                        } else {
                            selected.set(Calendar.HOUR_OF_DAY, 0);
                            selected.set(Calendar.MINUTE, 0);
                            selected.set(Calendar.SECOND, 0);
                            selected.set(Calendar.MILLISECOND, 0);
                            commitDate(selected.getTimeInMillis());
                        }
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
                    initial.get(Calendar.DAY_OF_MONTH));
            dialog.show();
        }
        private void showTimePicker(Calendar initial) {
            TimePickerDialog dialog = new TimePickerDialog(getContext(),
                    (picker, hour, minute) -> {
                        Calendar selected = (Calendar)initial.clone();
                        selected.set(Calendar.HOUR_OF_DAY, hour);
                        selected.set(Calendar.MINUTE, minute);
                        selected.set(Calendar.SECOND, 0);
                        selected.set(Calendar.MILLISECOND, 0);
                        commitDate(selected.getTimeInMillis());
                    }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(getContext()));
            dialog.show();
        }
        private void commitDate(long millis) {
            dateMillis = millis;
            dateControl.setText(formatDate(entry.constraints, dateMillis));
            localChange();
        }
        NativeScreen.State state() {
            NativeScreen.State state = new NativeScreen.State(); state.id = entry.id;
            if (dateControl != null) {
                state.text = dateMillis == null ? "" : Long.toString(dateMillis);
            } else if (editor != null) {
                state.text = editor.getText().toString(); state.caret = Math.max(0, editor.getSelectionStart());
            } else if (choices != null) {
                state.flags = new boolean[Math.max(0, choiceCount)];
                if (popup != null) {
                    int selected = popupSelection;
                    if (selected >= 0 && selected < state.flags.length) state.flags[selected] = true;
                } else for (int i = 0; i < state.flags.length; i++)
                    state.flags[i] = ((CompoundButton)choices.getChildAt(i)).isChecked();
            } else if (slider != null) state.value = slider.getProgress();
            else return null;
            return state;
        }
    }
}
