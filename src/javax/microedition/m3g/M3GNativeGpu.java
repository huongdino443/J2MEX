package javax.microedition.m3g;

/** Optional Android GLES backend registered by the embedded native host. */
final class M3GNativeGpu {
    static final int STATE_INT_COUNT = 17;
    static final int PARAMETER_FLOAT_COUNT = 46;
    static final int PROFILE_STAT_COUNT = 27;
    private static boolean backendReported;
    private M3GNativeGpu() { }

    static boolean isAvailable() {
        try { return nativeAvailable(); }
        catch (LinkageError unavailable) { return false; }
    }

    static boolean beginFrame(int width, int height, int[] initialPixels) {
        boolean started;
        try { started = nativeBeginFrame(width, height, initialPixels); }
        catch (LinkageError unavailable) { started = false; }
        if (!backendReported) {
            backendReported = true;
            System.out.println(started
                    ? "[M3G] Experimental GLES2 renderer active"
                    : "[M3G] GLES2 unavailable; using Java renderer");
        }
        return started;
    }

    static boolean clear(int color, boolean clearColor, boolean clearDepth) {
        try { return nativeClear(color, clearColor, clearDepth); }
        catch (LinkageError unavailable) { return false; }
    }

    static boolean draw(int[] vertices, int vertexWordCount, short[] indices, int indexCount,
            int textureId, int[] texturePixels, int[] state, float[] parameters,
            boolean profileEnabled) {
        try { return nativeDraw(vertices, vertexWordCount, indices, indexCount, textureId,
                texturePixels, state, parameters, profileEnabled); }
        catch (LinkageError unavailable) { return false; }
    }

    static boolean takeProfileStats(long[] stats) {
        try { return nativeTakeProfileStats(stats); }
        catch (LinkageError unavailable) { return false; }
    }

    static boolean readFrame(int[] pixels) {
        try { return nativeReadFrame(pixels); }
        catch (LinkageError unavailable) { return false; }
    }

    static void endFrame() {
        try { nativeEndFrame(); }
        catch (LinkageError ignored) { }
    }

    private static native boolean nativeBeginFrame(int width, int height, int[] initialPixels);
    private static native boolean nativeClear(int color, boolean clearColor, boolean clearDepth);
    private static native boolean nativeDraw(int[] vertices, int vertexWordCount,
            short[] indices, int indexCount, int textureId, int[] texturePixels,
            int[] state, float[] parameters, boolean profileEnabled);
    private static native boolean nativeTakeProfileStats(long[] stats);
    private static native boolean nativeReadFrame(int[] pixels);
    private static native void nativeEndFrame();
    private static native boolean nativeAvailable();
}
