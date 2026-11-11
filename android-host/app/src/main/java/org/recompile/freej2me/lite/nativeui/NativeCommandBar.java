package org.recompile.freej2me.lite.nativeui;

import org.recompile.freej2me.lite.AppLocalization;
import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import java.util.ArrayList;
import java.util.List;

/** phoneME-iOS-style left action / right soft-key command bar. */
public final class NativeCommandBar extends LinearLayout {
    public static final class Action {
        public final int id;
        public final int type;
        public final int priority;
        public final String label;

        public Action(int id, int type, int priority, String label) {
            this.id = id;
            this.type = type;
            this.priority = priority;
            this.label = label == null ? "" : label;
        }
    }

    public interface Listener {
        void onCommand(int id);
    }

    private final Listener listener;
    private final LinearLayout row;
    private final List<Action> contextActions = new ArrayList<>();
    private View contextAnchor;

    public NativeCommandBar(Context context, Listener listener) {
        this(context, listener, 16, NativeUiStyle.COMMAND_DIVIDER_INSET_DP);
    }

    public NativeCommandBar(Context context, Listener listener, int horizontalInset) {
        this(context, listener, horizontalInset, NativeUiStyle.COMMAND_DIVIDER_INSET_DP);
    }

    public NativeCommandBar(Context context, Listener listener, int horizontalInset,
            int dividerInset) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);

        View divider = new View(context);
        divider.setBackgroundColor(NativeUiStyle.DIVIDER);
        LayoutParams dividerParams = new LayoutParams(-1, dp(1));
        dividerParams.leftMargin = dp(dividerInset);
        dividerParams.rightMargin = dp(dividerInset);
        addView(divider, dividerParams);

        row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(horizontalInset), dp(10), dp(horizontalInset), dp(10));
        addView(row, new LayoutParams(-1, dp(72)));
        setVisibility(GONE);
    }

    public void setActions(List<Action> actions) {
        row.removeAllViews();
        contextActions.clear();
        contextAnchor = null;
        if (actions == null || actions.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        List<Action> right = new ArrayList<>();
        for (Action action : actions) {
            if (isNegativeSoftKey(action.type)) right.add(action);
        }
        Action rightAction = right.isEmpty() ? null : primaryRightAction(right);
        List<Action> left = new ArrayList<>(actions);
        if (rightAction != null) left.remove(rightAction);

        FrameLayout leftSlot = new FrameLayout(getContext());
        if (left.size() == 1) {
            Button direct = commandButton(left.get(0), true);
            contextAnchor = direct;
            leftSlot.addView(direct, buttonLayoutParams());
        } else if (left.size() > 0) {
            Button options = button(AppLocalization.text(getContext(), "Tùy chọn"));
            options.setContentDescription(AppLocalization.text(getContext(), "Tùy chọn")
                    + ", " + left.size() + " " + (AppLocalization.isEnglish(getContext())
                            ? "commands" : "lệnh"));
            NativeUiStyle.styleCommandButton(getContext(), options, true);
            options.setOnClickListener(view -> showMenu(options, left));
            contextAnchor = options;
            leftSlot.addView(options, buttonLayoutParams());
        }
        contextActions.addAll(left);

        FrameLayout rightSlot = new FrameLayout(getContext());
        if (rightAction != null) addButton(rightSlot, rightAction, false);

        row.addView(leftSlot, slotLayoutParams());
        View gap = new View(getContext());
        row.addView(gap, new LayoutParams(dp(10),
                dp(NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP)));
        row.addView(rightSlot, slotLayoutParams());
        setVisibility(VISIBLE);
    }

    private void addButton(FrameLayout slot, Action action, boolean prominent) {
        slot.addView(commandButton(action, prominent), buttonLayoutParams());
    }

    private Button commandButton(Action action, boolean prominent) {
        Button button = button(action.label);
        NativeUiStyle.styleCommandButton(getContext(), button, prominent);
        button.setOnClickListener(view -> listener.onCommand(action.id));
        return button;
    }

    private void showMenu(View anchor, List<Action> actions) {
        showMenu(anchor, actions, null);
    }

    public boolean showLongPressMenu(Runnable onDismiss) {
        if (contextAnchor == null || contextActions.isEmpty()) return false;
        showMenu(contextAnchor, contextActions, onDismiss);
        return true;
    }

    private void showMenu(View anchor, List<Action> actions, Runnable onDismiss) {
        int[] focus = {0};
        ListView menu = new ListView(getContext());
        menu.setBackground(NativeUiStyle.menuSurface(getContext()));
        menu.setClipToOutline(true);
        menu.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        menu.setPadding(dp(1), dp(1), dp(1), dp(1));
        menu.setDivider(new ColorDrawable(Color.TRANSPARENT));
        menu.setDividerHeight(0);
        menu.setSelector(new ColorDrawable(Color.TRANSPARENT));
        menu.setVerticalScrollBarEnabled(false);

        ArrayAdapter<Action> adapter = new ArrayAdapter<Action>(
                getContext(), android.R.layout.simple_list_item_1, actions) {
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                TextView item = (TextView)super.getView(position, recycled, parent);
                item.setText(actions.get(position).label);
                item.setTextColor(NativeUiStyle.menuPrimaryText(getContext()));
                item.setTextSize(16);
                item.setSingleLine(true);
                item.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.setMinHeight(dp(NativeUiStyle.COMMAND_MENU_ROW_HEIGHT_DP));
                item.setPadding(dp(18), dp(10), dp(18), dp(10));
                item.setBackground(NativeUiStyle.commandMenuRow(getContext()));
                item.setSelected(position == focus[0]);
                return item;
            }
        };
        menu.setAdapter(adapter);
        menu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (position >= 0 && focus[0] != position) {
                    focus[0] = position;
                    adapter.notifyDataSetChanged();
                }
            }
        });

        CharSequence[] labels = new CharSequence[actions.size()];
        for (int i = 0; i < actions.size(); i++) labels[i] = actions.get(i).label;
        Rect visibleFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int leftInset = Math.max(dp(8), anchorLocation[0] - visibleFrame.left);
        int width = NativeUiStyle.menuWidth(
                getContext(), labels, visibleFrame.width(), leftInset);
        int height = NativeUiStyle.menuHeight(getContext(), actions.size(),
                visibleFrame.height() - dp(16));
        PopupWindow popup = new PopupWindow(menu, width, height, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setClippingEnabled(true);
        if (onDismiss != null) popup.setOnDismissListener(() -> onDismiss.run());
        menu.setSelection(0);
        int horizontalOffset = visibleFrame.left + leftInset - anchorLocation[0];
        // The divider sits halfway between the menu and button: each side
        // has a 12dp gap after the 48dp button is vertically centered.
        popup.showAsDropDown(anchor, horizontalOffset,
                -height - anchor.getHeight()
                        - dp(NativeUiStyle.COMMAND_MENU_BUTTON_GAP_DP));
        menu.requestFocus();
        menu.setSelection(0);
        menu.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= actions.size()) return;
            Action selected = actions.get(position);
            popup.dismiss();
            listener.onCommand(selected.id);
        });
    }

    private Action primaryRightAction(List<Action> actions) {
        Action best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Action action : actions) {
            int rank = negativeSoftKeyRank(action.type);
            if (best == null || rank < bestRank
                    || rank == bestRank && action.priority < best.priority) {
                best = action;
                bestRank = rank;
            }
        }
        return best;
    }

    private boolean isNegativeSoftKey(int type) {
        return negativeSoftKeyRank(type) >= 0;
    }

    private int negativeSoftKeyRank(int type) {
        switch (type) {
            case 3: return 0; // CANCEL
            case 2: return 1; // BACK
            case 6: return 2; // STOP
            case 7: return 3; // EXIT
            default: return -1;
        }
    }

    private FrameLayout.LayoutParams buttonLayoutParams() {
        return new FrameLayout.LayoutParams(-1,
                dp(NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP), Gravity.CENTER);
    }

    private LayoutParams slotLayoutParams() {
        return new LayoutParams(0, dp(NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP), 1);
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        return button;
    }

    private int dp(int value) {
        return NativeUiStyle.dp(getContext(), value);
    }
}
