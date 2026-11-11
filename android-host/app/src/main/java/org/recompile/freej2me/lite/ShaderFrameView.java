package org.recompile.freej2me.lite;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLES2 two-pass presentation for the optional PhoneME Turbo CRT Grid filter. */
final class ShaderFrameView extends GLSurfaceView implements FramePresentation {
    private static final String TAG = "J2MEX-CRT";
    private static final float BACKGROUND_RED = 14f / 255f;
    private static final float BACKGROUND_GREEN = 24f / 255f;
    private static final float BACKGROUND_BLUE = 38f / 255f;
    private final FrameView.Pointer pointer;
    private final RendererImpl renderer;
    private volatile int sourceWidth;
    private volatile int sourceHeight;
    private volatile int canvasScalePercent = 100;
    private volatile int canvasPosition;
    private volatile boolean pointerInputEnabled = true;
    private boolean touching;

    ShaderFrameView(Context context, FrameView.Pointer pointer, int refreshRateHz,
            int initialSourceWidth, int initialSourceHeight) {
        super(context);
        this.pointer = pointer;
        sourceWidth = Math.max(1, initialSourceWidth);
        sourceHeight = Math.max(1, initialSourceHeight);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new RendererImpl(this);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setRefreshRateHz(refreshRateHz);
        setContentDescription("Màn hình MIDlet, bộ lọc CRT Grid đang bật");
    }

    @Override public void setPointerInputEnabled(boolean enabled) {
        pointerInputEnabled = enabled;
        if (!enabled) touching = false;
        setContentDescription(enabled
                ? "Màn hình MIDlet, hỗ trợ cảm ứng, bộ lọc CRT Grid"
                : "Màn hình MIDlet, không nhận sự kiện chạm, bộ lọc CRT Grid");
    }

    @Override public void setRefreshRateHz(int refreshRateHz) {
        renderer.setRefreshRateHz(refreshRateHz);
    }

    @Override public void setCanvasPresentation(int scalePercent, int position) {
        canvasScalePercent = Math.max(50, Math.min(100, scalePercent));
        canvasPosition = Math.max(0, Math.min(2, position));
        requestRender();
    }
    @Override public boolean getCanvasBounds(RectF output) {
        calculateDestination(getWidth(), getHeight(), sourceWidth, sourceHeight,
                canvasScalePercent, canvasPosition, output);
        return !output.isEmpty();
    }

    @Override public void frame(int width, int height, int[] pixels) {
        if (width < 1 || height < 1 || pixels == null
                || (long)width * height > pixels.length) return;
        sourceWidth = width;
        sourceHeight = height;
        renderer.submitFrame(width, height, pixels);
        requestRender();
    }

    @Override public String drainFrameStats() {
        return renderer.drainFrameStats();
    }

    @Override public synchronized boolean onTouchEvent(MotionEvent event) {
        if (!pointerInputEnabled) {
            touching = false;
            return true;
        }
        RectF destination = destinationForTouch();
        if (sourceWidth <= 0 || sourceHeight <= 0 || destination.isEmpty()) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN)
            touching = destination.contains(event.getX(), event.getY());
        if (!touching) return true;
        int x = Math.max(0, Math.min(sourceWidth - 1,
                (int)((event.getX() - destination.left) * sourceWidth / destination.width())));
        int y = Math.max(0, Math.min(sourceHeight - 1,
                (int)((event.getY() - destination.top) * sourceHeight / destination.height())));
        if (action == MotionEvent.ACTION_DOWN) pointer.send(3, x, y);
        else if (action == MotionEvent.ACTION_MOVE) pointer.send(4, x, y);
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pointer.send(5, x, y);
            touching = false;
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private RectF destinationForTouch() {
        RectF result = new RectF();
        calculateDestination(getWidth(), getHeight(), sourceWidth, sourceHeight,
                canvasScalePercent, canvasPosition, result);
        return result;
    }

    private static void calculateDestination(int viewportWidth, int viewportHeight,
            int sourceWidth, int sourceHeight, int scalePercent, int position, RectF output) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            output.setEmpty();
            return;
        }
        float scale = Math.min(viewportWidth / (float)sourceWidth,
                viewportHeight / (float)sourceHeight) * scalePercent / 100f;
        float width = sourceWidth * scale;
        float height = sourceHeight * scale;
        float top = position == 2 ? viewportHeight - height
                : position == 1 ? (viewportHeight - height) / 2f : 0f;
        output.set((viewportWidth - width) / 2f, top,
                (viewportWidth + width) / 2f, top + height);
    }

    private static final class RendererImpl implements GLSurfaceView.Renderer {
        private static final int GL_FRAMEBUFFER = 36160;
        private static final int GL_COLOR_ATTACHMENT0 = 36064;
        private static final int GL_FRAMEBUFFER_COMPLETE = 36053;
        private static final int GL_TEXTURE_2D = 3553;
        private static final int GL_RGB = 6407;
        private static final int GL_RGBA = 6408;
        private static final int GL_UNSIGNED_BYTE = 5121;
        private static final int GL_UNSIGNED_SHORT_5_6_5 = 33635;
        private static final int GL_TEXTURE0 = 33984;
        private static final int GL_COLOR_BUFFER_BIT = 16384;
        private static final int GL_VERTEX_SHADER = 35633;
        private static final int GL_FRAGMENT_SHADER = 35632;
        private static final int GL_COMPILE_STATUS = 35713;
        private static final int GL_LINK_STATUS = 35714;
        private static final int GL_UNPACK_ALIGNMENT = 3317;

        private static final float[] QUAD = {
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
        };
        private static final String FALLBACK_VERTEX =
                "#version 100\n"
                + "precision highp float;\n"
                + "attribute vec2 aPosition;\n"
                + "attribute vec2 aTexCoord;\n"
                + "uniform vec2 uOutputOffset;\n"
                + "uniform vec2 uOutputSize;\n"
                + "uniform vec2 uScreenSize;\n"
                + "varying vec2 vTexCoord;\n"
                + "varying vec2 vPixCoord;\n"
                + "void main(){\n"
                + "vTexCoord=aTexCoord;\n"
                + "vec2 range=uOutputSize/uScreenSize;\n"
                + "vec2 origin=uOutputOffset/uScreenSize*2.0;\n"
                + "vec2 pos=(aPosition*0.5+0.5)*range*2.0-1.0+origin;\n"
                + "gl_Position=vec4(pos,0.0,1.0);\n"
                + "vPixCoord=aTexCoord*uOutputSize;\n"
                + "}\n";
        private static final String FBO_PASSTHROUGH =
                "#version 100\n"
                + "precision mediump float;\n"
                + "uniform sampler2D uTexture;\n"
                + "varying vec2 vTexCoord;\n"
                + "varying vec2 vPixCoord;\n"
                + "void main(){gl_FragColor=texture2D(uTexture,"
                + "vec2(vTexCoord.x,1.0-vTexCoord.y));}\n";

        private final ShaderFrameView owner;
        private final Object frameLock = new Object();
        private final Object statsLock = new Object();
        private int[] pendingPixels = new int[0];
        private int[] sparePixels = new int[0];
        private int pendingWidth;
        private int pendingHeight;
        private boolean frameQueued;
        private boolean hasFrame;
        private int sourceWidth;
        private int sourceHeight;
        private int viewportWidth;
        private int viewportHeight;
        private int outputWidth;
        private int outputHeight;
        private int outputX;
        private int outputY;
        private int sourceTexture;
        private int upscaleTexture;
        private int upscaleFramebuffer;
        private int framebufferWidth;
        private int framebufferHeight;
        private boolean framebufferReady;
        private Program upscaleProgram;
        private Program lcdProgram;
        private Program directPassthroughProgram;
        private Program fboPassthroughProgram;
        private ShortBuffer uploadPixels;
        private long expectedIntervalNanos;
        private long lastDrawNanos;
        private long renderedFrames;
        private long intervalTotalNanos;
        private long intervalMaxNanos;
        private long drawTotalNanos;
        private long drawMaxNanos;

        RendererImpl(ShaderFrameView owner) {
            this.owner = owner;
            sourceWidth = owner.sourceWidth;
            sourceHeight = owner.sourceHeight;
            setRefreshRateHz(60);
        }

        void setRefreshRateHz(int refreshRateHz) {
            expectedIntervalNanos = 1_000_000_000L
                    / Math.max(30, Math.min(240, refreshRateHz));
        }

        void submitFrame(int width, int height, int[] pixels) {
            int count = width * height;
            synchronized (frameLock) {
                if (pendingPixels.length != count) {
                    pendingPixels = new int[count];
                    sparePixels = new int[count];
                }
                System.arraycopy(pixels, 0, pendingPixels, 0, count);
                pendingWidth = width;
                pendingHeight = height;
                frameQueued = true;
            }
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            sourceTexture = upscaleTexture = upscaleFramebuffer = 0;
            framebufferReady = false;
            upscaleProgram = createProgram("shaders/xbrz_bicubic.frag",
                    "shaders/passthrough.frag", "xBRZ pass");
            lcdProgram = createProgram("shaders/lcd_grid.frag",
                    null, "LCD Grid pass");
            fboPassthroughProgram = createProgramFromSource(FBO_PASSTHROUGH,
                    "FBO orientation fallback");
            if (lcdProgram == null) lcdProgram = fboPassthroughProgram;
            directPassthroughProgram = createProgram("shaders/passthrough.frag",
                    null, "direct fallback");
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            ensureSourceTexture();
            if (upscaleProgram == null || lcdProgram == null)
                Log.e(TAG, "CRT shaders unavailable; attempting pass-through rendering");
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewportWidth = width;
            viewportHeight = height;
            GLES20.glViewport(0, 0, width, height);
            updateOutputGeometry();
        }

        @Override public void onDrawFrame(GL10 gl) {
            long started = System.nanoTime();
            int[] frame = null;
            int newWidth = 0;
            int newHeight = 0;
            synchronized (frameLock) {
                if (frameQueued) {
                    frame = pendingPixels;
                    pendingPixels = sparePixels;
                    sparePixels = frame;
                    newWidth = pendingWidth;
                    newHeight = pendingHeight;
                    frameQueued = false;
                }
            }
            if (frame != null) {
                if (newWidth != sourceWidth || newHeight != sourceHeight) {
                    sourceWidth = newWidth;
                    sourceHeight = newHeight;
                    ensureSourceTexture();
                    updateOutputGeometry();
                }
                uploadFrame(frame, newWidth, newHeight);
                hasFrame = true;
            }
            if (!hasFrame || viewportWidth <= 0 || viewportHeight <= 0) {
                GLES20.glClearColor(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE, 1f);
                GLES20.glClear(GL_COLOR_BUFFER_BIT);
                recordDraw(started);
                return;
            }
            updateOutputGeometry();
            if (framebufferReady && upscaleProgram != null && lcdProgram != null) {
                GLES20.glBindFramebuffer(GL_FRAMEBUFFER, upscaleFramebuffer);
                GLES20.glViewport(0, 0, outputWidth, outputHeight);
                GLES20.glClearColor(0f, 0f, 0f, 1f);
                GLES20.glClear(GL_COLOR_BUFFER_BIT);
                drawProgram(upscaleProgram, sourceTexture, outputWidth, outputHeight,
                        0, 0, outputWidth, outputHeight, sourceWidth, sourceHeight);

                GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
                GLES20.glClearColor(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE, 1f);
                GLES20.glClear(GL_COLOR_BUFFER_BIT);
                drawProgram(lcdProgram, upscaleTexture, viewportWidth, viewportHeight,
                        outputX, outputY, outputWidth, outputHeight, sourceWidth, sourceHeight);
            } else if (directPassthroughProgram != null) {
                GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
                GLES20.glClearColor(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE, 1f);
                GLES20.glClear(GL_COLOR_BUFFER_BIT);
                drawProgram(directPassthroughProgram, sourceTexture,
                        viewportWidth, viewportHeight, outputX, outputY,
                        outputWidth, outputHeight, sourceWidth, sourceHeight);
            } else {
                GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
                GLES20.glClearColor(BACKGROUND_RED, BACKGROUND_GREEN, BACKGROUND_BLUE, 1f);
                GLES20.glClear(GL_COLOR_BUFFER_BIT);
                Log.e(TAG, "No usable GL program is available");
            }
            GLES20.glBindTexture(GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
            recordDraw(started);
        }

        String drainFrameStats() {
            synchronized (statsLock) {
                String result = String.format(Locale.US,
                        "glDraws=%d gapAvg/Max=%.2f/%.2fms >1.5vsync=%d drawCallAvg/Max=%.2f/%.2fms",
                        renderedFrames,
                        renderedFrames < 2 ? 0
                                : intervalTotalNanos / ((renderedFrames - 1) * 1_000_000.0),
                        intervalMaxNanos / 1_000_000.0,
                        renderedFrames < 2 ? 0 : countLateIntervals(),
                        renderedFrames == 0 ? 0
                                : drawTotalNanos / (renderedFrames * 1_000_000.0),
                        drawMaxNanos / 1_000_000.0);
                renderedFrames = intervalTotalNanos = intervalMaxNanos = 0;
                drawTotalNanos = drawMaxNanos = 0;
                lateIntervals = 0;
                return result;
            }
        }

        private long lateIntervals;

        private long countLateIntervals() {
            return lateIntervals;
        }

        private void recordDraw(long started) {
            long finished = System.nanoTime();
            synchronized (statsLock) {
                if (lastDrawNanos != 0) {
                    long interval = started - lastDrawNanos;
                    intervalTotalNanos += interval;
                    intervalMaxNanos = Math.max(intervalMaxNanos, interval);
                    if (interval > expectedIntervalNanos * 3 / 2) lateIntervals++;
                }
                lastDrawNanos = started;
                renderedFrames++;
                long cost = finished - started;
                drawTotalNanos += cost;
                drawMaxNanos = Math.max(drawMaxNanos, cost);
            }
        }

        private void ensureSourceTexture() {
            if (sourceWidth <= 0 || sourceHeight <= 0) return;
            if (sourceTexture != 0) {
                GLES20.glDeleteTextures(1, new int[]{sourceTexture}, 0);
                sourceTexture = 0;
            }
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            sourceTexture = ids[0];
            GLES20.glBindTexture(GL_TEXTURE_2D, sourceTexture);
            GLES20.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, sourceWidth, sourceHeight,
                    0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, null);
            setTextureParameters(GLES20.GL_NEAREST);
            GLES20.glBindTexture(GL_TEXTURE_2D, 0);
        }

        private void uploadFrame(int[] pixels, int width, int height) {
            int count = width * height;
            if (uploadPixels == null || uploadPixels.capacity() != count) {
                ByteBuffer bytes = ByteBuffer.allocateDirect(count * 2)
                        .order(ByteOrder.nativeOrder());
                uploadPixels = bytes.asShortBuffer();
            }
            uploadPixels.clear();
            for (int index = 0; index < count; index++) {
                int color = pixels[index];
                int red = (color >>> 16) & 0xff;
                int green = (color >>> 8) & 0xff;
                int blue = color & 0xff;
                uploadPixels.put((short)(((red >>> 3) << 11)
                        | ((green >>> 2) << 5) | (blue >>> 3)));
            }
            uploadPixels.position(0);
            GLES20.glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
            GLES20.glBindTexture(GL_TEXTURE_2D, sourceTexture);
            GLES20.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL_RGB, GL_UNSIGNED_SHORT_5_6_5, uploadPixels);
            GLES20.glBindTexture(GL_TEXTURE_2D, 0);
        }

        private void updateOutputGeometry() {
            if (viewportWidth <= 0 || viewportHeight <= 0) return;
            RectF destination = new RectF();
            calculateDestination(viewportWidth, viewportHeight, sourceWidth, sourceHeight,
                    owner.canvasScalePercent, owner.canvasPosition, destination);
            if (destination.isEmpty()) {
                outputWidth = outputHeight = 0;
                framebufferReady = false;
                return;
            }
            outputWidth = Math.max(1, Math.round(destination.width()));
            outputHeight = Math.max(1, Math.round(destination.height()));
            outputX = Math.round(destination.left);
            int top = Math.round(destination.top);
            outputY = viewportHeight - top - outputHeight;
            rebuildUpscaleFramebuffer();
        }

        private void rebuildUpscaleFramebuffer() {
            if (outputWidth <= 0 || outputHeight <= 0) {
                framebufferReady = false;
                return;
            }
            if (upscaleFramebuffer != 0 && framebufferWidth == outputWidth
                    && framebufferHeight == outputHeight) return;
            deleteFramebuffer();
            framebufferWidth = outputWidth;
            framebufferHeight = outputHeight;

            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            upscaleTexture = ids[0];
            GLES20.glBindTexture(GL_TEXTURE_2D, upscaleTexture);
            GLES20.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, outputWidth, outputHeight,
                    0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            setTextureParameters(GLES20.GL_LINEAR);

            GLES20.glGenFramebuffers(1, ids, 0);
            upscaleFramebuffer = ids[0];
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, upscaleFramebuffer);
            GLES20.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D, upscaleTexture, 0);
            int status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
            framebufferReady = status == GL_FRAMEBUFFER_COMPLETE;
            if (!framebufferReady) {
                Log.e(TAG, "Upscale framebuffer incomplete: 0x"
                        + Integer.toHexString(status));
                deleteFramebuffer();
            }
        }

        private void deleteFramebuffer() {
            if (upscaleFramebuffer != 0) {
                GLES20.glDeleteFramebuffers(1, new int[]{upscaleFramebuffer}, 0);
                upscaleFramebuffer = 0;
            }
            if (upscaleTexture != 0) {
                GLES20.glDeleteTextures(1, new int[]{upscaleTexture}, 0);
                upscaleTexture = 0;
            }
            framebufferReady = false;
        }

        private void setTextureParameters(int filter) {
            GLES20.glTexParameteri(GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
            GLES20.glTexParameteri(GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
            GLES20.glTexParameteri(GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
        }

        private void drawProgram(Program program, int texture, int screenWidth,
                int screenHeight, int offsetX, int offsetY, int width, int height,
                int inputWidth, int inputHeight) {
            GLES20.glUseProgram(program.id);
            GLES20.glActiveTexture(GL_TEXTURE0);
            GLES20.glBindTexture(GL_TEXTURE_2D, texture);
            if (program.texture >= 0) GLES20.glUniform1i(program.texture, 0);
            if (program.outputSize >= 0)
                GLES20.glUniform2f(program.outputSize, width, height);
            if (program.screenSize >= 0)
                GLES20.glUniform2f(program.screenSize, screenWidth, screenHeight);
            if (program.outputOffset >= 0)
                GLES20.glUniform2f(program.outputOffset, offsetX, offsetY);
            if (program.sourceSize >= 0)
                GLES20.glUniform2f(program.sourceSize, inputWidth, inputHeight);

            owner.quadBufferPosition(0);
            GLES20.glEnableVertexAttribArray(program.position);
            GLES20.glVertexAttribPointer(program.position, 2, GLES20.GL_FLOAT,
                    false, 16, owner.quadBuffer());
            owner.quadBufferPosition(2);
            GLES20.glEnableVertexAttribArray(program.texCoord);
            GLES20.glVertexAttribPointer(program.texCoord, 2, GLES20.GL_FLOAT,
                    false, 16, owner.quadBuffer());
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(program.position);
            GLES20.glDisableVertexAttribArray(program.texCoord);
            owner.quadBufferPosition(0);
        }

        private Program createProgram(String fragmentPath, String fallbackFragmentPath,
                String label) {
            String vertex = readShader("shaders/vertex.vert");
            String fragment = readShader(fragmentPath);
            if (fragment == null && fallbackFragmentPath != null)
                fragment = readShader(fallbackFragmentPath);
            Program result = vertex == null || fragment == null
                    ? null : linkProgram(vertex, fragment);
            if (result == null) {
                Log.w(TAG, label + " shader failed; retrying with fallback vertex");
                if (fragment != null) result = linkProgram(FALLBACK_VERTEX, fragment);
            }
            if (result == null && fallbackFragmentPath != null) {
                String fallback = readShader(fallbackFragmentPath);
                if (fallback != null) result = linkProgram(FALLBACK_VERTEX, fallback);
            }
            return result;
        }

        private Program createProgramFromSource(String fragment, String label) {
            String vertex = readShader("shaders/vertex.vert");
            Program result = vertex == null ? null : linkProgram(vertex, fragment);
            if (result == null) {
                Log.w(TAG, label + " using fallback vertex");
                result = linkProgram(FALLBACK_VERTEX, fragment);
            }
            return result;
        }

        private String readShader(String path) {
            StringBuilder source = new StringBuilder();
            try (InputStream input = owner.getContext().getAssets().open(path);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) source.append(line).append('\n');
                return source.toString();
            } catch (IOException error) {
                Log.e(TAG, "Cannot load shader " + path, error);
                return null;
            }
        }

        private Program linkProgram(String vertexSource, String fragmentSource) {
            int vertex = compileShader(GL_VERTEX_SHADER, vertexSource);
            if (vertex == 0) return null;
            int fragment = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
            if (fragment == 0) {
                GLES20.glDeleteShader(vertex);
                return null;
            }
            int id = GLES20.glCreateProgram();
            GLES20.glAttachShader(id, vertex);
            GLES20.glAttachShader(id, fragment);
            GLES20.glLinkProgram(id);
            int[] status = new int[1];
            GLES20.glGetProgramiv(id, GL_LINK_STATUS, status, 0);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (status[0] == 0) {
                Log.e(TAG, "GL program link failed: " + GLES20.glGetProgramInfoLog(id));
                GLES20.glDeleteProgram(id);
                return null;
            }
            return new Program(id);
        }

        private int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                Log.e(TAG, "GL shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }
    }

    private FloatBuffer quadBuffer;

    private FloatBuffer quadBuffer() {
        if (quadBuffer == null) {
            ByteBuffer bytes = ByteBuffer.allocateDirect(16 * 4)
                    .order(ByteOrder.nativeOrder());
            quadBuffer = bytes.asFloatBuffer();
            quadBuffer.put(RendererImpl.QUAD);
            quadBuffer.position(0);
        }
        return quadBuffer;
    }

    private void quadBufferPosition(int position) {
        quadBuffer().position(position);
    }

    private static final class Program {
        final int id;
        final int position;
        final int texCoord;
        final int texture;
        final int outputSize;
        final int screenSize;
        final int outputOffset;
        final int sourceSize;

        Program(int id) {
            this.id = id;
            position = GLES20.glGetAttribLocation(id, "aPosition");
            texCoord = GLES20.glGetAttribLocation(id, "aTexCoord");
            texture = GLES20.glGetUniformLocation(id, "uTexture");
            outputSize = GLES20.glGetUniformLocation(id, "uOutputSize");
            screenSize = GLES20.glGetUniformLocation(id, "uScreenSize");
            outputOffset = GLES20.glGetUniformLocation(id, "uOutputOffset");
            sourceSize = GLES20.glGetUniformLocation(id, "uSrcSize");
        }
    }

}
