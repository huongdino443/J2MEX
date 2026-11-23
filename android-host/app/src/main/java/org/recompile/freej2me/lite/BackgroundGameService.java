package org.recompile.freej2me.lite;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.List;

/** Keeps the active runtime process important while the user switches away from a game. */
public class BackgroundGameService extends Service {
    private static final String CHANNEL_ID = "background_game";
    private static final String NOTIFICATION_GROUP = "j2mex_running_game_sessions";
    private static final int NOTIFICATION_ID_BASE = 32901;
    private static final int SUMMARY_NOTIFICATION_ID = 32900;
    private static volatile BackgroundGameService activeService;
    private static volatile boolean backgroundActivityEnabled = true;
    private volatile String sessionId;
    private volatile String sessionTitle;
    private volatile int sessionSlot;
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                updateWakeLock(true, "screen-off");
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                updateWakeLock(false, "screen-on");
            }
        }
    };
    private final BroadcastReceiver backgroundActivityReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !AppPreferences.ACTION_BACKGROUND_ACTIVITY_CHANGED
                    .equals(intent.getAction())) return;
            boolean enabled = intent.getBooleanExtra(
                    AppPreferences.EXTRA_KEEP_GAME_IN_BACKGROUND, false);
            updateBackgroundActivitySettingInProcess(context, enabled);
            if (!enabled) {
                stopForeground(true);
                stopSelf();
            }
        }
    };
    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;
    private boolean screenStateReceiverRegistered;
    private volatile boolean wakeLockHeld;

    static String diagnosticState() {
        BackgroundGameService service = activeService;
        if (service == null) return "service=inactive,wakeLock=off";
        boolean interactive = true;
        try {
            if (service.powerManager != null)
                interactive = service.powerManager.isInteractive();
        } catch (RuntimeException ignored) { }
        return "service=active,screen=" + (interactive ? "on" : "off")
                + ",wakeLock=" + (service.wakeLockHeld ? "on" : "off");
    }

    public static boolean start(Context context, RuntimeSessionStore.Session session) {
        if (session == null) return false;
        Class<? extends BackgroundGameService> serviceClass = serviceClassForSlot(session.slot);
        Intent intent = new Intent(context, serviceClass);
        intent.putExtra(RuntimeSessionStore.EXTRA_SESSION_ID, session.id);
        intent.putExtra(RuntimeSessionStore.EXTRA_SESSION_SLOT, session.slot);
        intent.putExtra("session_title", session.title);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException error) {
            android.util.Log.w("J2MEX", "Could not keep the game runtime in foreground", error);
            return false;
        }
    }

    public static void stop(Context context, int slot) {
        Class<? extends BackgroundGameService> serviceClass = serviceClassForSlot(slot);
        context.stopService(new Intent(context, serviceClass));
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(notificationId(slot));
            updateGroupSummary(context, manager);
        }
    }

    static void applyBackgroundActivitySetting(Context context, boolean enabled) {
        updateBackgroundActivitySettingInProcess(context, enabled);
        AppPreferences.broadcastBackgroundActivityChanged(context, enabled);
        if (enabled) return;

        for (int slot = 0; slot < 5; slot++)
            context.stopService(new Intent(context, serviceClassForSlot(slot)));
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            for (int slot = 0; slot < 5; slot++) manager.cancel(notificationId(slot));
            manager.cancel(SUMMARY_NOTIFICATION_ID);
        }
    }

    static void updateBackgroundActivitySettingInProcess(Context context, boolean enabled) {
        backgroundActivityEnabled = enabled;
        if (!enabled) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(SUMMARY_NOTIFICATION_ID);
        }
    }

    protected int defaultSessionSlot() {
        return 0;
    }

    private static Class<? extends BackgroundGameService> serviceClassForSlot(int slot) {
        switch (slot) {
            case 0: return BackgroundGameService.class;
            case 1: return BackgroundGameService2.class;
            case 2: return BackgroundGameService3.class;
            case 3: return BackgroundGameService4.class;
            case 4: return BackgroundGameService5.class;
            default: throw new IllegalArgumentException("Unsupported runtime slot " + slot);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        sessionSlot = defaultSessionSlot();
        try {
            IntentFilter backgroundFilter =
                    new IntentFilter(AppPreferences.ACTION_BACKGROUND_ACTIVITY_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(backgroundActivityReceiver, backgroundFilter,
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(backgroundActivityReceiver, backgroundFilter);
            }
            createNotificationChannel();
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(notificationId(sessionSlot), notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(notificationId(sessionSlot), notification);
            }
            activeService = this;
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            try {
                registerScreenStateReceiver();
                updateWakeLock(powerManager == null || !powerManager.isInteractive(),
                        "service-start");
            } catch (RuntimeException error) {
                android.util.Log.w("J2MEX",
                        "Screen-state tracking unavailable; keeping the runtime awake", error);
                updateWakeLock(true, "screen-state-unavailable");
            }
        } catch (RuntimeException error) {
            android.util.Log.e("J2MEX", "Could not start the background game service", error);
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            sessionId = intent.getStringExtra(RuntimeSessionStore.EXTRA_SESSION_ID);
            sessionSlot = intent.getIntExtra(RuntimeSessionStore.EXTRA_SESSION_SLOT, 0);
            sessionTitle = intent.getStringExtra("session_title");
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(notificationId(sessionSlot), notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(notificationId(sessionSlot), notification);
            }
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) updateGroupSummary(this, manager);
        }
        return START_NOT_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (sessionId != null) {
            RuntimeSessionStore.remove(this, sessionId);
        }
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
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
        Intent returnIntent;
        if (sessionId != null) {
            RuntimeSessionStore.Session session = RuntimeSessionStore.find(this, sessionId);
            Class<? extends RuntimeActivity> activityClass =
                    RuntimeSessionStore.activityClassForSession(session);
            if (activityClass != null && session.slot == sessionSlot) {
                returnIntent = new Intent(this, activityClass);
                returnIntent.putExtra("jar", session.jarPath);
                returnIntent.putExtra("title", session.title);
                returnIntent.putExtra(RuntimeSessionStore.EXTRA_SESSION_ID, session.id);
                returnIntent.putExtra(RuntimeSessionStore.EXTRA_SESSION_SLOT, session.slot);
                returnIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            } else {
                returnIntent = new Intent(this, MainActivity.class);
                returnIntent.putExtra(RuntimeSessionStore.EXTRA_RETURN_TO_SESSION, sessionId);
                returnIntent.putExtra(RuntimeSessionStore.EXTRA_SESSION_SLOT, sessionSlot);
                returnIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            }
        } else {
            returnIntent = sessionManagerIntent(this);
        }
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, notificationId(sessionSlot),
                returnIntent, pendingFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_j2mex)
                .setContentTitle(sessionTitle == null || sessionTitle.trim().isEmpty()
                        ? AppLocalization.text(this, "J2MEX đang chạy nền") : sessionTitle)
                .setContentText(AppLocalization.text(this, "Chạm để quay lại"))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 31)
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (Build.VERSION.SDK_INT >= 20)
            builder.setGroup(NOTIFICATION_GROUP).setGroupSummary(false);
        return builder.build();
    }

    private static int notificationId(int slot) {
        return NOTIFICATION_ID_BASE + Math.max(0, slot);
    }

    private static Intent sessionManagerIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(RuntimeSessionStore.EXTRA_OPEN_SESSION_MANAGER, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    private static void updateGroupSummary(Context context, NotificationManager manager) {
        List<RuntimeSessionStore.Session> sessions = RuntimeSessionStore.list(context);
        if (!backgroundActivityEnabled || sessions.size() < 2) {
            manager.cancel(SUMMARY_NOTIFICATION_ID);
            return;
        }
        Intent intent = sessionManagerIntent(context);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, SUMMARY_NOTIFICATION_ID, intent, pendingFlags);
        Notification.Builder summary = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification.InboxStyle inbox = new Notification.InboxStyle();
        for (RuntimeSessionStore.Session session : sessions)
            inbox.addLine(session.title);
        summary.setSmallIcon(R.drawable.ic_stat_j2mex)
                .setContentTitle(AppLocalization.text(context, "J2MEX đang chạy nền"))
                .setContentText(sessions.size() + " "
                        + AppLocalization.text(context, "game đang chạy"))
                .setStyle(inbox)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 20)
            summary.setGroup(NOTIFICATION_GROUP).setGroupSummary(true);
        manager.notify(SUMMARY_NOTIFICATION_ID, summary.build());
    }

    private void registerScreenStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
        screenStateReceiverRegistered = true;
    }

    @SuppressLint("WakelockTimeout")
    private void updateWakeLock(boolean shouldHold, String reason) {
        if (shouldHold) {
            if (wakeLockHeld) return;
            PowerManager manager = powerManager;
            if (manager == null)
                manager = (PowerManager) getSystemService(POWER_SERVICE);
            if (manager == null) return;
            wakeLock = manager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "J2MEX:BackgroundGame");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
            wakeLockHeld = true;
        } else {
            if (!wakeLockHeld) return;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            wakeLock = null;
            wakeLockHeld = false;
        }
        android.util.Log.i("J2MEX", "[background-power] event=" + reason
                + " " + diagnosticState());
    }

    @Override public void onDestroy() {
        if (activeService == this) activeService = null;
        try {
            unregisterReceiver(backgroundActivityReceiver);
        } catch (RuntimeException ignored) { }
        if (screenStateReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (RuntimeException error) {
                android.util.Log.w("J2MEX", "Could not unregister screen-state receiver", error);
            }
            screenStateReceiverRegistered = false;
        }
        releaseWakeLock();
        stopForeground(true);
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(notificationId(sessionSlot));
            if (backgroundActivityEnabled) updateGroupSummary(this, manager);
            else manager.cancel(SUMMARY_NOTIFICATION_ID);
        }
        super.onDestroy();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        wakeLockHeld = false;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
