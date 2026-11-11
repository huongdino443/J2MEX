package org.recompile.freej2me.lite.nativeui;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.recompile.freej2me.protocol.NativeScreen;

/** Full-screen native Alert content, centered between the title and command bar. */
final class NativeAlertContent extends FrameLayout {
    private final LinearLayout items;
    private final int maxItemsHeight;

    NativeAlertContent(Context context) {
        super(context);
        setBackgroundColor(NativeUiStyle.GROUPED_BACKGROUND);

        maxItemsHeight = Math.min(dp(520),
                context.getResources().getDisplayMetrics().heightPixels * 75 / 100);
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int cap = maxItemsHeight;
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    cap = Math.min(cap, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(24), dp(16), dp(24), dp(16));
        items = new LinearLayout(context);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setGravity(Gravity.CENTER_HORIZONTAL);
        items.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(items);
        FrameLayout.LayoutParams contentParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        contentParams.leftMargin = dp(8);
        contentParams.rightMargin = dp(8);
        addView(scroll, contentParams);
    }

    void update(NativeScreen screen) {
        items.removeAllViews();
        for (NativeScreen.Entry item : screen.items) {
            if (item.image != null && item.image.length > 0) {
                ImageView image = new ImageView(getContext());
                image.setImageBitmap(BitmapFactory.decodeByteArray(item.image, 0, item.image.length));
                image.setAdjustViewBounds(true);
                image.setMaxWidth(dp(280));
                image.setMaxHeight(dp(180));
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams imageParams =
                        new LinearLayout.LayoutParams(-1, -2);
                imageParams.bottomMargin = dp(18);
                items.addView(image, imageParams);
            }
            if (item.kind == NativeScreen.GAUGE) {
                ProgressBar progress = new ProgressBar(getContext(), null,
                        android.R.attr.progressBarStyleHorizontal);
                NativeUiStyle.styleProgress(getContext(), progress);
                progress.setIndeterminate(item.maximum <= 0);
                if (item.maximum > 0) {
                    progress.setMax(item.maximum);
                    progress.setProgress(item.value);
                }
                LinearLayout.LayoutParams progressParams =
                        new LinearLayout.LayoutParams(-1, dp(28));
                progressParams.leftMargin = dp(20);
                progressParams.rightMargin = dp(20);
                progressParams.topMargin = dp(12);
                progressParams.bottomMargin = dp(8);
                items.addView(progress, progressParams);
            } else if (item.text != null && !item.text.isEmpty()) {
                TextView message = new TextView(getContext());
                message.setText(item.text);
                message.setTextColor(NativeUiStyle.primaryText(getContext()));
                message.setTextSize(17);
                message.setLineSpacing(NativeUiStyle.dp(getContext(),
                        NativeUiStyle.TEXT_LINE_SPACING_DP), 1.0f);
                message.setGravity(Gravity.CENTER);
                message.setPadding(0, dp(10), 0, dp(10));
                LinearLayout.LayoutParams messageParams =
                        new LinearLayout.LayoutParams(-1, -2);
                messageParams.bottomMargin = dp(10);
                items.addView(message, messageParams);
            }
        }
    }

    private int dp(int value) {
        return NativeUiStyle.dp(getContext(), value);
    }
}
