package org.recompile.freej2me.lite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Stores one small, private, static preview for each active runtime session. */
final class RuntimeSessionPreviewStore {
    private static final String DIRECTORY = "runtime-canvas-previews";
    private static final int MAX_DECODE_EDGE = 1200;

    private RuntimeSessionPreviewStore() { }

    static File file(Context context, String sessionId) {
        if (!isSafeSessionId(sessionId)) return null;
        return new File(new File(context.getCacheDir(), DIRECTORY), sessionId + ".jpg");
    }

    static boolean save(Context context, String sessionId, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return false;
        File target = file(context, sessionId);
        if (target == null) return false;
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs()
                && !parent.isDirectory())) return false;
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output))
                throw new IOException("Could not encode preview image");
            output.flush();
            output.getFD().sync();
            atomic.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            if (output != null) atomic.failWrite(output);
            android.util.Log.w("J2MEX", "Could not write runtime preview", error);
            return false;
        } catch (OutOfMemoryError error) {
            if (output != null) atomic.failWrite(output);
            android.util.Log.w("J2MEX", "Not enough memory to encode runtime preview", error);
            return false;
        }
        return true;
    }

    static Bitmap load(Context context, String sessionId) {
        File source = file(context, sessionId);
        if (source == null || !source.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sampleSize = 1;
        while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize)
                > MAX_DECODE_EDGE) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        } catch (OutOfMemoryError error) {
            android.util.Log.w("J2MEX", "Could not decode runtime preview", error);
            return null;
        }
    }

    static void delete(Context context, String sessionId) {
        File source = file(context, sessionId);
        if (source != null && source.exists()) new AtomicFile(source).delete();
    }

    private static boolean isSafeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        for (int index = 0; index < sessionId.length(); index++) {
            char value = sessionId.charAt(index);
            if (!(value >= 'a' && value <= 'z')
                    && !(value >= 'A' && value <= 'Z')
                    && !(value >= '0' && value <= '9')
                    && value != '-') return false;
        }
        return true;
    }
}
