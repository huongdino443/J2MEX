package org.recompile.freej2me.lite;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** CoreBridge's fixed-size glyph atlas, including the user-redrawn Vietnamese glyphs. */
final class CoreBridgeBitmapFont {
    private static final String BASE_ATLAS =
            "bitmap-font/atlas/turbo-corebridge-atlas.png";
    private static final String BASE_MAP =
            "bitmap-font/maps/turbo-corebridge-keymap.json";
    private static final String FONT_METRICS =
            "bitmap-font/maps/corebridge-runtime-font.json";

    private final Bitmap atlas;
    private final Map<Character, Glyph> glyphs;
    private final int glyphHeight;
    private final int ascent;
    private final int descent;
    private final int leading;

    private CoreBridgeBitmapFont(Bitmap atlas, Map<Character, Glyph> glyphs,
            int glyphHeight, int ascent, int descent, int leading) {
        this.atlas = atlas;
        this.glyphs = glyphs;
        this.glyphHeight = glyphHeight;
        this.ascent = ascent;
        this.descent = descent;
        this.leading = leading;
    }

    static CoreBridgeBitmapFont load(AssetManager assets) throws IOException {
        Bitmap source = decodeBitmap(assets, BASE_ATLAS);
        boolean keepSource = false;
        try {
            JSONObject keymap = new JSONObject(readAsset(assets, BASE_MAP));
            JSONObject metrics = new JSONObject(readAsset(assets, FONT_METRICS));
            int columns = keymap.getInt("columns");
            int cellWidth = keymap.getInt("cellWidth");
            int cellHeight = keymap.getInt("cellHeight");
            int atlasWidth = keymap.getInt("atlasWidth");
            int atlasHeight = keymap.getInt("atlasHeight");
            if (columns < 1 || cellWidth < 1 || cellHeight < 1
                    || atlasWidth != columns * cellWidth
                    || atlasHeight < 1 || atlasHeight % cellHeight != 0) {
                throw new IOException("Invalid CoreBridge bitmap-font atlas dimensions");
            }
            int rows = atlasHeight / cellHeight;
            if (source.getWidth() != atlasWidth || source.getHeight() != rows * cellHeight) {
                throw new IOException("CoreBridge bitmap-font atlas dimensions do not match its maps");
            }

            Map<Character, Glyph> glyphs = new HashMap<>();
            JSONArray entries = keymap.getJSONArray("entries");
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                String character = entry.getString("character");
                JSONObject cell = entry.getJSONObject("cell");
                if (character.length() != 1) continue;
                int glyphWidth = entry.getInt("glyphWidth");
                int advanceWidth = entry.getInt("advanceWidth");
                int x = cell.getInt("x");
                int y = cell.getInt("y");
                if (glyphWidth < 0 || glyphWidth > cellWidth || advanceWidth < 0
                        || x < 0 || y < 0 || x + glyphWidth > atlasWidth
                        || y + cellHeight > atlasHeight) {
                    throw new IOException("Invalid CoreBridge glyph metrics");
                }
                glyphs.put(character.charAt(0),
                        new Glyph(entry.getInt("index"), x, y, glyphWidth, advanceWidth));
            }

            keepSource = true;
            return new CoreBridgeBitmapFont(source, glyphs, cellHeight,
                    metrics.getInt("ascent"), metrics.getInt("descent"),
                    metrics.getInt("leading"));
        } catch (JSONException error) {
            throw new IOException("Invalid CoreBridge bitmap-font map", error);
        } finally {
            if (!keepSource && !source.isRecycled()) source.recycle();
        }
    }

    int measure(String text, int charCount, int style) {
        if (text == null || charCount <= 0) return 0;
        int count = Math.min(charCount, text.length());
        int width = 0;
        int boldExtra = (style & 1) != 0 ? 1 : 0;
        for (int index = 0; index < count; index++) {
            Glyph glyph = glyphs.get(text.charAt(index));
            if (glyph != null) width += glyph.advanceWidth + boldExtra;
        }
        return width;
    }

    int ascent() { return ascent; }
    int descent() { return descent; }
    int leading() { return leading; }
    int glyphHeight() { return glyphHeight; }

    void draw(Canvas canvas, String text, int style, Paint paint) {
        if (text == null || text.isEmpty()) return;
        int cursorX = 0;
        boolean bold = (style & 1) != 0;
        Rect source = new Rect();
        Rect destination = new Rect();
        for (int index = 0; index < text.length(); index++) {
            Glyph glyph = glyphs.get(text.charAt(index));
            if (glyph == null) continue;
            source.set(glyph.x, glyph.y, glyph.x + glyph.glyphWidth,
                    glyph.y + glyphHeight);
            destination.set(cursorX, 0, cursorX + glyph.glyphWidth, glyphHeight);
            canvas.drawBitmap(atlas, source, destination, paint);
            if (bold) {
                destination.offset(1, 0);
                canvas.drawBitmap(atlas, source, destination, paint);
                destination.offset(-1, 0);
            }
            cursorX += glyph.advanceWidth + (bold ? 1 : 0);
        }
    }

    private static Bitmap decodeBitmap(AssetManager assets, String path) throws IOException {
        try (InputStream input = assets.open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) throw new IOException("Cannot decode bitmap-font asset " + path);
            return bitmap;
        }
    }

    private static String readAsset(AssetManager assets, String path) throws IOException {
        try (InputStream input = assets.open(path);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Glyph {
        final int index;
        final int x;
        final int y;
        final int glyphWidth;
        final int advanceWidth;

        Glyph(int index, int x, int y, int glyphWidth, int advanceWidth) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.glyphWidth = glyphWidth;
            this.advanceWidth = advanceWidth;
        }
    }
}
