package org.recompile.freej2me.lite;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/** Shows a small down-arrow cue only while more vertical content is available. */
final class ScrollCueOverlay {
    private static final int COLOR_CUE_BACKGROUND = 0xFF2B3548;
    private static final int COLOR_CUE_ARROW = 0xFFB9C3FF;

    private ScrollCueOverlay() { }

    static void install(Activity activity, ScrollView scrollView, int bottomInsetDp) {
        if (scrollView == null || !(scrollView.getParent() instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) scrollView.getParent();
        int childIndex = parent.indexOfChild(scrollView);
        ViewGroup.LayoutParams parentParams = scrollView.getLayoutParams();
        parent.removeView(scrollView);

        FrameLayout container = new FrameLayout(activity);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        CueView cue = new CueView(activity);
        cue.setVisibility(View.GONE);
        cue.setAlpha(0f);
        cue.setElevation(dp(activity, 4));
        cue.setContentDescription("Cuộn xuống để xem thêm");
        int cueWidth = dp(activity, 42);
        int cueHeight = dp(activity, 34);
        FrameLayout.LayoutParams cueParams = new FrameLayout.LayoutParams(
                cueWidth, cueHeight, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cueParams.bottomMargin = dp(activity, Math.max(0, bottomInsetDp));
        container.addView(cue, cueParams);

        if (parentParams != null) parent.addView(container, childIndex, parentParams);
        else parent.addView(container, childIndex);

        final boolean[] cueVisible = {false};
        Runnable updateCue = () -> {
            boolean shouldShow = scrollView.getHeight() > 0
                    && scrollView.canScrollVertically(1);
            if (shouldShow == cueVisible[0]) return;
            cueVisible[0] = shouldShow;
            cue.animate().cancel();
            if (shouldShow) {
                cue.setVisibility(View.VISIBLE);
                cue.setAlpha(0f);
                cue.animate().alpha(1f).setDuration(140).start();
            } else {
                cue.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    if (!cueVisible[0]) cue.setVisibility(View.GONE);
                }).start();
            }
        };

        scrollView.getViewTreeObserver().addOnScrollChangedListener(updateCue::run);
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(updateCue::run);
        scrollView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.post(updateCue);
            }

            @Override public void onViewDetachedFromWindow(View view) { }
        });
        scrollView.post(updateCue);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class CueView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrowPath = new Path();
        private final RectF capsuleBounds = new RectF();

        CueView(Activity activity) {
            super(activity);
            backgroundPaint.setColor(COLOR_CUE_BACKGROUND);
            arrowPaint.setColor(COLOR_CUE_ARROW);
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(dp(activity, 2.5f));
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() * 0.5f;
            float centerY = getHeight() * 0.5f;
            capsuleBounds.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(capsuleBounds, getHeight() * 0.5f,
                    getHeight() * 0.5f, backgroundPaint);

            float shaftTop = centerY - getHeight() * 0.19f;
            float shaftBottom = centerY + getHeight() * 0.13f;
            float wing = getWidth() * 0.17f;
            arrowPath.reset();
            arrowPath.moveTo(centerX, shaftTop);
            arrowPath.lineTo(centerX, shaftBottom);
            arrowPath.moveTo(centerX - wing, centerY - getHeight() * 0.01f);
            arrowPath.lineTo(centerX, shaftBottom);
            arrowPath.lineTo(centerX + wing, centerY - getHeight() * 0.01f);
            canvas.drawPath(arrowPath, arrowPaint);
        }

        private static float dp(Activity activity, float value) {
            return value * activity.getResources().getDisplayMetrics().density;
        }
    }
}
