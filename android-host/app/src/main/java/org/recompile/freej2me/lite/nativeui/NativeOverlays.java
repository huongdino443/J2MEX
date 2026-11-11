package org.recompile.freej2me.lite.nativeui;

import org.recompile.freej2me.lite.R;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import org.recompile.freej2me.protocol.NativeScreen;

/** Native command popup and Alert/progress presentation; never writes Canvas pixels. */
public final class NativeOverlays {
    public interface Events { void send(NativeScreen.Event event); }
    private final Context context;
    private final Events events;
    private NativeScreen shown;
    private PopupWindow popup;
    private ListView list;
    private int focus;
    private long sequence;
    private boolean programmatic;

    public NativeOverlays(Context context, Events events) {
        this.context = new ContextThemeWrapper(context, R.style.NativeLcdUiTheme);
        this.events = events;
    }
    private int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    private void bind(NativeScreen next) {
        if (shown == null || shown.epoch != next.epoch) sequence = next.acknowledgedSequence;
        else sequence = Math.max(sequence, next.acknowledgedSequence);
        shown = next;
    }
    public void showCommands(NativeScreen next, View anchor, int bottomInset) {
        boolean same = shown != null && shown.epoch == next.epoch && popup != null && popup.isShowing();
        if (!same) clear();
        bind(next);

        final ArrayList<NativeScreen.Action> actions = new ArrayList<>(next.commands);
        CharSequence[] labels = new CharSequence[actions.size()];
        for (int i = 0; i < actions.size(); i++) labels[i] = actions.get(i).label;
        android.graphics.Rect visibleFrame = new android.graphics.Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int leftInset = Math.max(dp(8), anchorLocation[0] - visibleFrame.left);
        int width = NativeUiStyle.menuWidth(
                context, labels, visibleFrame.width(), leftInset);
        int topInset = dp(8);
        int availableHeight = Math.max(0,
                visibleFrame.height() - Math.max(0, bottomInset) - topInset);
        int height = NativeUiStyle.menuHeight(context, actions.size(), availableHeight);

        if (!same) {
            focus = 0;
            list = new ListView(context);
            list.setBackground(NativeUiStyle.menuSurface(context));
            list.setClipToOutline(true);
            list.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            list.setPadding(dp(1), dp(1), dp(1), dp(1));
            list.setDivider(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            list.setDividerHeight(0);
            popup = new PopupWindow(list, width, height, false);
            popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            popup.setElevation(dp(8));
            popup.setOutsideTouchable(true);
            popup.setOnDismissListener(() -> { if (!programmatic) dispatch(NativeScreen.Event.BACK, 0); });
            list.setOnItemClickListener((parent, view, position, id) -> select(position));
        } else {
            popup.setWidth(width);
            popup.setHeight(height);
        }
        focus = Math.max(0, Math.min(focus, actions.size() - 1));
        list.setAdapter(new ArrayAdapter<NativeScreen.Action>(context, android.R.layout.simple_list_item_1, actions) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView view = (TextView)super.getView(position, recycled, parent);
                view.setText(actions.get(position).label);
                view.setTextColor(NativeUiStyle.menuPrimaryText(context));
                view.setTextSize(16);
                view.setSingleLine(true);
                view.setEllipsize(android.text.TextUtils.TruncateAt.END);
                view.setMinHeight(dp(NativeUiStyle.COMMAND_MENU_ROW_HEIGHT_DP));
                view.setPadding(dp(18), dp(10), dp(18), dp(10));
                view.setBackground(NativeUiStyle.commandMenuRow(context));
                view.setSelected(position == focus);
                return view;
            }
        });
        if (!same) popup.showAtLocation(anchor,
                Gravity.BOTTOM | Gravity.LEFT, leftInset, bottomInset);
        else popup.update(leftInset, bottomInset, width, height);
        list.setSelection(focus);
    }
    private void select(int position) {
        if (shown == null || position < 0 || position >= shown.commands.size()) return;
        int id = shown.commands.get(position).id;
        dispatch(NativeScreen.Event.COMMAND, id);
        closePopup();
    }
    public boolean key(int code, boolean down) {
        if (popup == null || !popup.isShowing()) return false;
        if (!down) return true;
        if (code == -1 || code == 50) focus--;
        else if (code == -2 || code == 56) focus++;
        else if (code == -6 || code == -5 || code == 53) { select(focus); return true; }
        else if (code == -7) { back(); return true; }
        else return true;
        int count = shown.commands.size();
        if (count > 0) focus = (focus + count) % count;
        ((BaseAdapter)list.getAdapter()).notifyDataSetChanged();
        list.setSelection(focus);
        return true;
    }
    public boolean back() {
        if (popup != null && popup.isShowing()) {
            dispatch(NativeScreen.Event.BACK, 0); closePopup(); return true;
        }
        return false;
    }
    private void dispatch(int action, int target) {
        if (shown == null) return;
        NativeScreen.Event event = new NativeScreen.Event();
        event.epoch = shown.epoch; event.sequence = ++sequence; event.action = action; event.target = target;
        events.send(event);
    }
    private void closePopup() {
        programmatic = true;
        if (popup != null) popup.dismiss();
        popup = null; list = null;
        programmatic = false;
    }
    public void clear() {
        closePopup();
        shown = null;
    }
}
