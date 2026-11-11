package org.recompile.freej2me.lite.nativeui;

import org.recompile.freej2me.lite.AppLocalization;
import org.recompile.freej2me.lite.R;

import android.content.Context;
import android.text.InputFilter;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Full-screen Android editor for MIDP TextBox and software-rendered TextField input requests. */
public final class NativeTextInputView extends LinearLayout {
    public interface Listener {
        void submit(String text, int commandIndex);
    }

    private final EditText editor;
    private final Listener listener;
    private final int defaultCommand;

    public NativeTextInputView(Context context, String title, String value, int maxSize, int constraints,
            String[] commands, int[] commandTypes, int defaultCommand, Listener listener) {
        super(new ContextThemeWrapper(context, R.style.NativeLcdUiTheme));
        Context uiContext = getContext();
        this.listener = listener;
        this.defaultCommand = defaultCommand;
        setOrientation(VERTICAL);
        setFocusableInTouchMode(true);
        setPadding(dp(16), 0, dp(16), dp(6));
        setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);

        TextView heading = text(title == null || title.trim().isEmpty() ? "Nhập văn bản" : title, 22);
        heading.setTextSize(21);
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        heading.setSingleLine(true);
        heading.setMaxLines(1);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(0, dp(4), 0, dp(4));
        addView(heading, new LayoutParams(-1, dp(NativeUiStyle.TITLE_BAR_HEIGHT_DP)));

        editor = new EditText(uiContext);
        editor.setInputType(NativeInputTypes.of(constraints));
        boolean password = NativeInputTypes.isPassword(constraints);
        if (password) editor.setTransformationMethod(PasswordTransformationMethod.getInstance());
        editor.setText(value == null ? "" : value);
        editor.setSelection(editor.length());
        editor.setTextColor(NativeUiStyle.primaryText(uiContext));
        editor.setHintTextColor(NativeUiStyle.secondaryText(uiContext));
        editor.setHint(AppLocalization.text(context, "Nhập văn bản…"));
        editor.setBackgroundTintList(null);
        editor.setBackground(NativeUiStyle.control(uiContext));
        editor.setPadding(dp(14), dp(10), dp(14), dp(10));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setLineSpacing(NativeUiStyle.dp(uiContext,
                NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
        editor.setMinLines(password ? 1 : 4);
        editor.setMaxLines(password ? 1 : Integer.MAX_VALUE);
        editor.setHorizontallyScrolling(password);
        editor.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (maxSize > 0) editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxSize)});
        addView(editor, new LayoutParams(-1, 0, 1));
        editor.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top <= oldBottom - oldTop) return;
            editor.post(this::clampScrollToExpandedViewport);
        });
        editor.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                submit();
                return true;
            }
            return false;
        });

        TextView counter = text("", 12);
        counter.setTextColor(NativeUiStyle.secondaryText(uiContext));
        counter.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        counter.setPadding(dp(4), 0, 0, 0);
        counter.setVisibility(maxSize > 0 ? VISIBLE : GONE);
        addView(counter, new LayoutParams(-1, dp(26)));
        Runnable updateCounter = () -> {
            if (maxSize > 0) counter.setText(editor.length() + "/" + maxSize);
        };
        updateCounter.run();
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable editable) { updateCounter.run(); }
        });

        List<NativeCommandBar.Action> actions = new ArrayList<>();
        if (commands != null) {
            for (int i = 0; i < commands.length; i++) {
                int type = commandTypes != null && i < commandTypes.length
                        ? commandTypes[i] : -1;
                actions.add(new NativeCommandBar.Action(i, type, i, commands[i]));
            }
        }
        if (defaultCommand < 0) {
            actions.add(0, new NativeCommandBar.Action(-1, 4, Integer.MIN_VALUE, "Xong"));
        }
        NativeCommandBar commandBar = new NativeCommandBar(uiContext,
                index -> listener.submit(editor.getText().toString(), index), 0, 0);
        commandBar.setActions(actions);
        addView(commandBar, new LayoutParams(-1, -2));

        post(() -> {
            editor.requestFocus();
            InputMethodManager manager = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void clampScrollToExpandedViewport() {
        android.text.Layout textLayout = editor.getLayout();
        if (textLayout == null || editor.getHeight() <= 0) return;
        int viewportHeight = editor.getHeight()
                - editor.getCompoundPaddingTop() - editor.getCompoundPaddingBottom();
        int maximumScrollY = Math.max(0, textLayout.getHeight() - viewportHeight);
        int targetScrollY = Math.min(Math.max(0, editor.getScrollY()), maximumScrollY);
        if (targetScrollY != editor.getScrollY())
            editor.scrollTo(editor.getScrollX(), targetScrollY);
    }

    private void submit() {
        listener.submit(editor.getText().toString(), defaultCommand);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(NativeUiStyle.primaryText(getContext()));
        view.setLineSpacing(NativeUiStyle.dp(getContext(),
                NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
