package org.recompile.freej2me.lite.nativeui;

import android.content.Context;
import org.recompile.freej2me.lite.R;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import org.recompile.freej2me.protocol.NativeScreen;

/** Android-only screen controller; no MIDlet classes or Canvas rendering here. */
public final class NativeLcdUiView extends LinearLayout {
    public interface Events { void send(NativeScreen.Event event); }
    private final Events events;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView title, ticker;
    private final FrameLayout content;
    private final NativeCommandBar commandBar;
    private final Runnable flush = () -> dispatch(NativeScreen.Event.CHANGE, 0);
    private NativeScreen shown;
    private ListView list;
    private NativeChoiceAdapter adapter;
    private NativeFormContent form;
    private NativeAlertContent alert;
    private long sequence;
    private boolean dirty;
    private boolean contextMenuOpen;
    private int focusedRow, focusedItem;

    public NativeLcdUiView(Context context, Events events) {
        super(new ContextThemeWrapper(context, R.style.NativeLcdUiTheme));
        this.events = events;
        setOrientation(VERTICAL);
        setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
        title = new TextView(getContext());
        title.setLineSpacing(NativeUiStyle.dp(getContext(),
                NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setSingleLine(true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(NativeUiStyle.primaryText(getContext()));
        title.setPadding(dp(12), 0, dp(12), 0);
        title.setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
        addView(title, new LayoutParams(-1, dp(NativeUiStyle.TITLE_BAR_HEIGHT_DP)));
        ticker = new TextView(getContext());
        ticker.setLineSpacing(NativeUiStyle.dp(getContext(),
                NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
        ticker.setTextSize(13);
        ticker.setIncludeFontPadding(false);
        ticker.setTextColor(NativeUiStyle.secondaryText(getContext()));
        ticker.setPadding(dp(12), dp(2), dp(12), dp(4));
        ticker.setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
        addView(ticker);
        View titleDivider = new View(getContext());
        titleDivider.setBackgroundColor(NativeUiStyle.DIVIDER);
        LayoutParams titleDividerParams = new LayoutParams(-1, dp(1));
        titleDividerParams.leftMargin = dp(NativeUiStyle.TITLE_DIVIDER_INSET_DP);
        titleDividerParams.rightMargin = dp(NativeUiStyle.TITLE_DIVIDER_INSET_DP);
        addView(titleDivider, titleDividerParams);
        content = new FrameLayout(getContext());
        content.setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
        addView(content, new LayoutParams(-1, 0, 1));
        commandBar = new NativeCommandBar(getContext(),
                id -> dispatch(NativeScreen.Event.COMMAND, id));
        addView(commandBar, new LayoutParams(-1, -2));
    }
    private int dp(int value) { return NativeChoiceAdapter.dp(getContext(), value); }
    private void finishLongPress(int rowId) {
        contextMenuOpen = false;
        if (adapter != null) {
            adapter.setContextMenuRow(NativeChoiceAdapter.NO_CONTEXT_MENU_ROW);
        }
        if (shown == null || list == null || adapter == null
                || shown.kind != NativeScreen.LIST
                || shown.choiceType != NativeScreen.IMPLICIT) return;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).id != rowId) continue;
            list.setItemChecked(i, false);
            list.clearChoices();
            list.invalidateViews();
            dispatch(NativeScreen.Event.CHANGE, 0);
            return;
        }
    }
    private void changed() {
        dirty = true; handler.removeCallbacks(flush); handler.postDelayed(flush, 100);
    }
    public void show(NativeScreen screen) {
        if (shown != null && (screen.epoch < shown.epoch
                || screen.epoch == shown.epoch && screen.revision <= shown.revision)) return;
        boolean same = shown != null && shown.epoch == screen.epoch && shown.kind == screen.kind;
        boolean preserve = same && (dirty || screen.acknowledgedSequence < sequence);
        if (!same) {
            handler.removeCallbacks(flush); dirty = false; sequence = 0;
            contextMenuOpen = false;
            content.removeAllViews(); list = null; adapter = null; form = null; alert = null;
            focusedRow = screen.focusId; focusedItem = 0;
        }
        if (preserve && screen.kind == NativeScreen.LIST) {
            for (NativeScreen.Entry row : screen.items)
                for (NativeScreen.Entry old : shown.items) if (row.id == old.id) row.selected = old.selected;
        }
        shown = screen;
        title.setText(screen.title);
        ticker.setText(screen.ticker); ticker.setVisibility(screen.ticker.isEmpty() ? GONE : VISIBLE);
        if (screen.kind == NativeScreen.LIST) {
            if (list == null) {
                list = new ListView(getContext());
                list.setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);
                list.setPadding(dp(12), dp(8), dp(12), dp(8));
                list.setClipToPadding(false);
                list.setDivider(new ColorDrawable(Color.TRANSPARENT));
                list.setDividerHeight(dp(4));
                list.setSelector(NativeUiStyle.commandMenuRow(getContext()));
                list.setChoiceMode(screen.choiceType == NativeScreen.IMPLICIT
                        ? AbsListView.CHOICE_MODE_SINGLE : AbsListView.CHOICE_MODE_NONE);
                adapter = new NativeChoiceAdapter(getContext(), screen.choiceType, screen.items);
                list.setAdapter(adapter);
                list.setOnItemClickListener((parent, view, position, id) -> {
                    adapter.choose(position); focusedRow = adapter.getItem(position).id;
                    if (shown.choiceType == NativeScreen.IMPLICIT) {
                        list.clearChoices();
                        dispatch(NativeScreen.Event.SELECT, focusedRow);
                    } else changed();
                });
                list.setOnItemLongClickListener((parent, view, position, id) -> {
                    if (shown == null || position < 0 || position >= adapter.getCount()) return false;
                    NativeScreen.Entry row = adapter.getItem(position);
                    if (!row.longPressEnabled || shown.choiceType != NativeScreen.IMPLICIT) return false;
                    handler.removeCallbacks(flush);
                    dirty = false;
                    contextMenuOpen = true;
                    focusedRow = row.id;
                    adapter.choose(position);
                    adapter.setContextMenuRow(row.id);
                    list.setItemChecked(position, true);
                    list.setSelection(position);
                    boolean opened = commandBar.showLongPressMenu(() -> finishLongPress(row.id));
                    if (!opened) finishLongPress(row.id);
                    return true;
                });
                list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(AdapterView<?> parent) { }
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (shown == null || position < 0 || position >= adapter.getCount()) return;
                        int rowId = adapter.getItem(position).id;
                        if (focusedRow != rowId) {
                            focusedRow = rowId;
                            if (contextMenuOpen) return;
                            if (shown.choiceType == NativeScreen.IMPLICIT) {
                                adapter.choose(position); changed();
                            }
                        }
                    }
                });
                content.addView(list, new FrameLayout.LayoutParams(-1, -1));
            } else adapter.replace(screen.items);
            if (!same || !preserve && screen.choiceType != NativeScreen.MULTIPLE && focusedRow != screen.focusId) {
                focusedRow = screen.focusId;
                for (int i = 0; i < screen.items.size(); i++) if (screen.items.get(i).id == screen.focusId) list.setSelection(i);
            }
        } else if (screen.kind == NativeScreen.ALERT) {
            if (alert == null) {
                alert = new NativeAlertContent(getContext());
                content.addView(alert, new FrameLayout.LayoutParams(-1, -1));
            }
            alert.update(screen);
        } else {
            if (form == null) {
                form = new NativeFormContent(getContext(), this::changed,
                        id -> dispatch(NativeScreen.Event.ITEM, id));
                content.addView(form, new FrameLayout.LayoutParams(-1, -1));
            }
            form.update(screen.items, preserve);
            if (focusedItem != screen.focusId) { form.focus(screen.focusId); focusedItem = screen.focusId; }
        }
        commands(screen.commands);
    }
    private void commands(List<NativeScreen.Action> commands) {
        List<NativeScreen.Action> sorted = new ArrayList<>(commands);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.priority, b.priority));
        List<NativeCommandBar.Action> actions = new ArrayList<>(sorted.size());
        for (NativeScreen.Action command : sorted) {
            actions.add(new NativeCommandBar.Action(
                    command.id, command.type, command.priority, command.label));
        }
        commandBar.setActions(actions);
    }
    private void dispatch(int action, int target) {
        handler.removeCallbacks(flush);
        if (shown == null) return;
        NativeScreen.Event event = new NativeScreen.Event();
        event.epoch = shown.epoch; event.sequence = ++sequence; event.action = action; event.target = target;
        if (list != null) {
            for (NativeScreen.Entry row : shown.items) {
                NativeScreen.State state = new NativeScreen.State();
                state.id = row.id; state.value = row.selected ? 1 : 0; event.states.add(state);
            }
        } else if (form != null) event.states.addAll(form.states());
        dirty = false;
        events.send(event);
    }
    public void back() { dispatch(NativeScreen.Event.BACK, 0); }
    public void clear() {
        handler.removeCallbacks(flush); dirty = false; shown = null;
        content.removeAllViews(); list = null; adapter = null; form = null; alert = null;
        InputMethodManager keyboard = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
    }
}
