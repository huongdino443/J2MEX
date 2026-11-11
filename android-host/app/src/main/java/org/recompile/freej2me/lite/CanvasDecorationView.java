package org.recompile.freej2me.lite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Draws optional rounded canvas corners and an outline without touching game pixels. */
final class CanvasDecorationView extends View {
    private static final int BACKGROUND = Color.rgb(14, 24, 38);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF destination = new RectF();
    private final Path outsidePath = new Path();
    private volatile int sourceWidth;
    private volatile int sourceHeight;
    private volatile int scalePercent = 100;
    private volatile int position;
    private volatile boolean enabled = true;

    CanvasDecorationView(Context context) {
        super(context);
        maskPaint.setColor(BACKGROUND);
        borderPaint.setColor(Color.BLACK);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setSourceSize(int width, int height) {
        if (sourceWidth == width && sourceHeight == height) return;
        sourceWidth = Math.max(0, width);
        sourceHeight = Math.max(0, height);
        postInvalidateOnAnimation();
    }

    void setCanvasPresentation(int scale, int canvasPosition) {
        scalePercent = Math.max(50, Math.min(100, scale));
        position = Math.max(0, Math.min(2, canvasPosition));
        invalidate();
    }

    void setOutlineEnabled(boolean value) {
        enabled = value;
        setVisibility(value ? View.VISIBLE : View.GONE);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = sourceWidth;
        int height = sourceHeight;
        int viewportWidth = getWidth();
        int viewportHeight = getHeight();
        if (!enabled || width <= 0 || height <= 0
                || viewportWidth <= 0 || viewportHeight <= 0) return;

        float scale = Math.min(viewportWidth / (float) width,
                viewportHeight / (float) height) * scalePercent / 100f;
        float canvasWidth = width * scale;
        float canvasHeight = height * scale;
        float top = position == 2 ? viewportHeight - canvasHeight
                : position == 1 ? (viewportHeight - canvasHeight) / 2f : 0f;
        destination.set((viewportWidth - canvasWidth) / 2f, top,
                (viewportWidth + canvasWidth) / 2f, top + canvasHeight);

        float radius = Math.min(dp(10), Math.min(destination.width(),
                destination.height()) / 2f);
        outsidePath.reset();
        outsidePath.setFillType(Path.FillType.EVEN_ODD);
        outsidePath.addRect(0, 0, viewportWidth, viewportHeight, Path.Direction.CW);
        outsidePath.addRoundRect(destination, radius, radius, Path.Direction.CW);
        canvas.drawPath(outsidePath, maskPaint);

        float inset = borderPaint.getStrokeWidth() / 2f;
        RectF border = new RectF(destination);
        border.inset(inset, inset);
        canvas.drawRoundRect(border, Math.max(0, radius - inset),
                Math.max(0, radius - inset), borderPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
