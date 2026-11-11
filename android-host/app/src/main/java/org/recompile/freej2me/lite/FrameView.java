package org.recompile.freej2me.lite;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

final class FrameView extends View implements FramePresentation {
    interface Pointer { void send(int action, int x, int y); }
    private Bitmap bitmap;
    private final Paint paint = new Paint();
    private final RectF destination = new RectF();
    private final Pointer pointer;
    private volatile long expectedFrameIntervalNanos;
    private volatile int canvasScalePercent = 100;
    private volatile int canvasPosition;
    private volatile boolean pointerInputEnabled = true;
    private boolean touching;
    private long lastDrawNanos;
    private long drawSamples, drawIntervalTotal, drawIntervalMax, missedIntervals;
    private long drawCostTotal, drawCostMax;
    FrameView(Context context, Pointer listener, int refreshRateHz) {
        super(context); pointer = listener;
        setRefreshRateHz(refreshRateHz);
        paint.setFilterBitmap(false); setContentDescription("Màn hình MIDlet, hỗ trợ cảm ứng");
    }
    @Override public void setPointerInputEnabled(boolean enabled) {
        pointerInputEnabled = enabled;
        if (!enabled) touching = false;
        setContentDescription(enabled ? "Màn hình MIDlet, hỗ trợ cảm ứng"
                : "Màn hình MIDlet, không nhận sự kiện chạm");
    }
    @Override public void setRefreshRateHz(int refreshRateHz) {
        expectedFrameIntervalNanos = 1_000_000_000L / Math.max(30, Math.min(240, refreshRateHz));
    }
    @Override public void setCanvasPresentation(int scalePercent, int position) {
        canvasScalePercent = Math.max(50, Math.min(100, scalePercent));
        canvasPosition = Math.max(0, Math.min(2, position));
        postInvalidateOnAnimation();
    }
    @Override public synchronized boolean getCanvasBounds(RectF output) {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) {
            output.setEmpty();
            return false;
        }
        updateDestination(bitmap.getWidth(), bitmap.getHeight());
        output.set(destination);
        return !destination.isEmpty();
    }
    @Override public synchronized void frame(int width, int height, int[] pixels) {
        if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
            if (bitmap != null) bitmap.recycle();
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        postInvalidateOnAnimation();
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        long drawStarted = System.nanoTime();
        if (lastDrawNanos != 0) {
            long interval = drawStarted - lastDrawNanos;
            drawIntervalTotal += interval;
            drawIntervalMax = Math.max(drawIntervalMax, interval);
            if (interval > expectedFrameIntervalNanos * 3 / 2) missedIntervals++;
            drawSamples++;
        }
        lastDrawNanos = drawStarted;
        super.onDraw(canvas); canvas.drawColor(Color.rgb(14, 24, 38));
        if (bitmap == null) return;
        updateDestination(bitmap.getWidth(), bitmap.getHeight());
        long drawCostStarted = System.nanoTime();
        canvas.drawBitmap(bitmap, null, destination, paint);
        long drawCost = System.nanoTime() - drawCostStarted;
        drawCostTotal += drawCost;
        drawCostMax = Math.max(drawCostMax, drawCost);
    }
    private void updateDestination(int sourceWidth, int sourceHeight) {
        float scale = Math.min(getWidth() / (float) sourceWidth,
                getHeight() / (float) sourceHeight) * canvasScalePercent / 100f;
        float width = sourceWidth * scale, height = sourceHeight * scale;
        float top = canvasPosition == 2 ? getHeight() - height
                : canvasPosition == 1 ? (getHeight() - height) / 2f : 0f;
        destination.set((getWidth() - width) / 2f, top,
                (getWidth() + width) / 2f, top + height);
    }
    @Override public synchronized String drainFrameStats() {
        String stats = String.format(Locale.US,
                "uiDraws=%d gapAvg/Max=%.2f/%.2fms >1.5vsync=%d drawCallAvg/Max=%.2f/%.2fms",
                drawSamples,
                drawSamples == 0 ? 0 : drawIntervalTotal / (drawSamples * 1_000_000.0),
                drawSamples == 0 ? 0 : drawIntervalMax / 1_000_000.0,
                missedIntervals,
                drawSamples == 0 ? 0 : drawCostTotal / (drawSamples * 1_000_000.0),
                drawSamples == 0 ? 0 : drawCostMax / 1_000_000.0);
        drawSamples = drawIntervalTotal = drawIntervalMax = missedIntervals = 0;
        drawCostTotal = drawCostMax = 0;
        return stats;
    }
    @Override public synchronized boolean onTouchEvent(MotionEvent event) {
        if (!pointerInputEnabled) { touching = false; return true; }
        if (bitmap == null || destination.width() == 0) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) touching = destination.contains(event.getX(), event.getY());
        if (!touching) return true;
        int x = Math.max(0, Math.min(bitmap.getWidth() - 1,
                (int) ((event.getX() - destination.left) * bitmap.getWidth() / destination.width())));
        int y = Math.max(0, Math.min(bitmap.getHeight() - 1,
                (int) ((event.getY() - destination.top) * bitmap.getHeight() / destination.height())));
        if (action == MotionEvent.ACTION_DOWN) pointer.send(3, x, y);
        else if (action == MotionEvent.ACTION_MOVE) pointer.send(4, x, y);
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pointer.send(5, x, y); touching = false; performClick();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
