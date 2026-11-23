package org.recompile.freej2me.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Draws original JAR artwork without smoothing pixel art, with a branded fallback. */
final class GameIconView extends View {
    private static final int SURFACE = Color.rgb(35, 43, 57);
    private static final int ACCENT = Color.rgb(185, 195, 255);
    private static final int TEXT = Color.rgb(232, 239, 247);
    private static final int ROUNDED_EDGE_ALPHA_THRESHOLD = 200;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private String title = "MIDlet";
    private boolean fullBleedSquare;
    private boolean sessionIconStyle;
    private float cornerRadiusDp = 14f;
    private int artworkSurface = SURFACE;

    GameIconView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setArtwork(Bitmap artwork, String gameTitle) {
        bitmap = artwork;
        title = gameTitle == null ? "MIDlet" : gameTitle;
        fullBleedSquare = artwork != null && !artwork.isRecycled()
                && hasOpaqueSquareEdges(artwork);
        artworkSurface = artwork == null || artwork.isRecycled()
                ? SURFACE : estimateArtworkColor(artwork);
        invalidate();
    }

    void setSessionArtwork(Bitmap artwork, String gameTitle) {
        sessionIconStyle = true;
        setArtwork(artwork, gameTitle);
    }

    void setCornerRadiusDp(float radiusDp) {
        cornerRadiusDp = Math.max(0, radiusDp);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(1);
        RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = sessionIconStyle
                ? Math.min(bounds.width(), bounds.height()) * 0.22f
                : dp(cornerRadiusDp);
        Path clip = new Path();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        paint.setColor(artworkSurface);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bounds, paint);
        if (bitmap != null && !bitmap.isRecycled()) {
            float scale;
            if (sessionIconStyle) {
                scale = Math.max(bounds.width() / bitmap.getWidth(),
                        bounds.height() / bitmap.getHeight());
            } else if (fullBleedSquare) {
                scale = Math.max(bounds.width() / bitmap.getWidth(),
                        bounds.height() / bitmap.getHeight());
            } else {
                float available = Math.min(bounds.width(), bounds.height()) - dp(14);
                scale = Math.min(available / bitmap.getWidth(),
                        available / bitmap.getHeight());
            }
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            RectF destination = new RectF(
                    bounds.centerX() - width / 2f,
                    bounds.centerY() - height / 2f,
                    bounds.centerX() + width / 2f,
                    bounds.centerY() + height / 2f);
            paint.setFilterBitmap(false);
            canvas.drawBitmap(bitmap, null, destination, paint);
        } else {
            drawFallback(canvas, bounds);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x55B9C3FF);
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }

    private boolean hasOpaqueSquareEdges(Bitmap image) {
        float aspect = image.getWidth() / (float) image.getHeight();
        if (aspect < 0.88f || aspect > 1.14f) return false;

        int samplesPerSide = 24;
        int opaqueSamples = 0;
        int totalSamples = samplesPerSide * 4;
        for (int i = 0; i < samplesPerSide; i++) {
            float position = 0.08f + 0.84f * (i + 0.5f) / samplesPerSide;
            int x = Math.min(image.getWidth() - 1,
                    Math.max(0, Math.round(position * (image.getWidth() - 1))));
            int y = Math.min(image.getHeight() - 1,
                    Math.max(0, Math.round(position * (image.getHeight() - 1))));
            if (Color.alpha(image.getPixel(x, 0)) >= 232) opaqueSamples++;
            if (Color.alpha(image.getPixel(x, image.getHeight() - 1)) >= 232) opaqueSamples++;
            if (Color.alpha(image.getPixel(0, y)) >= 232) opaqueSamples++;
            if (Color.alpha(image.getPixel(image.getWidth() - 1, y)) >= 232) opaqueSamples++;
        }
        return opaqueSamples >= Math.ceil(totalSamples * 0.92f)
                || hasRoundedSquareEdges(image, aspect);
    }

    private boolean hasRoundedSquareEdges(Bitmap image, float aspect) {
        if (aspect < 0.96f || aspect > 1.04f) return false;

        int width = image.getWidth();
        int height = image.getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int left = firstOpaquePixelInRow(image, centerY);
        int right = lastOpaquePixelInRow(image, centerY);
        int top = firstOpaquePixelInColumn(image, centerX);
        int bottom = lastOpaquePixelInColumn(image, centerX);
        if (left < 0 || right <= left || top < 0 || bottom <= top) return false;

        int artworkWidth = right - left + 1;
        int artworkHeight = bottom - top + 1;
        float artworkAspect = artworkWidth / (float) artworkHeight;
        if (artworkWidth / (float) width < 0.72f
                || artworkHeight / (float) height < 0.72f
                || artworkAspect < 0.92f || artworkAspect > 1.08f) {
            return false;
        }
        if (Color.alpha(image.getPixel(left, top)) >= 232
                || Color.alpha(image.getPixel(right, top)) >= 232
                || Color.alpha(image.getPixel(left, bottom)) >= 232
                || Color.alpha(image.getPixel(right, bottom)) >= 232) return false;

        int opaqueEdgeSamples = 0;
        int totalEdgeSamples = 0;
        for (int index = 0; index < 12; index++) {
            float position = 0.30f + 0.40f * (index + 0.5f) / 12f;
            int x = left + Math.round(position * (artworkWidth - 1));
            int y = top + Math.round(position * (artworkHeight - 1));
            if (Color.alpha(image.getPixel(x, top)) >= ROUNDED_EDGE_ALPHA_THRESHOLD)
                opaqueEdgeSamples++;
            if (Color.alpha(image.getPixel(x, bottom)) >= ROUNDED_EDGE_ALPHA_THRESHOLD)
                opaqueEdgeSamples++;
            if (Color.alpha(image.getPixel(left, y)) >= ROUNDED_EDGE_ALPHA_THRESHOLD)
                opaqueEdgeSamples++;
            if (Color.alpha(image.getPixel(right, y)) >= ROUNDED_EDGE_ALPHA_THRESHOLD)
                opaqueEdgeSamples++;
            totalEdgeSamples += 4;
        }
        return opaqueEdgeSamples >= Math.ceil(totalEdgeSamples * 0.92f);
    }

    private int firstOpaquePixelInRow(Bitmap image, int y) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (Color.alpha(image.getPixel(x, y)) >= 232) return x;
        }
        return -1;
    }

    private int lastOpaquePixelInRow(Bitmap image, int y) {
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            if (Color.alpha(image.getPixel(x, y)) >= 232) return x;
        }
        return -1;
    }

    private int firstOpaquePixelInColumn(Bitmap image, int x) {
        for (int y = 0; y < image.getHeight(); y++) {
            if (Color.alpha(image.getPixel(x, y)) >= 232) return y;
        }
        return -1;
    }

    private int lastOpaquePixelInColumn(Bitmap image, int x) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            if (Color.alpha(image.getPixel(x, y)) >= 232) return y;
        }
        return -1;
    }

    private int estimateArtworkColor(Bitmap image) {
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 48);
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int color = image.getPixel(x, y);
                int alpha = Color.alpha(color);
                if (alpha < 32) continue;
                red += (long) Color.red(color) * alpha;
                green += (long) Color.green(color) * alpha;
                blue += (long) Color.blue(color) * alpha;
                weight += alpha;
            }
        }
        if (weight == 0) return SURFACE;

        float artworkMix = 0.4f;
        int averageRed = (int) (red / weight);
        int averageGreen = (int) (green / weight);
        int averageBlue = (int) (blue / weight);
        return Color.rgb(
                Math.round(Color.red(SURFACE) * (1f - artworkMix) + averageRed * artworkMix),
                Math.round(Color.green(SURFACE) * (1f - artworkMix) + averageGreen * artworkMix),
                Math.round(Color.blue(SURFACE) * (1f - artworkMix) + averageBlue * artworkMix));
    }

    private void drawFallback(Canvas canvas, RectF bounds) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x1EB9C3FF);
        float unit = dp(8);
        float left = bounds.left + bounds.width() * 0.18f;
        float top = bounds.top + bounds.height() * 0.18f;
        canvas.drawRect(left, top, left + unit * 2, top + unit, paint);
        canvas.drawRect(left, top + unit * 1.5f, left + unit, top + unit * 3, paint);
        canvas.drawRect(bounds.right - unit * 3, bounds.bottom - unit * 2,
                bounds.right - unit, bounds.bottom - unit, paint);
        paint.setColor(TEXT);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        paint.setTextSize(Math.min(bounds.width(), bounds.height()) * 0.34f);
        String initial = title.trim().isEmpty() ? "J" : title.trim().substring(0, 1).toUpperCase();
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(initial, bounds.centerX(), baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
