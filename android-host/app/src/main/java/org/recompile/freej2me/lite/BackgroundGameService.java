package org.recompile.freej2me.lite;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Keeps the active runtime process important while the user switches away from a game. */
public final class BackgroundGameService extends Service {
    private static final String CHANNEL_ID = "background_game";
    private static final int NOTIFICATION_ID = 32901;
    private PowerManager.WakeLock wakeLock;

    public static boolean start(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        Intent intent = new Intent(context, BackgroundGameService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException error) {
            android.util.Log.w("J2MEX", "Could not keep the game runtime in foreground", error);
            return false;
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, BackgroundGameService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            acquireWakeLock();
        } catch (RuntimeException error) {
            android.util.Log.e("J2MEX", "Could not start the background game service", error);
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                AppLocalization.text(this, "Hoạt động dưới nền"),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(AppLocalization.text(this,
                "Thông báo khi J2MEX hoạt động dưới nền."));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent returnIntent = new Intent(this, RuntimeActivity.class);
        returnIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID,
                returnIntent, pendingFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_stat_j2mex)
                .setContentTitle(AppLocalization.text(this, "J2MEX đang chạy nền"))
                .setContentText(AppLocalization.text(this, "Chạm để quay lại"))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager == null) return;
        // The lock is scoped to this foreground service and is released on stop or process death.
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "J2MEX:BackgroundGame");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    @Override public void onDestroy() {
        releaseWakeLock();
        stopForeground(true);
        super.onDestroy();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
