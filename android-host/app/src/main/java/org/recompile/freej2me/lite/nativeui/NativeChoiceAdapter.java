/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2018 Nikita Shakarun
 * Copyright 2019-2023 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 *
 * Modified for FreeJ2ME Lite: ported from J2ME Loader CompoundListAdapter /
 * CompoundAdapter. Wire entries replace MIDP objects; framework widgets replace
 * AndroidX; bounded native icons and host-side choice state.
 */
package org.recompile.freej2me.lite.nativeui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.util.List;
import org.recompile.freej2me.protocol.NativeScreen;

final class NativeChoiceAdapter extends BaseAdapter {
    static final int NO_CONTEXT_MENU_ROW = Integer.MIN_VALUE;
    private List<NativeScreen.Entry> items;
    private final int type;
    private final int viewResource;
    private int contextMenuRowId = NO_CONTEXT_MENU_ROW;
    private int focusedRowId = NO_CONTEXT_MENU_ROW;

    NativeChoiceAdapter(Context context, int type, List<NativeScreen.Entry> items) {
        this.type = type; this.items = items;
        switch (type) {
            case NativeScreen.IMPLICIT: viewResource = android.R.layout.simple_list_item_activated_1; break;
            case NativeScreen.EXCLUSIVE: viewResource = android.R.layout.simple_list_item_single_choice; break;
            case NativeScreen.MULTIPLE: viewResource = android.R.layout.simple_list_item_multiple_choice; break;
            default: throw new IllegalArgumentException("Unsupported list type " + type);
        }
    }
    void replace(List<NativeScreen.Entry> next) { items = next; notifyDataSetChanged(); }
    void choose(int position) {
        if (type == NativeScreen.MULTIPLE) items.get(position).selected = !items.get(position).selected;
        else for (int i = 0; i < items.size(); i++) items.get(i).selected = i == position;
        notifyDataSetChanged();
    }
    void setContextMenuRow(int rowId) {
        if (contextMenuRowId == rowId) return;
        contextMenuRowId = rowId;
        notifyDataSetChanged();
    }
    void setFocusedRow(int rowId) {
        if (focusedRowId == rowId) return;
        focusedRowId = rowId;
        notifyDataSetChanged();
    }
    @Override public int getCount() { return items.size(); }
    @Override public NativeScreen.Entry getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return getItem(position).id; }
    @Override public boolean hasStableIds() { return true; }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) convertView = LayoutInflater.from(parent.getContext()).inflate(viewResource, parent, false);
        TextView text = (TextView)convertView;
        NativeScreen.Entry item = getItem(position);
        String value = item.text == null ? "" : item.text;
        String[] lines = value.split("\\r\\n|\\n|\\r", -1);
        String title = lines.length == 0 ? value : lines[0].trim();
        StringBuilder subtitle = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(line);
        }
        boolean hasSubtitle = subtitle.length() > 0;
        if (hasSubtitle) {
            SpannableStringBuilder styled = new SpannableStringBuilder(title);
            styled.append('\n');
            int subtitleStart = styled.length();
            styled.append(subtitle);
            styled.setSpan(new RelativeSizeSpan(0.8f), subtitleStart, styled.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new ForegroundColorSpan(
                            NativeUiStyle.secondaryText(parent.getContext())),
                    subtitleStart, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(styled);
        } else {
            text.setText(value);
        }
        text.setTextSize(17);
        text.setSingleLine(!hasSubtitle);
        text.setMaxLines(hasSubtitle ? 2 : 1);
        text.setLineSpacing(hasSubtitle
                ? NativeUiStyle.dp(parent.getContext(), NativeUiStyle.TEXT_LINE_SPACING_DP)
                : 0, 1.0f);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int rowHeight = dp(parent.getContext(), hasSubtitle
                ? NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP + 12
                : NativeUiStyle.COMMAND_BUTTON_HEIGHT_DP);
        text.setMinHeight(rowHeight);
        ViewGroup.LayoutParams params = text.getLayoutParams();
        if (params != null) {
            params.height = rowHeight;
            text.setLayoutParams(params);
        }
        text.setPadding(dp(parent.getContext(), 12), dp(parent.getContext(), 8),
                dp(parent.getContext(), 12), dp(parent.getContext(), 8));
        text.setCompoundDrawablePadding(dp(parent.getContext(), 10));
        text.setCompoundDrawables(icon(parent.getContext(), item.image, 32), null, null, null);
        text.setTextColor(NativeUiStyle.primaryText(parent.getContext()));
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        if (item.id == focusedRowId || item.id == contextMenuRowId) {
            text.setBackground(NativeUiStyle.activeListRow(parent.getContext()));
        } else {
            text.setBackgroundColor(Color.TRANSPARENT);
        }
        if (type != NativeScreen.IMPLICIT) ((CheckedTextView)text).setChecked(item.selected);
        return text;
    }
    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static Bitmap bitmap(byte[] bytes, int maxSide) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > maxSide)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
    static Drawable icon(Context context, byte[] bytes, int size) {
        Bitmap bitmap = bitmap(bytes, Math.max(64, dp(context, size)));
        if (bitmap == null) return null;
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        int target = dp(context, size), side = Math.max(bitmap.getWidth(), bitmap.getHeight());
        drawable.setBounds(0, 0, Math.max(1, bitmap.getWidth() * target / side),
                Math.max(1, bitmap.getHeight() * target / side));
        return drawable;
    }
}
