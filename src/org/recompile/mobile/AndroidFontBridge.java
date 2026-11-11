package org.recompile.mobile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronous bridge to Android Paint. Android performs drawing and measuring
 * with the same Typeface, including the device's own glyph fallback chain.
 */
public final class AndroidFontBridge {
    private static final int HANDSHAKE = 0x4A324D41;
    private static final int OP_METRICS = 1;
    private static final int OP_MEASURE = 2;
    private static final int OP_RENDER = 3;
    private static final int MAX_TEXT_BYTES = 1024 * 1024;
    private static final int MAX_RENDER_PIXELS = 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final long MAX_RENDER_CACHE_BYTES = 8L * 1024L * 1024L;

    private static final Map<String, Float> measuredWidths =
            new LinkedHashMap<String, Float>(64, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
                    return size() > 2048;
                }
            };
    private static final Map<String, FontMetrics> fontMetrics =
            new LinkedHashMap<String, FontMetrics>(32, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, FontMetrics> eldest) {
                    return size() > 256;
                }
            };
    private static final Map<String, RenderedText> renderedText =
            new LinkedHashMap<String, RenderedText>(32, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, RenderedText> eldest) {
                    return false;
                }
            };
    private static long renderedCacheBytes;

    private static Socket socket;
    private static DataInputStream input;
    private static DataOutputStream output;

    private AndroidFontBridge() { }

    public static synchronized void connect(int port, String token) throws IOException {
        if (port <= 0 || port > 65535)
            throw new IOException("Invalid Android font bridge port: " + port);
        Socket candidate = new Socket();
        candidate.setTcpNoDelay(true);
        candidate.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 15000);
        DataInputStream incoming = new DataInputStream(
                new BufferedInputStream(candidate.getInputStream(), 32768));
        DataOutputStream outgoing = new DataOutputStream(
                new BufferedOutputStream(candidate.getOutputStream(), 32768));
        outgoing.writeUTF(token == null ? "" : token);
        outgoing.flush();
        int handshake = incoming.readInt();
        if (handshake != HANDSHAKE) {
            candidate.close();
            throw new IOException("Android font bridge handshake mismatch");
        }
        socket = candidate;
        input = incoming;
        output = outgoing;
        System.out.println("Android Paint/Canvas font bridge connected");
    }

    public static synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public static synchronized void close() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        socket = null;
        input = null;
        output = null;
        measuredWidths.clear();
        fontMetrics.clear();
        renderedText.clear();
        renderedCacheBytes = 0;
    }

    public static synchronized FontMetrics metrics(int face, int style, int pointSize) {
        if (!isConnected()) return null;
        String key = face + ":" + (style & 7) + ":" + pointSize;
        FontMetrics cached = fontMetrics.get(key);
        if (cached != null) return cached;
        try {
            output.writeInt(OP_METRICS);
            writeFont(output, face, style, pointSize);
            output.flush();
            FontMetrics result = readMetrics(input);
            fontMetrics.put(key, result);
            FontInputDiagnostic.fontMetrics(face, style, pointSize, result.ascent,
                    result.descent, result.leading, result.ascentOffset);
            return result;
        } catch (IOException error) {
            throw bridgeFailure("font metrics", error);
        }
    }

    public static synchronized float measure(int face, int style, int pointSize, String text) {
        if (!isConnected()) return Float.NaN;
        if (text == null || text.isEmpty()) return 0f;
        String key = face + ":" + (style & 7) + ":" + pointSize + ":" + text;
        Float cached = measuredWidths.get(key);
        if (cached != null) {
            FontInputDiagnostic.fontText("measure-cache", face, style, pointSize,
                    text, cached.floatValue());
            return cached.floatValue();
        }
        try {
            output.writeInt(OP_MEASURE);
            writeFont(output, face, style, pointSize);
            writeText(output, text);
            output.writeInt(text.length());
            output.flush();
            float width = input.readFloat();
            if (!Float.isFinite(width) || width < 0f)
                throw new IOException("Android Paint returned an invalid text advance");
            FontInputDiagnostic.fontText("measure", face, style, pointSize, text, width);
            if (text.length() <= 4096) measuredWidths.put(key, Float.valueOf(width));
            return width;
        } catch (IOException error) {
            throw bridgeFailure("text measurement", error);
        }
    }

    public static synchronized RenderedText render(int face, int style, int pointSize, String text) {
        if (!isConnected()) return null;
        if (text == null || text.isEmpty()) return null;
        String key = face + ":" + (style & 7) + ":" + pointSize + ":" + text;
        RenderedText cached = renderedText.get(key);
        if (cached != null) {
            FontInputDiagnostic.fontText("render-cache", face, style, pointSize,
                    text, cached.advance);
            return cached;
        }
        try {
            output.writeInt(OP_RENDER);
            writeFont(output, face, style, pointSize);
            writeText(output, text);
            output.flush();
            float advance = input.readFloat();
            FontMetrics metrics = readMetrics(input);
            int left = input.readInt();
            int top = input.readInt();
            int width = input.readInt();
            int height = input.readInt();
            long pixelCount = (long)width * height;
            if (!Float.isFinite(advance) || advance < 0f || width < 0 || height < 0
                    || pixelCount > MAX_RENDER_PIXELS)
                throw new IOException("Android Paint returned invalid rendered text bounds");
            int[] pixels = new int[(int)pixelCount];
            for (int i = 0; i < pixels.length; i++) pixels[i] = input.readInt();
            RenderedText result = new RenderedText(advance, metrics, left, top, width, height, pixels);
            FontInputDiagnostic.fontText("render", face, style, pointSize, text, advance);
            FontInputDiagnostic.fontMetrics(face, style, pointSize, metrics.ascent,
                    metrics.descent, metrics.leading, metrics.ascentOffset);
            if (text.length() <= 4096) {
                measuredWidths.put(key, Float.valueOf(advance));
                cacheRenderedText(key, result);
            }
            return result;
        } catch (IOException error) {
            throw bridgeFailure("text rendering", error);
        }
    }

    private static void cacheRenderedText(String key, RenderedText text) {
        long bytes = (long)text.mask.length * 4L;
        if (bytes > MAX_RENDER_CACHE_BYTES) return;
        RenderedText previous = renderedText.remove(key);
        if (previous != null) renderedCacheBytes -= (long)previous.mask.length * 4L;
        Iterator<Map.Entry<String, RenderedText>> entries = renderedText.entrySet().iterator();
        while ((renderedCacheBytes + bytes > MAX_RENDER_CACHE_BYTES
                || renderedText.size() >= MAX_CACHE_ENTRIES) && entries.hasNext()) {
            Map.Entry<String, RenderedText> eldest = entries.next();
            renderedCacheBytes -= (long)eldest.getValue().mask.length * 4L;
            entries.remove();
        }
        renderedText.put(key, text);
        renderedCacheBytes += bytes;
    }

    private static void writeFont(DataOutputStream stream, int face, int style, int pointSize)
            throws IOException {
        stream.writeInt(face);
        stream.writeInt(style);
        stream.writeInt(pointSize);
    }

    private static void writeText(DataOutputStream stream, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IOException("MIDlet text exceeds 1 MiB");
        stream.writeInt(bytes.length);
        stream.write(bytes);
    }

    private static FontMetrics readMetrics(DataInputStream stream) throws IOException {
        return new FontMetrics(stream.readInt(), stream.readInt(), stream.readInt(), stream.readInt());
    }

    private static IllegalStateException bridgeFailure(String action, IOException error) {
        return new IllegalStateException("Android Paint/Canvas " + action + " failed", error);
    }

    public static final class FontMetrics {
        public final int ascent;
        public final int descent;
        public final int leading;
        public final int ascentOffset;

        FontMetrics(int ascent, int descent, int leading, int ascentOffset) {
            this.ascent = ascent;
            this.descent = descent;
            this.leading = leading;
            this.ascentOffset = ascentOffset;
        }

        public int height() { return ascent + descent + leading; }
    }

    public static final class RenderedText {
        public final float advance;
        public final FontMetrics metrics;
        public final int left;
        public final int top;
        public final int width;
        public final int height;
        public final int[] mask;

        RenderedText(float advance, FontMetrics metrics, int left, int top,
                int width, int height, int[] mask) {
            this.advance = advance;
            this.metrics = metrics;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.mask = mask;
        }
    }
}
