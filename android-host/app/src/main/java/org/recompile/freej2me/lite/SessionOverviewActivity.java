package org.recompile.freej2me.lite;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-style task switcher for the current five-session runtime pool. */
public final class SessionOverviewActivity extends LocalizedActivity {
    static final String EXTRA_OPEN_HOME = "org.recompile.freej2me.lite.OPEN_HOME_FROM_OVERVIEW";

    private static final int COLOR_BACKGROUND = Color.rgb(14, 24, 38);
    private static final int COLOR_SURFACE = Color.rgb(25, 36, 52);
    private static final int COLOR_TEXT = Color.rgb(232, 239, 247);
    private static final int COLOR_MUTED = Color.rgb(157, 170, 187);
    private static final int COLOR_ACCENT = Color.rgb(185, 195, 255);
    private static final int COLOR_BORDER = 0xFF56647A;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService assetLoader = Executors.newSingleThreadExecutor();
    private final ArrayList<RuntimeSessionStore.Session> sessions = new ArrayList<>();
    private final Map<String, Bitmap> previews = new HashMap<>();
    private final Map<String, Bitmap> rotatedPreviews = new HashMap<>();
    private final Map<String, Bitmap> gameIcons = new HashMap<>();
    private final Map<String, SessionCard> cards = new HashMap<>();
    private final Set<String> loadingAssets = new HashSet<>();
    private FrameLayout root;
    private CarouselStage carousel;
    private LinearLayout portraitHeader;
    private GameIconView portraitHeaderIcon;
    private TextView portraitHeaderTitle;
    private TextView portraitHeaderClose;
    private TextView homeButton;
    private TextView heapValue;
    private PopupWindow activeTaskPopup;
    private View activeTaskPopupDim;
    private String selectedSessionId;
    private boolean homeLaunchPending;
    private boolean emptyReturnScheduled;
    private boolean heapUpdateReceiverRegistered;
    private String heapSampleSessionId;
    private long heapSampleAt;
    private final Runnable heapQueryAction = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            requestSelectedHeap();
            mainHandler.postDelayed(this, 1_000L);
        }
    };
    private final BroadcastReceiver heapUpdateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !RuntimeSessionStore.ACTION_HOTSPOT_MEMORY_UPDATE.equals(
                            intent.getAction())) return;
            String sessionId = intent.getStringExtra(RuntimeSessionStore.EXTRA_SESSION_ID);
            if (sessionId == null || !sessionId.equals(selectedSessionId)) return;
            long usedBytes = intent.getLongExtra(
                    RuntimeSessionStore.EXTRA_HOTSPOT_MEMORY_USED, -1L);
            long maxBytes = intent.getLongExtra(
                    RuntimeSessionStore.EXTRA_HOTSPOT_MEMORY_MAX, -1L);
            if (usedBytes < 0 || maxBytes <= 0) return;
            heapSampleSessionId = sessionId;
            heapSampleAt = android.os.SystemClock.elapsedRealtime();
            heapValue.setText(formatHeap(usedBytes, maxBytes));
        }
    };
    private final Runnable emptyReturnAction = () -> {
        emptyReturnScheduled = false;
        if (isFinishing() || isDestroyed()) return;
        refreshSessions();
        if (sessions.isEmpty()) returnToHome();
    };

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selectedSessionId = state.getString("overview.selectedSession");
        if (selectedSessionId == null && getIntent() != null)
            selectedSessionId = getIntent().getStringExtra(RuntimeSessionStore.EXTRA_SESSION_ID);

        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        window.setBackgroundDrawable(new ColorDrawable(COLOR_BACKGROUND));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        CutoutSafeArea.configureWindow(window);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setClipChildren(false);
        carousel = new CarouselStage(this);
        carousel.setClipChildren(false);
        carousel.setClipToPadding(false);
        FrameLayout.LayoutParams carouselParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(carousel, carouselParams);

        portraitHeader = new LinearLayout(this);
        portraitHeader.setGravity(Gravity.CENTER_VERTICAL);
        portraitHeader.setVisibility(View.GONE);
        portraitHeader.setElevation(dp(this, 18));
        portraitHeaderIcon = new GameIconView(this);
        portraitHeader.addView(portraitHeaderIcon, new LinearLayout.LayoutParams(
                dp(this, 30), dp(this, 30)));
        portraitHeaderTitle = new TextView(this);
        portraitHeaderTitle.setTextColor(COLOR_TEXT);
        portraitHeaderTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        portraitHeaderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        portraitHeaderTitle.setSingleLine(true);
        installSlideTitleTruncation(portraitHeaderTitle);
        LinearLayout.LayoutParams portraitTitleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        portraitTitleParams.setMargins(dp(this, 10), 0, dp(this, 6), 0);
        portraitHeader.addView(portraitHeaderTitle, portraitTitleParams);
        portraitHeaderClose = createPlainCloseButton(this);
        portraitHeaderClose.setOnClickListener(view -> {
            RuntimeSessionStore.Session selected = findSession(selectedSessionId);
            if (selected != null) confirmClose(selected);
        });
        portraitHeader.addView(portraitHeaderClose);
        carousel.addView(portraitHeader);

        homeButton = new TextView(this);
        homeButton.setText(AppLocalization.text(this, "Trang chủ"));
        homeButton.setTextColor(COLOR_BACKGROUND);
        homeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        homeButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        homeButton.setGravity(Gravity.CENTER);
        homeButton.setPadding(dp(this, 24), 0, dp(this, 24), 0);
        homeButton.setBackground(pill(COLOR_ACCENT, dp(this, 40), 0));
        homeButton.setClickable(true);
        homeButton.setFocusable(true);
        homeButton.setOnClickListener(view -> returnToHome());

        heapValue = new TextView(this);
        heapValue.setText("--/-- MiB");
        heapValue.setTextColor(COLOR_MUTED);
        heapValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heapValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heapValue.setSingleLine(true);
        heapValue.setGravity(Gravity.CENTER);
        heapValue.setElevation(dp(this, 16));
        heapValue.setVisibility(View.GONE);

        CutoutSafeArea.install(root);
        setContentView(root);
        root.requestApplyInsets();

        refreshSessions();
        // A PixelCopy callback may finish just after the overview starts.
        mainHandler.postDelayed(this::refreshSessions, 700);
        mainHandler.postDelayed(this::refreshSessions, 1_500);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("overview.selectedSession", selectedSessionId);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        RuntimeSessionStore.Session audioOwner = RuntimeSessionStore.foreground(this);
        RuntimeSessionStore.publishAudioOwner(this,
                audioOwner == null ? null : audioOwner.id);
        refreshSessions();
        mainHandler.removeCallbacks(heapQueryAction);
        mainHandler.post(heapQueryAction);
    }

    @Override protected void onPause() {
        mainHandler.removeCallbacks(heapQueryAction);
        super.onPause();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(
                RuntimeSessionStore.ACTION_HOTSPOT_MEMORY_UPDATE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(heapUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(heapUpdateReceiver, filter);
        }
        heapUpdateReceiverRegistered = true;
    }

    @Override protected void onStop() {
        if (heapUpdateReceiverRegistered) {
            try {
                unregisterReceiver(heapUpdateReceiver);
            } catch (RuntimeException error) {
                android.util.Log.w("J2MEX", "Could not unregister heap-update listener", error);
            }
            heapUpdateReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (heapUpdateReceiverRegistered) {
            try {
                unregisterReceiver(heapUpdateReceiver);
            } catch (RuntimeException ignored) { }
            heapUpdateReceiverRegistered = false;
        }
        if (activeTaskPopup != null) activeTaskPopup.dismiss();
        assetLoader.shutdownNow();
        for (Bitmap bitmap : previews.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        for (Bitmap bitmap : rotatedPreviews.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        for (Bitmap bitmap : gameIcons.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        previews.clear();
        rotatedPreviews.clear();
        gameIcons.clear();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        super.onBackPressed();
    }

    private void refreshSessions() {
        if (isFinishing() || isDestroyed()) return;
        if (!sessions.isEmpty()) {
            mainHandler.removeCallbacks(emptyReturnAction);
            emptyReturnScheduled = false;
        }
        pruneStaleSessions();
        ArrayList<RuntimeSessionStore.Session> current =
                new ArrayList<>(RuntimeSessionStore.list(this));
        sessions.clear();
        sessions.addAll(current);
        Set<String> activeIds = new HashSet<>();
        for (RuntimeSessionStore.Session session : sessions) activeIds.add(session.id);
        discardClosedSessionAssets(activeIds);
        if (findSession(selectedSessionId) == null) {
            RuntimeSessionStore.Session foreground = RuntimeSessionStore.foreground(this);
            selectedSessionId = foreground == null
                    ? (sessions.isEmpty() ? null : sessions.get(0).id) : foreground.id;
        }
        if (heapSampleSessionId != null && !heapSampleSessionId.equals(selectedSessionId)) {
            heapSampleSessionId = null;
            heapSampleAt = 0L;
            heapValue.setText("--/-- MiB");
        }
        renderCards();
    }

    private void discardClosedSessionAssets(Set<String> activeIds) {
        for (String id : new HashSet<>(previews.keySet())) {
            if (activeIds.contains(id)) continue;
            recycle(previews.remove(id));
            recycle(rotatedPreviews.remove(id));
        }
        for (String id : new HashSet<>(rotatedPreviews.keySet())) {
            if (!activeIds.contains(id)) recycle(rotatedPreviews.remove(id));
        }
        for (String id : new HashSet<>(gameIcons.keySet())) {
            if (activeIds.contains(id)) continue;
            recycle(gameIcons.remove(id));
        }
        loadingAssets.retainAll(activeIds);
    }

    private void pruneStaleSessions() {
        for (RuntimeSessionStore.Session session : RuntimeSessionStore.list(this)) {
            if (session.processId <= 0
                    && System.currentTimeMillis() - session.startedAt < 120_000L) continue;
            if (!isProcessAlive(session) || !new File(session.jarPath).isFile()) {
                RuntimeSessionStore.remove(this, session.id);
                BackgroundGameService.stop(this, session.slot);
            }
        }
    }

    private boolean isProcessAlive(RuntimeSessionStore.Session session) {
        if (session == null || session.processId <= 0) return false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return false;
        List<ActivityManager.RunningAppProcessInfo> processes =
                manager.getRunningAppProcesses();
        if (processes == null) return false;
        String expected = RuntimeSessionStore.processName(this, session.slot);
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.pid == session.processId && expected.equals(process.processName))
                return true;
        }
        return false;
    }

    private void renderCards() {
        if (carousel == null) return;
        carousel.removeAllViews();
        cards.clear();
        if (sessions.isEmpty()) {
            if (!emptyReturnScheduled) {
                emptyReturnScheduled = true;
                mainHandler.postDelayed(emptyReturnAction, 1_000L);
            }
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(this, 26), dp(this, 20), dp(this, 26), dp(this, 100));
            TextView emptyMessage = new TextView(this);
            emptyMessage.setText(AppLocalization.text(this, "Không có tác vụ đang chạy"));
            emptyMessage.setTextColor(COLOR_TEXT);
            emptyMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            emptyMessage.setGravity(Gravity.CENTER);
            empty.addView(emptyMessage);
            carousel.addView(empty, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            carousel.addView(portraitHeader);
            heapValue.setVisibility(View.GONE);
            updateSelectedChrome();
            return;
        }
        mainHandler.removeCallbacks(emptyReturnAction);
        emptyReturnScheduled = false;
        for (RuntimeSessionStore.Session session : sessions) {
            SessionCard card = new SessionCard(this, session);
            cards.put(session.id, card);
            Bitmap preview = previews.get(session.id);
            Bitmap icon = gameIcons.get(session.id);
            card.setPreview(preview);
            card.setGameIcon(icon);
            card.setHighlighted(session.foreground);
            card.setOnClickListener(view -> openSession(session));
            carousel.addView(card);
            loadAssets(session);
        }
        carousel.addView(portraitHeader);
        FrameLayout.LayoutParams homeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(this, 42),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        homeParams.bottomMargin = dp(this, 24);
        carousel.addView(homeButton, homeParams);
        carousel.addView(heapValue, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        heapValue.setVisibility(View.VISIBLE);
        updateSelectedChrome();
        carousel.requestLayout();
    }

    private void requestSelectedHeap() {
        RuntimeSessionStore.Session selected = findSession(selectedSessionId);
        if (selected == null || heapValue == null || heapValue.getVisibility() != View.VISIBLE)
            return;
        if (heapSampleSessionId != null && !heapSampleSessionId.equals(selected.id)) {
            heapSampleSessionId = null;
            heapSampleAt = 0L;
        }
        if (heapSampleSessionId == null
                || android.os.SystemClock.elapsedRealtime() - heapSampleAt > 2_500L) {
            heapSampleSessionId = null;
            heapValue.setText("--/-- MiB");
        }
        Intent query = new Intent(RuntimeSessionStore.ACTION_HOTSPOT_MEMORY_QUERY)
                .setPackage(getPackageName())
                .putExtra(RuntimeSessionStore.EXTRA_SESSION_ID, selected.id);
        sendBroadcast(query);
    }

    private static String formatHeap(long usedBytes, long maxBytes) {
        long mebibyte = 1024L * 1024L;
        long used = Math.round(usedBytes / (double) mebibyte);
        long maximum = Math.round(maxBytes / (double) mebibyte);
        return String.format(Locale.US, "%d/%d MiB", Math.min(used, maximum), maximum);
    }

    private void updateSelectedChrome() {
        boolean portrait = isPortraitOverview();
        RuntimeSessionStore.Session selected = findSession(selectedSessionId);
        if (portraitHeader != null) {
            portraitHeader.setVisibility(portrait && selected != null ? View.VISIBLE : View.GONE);
            if (selected != null) {
                setSlideTitle(portraitHeaderTitle, selected.title);
                portraitHeaderIcon.setSessionArtwork(
                        gameIcons.get(selected.id), selected.title);
                portraitHeaderClose.setContentDescription(AppLocalization.text(this,
                        "Dừng game"));
            }
        }
        for (RuntimeSessionStore.Session session : sessions) {
            SessionCard card = cards.get(session.id);
            if (card != null)
                card.setSelected(session.id.equals(selectedSessionId), portrait);
        }
    }

    private void installSlideTitleTruncation(TextView title) {
        title.setEllipsize(null);
        title.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> fitSlideTitle(title));
    }

    private void setSlideTitle(TextView title, String fullTitle) {
        title.setTag(fullTitle == null ? "" : fullTitle);
        title.setText(fullTitle == null ? "" : fullTitle);
        title.post(() -> fitSlideTitle(title));
    }

    private void fitSlideTitle(TextView title) {
        Object taggedTitle = title.getTag();
        if (!(taggedTitle instanceof String)) return;
        int availableWidth = title.getWidth()
                - title.getTotalPaddingLeft() - title.getTotalPaddingRight();
        if (availableWidth <= 0) return;
        String fullTitle = (String) taggedTitle;
        String fittedTitle = truncateSlideTitle(fullTitle, title.getPaint(), availableWidth);
        if (!TextUtils.equals(fittedTitle, title.getText())) title.setText(fittedTitle);
    }

    private String truncateSlideTitle(String title, Paint paint, int width) {
        if (title == null || title.isEmpty() || width <= 0
                || paint.measureText(title) <= width) return title == null ? "" : title;
        String suffix = "...";
        if (paint.measureText(suffix) > width) return "";
        int codePointCount = title.codePointCount(0, title.length());
        int low = 0;
        int high = codePointCount;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String prefix = title.substring(0, title.offsetByCodePoints(0, middle));
            if (paint.measureText(prefix + suffix) <= width) low = middle;
            else high = middle - 1;
        }
        return title.substring(0, title.offsetByCodePoints(0, low)) + suffix;
    }

    private boolean isPortraitOverview() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) return true;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return false;
        return getResources().getDisplayMetrics().heightPixels
                >= getResources().getDisplayMetrics().widthPixels;
    }

    private void loadAssets(RuntimeSessionStore.Session session) {
        if (previews.containsKey(session.id) && gameIcons.containsKey(session.id)
                || !loadingAssets.add(session.id)) return;
        assetLoader.execute(() -> {
            Bitmap preview = RuntimeSessionPreviewStore.load(this, session.id);
            Bitmap icon = null;
            File jar = new File(session.jarPath);
            if (jar.isFile()) {
                try {
                    icon = GameLibrary.readGame(jar, session.title).icon;
                } catch (IOException | RuntimeException error) {
                    android.util.Log.w("J2MEX", "Could not load session card icon", error);
                }
            }
            final Bitmap loadedPreview = preview;
            final Bitmap loadedIcon = icon;
            mainHandler.post(() -> {
                loadingAssets.remove(session.id);
                if (isFinishing() || isDestroyed()
                        || findSession(session.id) == null) {
                    recycle(loadedPreview);
                    recycle(loadedIcon);
                    return;
                }
                if (loadedPreview != null) previews.put(session.id, loadedPreview);
                if (loadedIcon != null) gameIcons.put(session.id, loadedIcon);
                SessionCard card = cards.get(session.id);
                if (card != null) {
                    card.setPreview(loadedPreview);
                    card.setGameIcon(loadedIcon);
                }
                if (session.id.equals(selectedSessionId)) updateSelectedChrome();
            });
        });
    }

    private void openSession(RuntimeSessionStore.Session session) {
        if (session == null) return;
        if (session.processId <= 0 && System.currentTimeMillis() - session.startedAt < 120_000L) {
            Toast.makeText(this, AppLocalization.text(this, "Game đang khởi động."),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        File jar = new File(session.jarPath);
        if (!jar.isFile() || !isProcessAlive(session)) {
            RuntimeSessionStore.remove(this, session.id);
            BackgroundGameService.stop(this, session.slot);
            refreshSessions();
            Toast.makeText(this, AppLocalization.text(this,
                    "Game này không còn chạy. Hãy mở lại từ thư viện."),
                    Toast.LENGTH_LONG).show();
            return;
        }
        MainActivity.setLauncherHomePinned(this, false);
        try {
            RuntimeSessionStore.setForeground(this, session.id);
        } catch (IOException error) {
            showError(error);
            return;
        }
        selectedSessionId = session.id;
        Intent intent = new Intent(this, runtimeActivityFor(session));
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("jar", jar.getAbsolutePath());
        intent.putExtra("title", session.title);
        intent.putExtra(RuntimeSessionStore.EXTRA_SESSION_ID, session.id);
        intent.putExtra(RuntimeSessionStore.EXTRA_SESSION_SLOT, session.slot);
        AppPreferences.addRuntimeSnapshotExtras(this, intent);
        startActivity(intent);
        finish();
    }

    private Class<? extends RuntimeActivity> runtimeActivityFor(
            RuntimeSessionStore.Session session) {
        if (session.slot >= 2) {
            switch (session.slot) {
                case 2: return RuntimeThirdActivity.class;
                case 3: return RuntimeFourthActivity.class;
                case 4: return RuntimeFifthActivity.class;
                default: throw new IllegalArgumentException("Unsupported runtime slot");
            }
        }
        int orientation = GameSettings.load(this, new File(session.jarPath)).orientationMode;
        if (session.slot == 0) {
            if (orientation == GameSettings.ORIENTATION_LANDSCAPE)
                return RuntimeLandscapeActivity.class;
            if (orientation == GameSettings.ORIENTATION_PORTRAIT)
                return RuntimePortraitActivity.class;
            return RuntimeActivity.class;
        }
        if (orientation == GameSettings.ORIENTATION_LANDSCAPE)
            return RuntimeSecondLandscapeActivity.class;
        if (orientation == GameSettings.ORIENTATION_PORTRAIT)
            return RuntimeSecondPortraitActivity.class;
        return RuntimeSecondActivity.class;
    }

    private void confirmClose(RuntimeSessionStore.Session session) {
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        showTaskPopup(
                AppLocalization.text(this, vietnamese ? "Dừng game này?" : "Stop this game?"),
                AppLocalization.text(this, vietnamese
                        ? "Phiên game sẽ bị đóng và kết nối hiện tại sẽ kết thúc."
                        : "This game session will close and its current connections will end."),
                vietnamese ? "Dừng" : "Stop",
                vietnamese ? "Hủy" : "Cancel",
                () -> closeSession(session));
    }

    private void closeSession(RuntimeSessionStore.Session session) {
        if (session == null) return;
        if (!isProcessAlive(session)) {
            RuntimeSessionStore.remove(this, session.id);
            BackgroundGameService.stop(this, session.slot);
            refreshSessions();
            return;
        }
        Intent intent = new Intent(this, runtimeActivityFor(session));
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("jar", session.jarPath);
        intent.putExtra("title", session.title);
        intent.putExtra(RuntimeSessionStore.EXTRA_SESSION_ID, session.id);
        intent.putExtra(RuntimeSessionStore.EXTRA_SESSION_SLOT, session.slot);
        intent.putExtra(RuntimeSessionStore.EXTRA_CLOSE_SESSION, true);
        startActivity(intent);
    }

    private void returnToHome() {
        if (homeLaunchPending) return;
        homeLaunchPending = true;
        MainActivity.setLauncherHomePinned(this, true);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_OPEN_HOME, true);
        startActivity(intent);
        finish();
    }

    private RuntimeSessionStore.Session findSession(String sessionId) {
        if (sessionId == null) return null;
        for (RuntimeSessionStore.Session session : sessions) {
            if (sessionId.equals(session.id)) return session;
        }
        return null;
    }

    private void showError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty())
            message = error == null ? "Không thể thực hiện." : error.toString();
        showTaskPopup(AppLocalization.text(this, "Không thể thực hiện"),
                AppLocalization.text(this, message), "Đóng", null, null);
    }

    private void showTaskPopup(String title, String message, String primaryLabel,
            String cancelLabel, Runnable primaryAction) {
        if (activeTaskPopup != null) activeTaskPopup.dismiss();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(this, 20), dp(this, 18), dp(this, 20), dp(this, 12));
        panel.setBackground(pill(COLOR_SURFACE, dp(this, 20), COLOR_BORDER));
        panel.setElevation(dp(this, 8));

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(COLOR_TEXT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText(message);
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(this, 10);
        panel.addView(body, bodyParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 12);
        panel.addView(actions, actionsParams);

        PopupWindow popup = new PopupWindow(panel,
                Math.min(dp(this, 400),
                        getResources().getDisplayMetrics().widthPixels - dp(this, 32)),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(this, 10));
        popup.setOutsideTouchable(true);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        TextView primaryButton = taskActionButton(primaryLabel, COLOR_ACCENT);
        TextView cancelButton = null;
        if (cancelLabel != null) {
            cancelButton = taskActionButton(cancelLabel, COLOR_MUTED);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actions.addView(cancelButton, cancelParams);
            cancelButton.setOnClickListener(view -> popup.dismiss());
        }
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (cancelButton != null) primaryParams.leftMargin = dp(this, 4);
        actions.addView(primaryButton, primaryParams);
        primaryButton.setOnClickListener(view -> {
            popup.dismiss();
            if (primaryAction != null) primaryAction.run();
        });

        ViewGroup contentRoot = findViewById(android.R.id.content);
        View dim = new View(this);
        dim.setBackgroundColor(0x80000000);
        dim.setClickable(true);
        contentRoot.addView(dim, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim.setOnClickListener(view -> popup.dismiss());
        activeTaskPopup = popup;
        activeTaskPopupDim = dim;
        popup.setOnDismissListener(() -> {
            if (dim.getParent() instanceof ViewGroup)
                ((ViewGroup) dim.getParent()).removeView(dim);
            if (activeTaskPopup == popup) {
                activeTaskPopup = null;
                activeTaskPopupDim = null;
            }
        });
        popup.showAtLocation(contentRoot, Gravity.CENTER, 0, 0);
    }

    private TextView taskActionButton(String label, int color) {
        TextView button = new TextView(this);
        button.setText(AppLocalization.text(this, label));
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(this, 14), dp(this, 8), dp(this, 14), dp(this, 8));
        button.setMinHeight(dp(this, 48));
        button.setMinWidth(dp(this, 64));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private Bitmap previewForOverview(String sessionId, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        boolean imageLandscape = bitmap.getWidth() > bitmap.getHeight();
        boolean overviewLandscape = !isPortraitOverview();
        if (imageLandscape == overviewLandscape) return bitmap;
        Bitmap cached = rotatedPreviews.get(sessionId);
        if (cached != null && !cached.isRecycled()) return cached;
        Matrix rotation = new Matrix();
        rotation.setRotate(overviewLandscape ? -90f : 90f);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), rotation, true);
        rotatedPreviews.put(sessionId, rotated);
        return rotated;
    }

    private static GradientDrawable pill(int color, int radius, int stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(radius);
        if (stroke != 0) result.setStroke(1, stroke);
        return result;
    }

    private TextView createPlainCloseButton(Context context) {
        TextView close = new TextView(context);
        close.setText(AppLocalization.text(context, "Đóng"));
        close.setTextColor(COLOR_ACCENT);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        close.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(AppLocalization.text(context, "Dừng game"));
        close.setMinWidth(dp(context, 52));
        close.setMinHeight(dp(context, 40));
        close.setClickable(true);
        close.setFocusable(true);
        return close;
    }

    private final class CarouselStage extends FrameLayout {
        private final int touchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        private float downX;
        private float downY;
        private boolean dragging;
        private boolean animateNextLayout;

        CarouselStage(Context context) {
            super(context);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            boolean landscape = width > height;
            int cardWidth = Math.round(width * (landscape ? 0.67f : 0.84f));
            int cardHeight = Math.round(height * (landscape ? 0.78f : 0.70f));
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child instanceof SessionCard) {
                    child.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY));
                } else if (child == heapValue) {
                    child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                            MeasureSpec.makeMeasureSpec(
                                    dp(SessionOverviewActivity.this, 28), MeasureSpec.AT_MOST));
                } else if (child == portraitHeader) {
                    child.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(dp(SessionOverviewActivity.this, 48),
                                    MeasureSpec.EXACTLY));
                } else if (child == homeButton) {
                    child.measure(MeasureSpec.makeMeasureSpec(
                                    Math.max(0, width - dp(SessionOverviewActivity.this, 32)),
                                    MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(dp(SessionOverviewActivity.this, 42),
                                    MeasureSpec.EXACTLY));
                } else {
                    child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                }
            }
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right,
                int bottom) {
            int width = right - left;
            int height = bottom - top;
            boolean landscape = width > height;
            int cardWidth = Math.round(width * (landscape ? 0.67f : 0.84f));
            int cardHeight = Math.round(height * (landscape ? 0.78f : 0.70f));
            int selectedIndex = selectedIndex();
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child == portraitHeader) {
                    SessionCard selectedCard = cards.get(selectedSessionId);
                    if (landscape || selectedCard == null || child.getVisibility() != View.VISIBLE) {
                        child.layout(0, 0, 0, 0);
                    } else {
                        int headerHeight = child.getMeasuredHeight();
                        int cardTop = (height - cardHeight) / 2
                                - dp(SessionOverviewActivity.this, 16);
                        int headerTop = cardTop - headerHeight
                                - dp(SessionOverviewActivity.this, 6);
                        int headerLeft = (width - child.getMeasuredWidth()) / 2;
                        child.layout(headerLeft, headerTop,
                                headerLeft + child.getMeasuredWidth(), headerTop + headerHeight);
                    }
                    continue;
                }
                if (child == homeButton) {
                    FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) child.getLayoutParams();
                    int buttonWidth = child.getMeasuredWidth();
                    int buttonHeight = child.getMeasuredHeight();
                    int buttonLeft = (width - buttonWidth) / 2;
                    int buttonTop = height - params.bottomMargin - buttonHeight;
                    child.layout(buttonLeft, buttonTop,
                            buttonLeft + buttonWidth, buttonTop + buttonHeight);
                    child.setElevation(dp(SessionOverviewActivity.this, 16));
                    continue;
                }
                if (child == heapValue) {
                    if (sessions.isEmpty() || child.getVisibility() != View.VISIBLE) {
                        child.layout(0, 0, 0, 0);
                    } else {
                        int heapWidth = child.getMeasuredWidth();
                        int heapHeight = child.getMeasuredHeight();
                        if (landscape) {
                            int cardLeft = (width - cardWidth) / 2;
                            int heapLeft = Math.max(0, (cardLeft - heapWidth) / 2);
                            int cardTop = (height - cardHeight) / 2
                                    - dp(SessionOverviewActivity.this, 16);
                            child.layout(heapLeft, cardTop,
                                    heapLeft + heapWidth, cardTop + heapHeight);
                        } else {
                            FrameLayout.LayoutParams homeParams =
                                    (FrameLayout.LayoutParams) homeButton.getLayoutParams();
                            int homeTop = height - homeParams.bottomMargin
                                    - homeButton.getMeasuredHeight();
                            int cardTop = (height - cardHeight) / 2
                                    - dp(SessionOverviewActivity.this, 16);
                            int cardBottom = cardTop + cardHeight;
                            int gapCenter = (cardBottom + homeTop) / 2;
                            int heapTop = gapCenter - heapHeight / 2;
                            int heapLeft = (width - heapWidth) / 2;
                            child.layout(heapLeft, heapTop,
                                    heapLeft + heapWidth, heapTop + heapHeight);
                        }
                    }
                    child.setElevation(dp(SessionOverviewActivity.this, 16));
                    continue;
                }
                if (!(child instanceof SessionCard)) {
                    child.layout(0, 0, width, height);
                    continue;
                }
                int centerTop = (height - cardHeight) / 2
                        - dp(SessionOverviewActivity.this, 16);
                child.layout((width - cardWidth) / 2, centerTop,
                        (width + cardWidth) / 2, centerTop + cardHeight);
                int cardIndex = sessions.indexOf(((SessionCard) child).session);
                boolean selected = cardIndex == selectedIndex;
                float scale = selected ? 1f : 0.83f;
                float offset = width * 0.58f;
                int relativeIndex = cardIndex - selectedIndex;
                float translationX = selected ? 0f : relativeIndex * offset;
                float translationY = selected ? 0f : dp(SessionOverviewActivity.this, 4);
                child.setElevation(dp(SessionOverviewActivity.this, selected ? 12 : 3));
                if (animateNextLayout) {
                    child.animate().cancel();
                    child.animate().translationX(translationX)
                            .translationY(translationY)
                            .scaleX(scale).scaleY(scale)
                            .alpha(selected ? 1f : 0.76f)
                            .setDuration(220).start();
                } else {
                    child.setScaleX(scale);
                    child.setScaleY(scale);
                    child.setAlpha(selected ? 1f : 0.76f);
                    child.setTranslationX(translationX);
                    child.setTranslationY(translationY);
                }
            }
            if (animateNextLayout) {
                animateNextLayout = false;
            }
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        dragging = true;
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && dragging) {
                float dx = event.getX() - downX;
                moveSelection(dx < 0 ? 1 : -1);
                dragging = false;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) dragging = false;
            return true;
        }

        void updatePosition() {
            animateNextLayout = true;
            requestLayout();
            invalidate();
        }
    }

    private int selectedIndex() {
        for (int index = 0; index < sessions.size(); index++) {
            if (sessions.get(index).id.equals(selectedSessionId)) return index;
        }
        return 0;
    }

    private void moveSelection(int offset) {
        if (sessions.size() < 2) return;
        int index = (selectedIndex() + offset + sessions.size()) % sessions.size();
        selectedSessionId = sessions.get(index).id;
        heapSampleSessionId = null;
        heapSampleAt = 0L;
        heapValue.setText("--/-- MiB");
        updateSelectedChrome();
        mainHandler.removeCallbacks(heapQueryAction);
        mainHandler.post(heapQueryAction);
        carousel.updatePosition();
    }

    private final class SessionCard extends FrameLayout {
        final RuntimeSessionStore.Session session;
        private final ImageView previewView;
        private final GameIconView iconView;
        private final View fallbackView;
        private final TextView stateView;
        private final LinearLayout topBar;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SessionCard(Context context, RuntimeSessionStore.Session session) {
            super(context);
            this.session = session;
            setBackground(pill(COLOR_SURFACE, dp(context, 26), 0));
            setClipToOutline(true);
            setClickable(true);
            setFocusable(true);
            setContentDescription(session.title);

            previewView = new ImageView(context);
            previewView.setBackgroundColor(COLOR_SURFACE);
            previewView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(previewView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout fallback = new LinearLayout(context);
            fallback.setGravity(Gravity.CENTER);
            fallback.setOrientation(LinearLayout.VERTICAL);
            fallback.setBackgroundColor(COLOR_SURFACE);
            iconView = new GameIconView(context);
            fallback.addView(iconView, new LinearLayout.LayoutParams(
                    dp(context, 72), dp(context, 72)));
            TextView unavailable = new TextView(context);
            unavailable.setText(AppLocalization.text(context, "Ảnh xem trước không khả dụng"));
            unavailable.setTextColor(COLOR_MUTED);
            unavailable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            unavailable.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams unavailableParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unavailableParams.topMargin = dp(context, 10);
            fallback.addView(unavailable, unavailableParams);
            addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fallbackView = fallback;

            topBar = new LinearLayout(context);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setPadding(dp(context, 14), dp(context, 12),
                    dp(context, 8), dp(context, 12));
            topBar.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xE60E1826, 0xA60E1826}));
            FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP);
            addView(topBar, topParams);

            GameIconView headerIcon = new GameIconView(context);
            headerIcon.setContentDescription(AppLocalization.text(context, "Icon game"));
            topBar.addView(headerIcon, new LinearLayout.LayoutParams(
                    dp(context, 30), dp(context, 30)));
            // Keep the fallback and header artwork in sync without retaining a second view.
            this.headerIcon = headerIcon;

            TextView title = new TextView(context);
            setSlideTitle(title, session.title);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true);
            installSlideTitleTruncation(title);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.setMargins(dp(context, 10), 0, dp(context, 8), 0);
            topBar.addView(title, titleParams);

            TextView close = createPlainCloseButton(context);
            close.setOnClickListener(view -> confirmClose(session));
            topBar.addView(close);

            stateView = new TextView(context);
            stateView.setTextColor(COLOR_TEXT);
            stateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            stateView.setPadding(dp(context, 12), dp(context, 7),
                    dp(context, 12), dp(context, 7));
            stateView.setBackground(pill(0xCC0E1826, dp(context, 16), 0));
            FrameLayout.LayoutParams stateParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.LEFT);
            stateParams.setMargins(dp(context, 14), 0, 0, dp(context, 14));
            addView(stateView, stateParams);

            updateHighlight();
        }

        private final GameIconView headerIcon;

        void setPreview(Bitmap bitmap) {
            if (bitmap != null && !bitmap.isRecycled()) {
                previewView.setImageBitmap(
                        previewForOverview(session.id, bitmap));
                previewView.setVisibility(View.VISIBLE);
                fallbackView.setVisibility(View.GONE);
            } else {
                previewView.setImageDrawable(null);
                previewView.setVisibility(View.GONE);
                fallbackView.setVisibility(View.VISIBLE);
            }
            requestLayout();
        }

        void setSelected(boolean selected, boolean externalPortraitHeader) {
            topBar.setVisibility(selected && !externalPortraitHeader
                    ? View.VISIBLE : View.GONE);
        }

        void setGameIcon(Bitmap bitmap) {
            iconView.setSessionArtwork(bitmap, session.title);
            headerIcon.setSessionArtwork(bitmap, session.title);
        }

        void setHighlighted(boolean active) {
            stateView.setText(AppLocalization.text(SessionOverviewActivity.this,
                    active ? "Đang hiển thị" : "Đang chạy nền"));
            stateView.setTextColor(active ? COLOR_ACCENT : COLOR_MUTED);
            updateHighlight();
        }

        private void updateHighlight() {
            invalidate();
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            float inset = dp(getContext(), 1);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(getContext(), session.foreground ? 1.7f : 1f));
            borderPaint.setColor(session.foreground ? COLOR_ACCENT : COLOR_BORDER);
            RectF bounds = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            float radius = dp(getContext(), 26);
            canvas.drawRoundRect(bounds, radius, radius, borderPaint);
        }

    }
}
