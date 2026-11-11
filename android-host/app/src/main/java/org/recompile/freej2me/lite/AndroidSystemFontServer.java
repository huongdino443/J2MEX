package org.recompile.freej2me.lite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.content.res.AssetManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Android-side Paint/Canvas endpoint for the embedded OpenJDK MIDP renderer. */
final class AndroidSystemFontServer implements Runnable {
    private static final int HANDSHAKE = 0x4A324D41;
    private static final int OP_METRICS = 1;
    private static final int OP_MEASURE = 2;
    private static final int OP_RENDER = 3;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_BITMAP_DIMENSION = 4096;
    private static final int TEXT_RASTER_Y_OFFSET_PX = 1;
    private static final int COREBRIDGE_BITMAP_TOP_OFFSET_PX = -12;

    private final ServerSocket server;
    private final String token;
    private final int fontScalePercent;
    private final CoreBridgeBitmapFont bitmapFont;

    AndroidSystemFontServer(ServerSocket server, String token, int fontScalePercent,
            boolean bitmapFontEnabled, AssetManager assets) throws IOException {
        this.server = server;
        this.token = token;
        this.fontScalePercent = fontScalePercent;
        bitmapFont = bitmapFontEnabled ? CoreBridgeBitmapFont.load(assets) : null;
    }

    @Override public void run() {
        try (Socket client = server.accept()) {
            client.setTcpNoDelay(true);
            DataInputStream input = new DataInputStream(
                    new BufferedInputStream(client.getInputStream(), 32768));
            DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(client.getOutputStream(), 32768));
            if (!token.equals(input.readUTF()))
                throw new IOException("Android font bridge authentication failed");
            output.writeInt(HANDSHAKE);
            output.flush();
            while (!server.isClosed() && !client.isClosed()) {
                int operation = input.readInt();
                int face = input.readInt();
                int style = input.readInt();
                int pointSize = input.readInt();
                Paint paint = createPaint(face, style, pointSize);
                if (operation == OP_METRICS) {
                    writeMetrics(output, paint, pointSize);
                } else if (operation == OP_MEASURE) {
                    String text = readText(input);
                    int charCount = input.readInt();
                    if (charCount < 0 || charCount > text.length())
                        throw new IOException("Invalid MIDP text character count");
                    output.writeFloat(bitmapFont == null
                            ? paint.measureText(text, 0, charCount)
                            : bitmapFont.measure(text, charCount, style));
                } else if (operation == OP_RENDER) {
                    String text = readText(input);
                    if (bitmapFont == null) writeRenderedText(output, paint, pointSize, text);
                    else writeBitmapRenderedText(output, paint, pointSize, style, text);
                } else {
                    throw new IOException("Unknown Android font request " + operation);
                }
                output.flush();
            }
        } catch (IOException error) {
            if (!server.isClosed())
                android.util.Log.e("FreeJ2ME", "Android Paint/Canvas font bridge stopped", error);
        } finally {
            try { server.close(); } catch (IOException ignored) { }
        }
    }

    private Paint createPaint(int face, int style, int pointSize) throws IOException {
        if (pointSize < 0 || pointSize > 512)
            throw new IOException("Invalid MIDP system font size " + pointSize);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextAlign(Paint.Align.LEFT);
        Typeface base;
        if (face == 32) base = Typeface.MONOSPACE;
        else if (face == 64) base = Typeface.SANS_SERIF;
        else base = Typeface.DEFAULT;
        int typefaceStyle = style & (Typeface.BOLD | Typeface.ITALIC);
        paint.setTypeface(Typeface.create(base, typefaceStyle));
        paint.setUnderlineText((style & 4) != 0);
        paint.setColor(Color.WHITE);
        paint.setColorFilter(null);
        paint.setTextSize(androidTextSize(paint, pointSize));
        return paint;
    }

    private float androidTextSize(Paint paint, int midpSize) {
        return (midpSize * (float)fontScalePercent) / 100f;
    }

    private void writeMetrics(DataOutputStream output, Paint paint, int pointSize)
            throws IOException {
        Paint.FontMetricsInt scaled = paint.getFontMetricsInt();
        int ascentOffset = ascentOffset(paint, pointSize);
        if (bitmapFont != null) {
            output.writeInt(bitmapFont.ascent());
            output.writeInt(bitmapFont.descent());
            output.writeInt(bitmapFont.leading());
            output.writeInt(ascentOffset);
            return;
        }
        output.writeInt(Math.abs(scaled.ascent));
        output.writeInt(scaled.descent);
        output.writeInt(scaled.leading);
        output.writeInt(ascentOffset);
    }

    private int ascentOffset(Paint paint, int pointSize) {
        paint.setTextSize(pointSize);
        int originalAscent = paint.getFontMetricsInt().ascent;
        paint.setTextSize(androidTextSize(paint, pointSize));
        return paint.getFontMetricsInt().ascent - originalAscent;
    }

    private void writeRenderedText(DataOutputStream output, Paint paint,
            int pointSize, String text) throws IOException {
        float advance = paint.measureText(text, 0, text.length());
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        Paint.FontMetricsInt scaled = paint.getFontMetricsInt();
        if (paint.isUnderlineText()) {
            bounds.left = Math.min(bounds.left, 0);
            bounds.right = Math.max(bounds.right, (int)Math.ceil(advance));
            bounds.bottom = Math.max(bounds.bottom, scaled.descent + 2);
        }
        final int padding = 2;
        int left = bounds.left - padding;
        int top = bounds.top - padding;
        int width = bounds.width() + padding * 2;
        int height = bounds.height() + padding * 2;

        paint.setTextSize(pointSize);
        int originalAscent = paint.getFontMetricsInt().ascent;
        paint.setTextSize(androidTextSize(paint, pointSize));
        int ascentOffset = paint.getFontMetricsInt().ascent - originalAscent;

        output.writeFloat(advance);
        output.writeInt(Math.abs(scaled.ascent));
        output.writeInt(scaled.descent);
        output.writeInt(scaled.leading);
        output.writeInt(ascentOffset);
        output.writeInt(left);
        output.writeInt(top);
        output.writeInt(width);
        output.writeInt(height);

        if (width == 0 || height == 0) return;
        if (width > MAX_BITMAP_DIMENSION || height > MAX_BITMAP_DIMENSION
                || (long)width * height > 1024L * 1024L)
            throw new IOException("Android text raster is too large");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawText(text, -left, -top + TEXT_RASTER_Y_OFFSET_PX, paint);
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int pixel : pixels) output.writeInt(pixel);
        } finally {
            bitmap.recycle();
        }
    }

    private void writeBitmapRenderedText(DataOutputStream output, Paint paint,
            int pointSize, int style, String text) throws IOException {
        int advance = bitmapFont.measure(text, text.length(), style);
        int width = advance;
        int height = width == 0 ? 0 : bitmapFont.glyphHeight();
        if (width > MAX_BITMAP_DIMENSION || height > MAX_BITMAP_DIMENSION
                || (long)width * height > 1024L * 1024L) {
            throw new IOException("CoreBridge bitmap text raster is too large");
        }
        output.writeFloat(advance);
        output.writeInt(bitmapFont.ascent());
        output.writeInt(bitmapFont.descent());
        output.writeInt(bitmapFont.leading());
        output.writeInt(ascentOffset(paint, pointSize));
        output.writeInt(0);
        output.writeInt(COREBRIDGE_BITMAP_TOP_OFFSET_PX);
        output.writeInt(width);
        output.writeInt(height);
        if (width == 0 || height == 0) return;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Paint glyphPaint = new Paint();
            glyphPaint.setFilterBitmap(false);
            glyphPaint.setDither(false);
            glyphPaint.setAntiAlias(false);
            glyphPaint.setColorFilter(new PorterDuffColorFilter(
                    Color.WHITE, PorterDuff.Mode.SRC_IN));
            bitmapFont.draw(new Canvas(bitmap), text, style, glyphPaint);
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int pixel : pixels) output.writeInt(pixel);
        } finally {
            bitmap.recycle();
        }
    }

    private static String readText(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES)
            throw new IOException("Invalid Android font text length");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
