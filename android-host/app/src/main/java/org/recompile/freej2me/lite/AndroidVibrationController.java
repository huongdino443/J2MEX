package org.recompile.freej2me.lite;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Sends game-requested vibration to Android's vibrator. */
final class AndroidVibrationController {
    private final Vibrator vibrator;
    private volatile boolean enabled;

    AndroidVibrationController(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    void setEnabled(boolean value) {
        enabled = value;
        if (!value) stop();
    }

    boolean isAvailable() {
        try {
            return vibrator != null && vibrator.hasVibrator();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    void vibrateForGame(long durationMillis, int strength) {
        if (!enabled || !isAvailable()) return;
        if (durationMillis == 0L || strength == 0) {
            stop();
            return;
        }
        try {
            int amplitude = toAmplitude(strength);
            if (durationMillis < 0L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{1000L}, new int[]{amplitude}, 0));
                } else {
                    vibrator.vibrate(new long[]{0L, 1000L}, 0);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, amplitude));
            } else {
                vibrator.vibrate(durationMillis);
            }
        } catch (RuntimeException ignored) {
            // Unsupported or restricted vibrator requests must not interrupt a game.
        }
    }

    void stop() {
        if (vibrator == null) return;
        try {
            vibrator.cancel();
        } catch (RuntimeException ignored) {
            // A vibration stop failure must not interrupt a game.
        }
    }

    private static int toAmplitude(int strength) {
        int bounded = Math.max(1, Math.min(100, strength));
        return Math.max(1, Math.min(255, bounded * 255 / 100));
    }
}
