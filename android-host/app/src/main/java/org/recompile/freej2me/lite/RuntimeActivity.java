package org.recompile.freej2me.lite;

import android.app.*;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.SparseIntArray;
import android.view.*;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.recompile.freej2me.protocol.NativeScreen;
import org.recompile.freej2me.protocol.FramebufferProtocol;
import org.recompile.freej2me.protocol.BluetoothProtocol;
import org.recompile.freej2me.protocol.AudioProtocol;
import org.recompile.freej2me.lite.nativeui.NativeLcdUiView;
import org.recompile.freej2me.lite.nativeui.NativeTextInputView;
import org.recompile.freej2me.lite.nativeui.NativeOverlays;
import org.recompile.freej2me.lite.audio.AndroidAudioController;

public final class RuntimeActivity extends LocalizedActivity {
    private static final int UI_BACKGROUND = Color.rgb(14, 24, 38);
    private static final int UI_SURFACE = Color.rgb(25, 36, 52);
    private static final int UI_TEXT = Color.rgb(232, 239, 247);
    private static final int UI_MUTED = Color.rgb(157, 170, 187);
    private static final int UI_ACCENT = Color.rgb(185, 195, 255);
    private static final int EDGE_PANEL_COLOR = 0xE6192434;
    private static final int EDGE_PANEL_STROKE_COLOR = 0x665C6675;
    private static final int DIALOG_ROW_COLOR = UI_SURFACE;
    private static final int DIALOG_ROW_STROKE_COLOR = 0x33455770;
    private static final int REQUEST_BT_PERMISSIONS = 7101;
    private static final int REQUEST_BT_ENABLE = 7102;
    private static final int REQUEST_BT_DISCOVERABLE = 7103;
    private static final int DEFAULT_DISCOVERABLE_SECONDS = 300;
    private static final int FONT_METRICS_MAGIC = 0x4A324D46;
    private static final int FONT_METRICS_VERSION = 1;
    private static final int FONT_METRICS_FAMILIES = 3;
    private static final int FONT_METRICS_STYLES = 4;
    private static final int FONT_METRICS_CHARACTERS = 65536;
    private static final int FONT_METRICS_CHUNK_SIZE = 1024;
    private static final int[] EMULATION_SPEED_PRESETS = {
            25, 50, 75, 100, 125, 150, 200, 300, 400, 600, 800, 1000,
            1200, 1500, BluetoothProtocol.MAX_EMULATION_SPEED_PERCENT
    };
    private static final String FONT_MATCH_PROBE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?+-_()[]{}";

    private static final class TypefaceFontMatch {
        final String path;
        final double score;
        TypefaceFontMatch(String path, double score) {
            this.path = path;
            this.score = score;
        }
    }

    private static final class BluetoothRequest {
        final int id, operation, flags, number;
        final String value, extra;
        final byte[] data;
        BluetoothRequest(int id, int operation, int flags, int number,
                String value, String extra, byte[] data) {
            this.id = id;
            this.operation = operation;
            this.flags = flags;
            this.number = number;
            this.value = value == null ? "" : value;
            this.extra = extra == null ? "" : extra;
            this.data = data == null ? new byte[0] : data;
        }
    }
    private static final class BluetoothResult {
        final boolean accepted;
        final int number;
        final String detail;
        final byte[] data;
        BluetoothResult(boolean accepted, int number, String detail, byte[] data) {
            this.accepted = accepted;
            this.number = number;
            this.detail = detail == null ? "" : detail;
            this.data = data == null ? new byte[0] : data;
        }
    }
    private static final class MemoryEditorEntry {
        final int id;
        final String type, value, location, referenceKind;
        MemoryEditorEntry(int id, String type, String value, String location,
                String referenceKind) {
            this.id = id;
            this.type = type == null ? "" : type;
            this.value = value == null ? "" : value;
            this.location = location == null ? "" : location;
            this.referenceKind = referenceKind == null ? "" : referenceKind;
        }
    }
    private static final class MemoryEditorReply {
        final int requestId, operation, total;
        final boolean success;
        final String message;
        final ArrayList<MemoryEditorEntry> entries;
        MemoryEditorReply(int requestId, int operation, boolean success, String message,
                int total, ArrayList<MemoryEditorEntry> entries) {
            this.requestId = requestId;
            this.operation = operation;
            this.success = success;
            this.message = message == null ? "" : message;
            this.total = total;
            this.entries = entries;
        }
    }
    private interface MemoryEditorCallback {
        void onReply(MemoryEditorReply reply);
    }
    private static final class ResponsiveDialog {
        final Dialog dialog;
        final Runnable resize;
        ResponsiveDialog(Dialog dialog, Runnable resize) {
            this.dialog = dialog;
            this.resize = resize;
        }
    }

    private FrameLayout root;
    private FramePresentation screen;
    private View screenView;
    private FrameLayout gamePane;
    private FrameLayout controlsLayer;
    private CanvasDecorationView canvasDecoration;
    private EdgeSwipeMenu edgeMenu;
    private EditText keyboardInput;
    private FpsOverlayView fpsOverlay;
    private GamepadControlView gamepadControls;
    private GameSettings gameSettings;
    private SparseIntArray physicalKeyMappings;
    private Dialog runtimeSettingsDialog;
    private TextView runtimeUpdateDescription;
    private TextView runtimeUpdateAction;
    private AppUpdateManager.Release runtimeAvailableUpdate;
    private boolean checkingForUpdate;
    private boolean gamepadVisibleForSession;
    private View leftSoftKeyAnchor;
    private NativeLcdUiView nativeScreen;
    private NativeOverlays overlays;
    private volatile boolean nativeVisible;
    // Keep input overlays hidden while the JVM starts and until the MIDlet's first screen is ready.
    private volatile boolean initialDisplayReady;
    private boolean awaitingInitialCanvasFrame;
    private int initialCanvasFrameBaseline;
    private volatile ServerSocket server;
    private volatile ServerSocket fontServer;
    private volatile Socket socket;
    private volatile DataOutputStream commands;
    private NativeTextInputView textInputPanel;
    private AndroidAudioController audio;
    private int textInputRequestId = -1;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final Set<Integer> heldKeys = new HashSet<>();
    private final Map<Long, Integer> physicalKeyEvents = new HashMap<>();
    private final ConcurrentMap<Integer, MemoryEditorCallback> memoryEditorReplies =
            new ConcurrentHashMap<>();
    private final AtomicInteger nextMemoryEditorRequest = new AtomicInteger();
    private final ArrayList<ResponsiveDialog> responsiveDialogs = new ArrayList<>();
    private final ArrayDeque<BluetoothRequest> bluetoothQueue = new ArrayDeque<>();
    private final ExecutorService bluetoothWorkers = Executors.newCachedThreadPool();
    private final ConcurrentMap<Integer, BluetoothServerSocket> bluetoothServers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, BluetoothSocket> bluetoothSockets = new ConcurrentHashMap<>();
    private final AtomicInteger nextBluetoothHandle = new AtomicInteger();
    private BluetoothRequest activeBluetoothRequest;
    private long discoverabilityGrantedUntil;
    private boolean discoverabilityDeclinedForSession;
    private boolean keyboardRequested;
    private BroadcastReceiver bluetoothDiscoveryReceiver;
    private BroadcastReceiver bluetoothUuidReceiver;
    private volatile boolean stopping;
    private volatile int emulationSpeedPercent = 100;
    private MemoryEditorDialog memoryEditorDialog;
    private int frameCount;
    private int fpsIntervalFrames;
    private long fpsIntervalStart;
    private boolean fpsEnabled;
    private volatile boolean debugLogsEnabled;
    private volatile boolean sessionLogStarted;
    private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;
    private Thread.UncaughtExceptionHandler diagnosticUncaughtExceptionHandler;
    private boolean statusBarVisible;
    private boolean orientationLocked;
    private volatile int displayRefreshHz;
    private int statusBarInsetTop;
    private DisplayManager displayManager;
    private boolean displayListenerRegistered;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { }
        @Override public void onDisplayRemoved(int displayId) { }
        @Override public void onDisplayChanged(int displayId) {
            if (displayId == getWindowManager().getDefaultDisplay().getDisplayId())
                updateDisplayRefreshRate();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(UI_BACKGROUND);
        getWindow().setNavigationBarColor(UI_BACKGROUND);
        getWindow().setBackgroundDrawable(new ColorDrawable(UI_BACKGROUND));
        getWindow().getDecorView().setBackgroundColor(UI_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        String jarPath = getIntent().getStringExtra("jar");
        gameSettings = GameSettings.load(this, jarPath == null ? null : new File(jarPath));
        physicalKeyMappings = PhysicalKeyMapping.decode(gameSettings.physicalKeyMappings);
        gamepadVisibleForSession = gameSettings.gamepadEnabled;
        fpsEnabled = getIntent().getBooleanExtra(
                AppPreferences.EXTRA_SHOW_FPS, AppPreferences.showFps(this));
        debugLogsEnabled = getIntent().getBooleanExtra(
                AppPreferences.EXTRA_DEBUG_LOGS, AppPreferences.debugLogsEnabled(this));
        installDiagnosticCrashHandler();
        statusBarVisible = getIntent().getBooleanExtra(
                AppPreferences.EXTRA_STATUS_BAR, AppPreferences.statusBarVisible(this));
        orientationLocked = getIntent().getBooleanExtra(
                AppPreferences.EXTRA_LOCK_ORIENTATION, AppPreferences.orientationLocked(this));
        if (orientationLocked) {
            setRequestedOrientation(isLandscape()
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        applyStatusBarPreference();
        gameSettings.gamepadLayoutId = GamepadTemplateLibrary.resolveId(
                this, gameSettings.gamepadLayoutId);
        root = new FrameLayout(this);
        root.setBackgroundColor(UI_BACKGROUND);
        gamePane = new FrameLayout(this);
        gamePane.setBackgroundColor(UI_BACKGROUND);
        root.addView(gamePane, new FrameLayout.LayoutParams(-1, -1));
        controlsLayer = new FrameLayout(this);
        controlsLayer.setClipChildren(false);
        controlsLayer.setClipToPadding(false);
        root.addView(controlsLayer, new FrameLayout.LayoutParams(-1, -1));
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        displayRefreshHz = readDisplayRefreshRate();
        if (gameSettings.crtFilterEnabled) {
            screen = new ShaderFrameView(this, (action, x, y) -> send(action, x, y),
                    displayRefreshHz, gameSettings.canvasWidth, gameSettings.canvasHeight);
        } else {
            screen = new FrameView(this, (action, x, y) -> send(action, x, y),
                    displayRefreshHz);
        }
        screenView = (View)screen;
        screen.setPointerInputEnabled(gameSettings.touchEnabled);
        gamePane.addView(screenView, new FrameLayout.LayoutParams(-1, -1,
                Gravity.TOP | Gravity.LEFT));
        canvasDecoration = new CanvasDecorationView(this);
        canvasDecoration.setOutlineEnabled(gameSettings.canvasOutlineEnabled);
        gamePane.addView(canvasDecoration, new FrameLayout.LayoutParams(-1, -1,
                Gravity.TOP | Gravity.LEFT));
        gamepadControls = new GamepadControlView(this,
                GamepadTemplateLibrary.get(this, gameSettings.gamepadLayoutId), this::key);
        gamepadControls.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                view.post(this::updateCanvasLayout);
            }
        });
        controlsLayer.addView(gamepadControls, new FrameLayout.LayoutParams(-1, -1,
                Gravity.TOP | Gravity.LEFT));
        nativeScreen = new NativeLcdUiView(this, this::sendNativeEvent);
        overlays = new NativeOverlays(this, this::sendNativeEvent);
        nativeScreen.setVisibility(View.GONE);
        root.addView(nativeScreen, new FrameLayout.LayoutParams(-1, -1));
        keyboardInput = createKeyboardInput();
        FrameLayout.LayoutParams keyboardParams = new FrameLayout.LayoutParams(1, 1,
                Gravity.TOP | Gravity.LEFT);
        root.addView(keyboardInput, keyboardParams);
        fpsOverlay = new FpsOverlayView(this);
        fpsOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams fpsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        fpsParams.topMargin = dp(8);
        fpsParams.rightMargin = dp(10);
        root.addView(fpsOverlay, fpsParams);
        edgeMenu = new EdgeSwipeMenu(this);
        edgeMenu.setVisibility(View.GONE);
        root.addView(edgeMenu, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            applyRuntimeInsets(insets);
            return insets;
        });
        audio = new AndroidAudioController(new File(getCacheDir(), "freej2me-audio"), this::sendAudioEvent);
        setContentView(root);
        gamePane.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateCanvasLayout());
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right <= left || bottom <= top) return;
            updateCanvasLayout();
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                if (edgeMenu != null) edgeMenu.refreshForCurrentViewport();
                resizeResponsiveDialogs();
            }
        });
        updateCanvasLayout();
        root.requestApplyInsets();
        new Thread(this::launchEngine, "RuntimeLauncher").start();
    }

    private void applyStatusBarPreference() {
        boolean editingTextInput = textInputPanel != null;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!editingTextInput) flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!statusBarVisible && !editingTextInput) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (statusBarVisible || editingTextInput) {
                    controller.show(WindowInsets.Type.statusBars());
                } else {
                    controller.hide(WindowInsets.Type.statusBars());
                }
            }
        }
    }

    private void applyRuntimeInsets(WindowInsets insets) {
        if (root == null || gamePane == null || controlsLayer == null) return;
        boolean editingTextInput = textInputPanel != null;
        int topInset = !editingTextInput && statusBarVisible
                ? insets.getStableInsetTop() : 0;
        if (!editingTextInput && statusBarVisible && topInset <= 0)
            topInset = insets.getSystemWindowInsetTop();
        if (!editingTextInput && !statusBarVisible && Build.VERSION.SDK_INT >= 28) {
            android.view.DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) topInset = cutout.getSafeInsetTop();
        }
        statusBarInsetTop = Math.max(0, topInset);
        if (fpsOverlay != null) {
            FrameLayout.LayoutParams fpsParams =
                    (FrameLayout.LayoutParams) fpsOverlay.getLayoutParams();
            int fpsTop = statusBarInsetTop + dp(8);
            if (fpsParams.topMargin != fpsTop) {
                fpsParams.topMargin = fpsTop;
                fpsOverlay.setLayoutParams(fpsParams);
            }
        }
        FrameLayout.LayoutParams gameParams =
                (FrameLayout.LayoutParams)gamePane.getLayoutParams();
        if (gameParams.topMargin != statusBarInsetTop) {
            gameParams.topMargin = statusBarInsetTop;
            gamePane.setLayoutParams(gameParams);
        }
        FrameLayout.LayoutParams nativeParams =
                (FrameLayout.LayoutParams)nativeScreen.getLayoutParams();
        if (nativeParams.topMargin != statusBarInsetTop) {
            nativeParams.topMargin = statusBarInsetTop;
            nativeScreen.setLayoutParams(nativeParams);
        }
        FrameLayout.LayoutParams controlsParams =
                (FrameLayout.LayoutParams)controlsLayer.getLayoutParams();
        // Status-bar insets belong to the game surface, never to the gamepad template.
        if (controlsParams.topMargin != 0) {
            controlsParams.topMargin = 0;
            controlsLayer.setLayoutParams(controlsParams);
        }
        if (gamepadControls != null) gamepadControls.setCoordinateOriginTopInset(0);
        if (edgeMenu != null) edgeMenu.setContentTopInset(statusBarInsetTop);
        if (textInputPanel != null) {
            FrameLayout.LayoutParams inputParams =
                    (FrameLayout.LayoutParams)textInputPanel.getLayoutParams();
            if (inputParams.topMargin != statusBarInsetTop) {
                inputParams.topMargin = statusBarInsetTop;
                textInputPanel.setLayoutParams(inputParams);
            }
        }
        updateCanvasLayout();
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isLandscapeViewport() {
        int width = root == null ? 0 : root.getWidth();
        int height = root == null ? 0 : root.getHeight();
        if (width > 0 && height > 0) return width > height;
        width = gamePane == null ? 0 : gamePane.getWidth();
        height = gamePane == null ? 0 : gamePane.getHeight();
        if (width > 0 && height > 0) return width > height;
        width = controlsLayer == null ? 0 : controlsLayer.getWidth();
        height = controlsLayer == null ? 0 : controlsLayer.getHeight();
        if (width > 0 && height > 0) return width > height;
        return isLandscape();
    }

    private void updateCanvasLayout() {
        if (gamePane == null || gamepadControls == null || screen == null
                || screenView == null || gameSettings == null || controlsLayer == null) return;
        // Use measured window bounds here: Configuration can briefly still report the
        // previous orientation while the gamepad and content are already being resized.
        boolean landscape = isLandscapeViewport();
        boolean controlsVisible = initialDisplayReady && gamepadVisibleForSession
                && !nativeVisible && textInputPanel == null;
        if (controlsLayer.getVisibility() != (controlsVisible ? View.VISIBLE : View.GONE)) {
            controlsLayer.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        }

        int reservedBottom = 0;
        int reservedLeft = 0;
        int reservedRight = 0;
        boolean separateCanvas = gameSettings.reserveGamepadArea;
        boolean reserveControls = separateCanvas && controlsVisible;
        if (reserveControls && controlsLayer.getWidth() > 0 && controlsLayer.getHeight() > 0) {
            int gamepadGap = dp(8);
            if (landscape) {
                int[] sides = gamepadControls.reservedSideWidths(
                        controlsLayer.getWidth(), controlsLayer.getHeight());
                reservedLeft = sides[0] > 0 ? sides[0] + gamepadGap : 0;
                reservedRight = sides[1] > 0 ? sides[1] + gamepadGap : 0;
            } else {
                reservedBottom = gamepadControls.reservedHeight(
                        gamepadControls.getWidth(), gamepadControls.getHeight());
                if (reservedBottom > 0) reservedBottom += gamepadGap;
            }
        }
        int edgeInset = gameSettings.canvasOutlineEnabled ? dp(8) : 0;
        int topMargin = edgeInset;
        int bottomMargin = reservedBottom + edgeInset;
        int leftMargin = reservedLeft + edgeInset;
        int rightMargin = reservedRight + edgeInset;
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams)screenView.getLayoutParams();
        if (params.width != FrameLayout.LayoutParams.MATCH_PARENT
                || params.height != FrameLayout.LayoutParams.MATCH_PARENT
                || params.gravity != (Gravity.TOP | Gravity.LEFT)
                || params.topMargin != topMargin
                || params.bottomMargin != bottomMargin
                || params.leftMargin != leftMargin
                || params.rightMargin != rightMargin) {
            params.width = FrameLayout.LayoutParams.MATCH_PARENT;
            params.height = FrameLayout.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.topMargin = topMargin;
            params.bottomMargin = bottomMargin;
            params.leftMargin = leftMargin;
            params.rightMargin = rightMargin;
            screenView.setLayoutParams(params);
        }
        FrameLayout.LayoutParams decorationParams =
                (FrameLayout.LayoutParams)canvasDecoration.getLayoutParams();
        if (decorationParams.width != FrameLayout.LayoutParams.MATCH_PARENT
                || decorationParams.height != FrameLayout.LayoutParams.MATCH_PARENT
                || decorationParams.gravity != (Gravity.TOP | Gravity.LEFT)
                || decorationParams.topMargin != topMargin
                || decorationParams.bottomMargin != bottomMargin
                || decorationParams.leftMargin != leftMargin
                || decorationParams.rightMargin != rightMargin) {
            decorationParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            decorationParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            decorationParams.gravity = Gravity.TOP | Gravity.LEFT;
            decorationParams.topMargin = topMargin;
            decorationParams.bottomMargin = bottomMargin;
            decorationParams.leftMargin = leftMargin;
            decorationParams.rightMargin = rightMargin;
            canvasDecoration.setLayoutParams(decorationParams);
        }
        int canvasScale = separateCanvas ? 100 : gameSettings.canvasScale;
        int canvasPosition = separateCanvas ? (landscape ? 1 : 0) : gameSettings.canvasPosition;
        screen.setCanvasPresentation(canvasScale, canvasPosition);
        canvasDecoration.setCanvasPresentation(canvasScale, canvasPosition);
        canvasDecoration.setOutlineEnabled(gameSettings.canvasOutlineEnabled);
    }
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundDrawable(int color, int radiusPx, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void styleMemoryInput(EditText field, boolean compact) {
        int radius = dp(14);
        int verticalPadding = dp(compact ? 8 : 10);
        field.setTextColor(UI_TEXT);
        field.setHintTextColor(UI_MUTED);
        field.setTextSize(16);
        field.setMinHeight(dp(48));
        field.setPadding(dp(14), verticalPadding, dp(14), verticalPadding);
        field.setBackground(roundDrawable(UI_SURFACE, radius, 0xFF3A4D65));
        field.setOnFocusChangeListener((view, focused) -> field.setBackground(
                roundDrawable(UI_SURFACE, radius,
                        focused ? 0xFF9FC2FF : 0xFF3A4D65)));
    }

    private EditText createKeyboardInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setTextColor(Color.TRANSPARENT);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setAlpha(0.01f);
        input.setCursorVisible(false);
        // This hidden input must not become the Activity's restored focus target.
        // It is made focusable only while the user explicitly opens the gameplay IME.
        input.setFocusable(false);
        input.setFocusableInTouchMode(false);
        input.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});
        input.addTextChangedListener(new TextWatcher() {
            private boolean clearingCommittedText;
            private int changedStart;
            private int removedCount;
            private int insertedCount;

            @Override public void beforeTextChanged(CharSequence text, int start,
                    int count, int after) { }

            @Override public void onTextChanged(CharSequence text, int start,
                    int before, int count) {
                changedStart = start;
                removedCount = before;
                insertedCount = count;
            }

            @Override public void afterTextChanged(Editable editable) {
                if (clearingCommittedText) return;
                int composingStart = BaseInputConnection.getComposingSpanStart(editable);
                int composingEnd = BaseInputConnection.getComposingSpanEnd(editable);
                int changedEnd = changedStart + insertedCount;
                boolean composing = composingStart >= 0 && composingEnd > composingStart
                        && composingStart < changedEnd && composingEnd > changedStart;
                if (composing) return;

                if (insertedCount > 0 && changedStart >= 0
                        && changedEnd <= editable.length()) {
                    String committed = editable.subSequence(changedStart, changedEnd).toString();
                    clearingCommittedText = true;
                    editable.delete(changedStart, changedEnd);
                    clearingCommittedText = false;
                    for (int offset = 0; offset < committed.length();) {
                        int codePoint = committed.codePointAt(offset);
                        key(codePoint, true);
                        key(codePoint, false);
                        offset += Character.charCount(codePoint);
                    }
                } else if (removedCount > 0) {
                    key(8, true);
                    key(8, false);
                }
            }
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                hideGameKeyboard();
                return true;
            }
            return false;
        });
        return input;
    }

    private void showGameKeyboard() {
        if (keyboardInput == null || textInputPanel != null) return;
        if (edgeMenu != null) edgeMenu.closeMenu();
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        keyboardRequested = true;
        keyboardInput.setFocusable(true);
        keyboardInput.setFocusableInTouchMode(true);
        keyboardInput.setText("");
        keyboardInput.requestFocus();
        keyboardInput.post(() -> {
            if (!keyboardRequested || !hasWindowFocus() || isFinishing() || isDestroyed()) return;
            InputMethodManager manager = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null)
                manager.showSoftInput(keyboardInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideGameKeyboard() {
        keyboardRequested = false;
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (keyboardInput == null) return;
        InputMethodManager manager = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null && keyboardInput.getWindowToken() != null)
            manager.hideSoftInputFromWindow(keyboardInput.getWindowToken(), 0);
        keyboardInput.clearFocus();
        keyboardInput.setFocusable(false);
        keyboardInput.setFocusableInTouchMode(false);
    }

    private void showMemoryEditor() {
        Dialog dialog = new Dialog(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(14));
        card.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                EDGE_PANEL_STROKE_COLOR));

        TextView title = new TextView(this);
        title.setText(AppLocalization.text(this, "Trình sửa bộ nhớ"));
        title.setTextColor(UI_TEXT);
        title.setTextSize(21);
        card.addView(title);

        TextView description = new TextView(this);
        description.setText(AppLocalization.text(this,
                "Chỉ dùng với game offline. Trình sửa chỉ tìm và thay đổi giá trị số "
                        + "trong bộ nhớ Java của game đang chạy. Sau khi mở, kết nối mạng "
                        + "sẽ bị chặn đến khi khởi động lại game; nếu game đã dùng mạng, "
                        + "tính năng không khả dụng."));
        description.setTextColor(UI_MUTED);
        description.setTextSize(14);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(7);
        card.addView(description, descriptionParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(12);
        card.addView(actions, actionsParams);
        TextView cancel = dialogAction(AppLocalization.text(this, "Hủy"));
        TextView proceed = dialogAction(AppLocalization.text(this, "Tiếp tục"));
        cancel.setTextColor(UI_MUTED);
        proceed.setTextColor(UI_ACCENT);
        actions.addView(cancel);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
        actions.addView(proceed);
        cancel.setOnClickListener(view -> dialog.dismiss());
        proceed.setOnClickListener(view -> {
            dialog.dismiss();
            sendMemoryEditorRequest(0, "", 0, "", reply -> {
                if (!reply.success) {
                    Toast.makeText(this, AppLocalization.text(this, reply.message),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                memoryEditorDialog = new MemoryEditorDialog();
                memoryEditorDialog.show();
            });
        });

        dialog.setContentView(card);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        registerResponsiveDialog(dialog,
                () -> setDialogWindowSize(dialog, 36, 480,
                        ViewGroup.LayoutParams.WRAP_CONTENT),
                null);
    }

    private final class MemoryEditorDialog {
        private final Dialog dialog = new Dialog(RuntimeActivity.this);
        private final EditText query = new EditText(RuntimeActivity.this);
        private final TextView status = new TextView(RuntimeActivity.this);
        private final LinearLayout results = new LinearLayout(RuntimeActivity.this);
        private TextView searchAction;
        private boolean closing;

        void show() {
            boolean landscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            LinearLayout card = new LinearLayout(RuntimeActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            int horizontalPadding = dp(landscape ? 14 : 18);
            card.setPadding(horizontalPadding, dp(landscape ? 10 : 16),
                    horizontalPadding, dp(landscape ? 9 : 14));
            card.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                    EDGE_PANEL_STROKE_COLOR));

            LinearLayout titleRow = new LinearLayout(RuntimeActivity.this);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(RuntimeActivity.this);
            title.setText(AppLocalization.text(RuntimeActivity.this, "Trình sửa bộ nhớ"));
            title.setTextColor(UI_TEXT);
            title.setTextSize(landscape ? 19 : 21);
            titleRow.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView close = new TextView(RuntimeActivity.this);
            close.setText("×");
            close.setTextColor(UI_TEXT);
            close.setTextSize(30);
            close.setGravity(Gravity.CENTER);
            close.setPadding(dp(8), 0, dp(4), 0);
            titleRow.addView(close);
            card.addView(titleRow);
            close.setOnClickListener(view -> close());

            TextView explanation = new TextView(RuntimeActivity.this);
            explanation.setText(AppLocalization.text(RuntimeActivity.this,
                    "Tìm giá trị số trong heap Java của MIDlet đang chạy. "
                            + "Kết quả bị giới hạn để tránh làm chậm game."));
            explanation.setTextColor(UI_MUTED);
            explanation.setTextSize(landscape ? 12 : 14);
            LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            explanationParams.topMargin = dp(landscape ? 2 : 5);
            card.addView(explanation, explanationParams);

            LinearLayout searchRow = new LinearLayout(RuntimeActivity.this);
            searchRow.setOrientation(LinearLayout.HORIZONTAL);
            searchRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams searchRowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            searchRowParams.topMargin = dp(landscape ? 6 : 12);
            card.addView(searchRow, searchRowParams);

            query.setSingleLine(true);
            String queryHintText = AppLocalization.text(RuntimeActivity.this, "Giá trị số");
            android.text.SpannableString queryHint =
                    new android.text.SpannableString(queryHintText);
            queryHint.setSpan(new android.text.style.AbsoluteSizeSpan(14, true), 0,
                    queryHint.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            query.setHint(queryHint);
            query.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            styleMemoryInput(query, landscape);
            query.setHintTextColor(0xFF8290A2);
            LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            searchRow.addView(query, queryParams);
            searchAction = dialogAction(AppLocalization.text(RuntimeActivity.this, "Tìm kiếm"));
            searchAction.setTextColor(UI_BACKGROUND);
            searchAction.setTextSize(14);
            searchAction.setBackground(roundDrawable(UI_ACCENT, dp(24), Color.TRANSPARENT));
            searchAction.setMinHeight(dp(48));
            searchAction.setPadding(dp(18), dp(10), dp(18), dp(10));
            LinearLayout.LayoutParams searchActionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            searchActionParams.leftMargin = dp(8);
            searchRow.addView(searchAction, searchActionParams);
            searchAction.setOnClickListener(view -> runSearch());

            status.setText(AppLocalization.text(RuntimeActivity.this,
                    "Nhập giá trị rồi tìm kiếm."));
            status.setTextColor(UI_TEXT);
            status.setTextSize(14);
            status.setPadding(0, dp(landscape ? 4 : 8), 0, dp(landscape ? 4 : 8));
            card.addView(status);

            ScrollView scroll = new ScrollView(RuntimeActivity.this);
            results.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(results, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            dialog.setContentView(card);
            dialog.setCancelable(true);
            DialogInterface.OnDismissListener dismissListener = ignored -> {
                if (!closing) sendMemoryEditorRequest(4, "", 0, "", null);
                if (memoryEditorDialog == this) memoryEditorDialog = null;
            };
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.dimAmount = 0.60f;
                window.setAttributes(attributes);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            registerResponsiveDialog(dialog, this::resizeToAvailableArea, dismissListener);
        }

        void resizeToAvailableArea() {
            Window window = dialog.getWindow();
            if (window == null || !dialog.isShowing()) return;
            setDialogWindowSize(dialog, 28, 520, availableDialogHeight());
        }

        private void runSearch() {
            String value = query.getText().toString().trim().replace(',', '.');
            if (value.isEmpty()) {
                query.setError(AppLocalization.text(RuntimeActivity.this, "Nhập giá trị số"));
                return;
            }
            status.setText(AppLocalization.text(RuntimeActivity.this, "Đang tìm kiếm…"));
            sendMemoryEditorRequest(1, value, 0, "", this::showResults);
        }

        private void showResults(MemoryEditorReply reply) {
            if (!reply.success) {
                results.removeAllViews();
                status.setText(AppLocalization.text(RuntimeActivity.this, reply.message));
                return;
            }
            status.setText(memoryResultCount(reply.total));
            results.removeAllViews();
            if (reply.entries.isEmpty()) {
                TextView empty = new TextView(RuntimeActivity.this);
                empty.setText(AppLocalization.text(RuntimeActivity.this, "Không có kết quả"));
                empty.setTextColor(UI_MUTED);
                empty.setTextSize(16);
                empty.setGravity(Gravity.CENTER);
                boolean landscape = getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
                empty.setPadding(0, dp(landscape ? 16 : 32), 0, dp(landscape ? 16 : 32));
                results.addView(empty);
                return;
            }
            boolean landscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            for (MemoryEditorEntry entry : reply.entries) {
                LinearLayout row = new LinearLayout(RuntimeActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(landscape ? 12 : 14), dp(landscape ? 7 : 10),
                        dp(landscape ? 12 : 14), dp(landscape ? 7 : 10));
                row.setBackground(roundDrawable(DIALOG_ROW_COLOR, dp(16),
                        DIALOG_ROW_STROKE_COLOR));
                LinearLayout valueRow = new LinearLayout(RuntimeActivity.this);
                valueRow.setOrientation(LinearLayout.HORIZONTAL);
                valueRow.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(valueRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView value = new TextView(RuntimeActivity.this);
                value.setText(entry.value);
                value.setTextColor(UI_TEXT);
                value.setTextSize(landscape ? 16 : 18);
                value.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                        android.graphics.Typeface.NORMAL));
                valueRow.addView(value, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView type = new TextView(RuntimeActivity.this);
                type.setText(entry.type);
                type.setTextColor(UI_ACCENT);
                type.setTextSize(12);
                type.setPadding(dp(8), dp(4), dp(8), dp(4));
                type.setBackground(roundDrawable(0xFF243951, dp(10), 0x554B5E7A));
                valueRow.addView(type);
                TextView metadata = new TextView(RuntimeActivity.this);
                metadata.setText(entry.location + "  ·  "
                        + AppLocalization.text(RuntimeActivity.this, entry.referenceKind));
                metadata.setTextColor(UI_MUTED);
                metadata.setTextSize(landscape ? 11 : 12);
                metadata.setMaxLines(2);
                metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                metadataParams.topMargin = dp(3);
                row.addView(metadata, metadataParams);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(landscape ? 4 : 7);
                results.addView(row, rowParams);
                row.setOnClickListener(view -> editEntry(entry));
            }
            if (reply.total > reply.entries.size()) {
                TextView more = new TextView(RuntimeActivity.this);
                more.setText(AppLocalization.text(RuntimeActivity.this,
                        "Hiển thị 100 kết quả đầu tiên"));
                more.setTextColor(UI_MUTED);
                more.setTextSize(13);
                more.setGravity(Gravity.CENTER);
                more.setPadding(0, dp(8), 0, dp(8));
                results.addView(more);
            }
        }

        private String memoryResultCount(int total) {
            if (AppLocalization.isEnglish(RuntimeActivity.this))
                return total + (total == 1 ? " result" : " results");
            return total + " kết quả";
        }

        private void editEntry(MemoryEditorEntry entry) {
            boolean landscape = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            Dialog editDialog = new Dialog(RuntimeActivity.this);
            LinearLayout card = new LinearLayout(RuntimeActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            int editPadding = dp(landscape ? 16 : 20);
            card.setPadding(editPadding, dp(landscape ? 12 : 18), editPadding,
                    dp(landscape ? 10 : 14));
            card.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                    EDGE_PANEL_STROKE_COLOR));

            TextView title = new TextView(RuntimeActivity.this);
            title.setText(AppLocalization.text(RuntimeActivity.this, "Sửa giá trị"));
            title.setTextColor(UI_TEXT);
            title.setTextSize(landscape ? 19 : 21);
            card.addView(title);

            TextView metadata = new TextView(RuntimeActivity.this);
            metadata.setText(entry.location + "  ·  " + entry.type + "  ·  "
                    + AppLocalization.text(RuntimeActivity.this, entry.referenceKind));
            metadata.setTextColor(UI_MUTED);
            metadata.setTextSize(12);
            metadata.setMaxLines(2);
            metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            metadataParams.topMargin = dp(landscape ? 3 : 5);
            card.addView(metadata, metadataParams);

            EditText value = new EditText(RuntimeActivity.this);
            value.setSingleLine(true);
            value.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            styleMemoryInput(value, landscape);
            value.setText(entry.value);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueParams.topMargin = dp(landscape ? 9 : 14);
            card.addView(value, valueParams);

            LinearLayout actions = new LinearLayout(RuntimeActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionsParams.topMargin = dp(landscape ? 8 : 14);
            card.addView(actions, actionsParams);
            TextView cancel = dialogAction(AppLocalization.text(RuntimeActivity.this, "Hủy"));
            TextView save = dialogAction(AppLocalization.text(RuntimeActivity.this, "Lưu"));
            cancel.setTextColor(UI_MUTED);
            save.setTextColor(UI_ACCENT);
            actions.addView(cancel);
            actions.addView(save);
            cancel.setOnClickListener(view -> editDialog.dismiss());
            save.setOnClickListener(view -> sendMemoryEditorRequest(3, "", entry.id,
                    value.getText().toString(), reply -> {
                        editDialog.dismiss();
                        showResults(reply);
                    }));

            editDialog.setContentView(card);
            editDialog.setCancelable(true);
            editDialog.show();
            Window window = editDialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            registerResponsiveDialog(editDialog,
                    () -> setDialogWindowSize(editDialog, 36, 480,
                            ViewGroup.LayoutParams.WRAP_CONTENT),
                    null);
        }

        private void close() {
            closing = true;
            sendMemoryEditorRequest(4, "", 0, "", null);
            dialog.dismiss();
        }
    }

    private void showEmulationSpeedDialog() {
        final int startingSpeed = Math.min(
                BluetoothProtocol.MAX_EMULATION_SPEED_PERCENT, emulationSpeedPercent);
        final Dialog dialog = new Dialog(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(14));
        card.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                EDGE_PANEL_STROKE_COLOR));

        TextView title = new TextView(this);
        title.setText(AppLocalization.text(this, "Tốc độ giả lập"));
        title.setTextColor(UI_TEXT);
        title.setTextSize(21);
        card.addView(title);

        TextView description = new TextView(this);
        description.setText(AppLocalization.text(this,
                "Thay đổi tốc độ đồng hồ MIDlet và các độ trễ được hỗ trợ. "
                        + "Âm thanh và giao diện Android vẫn ở tốc độ bình thường."));
        description.setTextColor(UI_MUTED);
        description.setTextSize(14);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(5);
        card.addView(description, descriptionParams);

        TextView value = new TextView(this);
        value.setTextColor(UI_ACCENT);
        value.setTextSize(20);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(10);
        card.addView(value, valueParams);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(EMULATION_SPEED_PRESETS.length - 1);
        int initialIndex = nearestSpeedPreset(emulationSpeedPercent);
        seekBar.setProgress(initialIndex);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(UI_ACCENT));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(UI_ACCENT));
        seekBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF455770));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = dp(8);
        card.addView(seekBar, seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = EMULATION_SPEED_PRESETS[Math.max(0,
                        Math.min(EMULATION_SPEED_PRESETS.length - 1, progress))];
                value.setText(formatSpeed(percent));
                if (fromUser) setEmulationSpeed(percent);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        value.setText(formatSpeed(emulationSpeedPercent));
        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        TextView minimum = new TextView(this);
        minimum.setText("0.25x");
        minimum.setTextColor(UI_MUTED);
        minimum.setTextSize(14);
        TextView maximum = new TextView(this);
        maximum.setText(formatSpeed(BluetoothProtocol.MAX_EMULATION_SPEED_PERCENT));
        maximum.setTextColor(UI_MUTED);
        maximum.setTextSize(14);
        range.addView(minimum, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        maximum.setGravity(Gravity.RIGHT);
        range.addView(maximum, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(range);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(22);
        card.addView(actions, actionsParams);
        TextView confirm = dialogAction(AppLocalization.text(this, "OK"));
        TextView reset = dialogAction(AppLocalization.text(this, "Thiết lập lại"));
        TextView cancel = dialogAction(AppLocalization.text(this, "Hủy"));
        cancel.setTextColor(UI_MUTED);
        reset.setTextColor(UI_ACCENT);
        confirm.setTextColor(UI_ACCENT);
        actions.addView(cancel);
        actions.addView(reset);
        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(
                0, dp(1), 1f));
        actions.addView(confirm);
        reset.setOnClickListener(view -> {
            seekBar.setProgress(nearestSpeedPreset(100));
            setEmulationSpeed(100);
        });
        cancel.setOnClickListener(view -> {
            setEmulationSpeed(startingSpeed);
            dialog.dismiss();
        });
        confirm.setOnClickListener(view -> dialog.dismiss());
        dialog.setContentView(card);
        dialog.setCancelable(true);
        dialog.setOnCancelListener(ignored -> setEmulationSpeed(startingSpeed));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        registerResponsiveDialog(dialog,
                () -> setDialogWindowSize(dialog, 40, 480,
                        ViewGroup.LayoutParams.WRAP_CONTENT),
                null);
    }

    private TextView dialogAction(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(0xFF9FC2FF);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private int nearestSpeedPreset(int percent) {
        int nearest = 0;
        for (int index = 1; index < EMULATION_SPEED_PRESETS.length; index++) {
            if (Math.abs(EMULATION_SPEED_PRESETS[index] - percent)
                    < Math.abs(EMULATION_SPEED_PRESETS[nearest] - percent)) nearest = index;
        }
        return nearest;
    }

    private static String formatSpeed(int percent) {
        if (percent % 100 == 0) return (percent / 100) + "x";
        String result = String.format(Locale.US, "%.2f", percent / 100f);
        while (result.endsWith("0")) result = result.substring(0, result.length() - 1);
        if (result.endsWith(".")) result = result.substring(0, result.length() - 1);
        return result + "x";
    }

    private void setEmulationSpeed(int percent) {
        int bounded = Math.max(25,
                Math.min(BluetoothProtocol.MAX_EMULATION_SPEED_PERCENT, percent));
        emulationSpeedPercent = bounded;
        send(BluetoothProtocol.SET_SPEED, bounded);
    }

    private int currentViewportWidthPx() {
        if (root != null && root.getWidth() > 0) return root.getWidth();
        Rect visibleFrame = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleFrame);
        if (visibleFrame.width() > 0) return visibleFrame.width();
        return getResources().getDisplayMetrics().widthPixels;
    }

    private void setDialogWindowSize(Dialog dialog, int sideMarginDp, int maxWidthDp,
            int height) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int availableWidth = Math.max(dp(1), currentViewportWidthPx() - dp(sideMarginDp));
        window.setLayout(Math.min(availableWidth, dp(maxWidthDp)), height);
    }

    private void registerResponsiveDialog(Dialog dialog, Runnable resize,
            DialogInterface.OnDismissListener onDismiss) {
        ResponsiveDialog entry = new ResponsiveDialog(dialog, resize);
        responsiveDialogs.add(entry);
        dialog.setOnDismissListener(dismissed -> {
            responsiveDialogs.remove(entry);
            if (onDismiss != null) onDismiss.onDismiss(dismissed);
        });
        resize.run();
    }

    private void resizeResponsiveDialogs() {
        for (ResponsiveDialog entry : new ArrayList<>(responsiveDialogs)) {
            if (!entry.dialog.isShowing()) {
                responsiveDialogs.remove(entry);
            } else {
                entry.resize.run();
            }
        }
    }

    private int availableDialogHeight() {
        int available = root != null && root.getHeight() > 0
                ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
        android.graphics.Rect visibleFrame = new android.graphics.Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleFrame);
        if (visibleFrame.height() > dp(200)) available = Math.min(available, visibleFrame.height());
        boolean landscape = isLandscapeViewport();
        int margin = landscape ? dp(20) : dp(36);
        int minimum = landscape ? dp(180) : dp(220);
        return Math.max(minimum, available - margin);
    }

    private void toggleGamepadForSession() {
        gamepadVisibleForSession = !gamepadVisibleForSession;
        if (!gamepadVisibleForSession && gamepadControls != null)
            gamepadControls.cancelActiveTouches();
        updateCanvasLayout();
    }

    private void showRuntimeSettings() {
        Dialog dialog = new Dialog(this);
        runtimeSettingsDialog = dialog;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(12));
        panel.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                EDGE_PANEL_STROKE_COLOR));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        boolean[] draftSettings = {
                fpsEnabled,
                statusBarVisible,
                orientationLocked
        };
        LinearLayout options = runtimeSettingsCard(content, "Tùy chọn");
        addRuntimeSettingsSwitch(options, "Hiển thị FPS",
                "Hiển thị tốc độ khung hình khi đang chơi game.",
                draftSettings[0], true, enabled -> draftSettings[0] = enabled);
        addRuntimeSettingsSwitch(options, "Ghi log để gỡ lỗi",
                "Chỉ áp dụng đầy đủ sau khi thoát rồi mở lại game.",
                debugLogsEnabled, false, null);
        addRuntimeSettingsSwitch(options, "Thanh trạng thái",
                "Hiển thị thanh trạng thái trong lúc chơi game.",
                draftSettings[1], true, enabled -> draftSettings[1] = enabled);
        addRuntimeSettingsSwitch(options, "Giữ hướng xoay hiện tại",
                "Khóa hướng màn hình theo chiều đang dùng khi mở game.",
                draftSettings[2], true, enabled -> draftSettings[2] = enabled);
        addRuntimeSettingsRow(options, "Ngôn ngữ",
                "Chọn tiếng Việt, tiếng Anh hoặc theo thiết bị.",
                runtimeLanguageChoiceLabel(), null);

        LinearLayout storage = runtimeSettingsCard(content, "Lưu trữ");
        String workingPath;
        try {
            workingPath = GameStorage.root(this).getAbsolutePath();
        } catch (IOException error) {
            workingPath = "";
        }
        addRuntimeSettingsRow(storage, "Thư mục làm việc", workingPath, "Chọn", null);

        LinearLayout details = runtimeSettingsCard(content, "Thông tin");
        addRuntimeUpdateRow(details);
        addRuntimeSettingsRow(details, "Giới thiệu",
                "Tổng quan ứng dụng và giấy phép thành phần.", "Xem",
                view -> showRuntimeIntroduction());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(6);
        panel.addView(actions, actionsParams);
        TextView cancel = dialogAction(AppLocalization.text(this, "Hủy"));
        cancel.setTextColor(UI_MUTED);
        TextView confirm = dialogAction(AppLocalization.text(this, "Xác nhận"));
        confirm.setTextColor(UI_ACCENT);
        actions.addView(cancel);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
        actions.addView(confirm);
        cancel.setOnClickListener(view -> dialog.dismiss());
        confirm.setOnClickListener(view -> {
            fpsEnabled = draftSettings[0];
            updateFpsOverlayVisibility();

            statusBarVisible = draftSettings[1];
            applyStatusBarPreference();
            if (root != null) root.requestApplyInsets();

            orientationLocked = draftSettings[2];
            AppPreferences.syncRuntimeSettingsToMain(this, fpsEnabled, debugLogsEnabled,
                    statusBarVisible, orientationLocked);
            dialog.dismiss();
            setRequestedOrientation(orientationLocked
                    ? (isLandscapeViewport()
                            ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        });
        DialogInterface.OnDismissListener dismissListener = ignored -> {
            if (runtimeSettingsDialog == dialog) runtimeSettingsDialog = null;
        };
        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.58f;
            window.setAttributes(attributes);
        }
        registerResponsiveDialog(dialog, this::resizeRuntimeSettingsDialog, dismissListener);
    }

    private void resizeRuntimeSettingsDialog() {
        if (runtimeSettingsDialog == null || !runtimeSettingsDialog.isShowing()) return;
        setDialogWindowSize(runtimeSettingsDialog, 28, 520, availableDialogHeight());
    }

    private String runtimeLanguageChoiceLabel() {
        int choice = AppPreferences.languageChoice(this);
        if (choice == 1) return AppLocalization.text(this, "Tiếng Việt");
        if (choice == 2) return "English";
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        return AppLocalization.text(this, vietnamese ? "Theo thiết bị" : "System");
    }

    private LinearLayout runtimeSettingsCard(LinearLayout parent, String section) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundDrawable(DIALOG_ROW_COLOR, dp(18),
                DIALOG_ROW_STROKE_COLOR));
        card.setClipToOutline(true);
        card.setPadding(dp(14), dp(8), dp(14), dp(10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(2), dp(4), dp(2), dp(10));
        parent.addView(card, cardParams);

        TextView heading = runtimeSettingsText(section, 14, UI_ACCENT);
        heading.setTypeface(Typeface.MONOSPACE);
        heading.setPadding(dp(2), dp(6), dp(2), dp(4));
        card.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void addRuntimeSettingsSwitch(LinearLayout parent, String label,
            String description, boolean checked, boolean enabled,
            RuntimeBooleanSetting setting) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(5), dp(2), dp(5));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(runtimeSettingsText(label, 15, UI_TEXT));
        TextView detail = runtimeSettingsText(description, 12, UI_MUTED);
        detail.setPadding(0, dp(3), dp(6), 0);
        textColumn.addView(detail);
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setEnabled(enabled);
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{UI_ACCENT, UI_MUTED}));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xFF5368D8, 0xFF46505D}));
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        row.setContentDescription(AppLocalization.text(this, label) + ". "
                + AppLocalization.text(this, description));
        if (!enabled || setting == null) {
            row.setAlpha(0.48f);
        } else {
            toggle.setOnCheckedChangeListener((button, value) -> setting.set(value));
            row.setClickable(true);
            row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        }
    }

    private void addRuntimeSettingsRow(LinearLayout parent, String label,
            String description, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(10), dp(2), dp(10));
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(runtimeSettingsText(label, 15, UI_TEXT));
        if (description != null && !description.isEmpty()) {
            TextView detail = runtimeSettingsText(description, 12, UI_MUTED);
            detail.setPadding(0, dp(3), dp(4), 0);
            textColumn.addView(detail);
        }
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null && !value.isEmpty()) {
            TextView valueView = runtimeSettingsText(value, 13, UI_ACCENT);
            valueView.setGravity(Gravity.CENTER);
            valueView.setMaxWidth(dp(140));
            valueView.setMaxLines(2);
            valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(valueView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(roundDrawable(Color.TRANSPARENT, dp(10), 0));
            row.setOnClickListener(listener);
        } else {
            row.setAlpha(0.48f);
        }
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addRuntimeUpdateRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(10), dp(2), dp(10));
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(runtimeSettingsText("Cập nhật", 15, UI_TEXT));
        runtimeUpdateDescription = runtimeSettingsText(
                "Chưa kiểm tra phiên bản mới", 12, UI_MUTED);
        runtimeUpdateDescription.setPadding(0, dp(3), dp(4), 0);
        textColumn.addView(runtimeUpdateDescription);
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        runtimeUpdateAction = runtimeSettingsText(
                runtimeAvailableUpdate == null ? "Kiểm tra" : "Tải về", 13, UI_ACCENT);
        runtimeUpdateAction.setGravity(Gravity.CENTER);
        row.addView(runtimeUpdateAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(roundDrawable(Color.TRANSPARENT, dp(10), 0));
        row.setOnClickListener(view -> showRuntimeUpdateCheck());
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshRuntimeUpdateRow();
    }

    private void refreshRuntimeUpdateRow() {
        if (runtimeUpdateDescription == null || runtimeUpdateAction == null) return;
        String state = checkingForUpdate ? "Đang kiểm tra cập nhật…"
                : runtimeAvailableUpdate == null ? "Chưa kiểm tra phiên bản mới"
                : "Đã có bản cập nhật mới";
        runtimeUpdateDescription.setText(AppLocalization.text(this, state));
        runtimeUpdateAction.setText(AppLocalization.text(this,
                runtimeAvailableUpdate == null ? "Kiểm tra" : "Tải về"));
    }

    private TextView runtimeSettingsText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(AppLocalization.text(this, value));
        text.setTextColor(color);
        text.setTextSize(size);
        return text;
    }

    private interface RuntimeBooleanSetting {
        void set(boolean enabled);
    }

    private void showRuntimeUpdateCheck() {
        if (runtimeAvailableUpdate != null) {
            AppUpdateUi.download(this, runtimeAvailableUpdate);
            return;
        }
        if (checkingForUpdate) return;
        checkingForUpdate = true;
        refreshRuntimeUpdateRow();
        AppUpdateUi.check(this, (release, error) -> {
            checkingForUpdate = false;
            runtimeAvailableUpdate = release;
            if (error != null && runtimeUpdateDescription != null) {
                runtimeUpdateDescription.setText(AppLocalization.text(this,
                        "Không kiểm tra được cập nhật"));
            } else if (release == null && runtimeUpdateDescription != null) {
                runtimeUpdateDescription.setText(AppLocalization.text(this,
                        "Hiện chưa có bản cập nhật"));
            } else if (runtimeUpdateDescription != null) {
                runtimeUpdateDescription.setText(AppLocalization.text(this,
                        "Đã có bản cập nhật mới"));
            }
            refreshRuntimeUpdateAction();
        });
    }

    private void refreshRuntimeUpdateAction() {
        if (runtimeUpdateAction != null) runtimeUpdateAction.setText(AppLocalization.text(this,
                runtimeAvailableUpdate == null ? "Kiểm tra" : "Tải về"));
    }

    private void showRuntimeIntroduction() {
        try {
            showRuntimeSettingsInfo("Giới thiệu",
                    ProjectInformation.introduction(this), true);
        } catch (IOException error) {
            Toast.makeText(this, AppLocalization.text(this, "Không thể thực hiện"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showRuntimeSettingsInfo(String title, String message, boolean scrollable) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                EDGE_PANEL_STROKE_COLOR));
        TextView heading = runtimeSettingsText(title, 21, UI_TEXT);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = runtimeSettingsText(message, 14, UI_TEXT);
        body.setTextIsSelectable(true);
        if (message.contains("https://github.com/huongdino443/J2MEX")) {
            Linkify.addLinks(body, Linkify.WEB_URLS);
            body.setLinkTextColor(UI_ACCENT);
            body.setMovementMethod(LinkMovementMethod.getInstance());
        }
        body.setLineSpacing(dp(2), 1f);
        final ScrollView scroll = scrollable ? new ScrollView(this) : null;
        if (scrollable) {
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(body, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(dp(160), availableDialogHeight() - dp(132)));
            scrollParams.topMargin = dp(12);
            panel.addView(scroll, scrollParams);
        } else {
            body.setPadding(0, dp(12), 0, 0);
            panel.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView close = runtimeSettingsText("Đóng", 14, UI_ACCENT);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = dp(10);
        actions.addView(close, closeParams);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        registerResponsiveDialog(dialog, () -> {
            if (scroll != null) {
                ViewGroup.LayoutParams scrollParams = scroll.getLayoutParams();
                int scrollHeight = Math.max(dp(160), availableDialogHeight() - dp(132));
                if (scrollParams.height != scrollHeight) {
                    scrollParams.height = scrollHeight;
                    scroll.setLayoutParams(scrollParams);
                }
            }
            setDialogWindowSize(dialog, 28, 520,
                    scrollable ? availableDialogHeight() : ViewGroup.LayoutParams.WRAP_CONTENT);
        }, null);
        close.setOnClickListener(view -> dialog.dismiss());
    }

    private void captureCanvasScreenshot() {
        if (nativeVisible || !initialDisplayReady || screenView == null
                || screenView.getWidth() <= 0 || screenView.getHeight() <= 0) {
            showRuntimeToast("Chưa có canvas game để chụp.");
            return;
        }
        RectF canvasBounds = new RectF();
        if (screen == null || !screen.getCanvasBounds(canvasBounds)) {
            showRuntimeToast("Chưa có canvas game để chụp.");
            return;
        }
        int width = screenView.getWidth();
        int height = screenView.getHeight();
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError error) {
            showRuntimeToast("Không thể chụp ảnh màn hình.");
            return;
        }
        if (screenView instanceof SurfaceView) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                bitmap.recycle();
                showRuntimeToast("Chụp ảnh kèm bộ lọc CRT cần Android 8.0 trở lên.");
                return;
            }
            PixelCopy.request((SurfaceView) screenView, bitmap, result -> {
                if (result != PixelCopy.SUCCESS) {
                    bitmap.recycle();
                    showRuntimeToast("Không thể chụp ảnh màn hình.");
                    return;
                }
                if (canvasDecoration != null) canvasDecoration.draw(new Canvas(bitmap));
                saveCanvasCrop(bitmap, canvasBounds);
            }, new Handler(Looper.getMainLooper()));
            return;
        }
        screenView.draw(new Canvas(bitmap));
        if (canvasDecoration != null) canvasDecoration.draw(new Canvas(bitmap));
        saveCanvasCrop(bitmap, canvasBounds);
    }

    private void saveCanvasCrop(Bitmap viewportBitmap, RectF canvasBounds) {
        int left = Math.max(0, (int) Math.floor(canvasBounds.left));
        int top = Math.max(0, (int) Math.floor(canvasBounds.top));
        int right = Math.min(viewportBitmap.getWidth(), (int) Math.ceil(canvasBounds.right));
        int bottom = Math.min(viewportBitmap.getHeight(), (int) Math.ceil(canvasBounds.bottom));
        if (right <= left || bottom <= top) {
            viewportBitmap.recycle();
            showRuntimeToast("Không thể chụp ảnh màn hình.");
            return;
        }
        Bitmap cropped;
        try {
            cropped = Bitmap.createBitmap(viewportBitmap, left, top,
                    right - left, bottom - top);
        } catch (RuntimeException | OutOfMemoryError error) {
            viewportBitmap.recycle();
            android.util.Log.w("FreeJ2ME", "Could not crop canvas screenshot", error);
            showRuntimeToast("Không thể chụp ảnh màn hình.");
            return;
        }
        if (cropped != viewportBitmap) viewportBitmap.recycle();
        saveCanvasScreenshot(cropped);
    }

    private void saveCanvasScreenshot(Bitmap bitmap) {
        new Thread(() -> {
            Uri insertedUri = null;
            try {
                String name = "J2MEX_" + System.currentTimeMillis() + ".png";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/J2MEX");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    insertedUri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (insertedUri == null) throw new IOException("Could not create image entry");
                    try (OutputStream output = getContentResolver().openOutputStream(insertedUri)) {
                        if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                            throw new IOException("Could not encode screenshot");
                    }
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(insertedUri, ready, null, null);
                } else {
                    boolean sharedStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                            || checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                                    == PackageManager.PERMISSION_GRANTED;
                    File base = sharedStorage
                            ? Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES)
                            : getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    if (base == null) throw new IOException("No writable image directory");
                    File directory = new File(base, "J2MEX");
                    if (!directory.isDirectory() && !directory.mkdirs())
                        throw new IOException("Could not create image directory");
                    File image = new File(directory, name);
                    try (FileOutputStream output = new FileOutputStream(image)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                            throw new IOException("Could not encode screenshot");
                    }
                    MediaScannerConnection.scanFile(this, new String[]{image.getAbsolutePath()},
                            new String[]{"image/png"}, null);
                }
                showRuntimeToast("Đã lưu ảnh vào thư mục Pictures/J2MEX.");
            } catch (Exception error) {
                if (insertedUri != null) {
                    try { getContentResolver().delete(insertedUri, null, null); }
                    catch (RuntimeException ignored) { }
                }
                android.util.Log.w("FreeJ2ME", "Could not save canvas screenshot", error);
                showRuntimeToast("Không thể lưu ảnh chụp.");
            } finally {
                bitmap.recycle();
            }
        }, "J2MEX-Screenshot").start();
    }

    private void showRuntimeToast(String message) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed())
                Toast.makeText(this, AppLocalization.text(this, message),
                        Toast.LENGTH_SHORT).show();
        });
    }

    private final class EdgeSwipeMenu extends FrameLayout {
        private final View handle;
        private final LinearLayout panel;
        private final LinearLayout actionRows;
        private final View logAction;
        private LinearLayout currentActionRow;
        private int actionCount;
        private boolean open;
        private float downX;
        private int contentTopInset;

        EdgeSwipeMenu(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);

            panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(10), dp(12), dp(10), dp(8));
            panel.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24), 0x665C6675));
            panel.setElevation(dp(12));
            panel.setVisibility(View.GONE);
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                    dp(360), ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.LEFT);
            addView(panel, panelParams);

            actionRows = new LinearLayout(context);
            actionRows.setOrientation(LinearLayout.VERTICAL);
            panel.addView(actionRows, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addAction(EdgeActionIcon.SETTINGS, "Cài đặt",
                    RuntimeActivity.this::showRuntimeSettings);
            addAction(EdgeActionIcon.GAMEPAD, "Gamepad",
                    RuntimeActivity.this::toggleGamepadForSession);
            addAction(EdgeActionIcon.SCREENSHOT, "Chụp màn hình",
                    RuntimeActivity.this::captureCanvasScreenshot);
            addAction(EdgeActionIcon.KEYBOARD, "Bàn phím",
                    RuntimeActivity.this::showGameKeyboard);
            addAction(EdgeActionIcon.SPEED, "Tốc độ giả lập",
                    RuntimeActivity.this::showEmulationSpeedDialog);
            addAction(EdgeActionIcon.MEMORY, "Trình sửa bộ nhớ",
                    RuntimeActivity.this::showMemoryEditor);
            logAction = addAction(EdgeActionIcon.LOG, "Lưu log",
                    () -> HostFiles.requestLog(RuntimeActivity.this));
            setLogActionEnabled(debugLogsEnabled);
            addAction(EdgeActionIcon.EXIT, "Thoát game",
                    RuntimeActivity.this::confirmStopGame);

            handle = new View(context) {
                private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    float x = dp(1);
                    float width = dp(4);
                    float top = dp(10);
                    float bottom = getHeight() - dp(10);
                    paint.setColor(0x70FFFFFF);
                    canvas.drawRoundRect(x, top, x + width, bottom,
                            width / 2f, width / 2f, paint);
                }
            };
            handle.setContentDescription(AppLocalization.text(RuntimeActivity.this,
                    "Vuốt sang phải để mở menu nhanh"));
            handle.setClickable(true);
            FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
                    dp(24), dp(88), Gravity.TOP | Gravity.LEFT);
            addView(handle, handleParams);
            handle.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float delta = event.getRawX() - downX;
                        if (!open && delta >= dp(32)) openMenu();
                        else if (open && delta <= -dp(24)) closeMenu();
                        else if (open) closeMenu();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            });
            setOnTouchListener((view, event) -> {
                if (!open) return false;
                if (event.getActionMasked() == MotionEvent.ACTION_UP) closeMenu();
                return true;
            });
        }

        private LinearLayout addAction(int icon, String description, Runnable action) {
            if (actionCount % 4 == 0) {
                currentActionRow = new LinearLayout(RuntimeActivity.this);
                currentActionRow.setOrientation(LinearLayout.HORIZONTAL);
                actionRows.addView(currentActionRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));
            }
            LinearLayout button = new LinearLayout(RuntimeActivity.this);
            button.setOrientation(LinearLayout.VERTICAL);
            button.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            button.setPadding(dp(2), dp(4), dp(2), dp(1));
            button.setContentDescription(AppLocalization.text(
                    RuntimeActivity.this, description));
            button.setFocusable(true);
            button.setClickable(true);
            EdgeActionIcon image = new EdgeActionIcon(RuntimeActivity.this, icon);
            button.addView(image, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView label = new TextView(RuntimeActivity.this);
            label.setText(AppLocalization.text(RuntimeActivity.this, description));
            label.setTextColor(UI_TEXT);
            label.setTextSize(10.5f);
            label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
            labelParams.topMargin = dp(3);
            button.addView(label, labelParams);
            currentActionRow.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            actionCount++;
            button.setOnClickListener(view -> {
                closeMenu();
                action.run();
            });
            button.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                    view.setAlpha(0.65f);
                else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                    view.setAlpha(1f);
                return false;
            });
            return button;
        }

        private void setLogActionEnabled(boolean enabled) {
            logAction.setEnabled(enabled);
            logAction.setAlpha(enabled ? 1f : 0.34f);
            logAction.setContentDescription(AppLocalization.text(RuntimeActivity.this,
                    enabled ? "Lưu log" : "Lưu log (bật Ghi log để gỡ lỗi trong mục Khác)"));
        }

        private void openMenu() {
            if (open) return;
            updatePanelWidth(getWidth() > 0 ? getWidth() : currentViewportWidthPx());
            open = true;
            panel.animate().cancel();
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationX(-Math.max(panel.getWidth(), dp(360)) - dp(20));
            setClickable(true);
            bringToFront();
            handle.bringToFront();
            panel.post(() -> {
                if (!open) return;
                panel.animate().cancel();
                panel.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            });
        }

        void toggleMenu() {
            if (open) closeMenu();
            else openMenu();
        }

        void closeMenu() {
            if (!open && panel.getVisibility() == View.GONE) return;
            open = false;
            setClickable(false);
            panel.animate().cancel();
            if (panel.getVisibility() == View.GONE) return;
            float closedX = -Math.max(panel.getWidth(), dp(360)) - dp(20);
            panel.animate()
                    .translationX(closedX)
                    .alpha(0f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .withEndAction(() -> {
                        if (!open) panel.setVisibility(View.GONE);
                    })
                    .start();
        }

        void setContentTopInset(int inset) {
            if (contentTopInset == inset) return;
            contentTopInset = inset;
            updatePosition(getHeight());
        }

        private void updatePanelWidth(int viewportWidth) {
            if (viewportWidth <= 0) return;
            int widthLimit = Math.max(dp(1), viewportWidth - dp(24));
            int preferredWidth = Math.min(dp(360), Math.round(viewportWidth * 0.90f));
            int nextWidth = Math.min(preferredWidth, widthLimit);
            FrameLayout.LayoutParams panelParams =
                    (FrameLayout.LayoutParams) panel.getLayoutParams();
            if (panelParams.width != nextWidth) {
                panelParams.width = nextWidth;
                panel.setLayoutParams(panelParams);
            }
        }

        void refreshForCurrentViewport() {
            int width = getWidth() > 0 ? getWidth() : currentViewportWidthPx();
            if (width <= 0) return;
            updatePanelWidth(width);
            panel.requestLayout();
            requestLayout();
            post(() -> {
                if (getHeight() > 0) updatePosition(getHeight());
            });
        }

        @Override protected void onSizeChanged(int width, int height,
                int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updatePanelWidth(width);
            updatePosition(height);
            panel.post(() -> {
                if (getHeight() > 0) updatePosition(getHeight());
            });
        }

        private void updatePosition(int height) {
            int availableHeight = Math.max(0, height - contentTopInset);
            int panelHeight = panel.getMeasuredHeight() > 0
                    ? panel.getMeasuredHeight() : dp(176);
            int desiredTop = contentTopInset + Math.round(availableHeight * 0.15f);
            int maxTop = Math.max(contentTopInset + dp(8),
                    height - panelHeight - dp(12));
            int top = Math.max(contentTopInset + dp(8), Math.min(desiredTop, maxTop));
            FrameLayout.LayoutParams handleParams =
                    (FrameLayout.LayoutParams) handle.getLayoutParams();
            handleParams.topMargin = top;
            handleParams.leftMargin = dp(1);
            handle.setLayoutParams(handleParams);
            FrameLayout.LayoutParams panelParams =
                    (FrameLayout.LayoutParams) panel.getLayoutParams();
            panelParams.topMargin = top;
            panelParams.leftMargin = dp(10);
            panelParams.gravity = Gravity.TOP | Gravity.LEFT;
            panel.setLayoutParams(panelParams);
            if (open) {
                panel.animate().cancel();
                panel.setVisibility(View.VISIBLE);
                panel.setTranslationX(0f);
                panel.setAlpha(1f);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!open) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) closeMenu();
            return true;
        }
    }

    private final class EdgeActionIcon extends ImageView {
        static final int EXIT = 1;
        static final int LOG = 2;
        static final int KEYBOARD = 3;
        static final int SPEED = 4;
        static final int MEMORY = 5;
        static final int SETTINGS = 6;
        static final int GAMEPAD = 7;
        static final int SCREENSHOT = 8;

        EdgeActionIcon(Context context, int type) {
            super(context);
            setImageResource(iconResource(type));
            setColorFilter(UI_TEXT);
            setScaleType(ScaleType.FIT_CENTER);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private int iconResource(int icon) {
            switch (icon) {
                case EXIT: return R.drawable.edge_exit;
                case LOG: return R.drawable.edge_log;
                case KEYBOARD: return R.drawable.edge_keyboard;
                case SPEED: return R.drawable.edge_speed;
                case MEMORY: return R.drawable.edge_memory;
                case SETTINGS: return R.drawable.edge_settings;
                case GAMEPAD: return R.drawable.edge_gamepad;
                case SCREENSHOT: return R.drawable.edge_screenshot;
                default: throw new IllegalArgumentException("Unknown edge icon " + icon);
            }
        }
    }

    private void launchEngine() {
        try {
            String jarPath = getIntent().getStringExtra("jar");
            File jar = jarPath == null ? null : new File(jarPath);
            File jarParent = jar == null ? null : jar.getParentFile();
            File work = jarParent == null
                    ? new File(getFilesDir(), "failed-launch-data")
                    : new File(jarParent, "data");
            if (debugLogsEnabled) {
                String version = "unknown";
                try {
                    version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception ignored) { }
                String header = "J2MEX " + version
                        + "; native TextBox/List/Form; headless framebuffer host\nMIDlet="
                        + (jar == null ? "(missing path)" : jar.getAbsolutePath())
                        + "\nAndroid=" + Build.VERSION.RELEASE
                        + " ABI=" + Arrays.toString(Build.SUPPORTED_ABIS)
                        + "\nAndroid device locale=" + Locale.getDefault().toLanguageTag()
                        + "\nAndroid font renderer="
                        + (gameSettings.bitmapFontEnabled ? "CoreBridge bitmap atlas"
                                : "Paint/Canvas system font")
                        + "; font scale=" + BuildConfig.FONT_SCALE_PERCENT + "/100 ("
                        + BuildConfig.FONT_SCALE_PERCENT + "%)"
                        + "\nCRT Grid filter=" + gameSettings.crtFilterEnabled
                        + "\nAndroid font raster Y offset=1 px"
                        + "\nFACE_MONOSPACE=Typeface.MONOSPACE (not the default sans face)"
                        + "\nRun stage=preparing runtime\n";
                try {
                    SessionLog.begin(getFilesDir(), work, header);
                    sessionLogStarted = true;
                } catch (IOException logError) {
                    android.util.Log.e("FreeJ2ME", "Cannot begin diagnostic session", logError);
                    update("Không thể khởi tạo log chẩn đoán: " + logError.getMessage());
                }
            }
            if (jar == null) throw new IOException("Thiếu đường dẫn MIDlet JAR");
            if (!jar.isFile()) throw new IOException("Không tìm thấy MIDlet JAR");
            HostFiles.mkdir(work);
            File audioCache = new File(getCacheDir(), "freej2me-audio");
            resetAudioCache(audioCache);
            File runtime = new File(getFilesDir(), "runtime");
            String token = UUID.randomUUID().toString();
            fontServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            fontServer.setSoTimeout(45000);
            Thread fontThread = new Thread(new AndroidSystemFontServer(fontServer, token,
                    BuildConfig.FONT_SCALE_PERCENT, gameSettings.bitmapFontEnabled, getAssets()),
                    "AndroidPaintFontServer");
            fontThread.setDaemon(true);
            fontThread.start();
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(45000);
            new Thread(() -> readFrames(token), "AndroidFramebuffer").start();
            appendSessionLog("[startup] Android font bridge port=" + fontServer.getLocalPort()
                    + "; AWT font rendering disabled");
            String cp = new File(getFilesDir(), "engine.jar") + ":" + new File(getFilesDir(), "bridge.jar");
            String deviceLocale = Locale.getDefault().toLanguageTag();
            String nativeLogPath = debugLogsEnabled && sessionLogStarted
                    ? HostFiles.log(this).getAbsolutePath() : "/dev/null";
            int result = NativeHost.launch(runtime.getAbsolutePath(), work.getAbsolutePath(),
                    getCacheDir().getAbsolutePath(), cp, nativeLogPath,
                    new String[]{jar.toURI().toASCIIString(),
                            Integer.toString(gameSettings.canvasWidth),
                            Integer.toString(gameSettings.canvasHeight),
                            Integer.toString(server.getLocalPort()),
                            token, work.getAbsolutePath(), audioCache.getCanonicalPath(),
                            Integer.toString(displayRefreshHz), deviceLocale,
                            Integer.toString(fontServer.getLocalPort()),
                            "--j2mex-touch-input=" + gameSettings.touchEnabled,
                            "--j2mex-ui-language=" + AppLocalization.languageTag(this)});
            appendSessionLog("[runtime-exit] native host result=" + result);
            update(result == -21
                    ? "Heap native không tương thích với Java 8. Hãy xuất log để xem chi tiết."
                    : "Java đã dừng (mã " + result + "). Xuất log để xem chi tiết.");
        } catch (Throwable error) {
            update("Khởi động thất bại: " + error + ". Hãy lưu log từ menu cạnh trái.");
            appendError(error);
        }
    }

    private static String systemTypefaceFamilyName(Typeface typeface, String fallback) {
        try {
            java.lang.reflect.Method method =
                    Typeface.class.getMethod("getSystemFontFamilyName");
            Object name = method.invoke(typeface);
            if (name != null && !name.toString().trim().isEmpty()) return name.toString();
        } catch (Exception ignored) { }
        return fallback;
    }

    private static String androidPaintWidthProfile(Typeface family) {
        String probe = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                + "ÀÁẠẢÃĂẰẮẶẲẴÂẦẤẬẨẪĐÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸ"
                + "àáạảãăằắặẳẵâầấậẩẫđèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹ"
                + " .,,:;!?+-_()[]{}";
        char[] characters = probe.toCharArray();
        StringBuilder profile = new StringBuilder();
        for (int style = 0; style <= Typeface.BOLD_ITALIC; style++) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Typeface.create(family, style));
            paint.setTextSize(100f);
            float[] widths = new float[characters.length];
            paint.getTextWidths(characters, 0, characters.length, widths);
            double totalWidth = 0d;
            for (float width : widths) totalWidth += width;
            if (style > 0) profile.append(',');
            profile.append(Double.toString(totalWidth));
        }
        return profile.toString();
    }

    private File ensureAndroidFontMetrics() throws IOException {
        Typeface[] families = {Typeface.DEFAULT, Typeface.SANS_SERIF, Typeface.MONOSPACE};
        String signature = systemTypefaceFamilyName(families[0], "system") + ":"
                + androidPaintWidthProfile(families[0]) + "|"
                + systemTypefaceFamilyName(families[1], "sans-serif") + ":"
                + androidPaintWidthProfile(families[1]) + "|"
                + systemTypefaceFamilyName(families[2], "monospace") + ":"
                + androidPaintWidthProfile(families[2]);
        int fingerprint = signature.hashCode();
        String fingerprintText = Integer.toHexString(fingerprint);
        File output = new File(getCacheDir(), "font-metrics-v" + FONT_METRICS_VERSION
                + "-" + fingerprintText + ".bin");
        if (isValidAndroidFontMetrics(output, fingerprint)) return output;

        File temporary = new File(output.getParentFile(), output.getName() + ".tmp");
        char[] characters = new char[FONT_METRICS_CHARACTERS];
        for (int i = 0; i < characters.length; i++) characters[i] = (char)i;
        float[] chunkWidths = new float[FONT_METRICS_CHUNK_SIZE];
        Typeface[] allFamilies = {Typeface.DEFAULT, Typeface.SANS_SERIF, Typeface.MONOSPACE};
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(temporary), 65536))) {
            data.writeInt(FONT_METRICS_MAGIC);
            data.writeInt(FONT_METRICS_VERSION);
            data.writeInt(fingerprint);
            data.writeInt(FONT_METRICS_FAMILIES);
            data.writeInt(FONT_METRICS_STYLES);
            data.writeInt(FONT_METRICS_CHARACTERS);
            for (Typeface family : allFamilies) {
                for (int style = 0; style < FONT_METRICS_STYLES; style++) {
                    Paint paint = new Paint();
                    paint.setTypeface(Typeface.create(family, style));
                    paint.setTextSize(100f);
                    data.writeFloat(paint.getFontSpacing() / 100f);
                    for (int start = 0; start < characters.length;
                            start += FONT_METRICS_CHUNK_SIZE) {
                        int count = Math.min(FONT_METRICS_CHUNK_SIZE, characters.length - start);
                        paint.getTextWidths(characters, start, count, chunkWidths);
                        for (int i = 0; i < count; i++) data.writeFloat(chunkWidths[i]);
                    }
                }
            }
            data.flush();
        } catch (IOException error) {
            temporary.delete();
            throw error;
        } catch (RuntimeException error) {
            temporary.delete();
            throw new IOException("Android Paint could not measure its font families", error);
        }
        if (!temporary.renameTo(output)) {
            temporary.delete();
            if (!isValidAndroidFontMetrics(output, fingerprint))
                throw new IOException("Could not save Android Paint font metrics");
        }
        return output;
    }

    private static boolean isValidAndroidFontMetrics(File file, int fingerprint) {
        long expectedLength = 24L + (long)FONT_METRICS_FAMILIES * FONT_METRICS_STYLES
                * (4L + (long)FONT_METRICS_CHARACTERS * 4L);
        if (!file.isFile() || file.length() != expectedLength) return false;
        try (DataInputStream data = new DataInputStream(new FileInputStream(file))) {
            return data.readInt() == FONT_METRICS_MAGIC
                    && data.readInt() == FONT_METRICS_VERSION
                    && data.readInt() == fingerprint
                    && data.readInt() == FONT_METRICS_FAMILIES
                    && data.readInt() == FONT_METRICS_STYLES
                    && data.readInt() == FONT_METRICS_CHARACTERS;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String findConfiguredAndroidFontPath(String familyName) throws IOException {
        File config = new File("/system/etc/fonts.xml");
        if (config.isFile()) {
            try (InputStream input = new FileInputStream(config)) {
                org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(input, "UTF-8");
                boolean inDefaultSans = false;
                int event = parser.getEventType();
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        String tag = parser.getName();
                        if ("family".equals(tag)) {
                            inDefaultSans = familyName.equals(parser.getAttributeValue(null, "name"));
                        } else if (inDefaultSans && "font".equals(tag)) {
                            String weightValue = parser.getAttributeValue(null, "weight");
                            int weight = 400;
                            if (weightValue != null) {
                                try { weight = Integer.parseInt(weightValue); }
                                catch (NumberFormatException ignored) { }
                            }
                            String style = parser.getAttributeValue(null, "style");
                            String name = parser.nextText().trim();
                            if (weight == 400 && (style == null || !"italic".equalsIgnoreCase(style))) {
                                File candidate = systemFontFile(name);
                                String lowerName = name.toLowerCase(Locale.ROOT);
                                if ((lowerName.endsWith(".ttf") || lowerName.endsWith(".otf"))
                                        && candidate.isFile() && candidate.canRead())
                                    return candidate.getCanonicalPath();
                            }
                        }
                    } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG
                            && "family".equals(parser.getName())) {
                        inDefaultSans = false;
                    }
                    event = parser.next();
                }
            } catch (Exception ignored) {
                // Try the known Android default sans-serif files below.
            }
        }

        String[] candidates = "monospace".equals(familyName)
                ? new String[]{"/system/fonts/DroidSansMono.ttf", "/system/fonts/RobotoMono-Regular.ttf",
                        "/system/fonts/NotoSansMono-Regular.ttf"}
                : new String[]{"/system/fonts/Roboto-Regular.ttf", "/system/fonts/RobotoStatic-Regular.ttf",
                        "/system/fonts/SamsungOneUI-Regular.ttf", "/system/fonts/SamsungOne.ttf",
                        "/system/fonts/NotoSans-Regular.ttf"};
        for (String path : candidates) {
            File candidate = new File(path);
            if (candidate.isFile() && candidate.canRead()) return candidate.getCanonicalPath();
        }
        throw new IOException("Không tìm thấy font " + familyName + " hệ thống trong /system/etc/fonts.xml");
    }

    private TypefaceFontMatch findMatchingAndroidTypefaceFont(String familyName,
            Typeface activeTypeface, String configuredFallbackPath) throws IOException {
        char[] probe = FONT_MATCH_PROBE.toCharArray();
        Paint activePaint = new Paint();
        activePaint.setTypeface(activeTypeface);
        activePaint.setTextSize(100f);
        float[] activeWidths = new float[probe.length];
        activePaint.getTextWidths(probe, 0, probe.length, activeWidths);

        File cachedMatch = new File(getCacheDir(), "font-source-v1-" + familyName + "-"
                + Integer.toHexString(Arrays.hashCode(activeWidths)) + ".bin");
        TypefaceFontMatch cached = readTypefaceFontMatch(cachedMatch);
        if (cached != null) return cached;

        TreeSet<String> candidates = new TreeSet<>();
        File configuredFallback = new File(configuredFallbackPath);
        if (configuredFallback.isFile() && configuredFallback.canRead())
            candidates.add(configuredFallback.getCanonicalPath());
        File[] fontDirectories = {
                new File("/system/fonts"),
                new File("/system_ext/fonts"),
                new File("/product/fonts"),
                new File("/vendor/fonts")
        };
        for (File directory : fontDirectories) collectTypefaceFontFiles(directory, candidates);

        String bestPath = configuredFallback.getCanonicalPath();
        double bestScore = Double.POSITIVE_INFINITY;
        for (String path : candidates) {
            try {
                Typeface candidateTypeface = Typeface.createFromFile(path);
                Paint candidatePaint = new Paint();
                candidatePaint.setTypeface(candidateTypeface);
                candidatePaint.setTextSize(100f);
                float[] candidateWidths = new float[probe.length];
                candidatePaint.getTextWidths(probe, 0, probe.length, candidateWidths);
                double difference = 0d;
                for (int i = 0; i < probe.length; i++)
                    difference += Math.abs(activeWidths[i] - candidateWidths[i]);
                double score = difference / probe.length;
                if (score < bestScore) {
                    bestScore = score;
                    bestPath = path;
                }
            } catch (RuntimeException ignored) {
                // Some device font files are collections or use formats Typeface cannot open.
            }
        }
        TypefaceFontMatch match = new TypefaceFontMatch(bestPath, bestScore);
        saveTypefaceFontMatch(cachedMatch, match);
        return match;
    }

    private static TypefaceFontMatch readTypefaceFontMatch(File file) {
        if (!file.isFile()) return null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new FileInputStream(file)))) {
            String path = input.readUTF();
            double score = input.readDouble();
            File font = new File(path);
            return font.isFile() && font.canRead()
                    ? new TypefaceFontMatch(font.getCanonicalPath(), score) : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void saveTypefaceFontMatch(File file, TypefaceFontMatch match) {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(file)))) {
            output.writeUTF(match.path);
            output.writeDouble(match.score);
        } catch (IOException ignored) { }
    }

    private static void collectTypefaceFontFiles(File directory, Set<String> output) {
        File[] entries = directory.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isFile() && entry.canRead()
                    && (name.endsWith(".ttf") || name.endsWith(".otf"))) {
                try { output.add(entry.getCanonicalPath()); }
                catch (IOException ignored) { }
            }
        }
    }

    private static File systemFontFile(String name) {
        File declared = new File(name);
        if (declared.isAbsolute()) return declared;
        File[] directories = {
                new File("/system/fonts"),
                new File("/system_ext/fonts"),
                new File("/product/fonts"),
                new File("/vendor/fonts")
        };
        for (File directory : directories) {
            File candidate = new File(directory, name);
            if (candidate.isFile()) return candidate;
        }
        return new File(directories[0], name);
    }

    private void readFrames(String token) {
        try {
            while (!stopping) {
                Socket candidate = server.accept(); candidate.setSoTimeout(10000); candidate.setTcpNoDelay(true);
                DataInputStream incoming = new DataInputStream(new BufferedInputStream(candidate.getInputStream(), 65536));
                if (!token.equals(incoming.readUTF())) { candidate.close(); continue; }
                socket = candidate; socket.setSoTimeout(0);
                commands = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                send(14, displayRefreshHz);
                byte[] bytes = null;
                int[] pixels = null;
                IntBuffer pixelBuffer = null;
                long lastFrameAt = 0, intervalTotal = 0, intervalMax = 0;
                long readTotal = 0, readMax = 0, decodeTotal = 0, decodeMax = 0;
                long bitmapTotal = 0, bitmapMax = 0;
                long lastSlowFrameLogAt = 0;
                int sampleFrames = 0, longIntervals = 0;
                int lastFrameWidth = 0, lastFrameHeight = 0;
                while (!stopping) {
                    int type = incoming.readInt();
                    if (type == 1) {
                        int width = incoming.readInt(), height = incoming.readInt();
                        if (width < 1 || height < 1
                                || width > FramebufferProtocol.MAX_EDGE
                                || height > FramebufferProtocol.MAX_EDGE)
                            throw new IOException("Kích thước framebuffer không hợp lệ: "
                                    + width + "x" + height + " (tối đa "
                                    + FramebufferProtocol.MAX_EDGE + " px mỗi cạnh)");
                        lastFrameWidth = width;
                        lastFrameHeight = height;
                        if (pixels == null || pixels.length != width * height) {
                            pixels = new int[width * height]; bytes = new byte[width * height * 4];
                            pixelBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
                        }
                        long readStarted = System.nanoTime();
                        incoming.readFully(bytes);
                        long readFinished = System.nanoTime();
                        pixelBuffer.clear();
                        pixelBuffer.get(pixels);
                        long decodeFinished = System.nanoTime();
                        screen.frame(width, height, pixels);
                        if (canvasDecoration != null)
                            canvasDecoration.setSourceSize(width, height);
                        frameCount++;
                        if (!initialDisplayReady && awaitingInitialCanvasFrame
                                && frameCount > initialCanvasFrameBaseline) {
                            awaitingInitialCanvasFrame = false;
                            initialDisplayReady = true;
                            runOnUiThread(() -> {
                                if (edgeMenu != null && textInputPanel == null)
                                    edgeMenu.setVisibility(View.VISIBLE);
                                updateFpsOverlayVisibility();
                                updateCanvasLayout();
                            });
                        }
                        recordFrameForFps();
                        long bitmapFinished = System.nanoTime();
                        if (!nativeVisible && (frameCount == 1 || frameCount % 300 == 0))
                            update("Đang nhận hình " + width + "×" + height + " • frame " + frameCount);
                        long interval = lastFrameAt == 0 ? 0 : readFinished - lastFrameAt;
                        if (lastFrameAt != 0) {
                            intervalTotal += interval;
                            intervalMax = Math.max(intervalMax, interval);
                            if (interval > 1_000_000_000L / displayRefreshHz * 3 / 2) longIntervals++;
                        }
                        lastFrameAt = readFinished;
                        readTotal += readFinished - readStarted;
                        readMax = Math.max(readMax, readFinished - readStarted);
                        decodeTotal += decodeFinished - readFinished;
                        decodeMax = Math.max(decodeMax, decodeFinished - readFinished);
                        bitmapTotal += bitmapFinished - decodeFinished;
                        bitmapMax = Math.max(bitmapMax, bitmapFinished - decodeFinished);
                        sampleFrames++;
                        if (sampleFrames >= 300) {
                            String uiDrawStats = screen.drainFrameStats();
                            boolean slowFramePipeline = longIntervals >= 3
                                    || readMax > 10_000_000L
                                    || decodeMax > 10_000_000L
                                    || bitmapMax > 16_000_000L;
                            long now = System.nanoTime();
                            if (debugLogsEnabled && slowFramePipeline
                                    && (lastSlowFrameLogAt == 0
                                            || now - lastSlowFrameLogAt >= 15_000_000_000L)) {
                                try {
                                    SessionLog.append(getFilesDir(), String.format(Locale.US,
                                            "[frame-pipeline] slow window frames=%d gapAvg/Max=%.2f/%.2fms " +
                                            ">1.5vsync=%d readAvg/Max=%.2f/%.2fms " +
                                            "decodeAvg/Max=%.2f/%.2fms bitmapAvg/Max=%.2f/%.2fms %s",
                                            sampleFrames,
                                            intervalTotal / ((sampleFrames - 1) * 1_000_000.0),
                                            intervalMax / 1_000_000.0, longIntervals,
                                            readTotal / (sampleFrames * 1_000_000.0), readMax / 1_000_000.0,
                                            decodeTotal / (sampleFrames * 1_000_000.0), decodeMax / 1_000_000.0,
                                            bitmapTotal / (sampleFrames * 1_000_000.0), bitmapMax / 1_000_000.0,
                                            uiDrawStats));
                                    lastSlowFrameLogAt = now;
                                } catch (IOException error) {
                                    android.util.Log.w("FreeJ2ME", "Cannot append slow frame stats", error);
                                }
                            }
                            intervalTotal = intervalMax = readTotal = readMax = 0;
                            decodeTotal = decodeMax = bitmapTotal = bitmapMax = 0;
                            sampleFrames = longIntervals = 0;
                        }
                    } else if (type == 2 || type == 3) {
                        String message = incoming.readUTF();
                        if (message.startsWith("[frame-host]")) {
                            // HeadlessMain already writes these summaries to the native console log.
                        } else update(message);
                    }
                    else if (type == 4) {
                        int requestId = incoming.readInt();
                        String title = incoming.readUTF();
                        String value = readText(incoming);
                        int maxSize = incoming.readInt();
                        int constraints = incoming.readInt();
                        int count = incoming.readInt();
                        if (count < 0 || count > 256) throw new IOException("Số lệnh TextBox không hợp lệ");
                        String[] inputCommands = new String[count];
                        for (int i = 0; i < count; i++) inputCommands[i] = incoming.readUTF();
                        int typeCount = incoming.readInt();
                        if (typeCount != count) throw new IOException("Thiếu loại lệnh TextBox");
                        int[] inputCommandTypes = new int[typeCount];
                        for (int i = 0; i < typeCount; i++) inputCommandTypes[i] = incoming.readInt();
                        int defaultCommand = incoming.readInt();
                        if (defaultCommand < -1 || defaultCommand >= count)
                            throw new IOException("Lệnh TextBox mặc định không hợp lệ");
                        showTextInput(requestId, title, value, maxSize, constraints,
                                inputCommands, inputCommandTypes, defaultCommand);
                    } else if (type == 5) {
                        update(incoming.readUTF());
                        runOnUiThread(this::stopGame);
                    } else if (type == 6) {
                        dismissTextInput();
                    } else if (type == 7) {
                        NativeScreen snapshot = NativeScreen.read(incoming);
                        if (snapshot.kind == NativeScreen.FRAMEBUFFER && !initialDisplayReady) {
                            initialCanvasFrameBaseline = frameCount;
                            awaitingInitialCanvasFrame = true;
                        } else if (snapshot.kind != NativeScreen.COMMAND_MENU) {
                            initialDisplayReady = true;
                            awaitingInitialCanvasFrame = false;
                        }
                        nativeVisible = snapshot.kind == NativeScreen.LIST || snapshot.kind == NativeScreen.FORM
                                || snapshot.kind == NativeScreen.ALERT;
                        runOnUiThread(() -> showNativeScreen(snapshot));
                    } else if (type == 8) {
                        int action = incoming.readInt();
                        int playerId = incoming.readInt();
                        String text = incoming.readUTF();
                        String extra = incoming.readUTF();
                        long value = incoming.readLong();
                        int number = incoming.readInt();
                        int flags = incoming.readInt();
                        if (debugLogsEnabled) {
                            try {
                                SessionLog.append(getFilesDir(), "[audio-host] command action=" + action
                                        + " player=" + playerId + " number=" + number + " flags=" + flags);
                            } catch (IOException error) { appendError(error); }
                        }
                        if (audio != null) audio.handle(action, playerId, text, extra, value, number, flags);
                    } else if (type == BluetoothProtocol.MEMORY_EDITOR_REPLY) {
                        int requestId = incoming.readInt();
                        int operation = incoming.readInt();
                        boolean success = incoming.readBoolean();
                        String message = incoming.readUTF();
                        int total = incoming.readInt();
                        int count = incoming.readInt();
                        if (total < 0 || count < 0 || count > 100 || count > total)
                            throw new IOException("Invalid memory-editor response");
                        ArrayList<MemoryEditorEntry> entries = new ArrayList<>(count);
                        for (int index = 0; index < count; index++) {
                            entries.add(new MemoryEditorEntry(incoming.readInt(),
                                    incoming.readUTF(), incoming.readUTF(), incoming.readUTF(),
                                    incoming.readUTF()));
                        }
                        MemoryEditorReply reply = new MemoryEditorReply(requestId, operation,
                                success, message, total, entries);
                        MemoryEditorCallback callback =
                                memoryEditorReplies.remove(requestId);
                        if (callback != null) runOnUiThread(() -> callback.onReply(reply));
                    } else if (type == BluetoothProtocol.HOST_REQUEST) {
                        int requestId = incoming.readInt();
                        int operation = incoming.readInt();
                        int flags = incoming.readInt();
                        int number = incoming.readInt();
                        String value = incoming.readUTF();
                        String extra = incoming.readUTF();
                        int length = incoming.readInt();
                        if (length < 0 || length > 1048576) throw new IOException("Bluetooth request too large");
                        byte[] data = new byte[length];
                        incoming.readFully(data);
                        appendBluetoothLog("host request received id=" + requestId
                                + " operation=" + operation + " flags=" + flags);
                        enqueueBluetoothRequest(new BluetoothRequest(requestId, operation, flags, number,
                                value, extra, data));
                    }
                    else {
                        if (debugLogsEnabled) {
                            try {
                                SessionLog.append(getFilesDir(), "[frame-rx] invalid-type=" + type
                                        + " after-frames=" + frameCount + " last-size="
                                        + lastFrameWidth + "x" + lastFrameHeight
                                        + " native-ui=" + nativeVisible);
                            } catch (IOException logError) { appendError(logError); }
                        }
                        throw new IOException("Sai giao thức framebuffer: " + type);
                    }
                }
                break;
            }
        } catch (Throwable error) {
            if (!stopping) {
                update("Kết nối engine dừng: " + error + ". Hãy lưu log từ menu cạnh trái.");
                appendError(error);
            }
        }
    }
    private int readDisplayRefreshRate() {
        try {
            float refreshRate = getWindowManager().getDefaultDisplay().getRefreshRate();
            if (Float.isNaN(refreshRate) || Float.isInfinite(refreshRate) || refreshRate < 30f) return 60;
            int hz = Math.round(refreshRate);
            return Math.max(30, Math.min(240, hz));
        } catch (RuntimeException error) { return 60; }
    }
    private void updateDisplayRefreshRate() {
        int refreshRate = readDisplayRefreshRate();
        if (refreshRate == displayRefreshHz) return;
        displayRefreshHz = refreshRate;
        if (screen != null) screen.setRefreshRateHz(refreshRate);
        send(14, refreshRate);
    }
    private static void resetAudioCache(File directory) throws IOException {
        if (directory.exists()) {
            File canonical = directory.getCanonicalFile();
            File parent = canonical.getParentFile();
            if (parent == null || !canonical.getName().equals("freej2me-audio"))
                throw new IOException("Invalid audio cache path");
            File[] files = canonical.listFiles();
            if (files == null) throw new IOException("Cannot list audio cache");
            for (File file : files) deleteAudioFile(file);
        }
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Cannot create audio cache");
    }
    private static void deleteAudioFile(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot clear audio cache");
            for (File child : children) deleteAudioFile(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete stale audio cache file");
    }

    private void installDiagnosticCrashHandler() {
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        diagnosticUncaughtExceptionHandler = (thread, error) -> {
            if (debugLogsEnabled && sessionLogStarted) {
                try {
                    SessionLog.appendUncaught(getFilesDir(), thread, error);
                } catch (IOException logError) {
                    android.util.Log.e("FreeJ2ME", "Cannot persist runtime crash report",
                            logError);
                }
            }
            if (previousUncaughtExceptionHandler != null) {
                previousUncaughtExceptionHandler.uncaughtException(thread, error);
            } else {
                android.util.Log.e("FreeJ2ME", "Uncaught runtime exception", error);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(diagnosticUncaughtExceptionHandler);
    }

    private void restoreDiagnosticCrashHandler() {
        if (Thread.getDefaultUncaughtExceptionHandler() == diagnosticUncaughtExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(previousUncaughtExceptionHandler);
        }
        diagnosticUncaughtExceptionHandler = null;
        previousUncaughtExceptionHandler = null;
    }

    private void appendSessionLog(String line) {
        if (!debugLogsEnabled || !sessionLogStarted) return;
        try {
            SessionLog.append(getFilesDir(), line);
        } catch (IOException error) {
            android.util.Log.w("FreeJ2ME", "Cannot append runtime diagnostic log", error);
        }
    }

    private void appendError(Throwable error) {
        if (!debugLogsEnabled) return;
        if (!sessionLogStarted) {
            android.util.Log.e("FreeJ2ME", "Runtime error before diagnostic logging started",
                    error);
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(HostFiles.log(this), true))) {
            error.printStackTrace(writer);
        } catch (IOException ignored) { android.util.Log.e("FreeJ2ME", "Cannot write error log", error); }
    }
    private void appendBluetoothLog(String message) {
        if (!debugLogsEnabled || !sessionLogStarted) return;
        try (PrintWriter writer = new PrintWriter(new FileWriter(HostFiles.log(this), true))) {
            writer.println("[bluetooth] " + message);
        } catch (IOException ignored) {
            android.util.Log.w("FreeJ2ME", "Cannot append Bluetooth diagnostic log", ignored);
        }
    }
    private void update(String text) {
        if (!debugLogsEnabled || text == null || text.startsWith("Đang nhận hình")) return;
        android.util.Log.d("FreeJ2ME", text);
    }

    private void recordFrameForFps() {
        if (!fpsEnabled) return;
        long now = SystemClock.elapsedRealtime();
        fpsIntervalFrames++;
        if (fpsIntervalStart == 0) {
            fpsIntervalStart = now;
            return;
        }
        long elapsed = now - fpsIntervalStart;
        if (elapsed < 1000) return;
        int fps = Math.round(fpsIntervalFrames * 1000f / Math.max(1, elapsed));
        fpsIntervalFrames = 0;
        fpsIntervalStart = now;
        runOnUiThread(() -> {
            if (fpsOverlay == null || isFinishing() || isDestroyed()) return;
            fpsOverlay.setFps(fps);
        });
    }

    private void updateFpsOverlayVisibility() {
        if (fpsOverlay == null) return;
        int visibility = initialDisplayReady && fpsEnabled
                && !nativeVisible && textInputPanel == null
                ? View.VISIBLE : View.GONE;
        if (fpsOverlay.getVisibility() != visibility) fpsOverlay.setVisibility(visibility);
    }

    private final class FpsOverlayView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int measuredWidth;
        private final int measuredHeight;
        private String label = "— FPS";

        FpsOverlayView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            textPaint.setColor(UI_TEXT);
            textPaint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
            textPaint.setTypeface(Typeface.MONOSPACE);
            textPaint.setTextAlign(Paint.Align.LEFT);
            TextView originalSize = new TextView(context);
            originalSize.setText("9999 FPS");
            originalSize.setTextSize(12);
            originalSize.setTypeface(Typeface.MONOSPACE);
            originalSize.setPadding(dp(10), dp(5), dp(10), dp(5));
            originalSize.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            measuredWidth = originalSize.getMeasuredWidth();
            measuredHeight = originalSize.getMeasuredHeight();
            backgroundPaint.setColor(0xC90B1420);
            borderPaint.setColor(0x665C6675);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1));
        }

        void setFps(int fps) {
            String next = fps + " FPS";
            if (next.equals(label)) return;
            label = next;
            invalidate();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(measuredWidth, measuredHeight);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float halfStroke = borderPaint.getStrokeWidth() / 2f;
            float visibleWidth = Math.min(getWidth(),
                    (float) Math.ceil(textPaint.measureText(label)) + dp(20));
            float left = getWidth() - visibleWidth;
            RectF bounds = new RectF(left + halfStroke, halfStroke,
                    getWidth() - halfStroke, getHeight() - halfStroke);
            float radius = dp(14);
            canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);
            canvas.drawRoundRect(bounds, radius, radius, borderPaint);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = dp(5) - metrics.top;
            canvas.drawText(label, left + dp(10), baseline, textPaint);
        }
    }
    private void sendMemoryEditorRequest(int operation, String query, int candidateId,
            String value, MemoryEditorCallback callback) {
        int requestId = nextMemoryEditorRequest.incrementAndGet();
        if (callback != null) memoryEditorReplies.put(requestId, callback);
        if (sender.isShutdown()) {
            memoryEditorReplies.remove(requestId);
            return;
        }
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) throw new IOException("Engine is not connected");
                stream.writeByte(BluetoothProtocol.MEMORY_EDITOR_COMMAND);
                stream.writeInt(operation);
                stream.writeInt(requestId);
                stream.writeUTF(query == null ? "" : query);
                stream.writeInt(candidateId);
                stream.writeUTF(value == null ? "" : value);
                stream.flush();
            } catch (IOException error) {
                MemoryEditorCallback failed = memoryEditorReplies.remove(requestId);
                if (failed != null) runOnUiThread(() -> failed.onReply(
                        new MemoryEditorReply(requestId, operation, false,
                                error.getMessage(), 0, new ArrayList<>())));
            }
        });
    }

    private void send(int type, int... values) {
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(type); for (int value : values) stream.writeInt(value); stream.flush();
            } catch (IOException error) { if (!stopping) update("Không gửi được điều khiển: " + error); }
        });
    }

    private void enqueueBluetoothRequest(BluetoothRequest request) {
        runOnUiThread(() -> {
            if (stopping || isFinishing() || isDestroyed()) {
                sendBluetoothResponse(request.id, false, "Bluetooth host is stopping");
                return;
            }
            bluetoothQueue.addLast(request);
            processBluetoothQueue();
        });
    }

    private void processBluetoothQueue() {
        if (activeBluetoothRequest != null || bluetoothQueue.isEmpty() || stopping) return;
        activeBluetoothRequest = bluetoothQueue.removeFirst();
        String[] required = bluetoothPermissions(activeBluetoothRequest.flags);
        ArrayList<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : required)
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        }
        appendBluetoothLog("permission check operation=" + activeBluetoothRequest.operation
                + " required=" + java.util.Arrays.toString(required)
                + " missing=" + missing);
        if (!missing.isEmpty()) {
            appendBluetoothLog("requesting runtime permissions " + missing);
            requestPermissions(missing.toArray(new String[missing.size()]), REQUEST_BT_PERMISSIONS);
            return;
        }
        continueBluetoothRequest();
    }

    private String[] bluetoothPermissions(int flags) {
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if ((flags & BluetoothProtocol.NEED_SCAN) != 0)
                permissions.add(android.Manifest.permission.BLUETOOTH_SCAN);
            if ((flags & BluetoothProtocol.NEED_CONNECT) != 0)
                permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            if ((flags & BluetoothProtocol.NEED_ADVERTISE) != 0)
                permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE);
        } else if ((flags & BluetoothProtocol.NEED_SCAN) != 0
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return permissions.toArray(new String[permissions.size()]);
    }

    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private void continueBluetoothRequest() {
        BluetoothRequest request = activeBluetoothRequest;
        if (request == null || stopping) return;
        if (request.operation == BluetoothProtocol.CLOSE) {
            runBluetoothWorker(request, null);
            return;
        }
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null) {
            finishBluetoothRequest(false, "This device has no Bluetooth adapter");
            return;
        }
        try {
            if (request.operation == BluetoothProtocol.CANCEL_INQUIRY) {
                try { adapter.cancelDiscovery(); }
                catch (SecurityException ignored) { }
                unregisterBluetoothDiscoveryReceiver();
                finishBluetoothRequest(true, "");
                return;
            }
            if (!adapter.isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_BT_ENABLE);
                return;
            }
            switch (request.operation) {
                case BluetoothProtocol.ACCESS:
                    finishBluetoothRequest(true, "");
                    break;
                case BluetoothProtocol.START_INQUIRY:
                    startBluetoothInquiry(adapter);
                    break;
                case BluetoothProtocol.SET_DISCOVERABLE: {
                    long now = SystemClock.elapsedRealtime();
                    if (adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        appendBluetoothLog("discoverability already active; skipping system prompt");
                        finishBluetoothRequest(true, "");
                        return;
                    }
                    if (now < discoverabilityGrantedUntil) {
                        appendBluetoothLog("discoverability already approved for this duration; skipping prompt");
                        finishBluetoothRequest(true, "");
                        return;
                    }
                    if (discoverabilityDeclinedForSession) {
                        appendBluetoothLog("discoverability was declined earlier in this game session");
                        finishBluetoothRequest(false, "Bluetooth discoverability was declined");
                        return;
                    }
                    appendBluetoothLog("requesting system discoverability consent for "
                            + DEFAULT_DISCOVERABLE_SECONDS + " seconds");
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DEFAULT_DISCOVERABLE_SECONDS),
                            REQUEST_BT_DISCOVERABLE);
                    break;
                }
                case BluetoothProtocol.GET_LOCAL_NAME:
                    finishBluetoothRequest(true, adapter.getName());
                    break;
                case BluetoothProtocol.FETCH_UUIDS:
                    startBluetoothUuidFetch(adapter, request);
                    break;
                case BluetoothProtocol.OPEN_SERVER:
                case BluetoothProtocol.OPEN_CLIENT:
                case BluetoothProtocol.ACCEPT:
                case BluetoothProtocol.READ:
                case BluetoothProtocol.WRITE:
                case BluetoothProtocol.AVAILABLE:
                case BluetoothProtocol.CLOSE:
                case BluetoothProtocol.GET_BONDED:
                    runBluetoothWorker(request, adapter);
                    break;
                default:
                    finishBluetoothRequest(false, "Unsupported Bluetooth operation");
                    break;
            }
        } catch (SecurityException error) {
            finishBluetoothRequest(false, "Bluetooth permission denied");
        } catch (RuntimeException error) {
            appendError(error);
            finishBluetoothRequest(false, "Bluetooth request failed: " + error.getMessage());
        }
    }

    private void runBluetoothWorker(BluetoothRequest request, BluetoothAdapter adapter) {
        activeBluetoothRequest = null;
        processBluetoothQueue();
        bluetoothWorkers.execute(() -> {
            BluetoothResult result;
            try {
                result = executeBluetoothIo(request, adapter);
            } catch (SecurityException error) {
                result = new BluetoothResult(false, 0, "Bluetooth permission denied", null);
            } catch (IOException | RuntimeException error) {
                result = new BluetoothResult(false, 0,
                        error.getMessage() == null ? error.toString() : error.getMessage(), null);
            }
            sendBluetoothResponse(request.id, result.accepted, result.number, result.detail, result.data);
        });
    }

    private BluetoothResult executeBluetoothIo(BluetoothRequest request, BluetoothAdapter adapter)
            throws IOException {
        switch (request.operation) {
            case BluetoothProtocol.OPEN_SERVER: {
                int colon = request.value.indexOf(':');
                if (colon <= 0) throw new IOException("Invalid Bluetooth server request");
                String serviceName = request.value.substring(colon + 1);
                if (serviceName.length() == 0) serviceName = "FreeJ2ME";
                java.util.UUID uuid = java.util.UUID.fromString(toJavaUuid(request.extra));
                boolean secure = request.number != 0;
                BluetoothServerSocket server = secure
                        ? adapter.listenUsingRfcommWithServiceRecord(serviceName, uuid)
                        : adapter.listenUsingInsecureRfcommWithServiceRecord(serviceName, uuid);
                int id = nextBluetoothHandle.incrementAndGet();
                bluetoothServers.put(Integer.valueOf(id), server);
                return new BluetoothResult(true, id, "", null);
            }
            case BluetoothProtocol.OPEN_CLIENT: {
                String address = colonAddress(request.value);
                BluetoothDevice device = adapter.getRemoteDevice(address);
                boolean secure = (request.number & 0x100) != 0;
                java.util.UUID uuid = java.util.UUID.fromString(toJavaUuid(request.extra));
                if (adapter.isDiscovering()) adapter.cancelDiscovery();
                BluetoothSocket socket = secure
                        ? device.createRfcommSocketToServiceRecord(uuid)
                        : device.createInsecureRfcommSocketToServiceRecord(uuid);
                try {
                    socket.connect();
                } catch (IOException error) {
                    try { socket.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
                    throw error;
                }
                int id = nextBluetoothHandle.incrementAndGet();
                bluetoothSockets.put(Integer.valueOf(id), socket);
                return new BluetoothResult(true, id, "", null);
            }
            case BluetoothProtocol.ACCEPT: {
                BluetoothServerSocket server = bluetoothServers.get(Integer.valueOf(request.number));
                if (server == null) throw new IOException("Bluetooth server is closed");
                BluetoothSocket socket = server.accept();
                int id = nextBluetoothHandle.incrementAndGet();
                bluetoothSockets.put(Integer.valueOf(id), socket);
                return new BluetoothResult(true, id, "", null);
            }
            case BluetoothProtocol.READ: {
                BluetoothSocket socket = bluetoothSockets.get(Integer.valueOf(request.number));
                if (socket == null) throw new IOException("Bluetooth connection is closed");
                int requested = 1024;
                try { requested = Integer.parseInt(request.extra); }
                catch (NumberFormatException ignored) { }
                requested = Math.max(1, Math.min(65536, requested));
                byte[] buffer = new byte[requested];
                int count = socket.getInputStream().read(buffer);
                if (count <= 0) return new BluetoothResult(true, count, "", new byte[0]);
                return new BluetoothResult(true, count, "", Arrays.copyOf(buffer, count));
            }
            case BluetoothProtocol.WRITE: {
                BluetoothSocket socket = bluetoothSockets.get(Integer.valueOf(request.number));
                if (socket == null) throw new IOException("Bluetooth connection is closed");
                socket.getOutputStream().write(request.data);
                socket.getOutputStream().flush();
                return new BluetoothResult(true, request.data.length, "", null);
            }
            case BluetoothProtocol.AVAILABLE: {
                BluetoothSocket socket = bluetoothSockets.get(Integer.valueOf(request.number));
                if (socket == null) throw new IOException("Bluetooth connection is closed");
                return new BluetoothResult(true, socket.getInputStream().available(), "", null);
            }
            case BluetoothProtocol.CLOSE: {
                Integer id = Integer.valueOf(request.number);
                BluetoothSocket socket = bluetoothSockets.remove(id);
                if (socket != null) {
                    socket.close();
                    return new BluetoothResult(true, 0, "", null);
                }
                BluetoothServerSocket server = bluetoothServers.remove(id);
                if (server != null) {
                    server.close();
                    return new BluetoothResult(true, 0, "", null);
                }
                return new BluetoothResult(true, 0, "", null);
            }
            case BluetoothProtocol.GET_BONDED: {
                java.util.Set<BluetoothDevice> devices = adapter.getBondedDevices();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeInt(devices.size());
                for (BluetoothDevice device : devices) {
                    output.writeUTF(device.getAddress().replace(":", ""));
                    String name;
                    try { name = device.getName(); }
                    catch (SecurityException ignored) { name = ""; }
                    output.writeUTF(name == null ? "" : name);
                }
                output.flush();
                return new BluetoothResult(true, devices.size(), "", bytes.toByteArray());
            }
            default:
                throw new IOException("Unsupported Bluetooth I/O operation");
        }
    }

    private static String toJavaUuid(String value) throws IOException {
        if (value == null || value.length() != 32) throw new IOException("Invalid Bluetooth UUID");
        return value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                + value.substring(12, 16) + "-" + value.substring(16, 20) + "-"
                + value.substring(20);
    }

    private static String colonAddress(String address) throws IOException {
        if (address == null) throw new IOException("Bluetooth address is missing");
        String hex = address.replace(":", "").toUpperCase(Locale.US);
        if (hex.length() != 12) throw new IOException("Invalid Bluetooth address");
        StringBuilder result = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i != 0) result.append(':');
            result.append(hex, i, i + 2);
        }
        return result.toString();
    }

    private void startBluetoothInquiry(BluetoothAdapter adapter) {
        unregisterBluetoothDiscoveryReceiver();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        bluetoothDiscoveryReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device;
                    if (Build.VERSION.SDK_INT >= 33)
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    else
                        device = (BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device == null) return;
                    try {
                        BluetoothClass deviceClass = device.getBluetoothClass();
                        sendBluetoothEvent(BluetoothProtocol.DEVICE_FOUND, device.getAddress(),
                                device.getName(), deviceClass == null ? 0 : deviceClass.getDeviceClass(),
                                BluetoothProtocol.INQUIRY_COMPLETED);
                    } catch (SecurityException error) {
                        sendBluetoothEvent(BluetoothProtocol.DEVICE_FOUND, device.getAddress(),
                                "", 0, BluetoothProtocol.INQUIRY_COMPLETED);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    unregisterBluetoothDiscoveryReceiver();
                    sendBluetoothEvent(BluetoothProtocol.INQUIRY_FINISHED, "", "", 0,
                            BluetoothProtocol.INQUIRY_COMPLETED);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(bluetoothDiscoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(bluetoothDiscoveryReceiver, filter);
        boolean started = adapter.startDiscovery();
        if (!started) unregisterBluetoothDiscoveryReceiver();
        finishBluetoothRequest(started, started ? "" : "Bluetooth discovery could not start");
    }

    private void startBluetoothUuidFetch(BluetoothAdapter adapter, BluetoothRequest request) {
        try {
            BluetoothDevice target = adapter.getRemoteDevice(colonAddress(request.value));
            unregisterBluetoothUuidReceiver();
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_UUID);
            bluetoothUuidReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    BluetoothDevice device;
                    if (Build.VERSION.SDK_INT >= 33)
                        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    else
                        device = (BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device == null || !request.value.equalsIgnoreCase(device.getAddress().replace(":", ""))) return;
                    StringBuilder found = new StringBuilder();
                    android.os.Parcelable[] parcelables =
                            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    if (parcelables != null) for (android.os.Parcelable parcelable : parcelables) {
                        if (!(parcelable instanceof android.os.ParcelUuid)) continue;
                        if (found.length() != 0) found.append(',');
                        found.append(((android.os.ParcelUuid)parcelable).getUuid().toString()
                                .replace("-", "").toUpperCase(Locale.US));
                    }
                    sendBluetoothEvent(BluetoothProtocol.UUIDS_FOUND, request.value,
                            found.toString(), 0, 0);
                    unregisterBluetoothUuidReceiver();
                }
            };
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(bluetoothUuidReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(bluetoothUuidReceiver, filter);
            boolean started = target.fetchUuidsWithSdp();
            if (!started) unregisterBluetoothUuidReceiver();
            finishBluetoothRequest(started, started ? "" : "Bluetooth service query could not start");
        } catch (SecurityException error) {
            unregisterBluetoothUuidReceiver();
            finishBluetoothRequest(false, "Bluetooth permission denied");
        } catch (IOException | IllegalArgumentException error) {
            unregisterBluetoothUuidReceiver();
            finishBluetoothRequest(false, error.getMessage());
        }
    }

    private void unregisterBluetoothUuidReceiver() {
        BroadcastReceiver receiver = bluetoothUuidReceiver;
        bluetoothUuidReceiver = null;
        if (receiver != null) {
            try { unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void unregisterBluetoothDiscoveryReceiver() {
        BroadcastReceiver receiver = bluetoothDiscoveryReceiver;
        bluetoothDiscoveryReceiver = null;
        if (receiver != null) {
            try { unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void finishBluetoothRequest(boolean accepted, String detail) {
        BluetoothRequest request = activeBluetoothRequest;
        activeBluetoothRequest = null;
        if (request != null) sendBluetoothResponse(request.id, accepted, detail);
        processBluetoothQueue();
    }

    private void sendBluetoothResponse(int id, boolean accepted, String detail) {
        sendBluetoothResponse(id, accepted, 0, detail, new byte[0]);
    }

    private void sendBluetoothResponse(int id, boolean accepted, int number, String detail, byte[] data) {
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(BluetoothProtocol.ENGINE_RESPONSE);
                stream.writeInt(id);
                stream.writeBoolean(accepted);
                stream.writeInt(number);
                stream.writeUTF(detail == null ? "" : detail);
                byte[] payload = data == null ? new byte[0] : data;
                if (payload.length > 1048576) throw new IOException("Bluetooth response too large");
                stream.writeInt(payload.length);
                stream.write(payload);
                stream.flush();
            } catch (IOException error) {
                if (!stopping) update("Không gửi được phản hồi Bluetooth: " + error);
            }
        });
    }

    private void sendBluetoothEvent(int event, String address, String name, int deviceClass, int result) {
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(BluetoothProtocol.ENGINE_EVENT);
                stream.writeInt(event);
                stream.writeUTF(address == null ? "" : address);
                stream.writeUTF(name == null ? "" : name);
                stream.writeInt(deviceClass);
                stream.writeInt(result);
                stream.flush();
            } catch (IOException error) {
                if (!stopping) update("Không gửi được sự kiện Bluetooth: " + error);
            }
        });
    }
    private void key(int code, boolean down) {
        if (overlays != null && overlays.key(code, down)) return;
        if (down) { if (heldKeys.add(code)) send(1, code); }
        else if (heldKeys.remove(code)) send(2, code);
    }
    private void showNativeScreen(NativeScreen snapshot) {
        if (stopping || isDestroyed()) return;
        if (snapshot.kind == NativeScreen.COMMAND_MENU) {
            for (int code : new HashSet<>(heldKeys)) {
                heldKeys.remove(code); send(2, code);
            }
            physicalKeyEvents.clear();
            View commandAnchor = leftSoftKeyAnchor != null && leftSoftKeyAnchor.isShown()
                    ? leftSoftKeyAnchor : root;
            overlays.showCommands(snapshot,
                    commandAnchor,
                    Math.max(0, gamePane.getHeight() - screenView.getHeight()));
            return;
        }
        if (textInputPanel == null && edgeMenu != null)
            edgeMenu.setVisibility(initialDisplayReady ? View.VISIBLE : View.GONE);
        overlays.clear();
        boolean nativeMode = snapshot.kind != NativeScreen.FRAMEBUFFER;
        if (nativeMode) {
            if (gamepadControls != null) gamepadControls.cancelActiveTouches();
            for (int code : new HashSet<>(heldKeys)) key(code, false);
            physicalKeyEvents.clear();
            dismissTextInput();
            gamePane.setVisibility(View.GONE);
            nativeScreen.setVisibility(View.VISIBLE);
            nativeScreen.show(snapshot);
        } else {
            nativeScreen.clear(); nativeScreen.setVisibility(View.GONE);
            gamePane.setVisibility(View.VISIBLE);
        }
        updateFpsOverlayVisibility();
        updateCanvasLayout();
    }
    private void sendNativeEvent(NativeScreen.Event event) {
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(11); NativeScreen.writeEvent(stream, event); stream.flush();
            } catch (IOException error) {
                if (!stopping) { update("Không gửi được sự kiện LCDUI: " + error); appendError(error); }
            }
        });
    }
    private void sendAudioEvent(int event, int playerId, long value, String detail) {
        if (debugLogsEnabled) {
            String state;
            switch (event) {
                case AudioProtocol.PREPARED: state = "prepared"; break;
                case AudioProtocol.STARTED: state = "started"; break;
                case AudioProtocol.STOPPED: state = "stopped"; break;
                case AudioProtocol.COMPLETED: state = "completed"; break;
                case AudioProtocol.ERROR: state = "decoder error"; break;
                case AudioProtocol.SEEK_COMPLETE: state = "seek-complete"; break;
                default: state = "event-" + event;
            }
            try {
                SessionLog.append(getFilesDir(), "[audio] player=" + playerId + " " + state
                        + (value >= 0 ? " duration-us=" + value : "") + (detail == null || detail.isEmpty() ? "" : " " + detail));
            } catch (IOException error) { appendError(error); }
        }
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(13);
                stream.writeInt(event);
                stream.writeInt(playerId);
                stream.writeLong(value);
                String message = detail == null ? "" : detail;
                if (message.length() > 8192) message = message.substring(0, 8192);
                stream.writeUTF(message);
                stream.flush();
            } catch (IOException error) {
                if (!stopping) update("Không gửi được trạng thái âm thanh: " + error);
            }
        });
    }
    private static String readText(DataInputStream stream) throws IOException {
        int length = stream.readInt();
        if (length < 0 || length > 1048576) throw new IOException("Nội dung nhập quá dài");
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
    private void sendTextInput(int requestId, String value) {
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(6);
                stream.writeInt(requestId);
                byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (bytes.length > 1048576) throw new IOException("Nội dung nhập quá dài");
                stream.writeInt(bytes.length);
                stream.write(bytes);
                stream.flush();
            } catch (IOException error) { if (!stopping) update("Không gửi được nội dung nhập: " + error); }
        });
    }
    private void sendTextInputResult(int requestId, String value, int commandIndex) {
        if (commands == null || sender.isShutdown()) return;
        sender.execute(() -> {
            try {
                DataOutputStream stream = commands;
                if (stream == null) return;
                stream.writeByte(12);
                stream.writeInt(requestId);
                byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (bytes.length > 1048576) throw new IOException("Nội dung nhập quá dài");
                stream.writeInt(bytes.length);
                stream.write(bytes);
                stream.writeInt(commandIndex);
                stream.flush();
            } catch (IOException error) { if (!stopping) update("Không gửi được nội dung nhập: " + error); }
        });
    }
    private void showTextInput(int requestId, String title, String value, int maxSize, int constraints,
            String[] inputCommands, int[] inputCommandTypes, int defaultCommand) {
        runOnUiThread(() -> {
            if (stopping || isFinishing() || isDestroyed()) return;
            dismissTextInput();
            hideGameKeyboard();
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            overlays.clear();
            textInputRequestId = requestId;
            if (edgeMenu != null) edgeMenu.setVisibility(View.GONE);
            gamePane.setVisibility(View.GONE);
            nativeScreen.setVisibility(View.GONE);
            textInputPanel = new NativeTextInputView(this, title, value, maxSize, constraints,
                    inputCommands, inputCommandTypes, defaultCommand, new NativeTextInputView.Listener() {
                public void submit(String text, int commandIndex) {
                    if (textInputRequestId != requestId) return;
                    sendTextInputResult(requestId, text, commandIndex);
                    dismissTextInput();
                }
            });
            applyStatusBarPreference();
            FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(-1, -1);
            inputParams.topMargin = 0;
            root.addView(textInputPanel, inputParams);
            root.requestApplyInsets();
            updateFpsOverlayVisibility();
            updateCanvasLayout();
        });
    }
    private void dismissTextInput() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::dismissTextInput);
            return;
        }
        NativeTextInputView panel = textInputPanel;
        if (panel == null) return;
        android.view.inputmethod.InputMethodManager manager =
                (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null && panel.getWindowToken() != null)
            manager.hideSoftInputFromWindow(panel.getWindowToken(), 0);
        if (panel.getParent() == root) root.removeView(panel);
        textInputPanel = null;
        textInputRequestId = -1;
        keyboardRequested = false;
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        applyStatusBarPreference();
        if (root != null) root.requestApplyInsets();
        if (edgeMenu != null) edgeMenu.setVisibility(View.VISIBLE);
        if (nativeVisible) {
            nativeScreen.setVisibility(View.VISIBLE);
            gamePane.setVisibility(View.GONE);
        } else {
            gamePane.setVisibility(View.VISIBLE);
            nativeScreen.setVisibility(View.GONE);
        }
        updateFpsOverlayVisibility();
        updateCanvasLayout();
    }
    private int code(KeyEvent event) {
        return PhysicalKeyMapping.codeForEvent(physicalKeyMappings, event);
    }
    private long physicalKeyIdentity(KeyEvent event) {
        return ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (nativeVisible) return super.onKeyDown(keyCode, event);
        long identity = physicalKeyIdentity(event);
        Integer held = physicalKeyEvents.get(identity);
        int code = held == null ? code(event) : held;
        if (code == 0) return super.onKeyDown(keyCode, event);
        if (held == null) physicalKeyEvents.put(identity, code);
        key(code, true); return true;
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (nativeVisible) return super.onKeyUp(keyCode, event);
        Integer held = physicalKeyEvents.remove(physicalKeyIdentity(event));
        int code = held == null ? code(event) : held;
        if (code == 0) return super.onKeyUp(keyCode, event);
        key(code, false); return true;
    }
    @Override protected void onResume() {
        super.onResume();
        AppUpdateManager.resumePendingInstall(this);
        if (textInputPanel == null) hideGameKeyboard();
        if (screen instanceof ShaderFrameView) ((ShaderFrameView)screen).onResume();
        if (displayManager != null && !displayListenerRegistered) {
            displayManager.registerDisplayListener(displayListener, new Handler(Looper.getMainLooper()));
            displayListenerRegistered = true;
        }
        updateDisplayRefreshRate();
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (root != null) {
            root.requestApplyInsets();
            root.requestLayout();
            root.post(() -> {
                updateCanvasLayout();
                if (edgeMenu != null) edgeMenu.refreshForCurrentViewport();
                resizeResponsiveDialogs();
            });
        }
    }
    @Override protected void onPause() {
        if (textInputPanel == null) hideGameKeyboard();
        if (screen instanceof ShaderFrameView) ((ShaderFrameView)screen).onPause();
        if (gamepadControls != null) gamepadControls.cancelActiveTouches();
        if (displayManager != null && displayListenerRegistered) {
            displayManager.unregisterDisplayListener(displayListener);
            displayListenerRegistered = false;
        }
        for (int code : new HashSet<>(heldKeys)) key(code, false);
        physicalKeyEvents.clear();
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && keyboardInput != null && textInputPanel == null
                && !keyboardRequested) {
            hideGameKeyboard();
        }
    }
    private void confirmStopGame() {
        if (stopping) return;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(14));
        panel.setBackground(roundDrawable(EDGE_PANEL_COLOR, dp(24),
                EDGE_PANEL_STROKE_COLOR));

        TextView title = runtimeSettingsText("Thoát game?", 20, UI_TEXT);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView message = runtimeSettingsText(
                "Bạn có chắc chắn muốn thoát khỏi game hiện tại không?", 15, UI_MUTED);
        message.setPadding(0, dp(10), 0, 0);
        panel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(14);
        panel.addView(actions, actionsParams);
        TextView cancel = dialogAction(AppLocalization.text(this, "Hủy"));
        TextView confirm = dialogAction(AppLocalization.text(this, "Thoát"));
        cancel.setTextColor(UI_MUTED);
        confirm.setTextColor(UI_ACCENT);
        actions.addView(cancel);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
        actions.addView(confirm);

        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.58f;
            window.setAttributes(attributes);
        }
        registerResponsiveDialog(dialog,
                () -> setDialogWindowSize(dialog, 36, 480,
                        ViewGroup.LayoutParams.WRAP_CONTENT),
                null);
        cancel.setOnClickListener(view -> dialog.dismiss());
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            stopGame();
        });
    }

    private void stopGame() {
        if (stopping) return;
        send(9); stopping = true; update("Đang dừng MIDlet…");
        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 350);
    }
    @Override public void onBackPressed() {
        if (edgeMenu != null) {
            edgeMenu.toggleMenu();
            return;
        }
        super.onBackPressed();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (AppUpdateManager.isInstallPermissionRequest(request)) {
            AppUpdateManager.resumePendingInstall(this);
            return;
        }
        if (request == REQUEST_BT_ENABLE) {
            BluetoothAdapter adapter = bluetoothAdapter();
            if (result == RESULT_OK && adapter != null && adapter.isEnabled())
                continueBluetoothRequest();
            else finishBluetoothRequest(false, "Bluetooth was not enabled");
            return;
        }
        if (request == REQUEST_BT_DISCOVERABLE) {
            boolean accepted = result != RESULT_CANCELED;
            if (accepted) {
                int duration = result > 0
                        ? Math.min(DEFAULT_DISCOVERABLE_SECONDS, result)
                        : DEFAULT_DISCOVERABLE_SECONDS;
                discoverabilityGrantedUntil = SystemClock.elapsedRealtime() + duration * 1000L;
                appendBluetoothLog("system discoverability consent accepted; result=" + result
                        + "; cachedSeconds=" + duration);
            } else {
                discoverabilityDeclinedForSession = true;
                appendBluetoothLog("system discoverability consent declined; suppressing retries this session");
            }
            finishBluetoothRequest(accepted,
                    accepted ? "" : "Bluetooth discoverability was declined");
            return;
        }
        if (request == HostFiles.EXPORT_LOG && result == RESULT_OK && data != null && data.getData() != null) {
            try { HostFiles.exportLog(this, data.getData()); update("Đã lưu log."); }
            catch (IOException error) { update("Không xuất được log: " + error); }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BT_PERMISSIONS) return;
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        appendBluetoothLog("runtime permission result granted=" + granted
                + " permissions=" + java.util.Arrays.toString(permissions));
        if (granted) continueBluetoothRequest();
        else finishBluetoothRequest(false, "Bluetooth permission was declined");
    }
    @Override protected void onDestroy() {
        restoreDiagnosticCrashHandler();
        super.onDestroy();
        if (overlays != null) overlays.clear();
        if (audio != null) audio.close();
        if (isFinishing()) {
            stopping = true; commands = null; sender.shutdownNow();
            unregisterBluetoothDiscoveryReceiver();
            unregisterBluetoothUuidReceiver();
            activeBluetoothRequest = null;
            bluetoothQueue.clear();
            bluetoothWorkers.shutdownNow();
            for (BluetoothSocket bluetoothSocket : bluetoothSockets.values()) {
                try { bluetoothSocket.close(); } catch (IOException ignored) { }
            }
            bluetoothSockets.clear();
            for (BluetoothServerSocket bluetoothServer : bluetoothServers.values()) {
                try { bluetoothServer.close(); } catch (IOException ignored) { }
            }
            bluetoothServers.clear();
            try {
                if (socket != null) socket.close();
                if (server != null) server.close();
                if (fontServer != null) fontServer.close();
            } catch (IOException ignored) { }
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
