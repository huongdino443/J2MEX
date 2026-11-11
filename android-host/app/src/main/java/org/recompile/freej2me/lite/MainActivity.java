package org.recompile.freej2me.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextWatcher;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.util.Linkify;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

    public final class MainActivity extends LocalizedActivity {
    private static MainActivity activeInstance;
    private static final int PICK_FILE_MANAGER = 73;
    private static final int REQUEST_STORAGE_PERMISSION = 74;
    private static final int REQUEST_ALL_FILES_SETTINGS = 75;
    private static final int REQUEST_GAME_SETTINGS = 76;
    private static final int PICK_STORAGE_FOLDER = 77;
    private static final int TAB_HOME = 0;
    private static final int TAB_COLLECTION = 1;
    private static final int TAB_ADD = 2;
    private static final int TAB_SEARCH = 3;
    private static final int TAB_MORE = 4;

    private static final int COLOR_BACKGROUND = Color.rgb(14, 24, 38);
    private static final int COLOR_SURFACE = Color.rgb(25, 36, 52);
    private static final int COLOR_SURFACE_SELECTED = Color.rgb(43, 53, 72);
    private static final int COLOR_TEXT = Color.rgb(232, 239, 247);
    private static final int COLOR_MUTED = Color.rgb(157, 170, 187);
    private static final int COLOR_ACCENT = Color.rgb(185, 195, 255);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<GameLibrary.Game> games = new ArrayList<>();
    private final ArrayList<GameCollectionStore.Collection> collections = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout header;
    private LinearLayout contentColumn;
    private FrameLayout contentStage;
    private ScrollView contentScroll;
    private GridLayout gameGrid;
    private EditText searchField;
    private ProgressBar progress;
    private TextView homeTitle;
    private View sortButton;
    private View latestLogExportRow;
    private View addNavigationItem;
    private View navigationGlow;
    private ImageView navigationBackdrop;
    private Bitmap navigationBackdropBitmap;
    private FrameLayout navigationBar;
    private LinearLayout navigationItemsRow;
    private View navigationIndicator;
    private View scrollTopFade;
    private PopupWindow activeStyledPopup;
    private View activePopupDim;
    private final View[] navigationItems = new View[5];
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable navigationBlurUpdate = this::refreshNavigationBlur;
    private boolean busy;
    private volatile boolean runtimeReady;
    private boolean libraryScanComplete;
    private boolean alphabetical;
    private int currentScreen = TAB_HOME;
    private int homeScrollY;
    private int collectionScrollY;
    private int searchScrollY;
    private int moreScrollY;
    private int activeNavigationTab = TAB_HOME;
    private float lastScrollTouchY;
    private float lastScrollTouchX;
    private int navigationGestureDirection;
    private int navigationGestureDistance;
    private int lastScrollDirection;
    private int lastContentScrollY;
    private int scrollRestoreGeneration;
    private int collapsingHeaderOffset;
    private boolean suppressHeaderScrollTracking;
    private boolean navigationIndicatorLaidOut;
    private boolean navigationHidden;
    private boolean searchCloseAnimating;
    private boolean openManagerAfterStorageGrant;
    private boolean folderPickerAfterStorageGrant;
    private boolean waitingForAllFilesSettings;
    private String restoredSearchQuery;
    private boolean headerOverlayMode;

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static TextView text(Activity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(232, 239, 247));
        view.setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 8));
        return view;
    }

    static android.widget.Button button(Activity activity, String value) {
        android.widget.Button button = new android.widget.Button(activity);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(15);
        return button;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        activeInstance = this;
        if (state != null) {
            int savedScreen = state.getInt("main.currentScreen", TAB_HOME);
            currentScreen = savedScreen == TAB_COLLECTION || savedScreen == TAB_SEARCH
                    || savedScreen == TAB_MORE ? savedScreen : TAB_HOME;
            homeScrollY = state.getInt("main.homeScrollY", 0);
            collectionScrollY = state.getInt("main.collectionScrollY", 0);
            searchScrollY = state.getInt("main.searchScrollY", 0);
            moreScrollY = state.getInt("main.moreScrollY", 0);
            restoredSearchQuery = state.getString("main.searchQuery", "");
        }
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (hasStorageAccess()) {
            initializeApplicationInterface();
        } else {
            window.setBackgroundDrawable(new ColorDrawable(COLOR_BACKGROUND));
            requestStoragePermissionOnFirstLaunch();
        }
    }

    private boolean interfaceInitialized;

    private void initializeApplicationInterface() {
        if (interfaceInitialized) return;
        interfaceInitialized = true;
        alphabetical = getPreferences(0).getBoolean("sortAlphabetical", false);
        try {
            GameStorage.initialize(this);
            collections.addAll(GameCollectionStore.load(this));
        } catch (IOException error) {
            interfaceInitialized = false;
            showError(error);
            return;
        }
        buildInterface();
        renderHeader();
        if (currentScreen == TAB_SEARCH && searchField != null
                && restoredSearchQuery != null && !restoredSearchQuery.isEmpty()) {
            searchField.setText(restoredSearchQuery);
        }
        setActiveNavigation(currentScreen);
        renderGames(scrollYForCurrentScreen());
        prepareRuntimeOnStartup();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (contentScroll != null) saveCurrentScroll();
        state.putInt("main.currentScreen", currentScreen);
        state.putInt("main.homeScrollY", homeScrollY);
        state.putInt("main.collectionScrollY", collectionScrollY);
        state.putInt("main.searchScrollY", searchScrollY);
        state.putInt("main.moreScrollY", moreScrollY);
        state.putString("main.searchQuery", searchField == null
                ? restoredSearchQuery : searchField.getText().toString());
        super.onSaveInstanceState(state);
    }

    private int scrollYForCurrentScreen() {
        if (currentScreen == TAB_COLLECTION) return collectionScrollY;
        if (currentScreen == TAB_SEARCH) return searchScrollY;
        if (currentScreen == TAB_MORE) return moreScrollY;
        return homeScrollY;
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(COLOR_BACKGROUND);
        header.setClickable(true);
        header.setPadding(dp(this, 20), dp(this, 10), dp(this, 16), dp(this, 10));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);

        contentStage = new FrameLayout(this);

        contentScroll = new ScrollView(this) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    trackNavigationGesture(event);
                }
                return super.onInterceptTouchEvent(event);
            }

            @Override public boolean onTouchEvent(MotionEvent event) {
                trackNavigationGesture(event);
                return super.onTouchEvent(event);
            }
        };
        contentScroll.setFillViewport(true);
        contentScroll.setClipToPadding(false);
        contentScroll.setVerticalScrollBarEnabled(false);
        contentStage.addView(contentScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentColumn = new LinearLayout(this);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        contentColumn.setPadding(dp(this, 12), dp(this, 8), dp(this, 12), dp(this, 100));
        contentScroll.addView(contentColumn, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollTopFade = new View(this);
        scrollTopFade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{COLOR_BACKGROUND, Color.TRANSPARENT}));
        scrollTopFade.setAlpha(0f);
        scrollTopFade.setClickable(false);
        scrollTopFade.setFocusable(false);
        FrameLayout.LayoutParams topFadeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 28), Gravity.TOP);
        contentStage.addView(scrollTopFade, topFadeParams);

        navigationGlow = new GlowView(this, 36, 8, 7);
        FrameLayout.LayoutParams glowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 80), Gravity.BOTTOM);
        glowParams.setMargins(dp(this, 4), 0, dp(this, 4), 0);
        contentStage.addView(navigationGlow, glowParams);

        navigationBackdrop = new ImageView(this);
        navigationBackdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        navigationBackdrop.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        dp(MainActivity.this, 36));
            }
        });
        navigationBackdrop.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= 31) {
            navigationBackdrop.setRenderEffect(RenderEffect.createBlurEffect(
                    dp(this, 42), dp(this, 42), Shader.TileMode.CLAMP));
        }
        FrameLayout.LayoutParams backdropParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 64), Gravity.BOTTOM);
        backdropParams.setMargins(dp(this, 12), 0, dp(this, 12), dp(this, 8));
        contentStage.addView(navigationBackdrop, backdropParams);

        navigationBar = createNavigationBar();
        FrameLayout.LayoutParams navigationParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 64), Gravity.BOTTOM);
        navigationParams.setMargins(dp(this, 12), 0, dp(this, 12), dp(this, 8));
        contentStage.addView(navigationBar, navigationParams);
        contentScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            handleContentScroll();
        });
        configureHeaderPlacement();
        setContentColumnLayout(false);
        contentStage.post(this::refreshNavigationBlur);
        setContentView(root);
    }

    private void configureHeaderPlacement() {
        boolean overlay = isLandscape()
                && (currentScreen == TAB_HOME || currentScreen == TAB_COLLECTION
                        || currentScreen == TAB_MORE);
        boolean attachedCorrectly = contentStage.getParent() == root
                && header.getParent() == (overlay ? contentStage : root)
                && progress.getParent() == (overlay ? contentStage : root);
        if (headerOverlayMode == overlay && attachedCorrectly) return;

        header.animate().cancel();
        if (header.getParent() instanceof ViewGroup)
            ((ViewGroup) header.getParent()).removeView(header);
        if (progress.getParent() instanceof ViewGroup)
            ((ViewGroup) progress.getParent()).removeView(progress);
        if (contentStage.getParent() instanceof ViewGroup)
            ((ViewGroup) contentStage.getParent()).removeView(contentStage);

        headerOverlayMode = overlay;
        collapsingHeaderOffset = 0;
        lastContentScrollY = 0;
        header.setVisibility(View.VISIBLE);
        header.setAlpha(1f);
        header.setTranslationY(0f);
        progress.setTranslationY(0f);

        if (overlay) {
            root.addView(contentStage, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 2), Gravity.TOP);
            progressParams.topMargin = dp(this, 70);
            contentStage.addView(progress, progressParams);
            contentStage.addView(header, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 70), Gravity.TOP));
            header.bringToFront();
        } else {
            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 70)));
            root.addView(progress, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 2)));
            root.addView(contentStage, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
    }

    private FrameLayout createNavigationBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(rounded(0xE51B2635, 36, 0));
        bar.setElevation(0);

        navigationIndicator = new View(this);
        navigationIndicator.setBackground(rounded(COLOR_SURFACE_SELECTED, 32, 0));
        navigationIndicator.setClickable(false);
        navigationIndicator.setFocusable(false);
        bar.addView(navigationIndicator, new FrameLayout.LayoutParams(0, 0,
                Gravity.TOP | Gravity.LEFT));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(this, 7), dp(this, 7), dp(this, 7), dp(this, 7));
        navigationItemsRow = row;
        bar.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        String[] labels = vietnamese
                ? new String[]{"Trang chủ", "Bộ sưu tập", "Thêm", "Tìm kiếm", "Khác"}
                : new String[]{"Home", "Collections", "Add", "Search", "More"};
        int[] icons = {IconView.HOME, IconView.FOLDER, IconView.ADD, IconView.SEARCH, IconView.MORE};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(this, 2), dp(this, 3), dp(this, 2), dp(this, 2));
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(labels[i]);
            IconView icon = new IconView(this, icons[i]);
            item.addView(icon, new LinearLayout.LayoutParams(dp(this, 21), dp(this, 21)));
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setTextColor(COLOR_MUTED);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(this, 3);
            item.addView(label, labelParams);
            item.setTag(new NavigationState(icon, label));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            itemParams.setMargins(dp(this, 2), 0, dp(this, 2), 0);
            row.addView(item, itemParams);
            navigationItems[i] = item;
            if (i == TAB_ADD) addNavigationItem = item;
            item.setOnClickListener(v -> selectNavigation(tab));
        }
        return bar;
    }

    private void selectNavigation(int tab) {
        if (busy) return;
        if (tab == TAB_ADD) {
            showAddSourcePopup(navigationItems[TAB_ADD], false);
            return;
        }
        if (tab == TAB_MORE) {
            changeScreen(TAB_MORE);
            return;
        }
        changeScreen(tab);
    }

    private void setActiveNavigation(int selected) {
        activeNavigationTab = selected;
        for (int i = 0; i < navigationItems.length; i++) {
            View item = navigationItems[i];
            if (item == null) continue;
            boolean active = i == selected;
            NavigationState state = (NavigationState) item.getTag();
            state.icon.setActive(active);
            state.label.setTextColor(active ? COLOR_ACCENT : COLOR_TEXT);
        }
        positionNavigationIndicator(true);
    }

    private void positionNavigationIndicator(boolean animate) {
        if (navigationIndicator == null || navigationItemsRow == null
                || activeNavigationTab < 0 || activeNavigationTab >= navigationItems.length) return;
        View target = navigationItems[activeNavigationTab];
        if (target == null) return;
        if (target.getWidth() <= 0 || target.getHeight() <= 0) {
            navigationItemsRow.post(() -> positionNavigationIndicator(false));
            return;
        }

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) navigationIndicator.getLayoutParams();
        params.width = target.getWidth();
        params.height = target.getHeight();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = 0;
        params.topMargin = navigationItemsRow.getTop() + target.getTop();
        navigationIndicator.setLayoutParams(params);
        float destinationX = navigationItemsRow.getLeft() + target.getLeft();
        navigationIndicator.animate().cancel();
        if (animate && navigationIndicatorLaidOut) {
            navigationIndicator.animate().translationX(destinationX)
                    .setDuration(230)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            navigationIndicator.setTranslationX(destinationX);
        }
        navigationIndicatorLaidOut = true;
    }

    private void renderHeader() {
        header.removeAllViews();
        sortButton = null;
        if (currentScreen == TAB_SEARCH) {
            header.setPadding(dp(this, 6), 0, dp(this, 6), 0);
            FrameLayout searchFrame = new FrameLayout(this);
            LinearLayout searchBox = new LinearLayout(this);
            searchBox.setGravity(Gravity.CENTER_VERTICAL);
            searchBox.setPadding(dp(this, 15), 0, dp(this, 14), 0);
             searchBox.setBackground(rounded(COLOR_SURFACE_SELECTED, 30, 0));
             searchBox.setElevation(0);
            IconView icon = new IconView(this, IconView.SEARCH);
             icon.setActive(false);
            searchBox.addView(icon, new LinearLayout.LayoutParams(dp(this, 22), dp(this, 22)));
            if (searchField == null) {
                searchField = new EditText(this);
                searchField.setSingleLine(true);
                searchField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                 searchField.setTextColor(COLOR_TEXT);
                 searchField.setHintTextColor(COLOR_MUTED);
                searchField.setHint(AppLocalization.text(this, "Tìm ứng dụng..."));
                searchField.setBackgroundColor(Color.TRANSPARENT);
                searchField.setPadding(dp(this, 12), 0, dp(this, 4), 0);
                searchField.setSelectAllOnFocus(false);
                searchField.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        int y = contentScroll.getScrollY();
                        renderGames(y);
                    }
                    @Override public void afterTextChanged(Editable s) { }
                });
            }
            if (searchField.getParent() != null) {
                ((ViewGroup) searchField.getParent()).removeView(searchField);
            }
            searchBox.addView(searchField, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            FrameLayout clearSearchButton = new FrameLayout(this);
            clearSearchButton.setContentDescription(
                    AppLocalization.text(this, "Xóa nội dung tìm kiếm"));
            clearSearchButton.setClickable(true);
            clearSearchButton.setFocusable(false);
            clearSearchButton.setOnClickListener(v -> changeScreen(TAB_HOME));
            IconView clearSearchIcon = new IconView(this, IconView.CLEAR);
            clearSearchButton.addView(clearSearchIcon, new FrameLayout.LayoutParams(
                    dp(this, 22), dp(this, 22), Gravity.CENTER));
            searchBox.addView(clearSearchButton, new LinearLayout.LayoutParams(
                    dp(this, 36), ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams searchBoxParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 50), Gravity.CENTER);
            searchBoxParams.setMargins(dp(this, 10), 0, dp(this, 10), 0);
            searchFrame.addView(searchBox, searchBoxParams);
            header.addView(searchFrame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            header.setPadding(dp(this, 20), dp(this, 10), dp(this, 16), dp(this, 10));
            boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
            String title = currentScreen == TAB_COLLECTION
                    ? (vietnamese ? "Bộ sưu tập" : "Collections")
                    : currentScreen == TAB_MORE
                            ? (vietnamese ? "Khác" : "More") : "J2MEX";
            homeTitle = new TextView(this);
            if ("J2MEX".equals(title)) {
                SpannableString brandTitle = new SpannableString(title);
                int xIndex = title.indexOf('X');
                if (xIndex >= 0) {
                    brandTitle.setSpan(new ForegroundColorSpan(COLOR_ACCENT),
                            xIndex, xIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                homeTitle.setText(brandTitle);
            } else {
                homeTitle.setText(title);
            }
            homeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    currentScreen == TAB_COLLECTION || currentScreen == TAB_MORE ? 22 : 27);
            homeTitle.setTextColor(COLOR_TEXT);
            homeTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            header.addView(homeTitle, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (currentScreen != TAB_MORE) {
                FrameLayout action = new FrameLayout(this);
                action.setBackground(rounded(COLOR_SURFACE, 24, 0x463D4A5E));
                boolean collectionScreen = currentScreen == TAB_COLLECTION;
                IconView actionIcon = new IconView(this,
                        collectionScreen ? IconView.ADD : IconView.FILTER);
                action.addView(actionIcon, new FrameLayout.LayoutParams(dp(this, 23), dp(this, 23),
                        Gravity.CENTER));
                action.setContentDescription(collectionScreen
                        ? "Tạo bộ sưu tập" : "Sắp xếp game");
                action.setClickable(true);
                action.setFocusable(true);
                action.setOnClickListener(collectionScreen
                        ? view -> showCreateCollectionPopup(null)
                        : this::showSortMenu);
                header.addView(action, new LinearLayout.LayoutParams(dp(this, 46), dp(this, 46)));
                sortButton = action;
            }
        }
    }

    private void changeScreen(int screen) {
        if (searchCloseAnimating) return;
        if (screen == currentScreen) {
            int active = screen == TAB_COLLECTION ? TAB_COLLECTION
                    : screen == TAB_SEARCH ? TAB_SEARCH
                    : screen == TAB_MORE ? TAB_MORE : TAB_HOME;
            setActiveNavigation(active);
            return;
        }
        if (currentScreen == TAB_SEARCH && screen != TAB_SEARCH) {
            animateSearchClose(screen);
            return;
        }
        applyScreenChange(screen);
    }

    private void animateSearchClose(int destination) {
        searchCloseAnimating = true;
        if (searchField != null) {
            searchField.setText("");
            InputMethodManager input = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            input.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            searchField.clearFocus();
        }
        header.animate().cancel();
        header.animate().alpha(0f).translationY(-dp(this, 28))
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    header.setTranslationY(0f);
                    searchCloseAnimating = false;
                    applyScreenChange(destination);
                })
                .start();
    }

    private void applyScreenChange(int screen) {
        if (screen == currentScreen) return;
        saveCurrentScroll();
        if (screen != TAB_SEARCH && searchField != null) {
            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            input.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
            searchField.clearFocus();
        }
        currentScreen = screen;
        configureHeaderPlacement();
        collapsingHeaderOffset = 0;
        lastContentScrollY = 0;
        header.setTranslationY(0f);
        progress.setTranslationY(0f);
        header.animate().cancel();
        header.setAlpha(0f);
        boolean openingSearch = screen == TAB_SEARCH;
        boolean openingHome = screen == TAB_HOME;
        boolean openingCollections = screen == TAB_COLLECTION;
        boolean openingMore = screen == TAB_MORE;
        boolean droppingHeader = openingSearch || openingHome || openingCollections || openingMore;
        header.setTranslationY(droppingHeader ? -dp(this, 24) : 0f);
        renderHeader();
        header.animate().alpha(1f).translationY(0f)
                .setDuration(droppingHeader ? 330 : 280)
                .setStartDelay(droppingHeader ? 16 : 0)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        int active = screen == TAB_COLLECTION ? TAB_COLLECTION
                : screen == TAB_SEARCH ? TAB_SEARCH
                : screen == TAB_MORE ? TAB_MORE : TAB_HOME;
        setActiveNavigation(active);
        setNavigationHidden(false, true);
        int y = screen == TAB_HOME ? homeScrollY
                : screen == TAB_COLLECTION ? collectionScrollY
                : screen == TAB_MORE ? moreScrollY : searchScrollY;
        contentColumn.animate().cancel();
        contentColumn.setAlpha(0f);
        contentColumn.setTranslationY(dp(this, 12));
        renderGames(y);
        contentColumn.animate().alpha(1f).translationY(0f)
                .setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        if (screen == TAB_SEARCH && searchField != null) {
            EditText focusedSearch = searchField;
            searchField.postDelayed(() -> {
                if (currentScreen != TAB_SEARCH || searchCloseAnimating) return;
                focusedSearch.requestFocus();
                InputMethodManager input = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                input.showSoftInput(focusedSearch, InputMethodManager.SHOW_IMPLICIT);
            }, 80);
        }
    }

    private void saveCurrentScroll() {
        if (currentScreen == TAB_HOME) homeScrollY = contentScroll.getScrollY();
        else if (currentScreen == TAB_COLLECTION) collectionScrollY = contentScroll.getScrollY();
        else if (currentScreen == TAB_SEARCH) searchScrollY = contentScroll.getScrollY();
        else if (currentScreen == TAB_MORE) moreScrollY = contentScroll.getScrollY();
    }

    private void renderGames(int restoreY) {
        if (contentColumn == null) return;
        if (currentScreen == TAB_COLLECTION) {
            renderCollections(restoreY);
            return;
        }
        if (currentScreen == TAB_MORE) {
            renderMore(restoreY);
            return;
        }
        if (currentScreen == TAB_HOME && games.isEmpty()
                && (!runtimeReady || !libraryScanComplete)) {
            contentColumn.removeAllViews();
            setContentColumnLayout(true);
            restoreContentScroll(0);
            return;
        }
        contentColumn.removeAllViews();
        ArrayList<GameLibrary.Game> visible = new ArrayList<>();
        String query = currentScreen == TAB_SEARCH && searchField != null
                ? searchField.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        boolean waitingForSearchQuery = currentScreen == TAB_SEARCH && query.isEmpty();
        if (!waitingForSearchQuery) {
            for (GameLibrary.Game game : games) {
                if (query.isEmpty()
                        || game.title.toLowerCase(Locale.ROOT).contains(query)
                        || game.sourceName.toLowerCase(Locale.ROOT).contains(query)) {
                    visible.add(game);
                }
            }
        }
        Comparator<GameLibrary.Game> comparator;
        if (currentScreen == TAB_COLLECTION || alphabetical) {
            comparator = (left, right) -> left.title.compareToIgnoreCase(right.title);
        } else {
            comparator = (left, right) -> Long.compare(
                    right.jar.lastModified(), left.jar.lastModified());
        }
        Collections.sort(visible, comparator);

        if (visible.isEmpty()) {
            setContentColumnLayout(true);
            addEmptyState(query, isLandscape());
        } else {
            setContentColumnLayout(false);
            gameGrid = new GridLayout(this);
            int columns = isLandscape() ? 6 : 3;
            gameGrid.setColumnCount(columns);
            gameGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            gameGrid.setUseDefaultMargins(false);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int contentWidth = screenWidth - dp(this, 48);
            int cellWidth = Math.max(dp(this, 54), contentWidth / columns);
            int iconSize = Math.min(dp(this, 124), Math.round(cellWidth * 0.92f));
            for (int i = 0; i < visible.size(); i++) {
                gameGrid.addView(createGameCell(
                        visible.get(i), iconSize, i / columns, i % columns),
                        gridCellParams(i / columns, i % columns));
            }
            int filledCellCount = ((visible.size() + columns - 1) / columns) * columns;
            for (int i = visible.size(); i < filledCellCount; i++) {
                View spacer = new View(this);
                spacer.setVisibility(View.INVISIBLE);
                gameGrid.addView(spacer, gridCellParams(i / columns, i % columns));
            }
            contentColumn.addView(gameGrid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        restoreContentScroll(restoreY);
    }

    private void renderCollections(int restoreY) {
        contentColumn.removeAllViews();
        setContentColumnLayout(collections.isEmpty());
        if (collections.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            int emptyVerticalPadding = isLandscape() ? 0 : 72;
            int emptyBottomPadding = isLandscape() ? 0 : 48;
            empty.setPadding(dp(this, 24), dp(this, emptyVerticalPadding),
                    dp(this, 24), dp(this, emptyBottomPadding));

            TextView title = new TextView(this);
            title.setText(AppLocalization.text(this, "Chưa có bộ sưu tập"));
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            empty.addView(title);

            TextView description = new TextView(this);
            description.setText(AppLocalization.text(
                    this, "Tạo bộ sưu tập để sắp xếp các game của bạn."));
            description.setTextColor(COLOR_MUTED);
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            description.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descriptionParams.topMargin = dp(this, 8);
            empty.addView(description, descriptionParams);

            TextView create = styledFilledActionButton("Tạo bộ sưu tập");
            create.setOnClickListener(v -> showCreateCollectionPopup(null));
            LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            createParams.topMargin = dp(this, 20);
            empty.addView(create, createParams);
            contentColumn.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (GameCollectionStore.Collection collection : collections) {
                contentColumn.addView(createCollectionCard(collection));
            }
        }

        restoreContentScroll(restoreY);
    }

    private void renderMore(int restoreY) {
        contentColumn.removeAllViews();
        latestLogExportRow = null;
        setContentColumnLayout(false);
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        LinearLayout preferencesCard = moreCard();
        addMoreSection(preferencesCard, vietnamese ? "Tùy chọn" : "Preferences");
        addMoreSwitch(preferencesCard,
                vietnamese ? "Hiển thị FPS" : "Show FPS",
                vietnamese ? "Hiển thị số khung hình nhận được mỗi giây khi chơi."
                        : "Show received frames per second while playing.",
                AppPreferences.showFps(this), AppPreferences::setShowFps);
        addMoreSwitch(preferencesCard,
                vietnamese ? "Thanh trạng thái" : "Status bar",
                vietnamese ? "Hiện thanh trạng thái trong lúc chơi game."
                        : "Keep the Android status bar visible while playing.",
                AppPreferences.statusBarVisible(this), AppPreferences::setStatusBarVisible);

        addMoreSwitch(preferencesCard,
                vietnamese ? "Giữ hướng xoay hiện tại" : "Lock current orientation",
                vietnamese ? "Khóa hướng màn hình theo chiều đang dùng khi mở game."
                        : "Lock the screen to its current orientation when a game starts.",
                AppPreferences.orientationLocked(this), AppPreferences::setOrientationLocked);
        addMoreSwitch(preferencesCard,
                vietnamese ? "Ghi log để gỡ lỗi" : "Diagnostic logging",
                vietnamese ? "Cho phép ghi log runtime và bật chức năng lưu log trong menu cạnh."
                        : "Write runtime logs and enable log export in the edge menu.",
                AppPreferences.debugLogsEnabled(this), (context, enabled) -> {
                    AppPreferences.setDebugLogsEnabled(context, enabled);
                    setLatestLogExportRowEnabled(enabled);
                });
        latestLogExportRow = addMoreRow(preferencesCard,
                vietnamese ? "Xuất log gần nhất" : "Export latest log",
                vietnamese ? "Cần bật ghi log trước để lưu nhật ký"
                        : "Enable logging to save a diagnostic log.",
                vietnamese ? "Xuất" : "Export",
                null);
        setLatestLogExportRowEnabled(AppPreferences.debugLogsEnabled(this));
        addMoreRow(preferencesCard,
                vietnamese ? "Ngôn ngữ" : "Language",
                vietnamese ? "Chọn ngôn ngữ hoặc dựa theo thiết bị."
                        : "Choose a language or follow the device.",
                languageChoiceLabel(), view -> showLanguagePopup());
        contentColumn.addView(preferencesCard);

        LinearLayout storageCard = moreCard();
        addMoreSection(storageCard, vietnamese ? "Lưu trữ" : "Storage");
        String storagePath;
        try {
            storagePath = GameStorage.root(this).getAbsolutePath();
        } catch (IOException error) {
            storagePath = vietnamese
                    ? "Không thể đọc thư mục lưu hiện tại."
                    : "The current storage folder is unavailable.";
        }
        addMoreRow(storageCard,
                vietnamese ? "Thư mục làm việc" : "Working folder",
                storagePath,
                vietnamese ? "Chọn" : "Choose",
                view -> chooseStorageLocation());
        contentColumn.addView(storageCard);

        LinearLayout infoCard = moreCard();
        addMoreSection(infoCard, vietnamese ? "Thông tin" : "About");
        addMoreRow(infoCard,
                vietnamese ? "Cập nhật" : "Updates",
                vietnamese ? "Kiểm tra phiên bản mới và hướng dẫn tải khi có bản phát hành."
                        : "Check for a newer version and download details when a release is available.",
                vietnamese ? "Kiểm tra" : "Check", view -> showUpdateCheck());
        addMoreRow(infoCard,
                vietnamese ? "Giới thiệu" : "About",
                vietnamese ? "Tổng quan ứng dụng và giấy phép thành phần."
                        : "App overview and component licenses.",
                vietnamese ? "Xem" : "View", view -> showIntroduction());
        contentColumn.addView(infoCard);

        restoreContentScroll(restoreY);
    }

    private void setLatestLogExportRowEnabled(boolean enabled) {
        if (latestLogExportRow == null) return;
        latestLogExportRow.setEnabled(enabled);
        latestLogExportRow.setAlpha(enabled ? 1f : 0.42f);
        latestLogExportRow.setClickable(enabled);
        latestLogExportRow.setFocusable(enabled);
        latestLogExportRow.setBackground(enabled ? rounded(Color.TRANSPARENT, 10, 0) : null);
        latestLogExportRow.setOnClickListener(enabled
                ? view -> HostFiles.requestLog(this) : null);
    }

    static void onRuntimePreferencesUpdated() {
        MainActivity activity = activeInstance;
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()
                    || activity.currentScreen != TAB_MORE || activity.contentScroll == null) {
                return;
            }
            activity.renderMore(activity.contentScroll.getScrollY());
        });
    }

    private void restoreContentScroll(int restoreY) {
        int target = Math.max(0, restoreY);
        int generation = ++scrollRestoreGeneration;
        suppressHeaderScrollTracking = true;
        lastContentScrollY = target;
        contentScroll.scrollTo(0, target);
        contentScroll.post(() -> {
            contentScroll.scrollTo(0, target);
            lastContentScrollY = contentScroll.getScrollY();
            if (generation == scrollRestoreGeneration) {
                suppressHeaderScrollTracking = false;
                updateScrollTopFade(contentScroll.getScrollY());
                scheduleNavigationBlur();
            }
        });
    }

    private LinearLayout moreCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(COLOR_SURFACE, 18, 0x463D4A5E));
        card.setClipToOutline(true);
        card.setPadding(dp(this, 14), dp(this, 8), dp(this, 14), dp(this, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(this, 2), dp(this, 4), dp(this, 2), dp(this, 10));
        card.setLayoutParams(params);
        return card;
    }

    private void addMoreSection(LinearLayout parent, String title) {
        TextView heading = new TextView(this);
        heading.setText(AppLocalization.text(this, title));
        heading.setTextColor(COLOR_ACCENT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        heading.setTypeface(Typeface.MONOSPACE);
        heading.setPadding(dp(this, 2), dp(this, 6), dp(this, 2), dp(this, 4));
        parent.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addMoreSwitch(LinearLayout parent, String title, String description,
            boolean checked, MoreSwitchListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(this, 2), dp(this, 5), dp(this, 2), dp(this, 5));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView label = moreBodyText(title, 15);
        textColumn.addView(label);
        TextView detail = moreBodyText(description, 12);
        detail.setTextColor(COLOR_MUTED);
        detail.setPadding(0, dp(this, 3), dp(this, 6), 0);
        textColumn.addView(detail);
        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{COLOR_ACCENT, COLOR_MUTED}));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xFF5368D8, 0xFF46505D}));
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(this, 48)));
        toggle.setOnCheckedChangeListener((button, value) -> listener.set(this, value));
        row.setContentDescription(AppLocalization.text(this, title) + ". "
                + AppLocalization.text(this, description));
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
    }

    private interface MoreSwitchListener {
        void set(Context context, boolean enabled);
    }

    private LinearLayout addMoreRow(LinearLayout parent, String title, String description,
            String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(this, 2), dp(this, 10), dp(this, 2), dp(this, 10));
        TextView label = moreBodyText(title, 15);
        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(label);
        if (description != null && !description.isEmpty()) {
            TextView detail = moreBodyText(description, 12);
            detail.setTextColor(COLOR_MUTED);
            detail.setPadding(0, dp(this, 3), dp(this, 4), 0);
            textColumn.addView(detail);
        }
        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null && !value.isEmpty()) {
            TextView valueView = moreBodyText(value, 13);
            valueView.setTextColor(COLOR_ACCENT);
            valueView.setGravity(Gravity.CENTER);
            row.addView(valueView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(rounded(Color.TRANSPARENT, 10, 0));
            row.setOnClickListener(listener);
        }
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView moreBodyText(String value, int size) {
        TextView text = new TextView(this);
        text.setText(AppLocalization.text(this, value));
        text.setTextColor(COLOR_TEXT);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        return text;
    }

    private String languageChoiceLabel() {
        int choice = AppPreferences.languageChoice(this);
        if (choice == 1) return "Tiếng Việt";
        if (choice == 2) return "English";
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        return vietnamese ? "Theo thiết bị" : "System";
    }

    private void showLanguagePopup() {
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        String[] options = vietnamese
                ? new String[]{"Theo cài đặt hệ thống", "English", "Tiếng Việt"}
                : new String[]{"System default", "English", "Tiếng Việt"};
        LinearLayout panel = collectionModalPanel(
                vietnamese ? "Ngôn ngữ ứng dụng" : "App language");
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        choicesParams.topMargin = dp(this, 8);
        panel.addView(choices, choicesParams);
        int selected = AppPreferences.languageChoice(this);
        for (int i = 0; i < options.length; i++) {
            final int choice = i == 0 ? 0 : i == 1 ? 2 : 1;
            RadioButton option = new RadioButton(this);
            option.setText(options[i]);
            option.setTextColor(COLOR_TEXT);
            option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            option.setButtonTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{COLOR_ACCENT, COLOR_MUTED}));
            option.setMinHeight(dp(this, 60));
            option.setPadding(dp(this, 6), 0, dp(this, 4), 0);
            option.setChecked(choice == selected);
            option.setFocusable(false);
            option.setFocusableInTouchMode(false);
            option.setBackgroundColor(Color.TRANSPARENT);
            choices.addView(option, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            option.setOnClickListener(view -> {
                AppPreferences.setLanguageChoice(this, choice);
                if (activeStyledPopup != null) activeStyledPopup.dismiss();
                recreate();
            });
        }
        showModalPopup(panel, false);
    }

    private void updateNavigationLanguage() {
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        String[] labels = vietnamese
                ? new String[]{"Trang chủ", "Bộ sưu tập", "Thêm", "Tìm kiếm", "Khác"}
                : new String[]{"Home", "Collections", "Add", "Search", "More"};
        for (int i = 0; i < navigationItems.length; i++) {
            if (navigationItems[i] == null) continue;
            NavigationState state = (NavigationState) navigationItems[i].getTag();
            state.label.setText(labels[i]);
            navigationItems[i].setContentDescription(labels[i]);
        }
    }

    private View createCollectionCard(GameCollectionStore.Collection collection) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(COLOR_SURFACE, 16, 0x463D4A5E));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(this, 2), dp(this, 4), dp(this, 2), dp(this, 8));
        card.setLayoutParams(cardParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(this, 14), dp(this, 6), dp(this, 8), dp(this, 6));
        headerRow.setMinimumHeight(dp(this, 64));

        IconView folder = new IconView(this, IconView.FOLDER);
        folder.setActive(true);
        headerRow.addView(folder, new LinearLayout.LayoutParams(dp(this, 25), dp(this, 25)));

        TextView name = new TextView(this);
        name.setText(collection.name);
        name.setTextColor(COLOR_TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(this, 14);
        headerRow.addView(name, nameParams);

        FrameLayout edit = collectionIconButton(IconView.EDIT, "Chỉnh sửa " + collection.name);
        edit.setOnClickListener(v -> showCollectionActions(edit, collection));
        headerRow.addView(edit, new LinearLayout.LayoutParams(dp(this, 42), dp(this, 48)));

        FrameLayout expand = collectionIconButton(IconView.CHEVRON,
                collection.expanded ? "Thu gọn bộ sưu tập" : "Mở bộ sưu tập");
        expand.setRotation(collection.expanded ? 180f : 0f);
        expand.setOnClickListener(v -> {
            toggleCollectionExpansion(collection, body, expand);
        });
        headerRow.addView(expand, new LinearLayout.LayoutParams(dp(this, 42), dp(this, 48)));
        headerRow.setOnClickListener(v -> {
            toggleCollectionExpansion(collection, body, expand);
        });
        card.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ArrayList<GameLibrary.Game> items = gamesInCollection(collection);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(AppLocalization.text(this, "Chưa có game trong bộ sưu tập này."));
            empty.setTextColor(COLOR_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setPadding(dp(this, 54), dp(this, 8), dp(this, 16), dp(this, 16));
            body.addView(empty);
        } else {
            View divider = new View(this);
            divider.setBackgroundColor(0x333D4A5E);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 1));
            dividerParams.setMargins(dp(this, 14), 0, dp(this, 14), dp(this, 4));
            body.addView(divider, dividerParams);
            if (isLandscape()) {
                GridLayout gameGrid = new GridLayout(this);
                gameGrid.setColumnCount(2);
                gameGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
                gameGrid.setUseDefaultMargins(false);
                for (int i = 0; i < items.size(); i++) {
                    gameGrid.addView(createCollectionGameRow(collection, items.get(i)),
                            gridCellParams(i / 2, i % 2));
                }
                body.addView(gameGrid, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                for (GameLibrary.Game game : items) {
                    body.addView(createCollectionGameRow(collection, game),
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }
        }
        body.setVisibility(collection.expanded ? View.VISIBLE : View.GONE);
        card.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void toggleCollectionExpansion(GameCollectionStore.Collection collection,
            View body, View arrow) {
        boolean expanding = !collection.expanded;
        collection.expanded = expanding;
        saveCollections();
        TransitionManager.beginDelayedTransition(contentColumn,
                new AutoTransition().setDuration(240)
                        .setInterpolator(new DecelerateInterpolator()));
        body.setVisibility(expanding ? View.VISIBLE : View.GONE);
        arrow.animate().cancel();
        arrow.animate().rotation(expanding ? 180f : 0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private FrameLayout collectionIconButton(int iconType, String description) {
        FrameLayout button = new FrameLayout(this);
        button.setBackground(rounded(Color.TRANSPARENT, 24, 0));
        button.setContentDescription(AppLocalization.text(this, description));
        button.setClickable(true);
        button.setFocusable(true);
        IconView icon = new IconView(this, iconType);
        button.addView(icon, new FrameLayout.LayoutParams(dp(this, 22), dp(this, 22),
                Gravity.CENTER));
        return button;
    }

    private View createCollectionGameRow(GameCollectionStore.Collection collection,
            GameLibrary.Game game) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(this, 14), dp(this, 6), dp(this, 14), dp(this, 6));
        row.setMinimumHeight(dp(this, 62));
        row.setClickable(true);
        row.setFocusable(true);

        GameIconView icon = new GameIconView(this);
        icon.setArtwork(game.icon, game.title);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(this, 46), dp(this, 46));
        row.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText(game.title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(this, 12);
        row.addView(title, titleParams);

        row.setOnClickListener(v -> openGame(game));
        row.setOnLongClickListener(v -> {
            showStyledPopup(v, new String[]{
                    "Chạy game", "Thiết lập", "Đặt lại dữ liệu",
                    "Gỡ cài đặt game", "Gỡ khỏi bộ sưu tập"},
                    false, false, position -> {
                if (position == 0) {
                    openGame(game);
                } else if (position == 1) {
                    openGameSettings(game.jar, game.title, false);
                } else if (position == 2) {
                    confirmResetGameData(game);
                } else if (position == 3) {
                    confirmUninstallGame(game);
                } else {
                    collection.gamePaths.remove(gamePath(game));
                    saveCollections();
                    renderCollections(contentScroll.getScrollY());
                }
            });
            return true;
        });
        return row;
    }

    private ArrayList<GameLibrary.Game> gamesInCollection(
            GameCollectionStore.Collection collection) {
        ArrayList<GameLibrary.Game> result = new ArrayList<>();
        for (String path : collection.gamePaths) {
            GameLibrary.Game game = findGameByPath(path);
            if (game != null) result.add(game);
        }
        return result;
    }

    private GameLibrary.Game findGameByPath(String path) {
        for (GameLibrary.Game game : games) {
            if (gamePath(game).equals(path)) return game;
        }
        return null;
    }

    private String gamePath(GameLibrary.Game game) {
        return game.jar.getAbsolutePath();
    }

    private void saveCollections() {
        try {
            GameCollectionStore.save(this, collections);
        } catch (IOException error) {
            showError(error);
        }
    }

    private TextView styledActionButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(AppLocalization.text(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? COLOR_ACCENT : COLOR_MUTED);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(this, 14), dp(this, 8), dp(this, 14), dp(this, 8));
        button.setMinHeight(dp(this, 48));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView styledFilledActionButton(String label) {
        TextView button = new TextView(this);
        button.setText(AppLocalization.text(this, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setTextColor(COLOR_BACKGROUND);
        button.setBackground(rounded(COLOR_ACCENT, 24, 0));
        button.setPadding(dp(this, 20), dp(this, 12), dp(this, 20), dp(this, 12));
        button.setMinHeight(dp(this, 48));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private interface CollectionNameListener {
        void onSave(String name);
    }

    private LinearLayout collectionModalPanel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(this, 20), dp(this, 18), dp(this, 20), dp(this, 16));
        panel.setBackground(rounded(COLOR_SURFACE, 20, 0xFF3D4A5E));
        panel.setElevation(dp(this, 8));

        TextView heading = new TextView(this);
        heading.setText(AppLocalization.text(this, title));
        heading.setTextColor(COLOR_TEXT);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private PopupWindow showModalPopup(View panel, boolean needsInputMethod) {
        if (activeStyledPopup != null) activeStyledPopup.dismiss();
        FrameLayout contentRoot = findViewById(android.R.id.content);
        View dim = new View(this);
        dim.setBackgroundColor(0x80000000);
        dim.setClickable(true);
        contentRoot.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(dp(this, 400), screenWidth - dp(this, 32));
        PopupWindow popup = new PopupWindow(panel, popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(this, 10));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(needsInputMethod
                ? PopupWindow.INPUT_METHOD_NEEDED : PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dim.setOnClickListener(view -> popup.dismiss());
        activeStyledPopup = popup;
        activePopupDim = dim;
        popup.setOnDismissListener(() -> {
            if (dim.getParent() instanceof ViewGroup) {
                ((ViewGroup) dim.getParent()).removeView(dim);
            }
            if (activeStyledPopup == popup) {
                activeStyledPopup = null;
                activePopupDim = null;
            }
        });
        popup.showAtLocation(contentRoot, Gravity.CENTER, 0, 0);
        return popup;
    }

    private void showCreateCollectionPopup(GameLibrary.Game gameToAdd) {
        showCollectionNamePopup("Tạo bộ sưu tập", "", null, name -> {
            GameCollectionStore.Collection collection =
                    new GameCollectionStore.Collection(name);
            if (gameToAdd != null) {
                collection.gamePaths.add(gamePath(gameToAdd));
                collection.expanded = true;
            }
            collections.add(0, collection);
            saveCollections();
            if (currentScreen == TAB_COLLECTION) {
                renderCollections(contentScroll.getScrollY());
            }
            Toast.makeText(this, AppLocalization.text(this, gameToAdd == null
                    ? "Đã tạo bộ sưu tập." : "Đã tạo bộ sưu tập và thêm game."),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showRenameCollectionPopup(GameCollectionStore.Collection collection) {
        showCollectionNamePopup("Đổi tên bộ sưu tập", collection.name, collection.id, name -> {
            collection.name = name;
            saveCollections();
            renderCollections(contentScroll.getScrollY());
        });
    }

    private void showCollectionNamePopup(String title, String initialName,
            String ignoredCollectionId, CollectionNameListener listener) {
        LinearLayout panel = collectionModalPanel(title);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setHint(AppLocalization.text(this, "Tên bộ sưu tập"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(rounded(COLOR_SURFACE_SELECTED, 12, 0xFF3D4A5E));
        input.setPadding(dp(this, 14), dp(this, 12), dp(this, 14), dp(this, 12));
        input.setText(initialName);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 52));
        inputParams.topMargin = dp(this, 16);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 18);
        panel.addView(actions, actionsParams);

        TextView cancel = styledActionButton("Hủy", false);
        TextView confirm = styledActionButton("Xác nhận", true);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confirmParams.leftMargin = dp(this, 10);
        actions.addView(confirm, confirmParams);

        PopupWindow popup = showModalPopup(panel, true);
        cancel.setOnClickListener(v -> popup.dismiss());
        confirm.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(AppLocalization.text(this, "Nhập tên bộ sưu tập"));
                input.requestFocus();
                return;
            }
            if (collectionNameExists(name, ignoredCollectionId)) {
                input.setError(AppLocalization.text(this, "Tên bộ sưu tập đã tồn tại"));
                input.requestFocus();
                return;
            }
            hidePopupKeyboard(input);
            popup.dismiss();
            if (listener != null) listener.onSave(name);
        });
        input.requestFocus();
        input.setSelection(input.length());
        input.postDelayed(() -> {
            if (popup.isShowing()) {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 160);
    }

    private boolean collectionNameExists(String name, String ignoredCollectionId) {
        for (GameCollectionStore.Collection collection : collections) {
            if (ignoredCollectionId != null && collection.id.equals(ignoredCollectionId)) continue;
            if (collection.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void hidePopupKeyboard(View view) {
        InputMethodManager input = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        input.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showCollectionActions(View anchor,
            GameCollectionStore.Collection collection) {
        showStyledPopup(anchor, new String[]{"Thêm game", "Đổi tên", "Xóa bộ sưu tập"},
                false, true, position -> {
            if (position == 0) {
                showCollectionGamePicker(collection);
            } else if (position == 1) {
                showRenameCollectionPopup(collection);
            } else if (position == 2) {
                showDeleteCollectionConfirmation(collection);
            }
        });
    }

    private void showDeleteCollectionConfirmation(
            GameCollectionStore.Collection collection) {
        LinearLayout panel = collectionModalPanel("Xóa bộ sưu tập?");
        TextView message = new TextView(this);
        message.setText(AppLocalization.text(this, "“" + collection.name
                + "” sẽ bị xóa. Các game trong thư viện vẫn được giữ nguyên."));
        message.setTextColor(COLOR_MUTED);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setPadding(0, dp(this, 12), 0, 0);
        panel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 18);
        panel.addView(actions, actionsParams);
        TextView cancel = styledActionButton("Hủy", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView confirm = styledActionButton("Xóa", true);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confirmParams.leftMargin = dp(this, 10);
        actions.addView(confirm, confirmParams);

        PopupWindow popup = showModalPopup(panel, false);
        cancel.setOnClickListener(v -> popup.dismiss());
        confirm.setOnClickListener(v -> {
            collections.remove(collection);
            saveCollections();
            popup.dismiss();
            renderCollections(contentScroll.getScrollY());
        });
    }

    private void showCollectionGamePicker(GameCollectionStore.Collection collection) {
        LinearLayout panel = collectionModalPanel("Thêm game");
        ArrayList<GameLibrary.Game> selectableGames = new ArrayList<>(games);
        Collections.sort(selectableGames,
                (left, right) -> left.title.compareToIgnoreCase(right.title));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        ArrayList<CheckBox> checkBoxes = new ArrayList<>();
        if (selectableGames.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(AppLocalization.text(this, "Chưa có game trong thư viện."));
            empty.setTextColor(COLOR_MUTED);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(this, 12), dp(this, 28), dp(this, 12), dp(this, 28));
            list.addView(empty);
        } else {
            int[][] states = {{android.R.attr.state_checked}, {}};
            int[] colors = {COLOR_ACCENT, COLOR_MUTED};
            ColorStateList tint = new ColorStateList(states, colors);
            for (GameLibrary.Game game : selectableGames) {
                CheckBox checkBox = new CheckBox(this);
                checkBox.setText(game.title);
                checkBox.setTextColor(COLOR_TEXT);
                checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                checkBox.setSingleLine(true);
                checkBox.setEllipsize(android.text.TextUtils.TruncateAt.END);
                checkBox.setButtonTintList(tint);
                checkBox.setPadding(dp(this, 4), dp(this, 4), dp(this, 8), dp(this, 4));
                checkBox.setMinHeight(dp(this, 50));
                checkBox.setChecked(collection.gamePaths.contains(gamePath(game)));
                checkBoxes.add(checkBox);
                list.addView(checkBox, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        listScroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(this, 180);
        int listHeight = Math.max(dp(this, 140),
                Math.min(dp(this, 380), availableHeight - dp(this, 150)));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight);
        scrollParams.topMargin = dp(this, 12);
        panel.addView(listScroll, scrollParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 14);
        panel.addView(actions, actionsParams);
        TextView cancel = styledActionButton("Hủy", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView confirm = styledActionButton("Xác nhận", true);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confirmParams.leftMargin = dp(this, 10);
        actions.addView(confirm, confirmParams);

        PopupWindow popup = showModalPopup(panel, false);
        cancel.setOnClickListener(v -> popup.dismiss());
        confirm.setOnClickListener(v -> {
            collection.gamePaths.clear();
            for (int i = 0; i < selectableGames.size(); i++) {
                if (checkBoxes.get(i).isChecked()) {
                    collection.gamePaths.add(gamePath(selectableGames.get(i)));
                }
            }
            collection.expanded = true;
            saveCollections();
            popup.dismiss();
            renderCollections(contentScroll.getScrollY());
        });
    }

    private void showAddGameToCollectionPopup(View anchor, GameLibrary.Game game) {
        if (collections.isEmpty()) {
            showCreateCollectionPopup(game);
            return;
        }
        String[] labels = new String[collections.size() + 1];
        for (int i = 0; i < collections.size(); i++) labels[i] = collections.get(i).name;
        labels[labels.length - 1] = "Tạo bộ sưu tập mới";
        showStyledPopup(anchor, labels, false, false, position -> {
            if (position == collections.size()) {
                showCreateCollectionPopup(game);
            } else {
                addGameToCollection(collections.get(position), game);
            }
        });
    }

    private void addGameToCollection(GameCollectionStore.Collection collection,
            GameLibrary.Game game) {
        String path = gamePath(game);
        if (!collection.gamePaths.contains(path)) {
            collection.gamePaths.add(path);
            collection.expanded = true;
            saveCollections();
            Toast.makeText(this, AppLocalization.text(this,
                    "Đã thêm vào “" + collection.name + "”."),
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, AppLocalization.text(this,
                    "Game đã có trong bộ sưu tập này."),
                    Toast.LENGTH_SHORT).show();
        }
        if (currentScreen == TAB_COLLECTION) {
            renderCollections(contentScroll.getScrollY());
        }
    }

    private void handleContentScroll() {
        if (contentScroll == null) return;
        int scrollY = contentScroll.getScrollY();
        int scrollDelta = scrollY - lastContentScrollY;
        if (suppressHeaderScrollTracking) {
            lastContentScrollY = scrollY;
        } else {
            if (headerOverlayMode && scrollDelta != 0) {
                int maxOffset = dp(this, 70);
                collapsingHeaderOffset = Math.max(0,
                        Math.min(maxOffset, collapsingHeaderOffset + scrollDelta));
                float translation = -collapsingHeaderOffset;
                header.setTranslationY(translation);
                progress.setTranslationY(translation);
            }
            lastContentScrollY = scrollY;
        }
        if (scrollY == 0 && lastScrollDirection < 0 && navigationHidden)
            setNavigationHidden(false, true);
        updateScrollTopFade(scrollY);
        scheduleNavigationBlur();
    }

    private void trackNavigationGesture(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastScrollTouchY = event.getY();
                lastScrollTouchX = event.getX();
                navigationGestureDirection = 0;
                navigationGestureDistance = 0;
                break;
            case MotionEvent.ACTION_MOVE: {
                float currentY = event.getY();
                int movement = Math.round(lastScrollTouchY - currentY);
                int horizontalMovement = Math.round(lastScrollTouchX - event.getX());
                if (movement != 0 && Math.abs(movement) >= Math.abs(horizontalMovement)) {
                    int direction = movement > 0 ? 1 : -1;
                    int distance = Math.abs(movement);
                    navigationGestureDistance = navigationGestureDirection == direction
                            ? navigationGestureDistance + distance : distance;
                    navigationGestureDirection = direction;
                    lastScrollDirection = direction;
                    int minScroll = dp(this, 16);
                    int hideThreshold = dp(this, 28);
                    int showThreshold = dp(this, 8);
                    if (direction > 0 && contentScroll.getScrollY() > minScroll
                            && navigationGestureDistance >= hideThreshold) {
                        setNavigationHidden(true, true);
                    } else if (direction < 0
                            && navigationGestureDistance >= showThreshold) {
                        if (navigationHidden) setNavigationHidden(false, true);
                    }
                }
                lastScrollTouchY = currentY;
                lastScrollTouchX = event.getX();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                navigationGestureDirection = 0;
                navigationGestureDistance = 0;
                break;
            default:
                break;
        }
    }

    private void updateScrollTopFade(int scrollY) {
        if (scrollTopFade != null) {
            scrollTopFade.setAlpha(Math.min(1f,
                    scrollY / (float) Math.max(1, dp(this, 24))));
        }
    }

    private void setNavigationHidden(boolean hidden, boolean animate) {
        if (navigationHidden == hidden) return;
        navigationHidden = hidden;
        int distance = (navigationBar != null && navigationBar.getHeight() > 0
                ? navigationBar.getHeight() : dp(this, 64)) + dp(this, 24);
        float translation = hidden ? distance : 0f;
        float alpha = hidden ? 0f : 1f;
        View[] views = {navigationGlow, navigationBackdrop, navigationBar};
        for (View view : views) {
            if (view == null) continue;
            view.animate().cancel();
            if (animate) {
                view.animate().translationY(translation).alpha(alpha)
                        .setDuration(hidden ? 190 : 210)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                view.setTranslationY(translation);
                view.setAlpha(alpha);
            }
        }
    }

    private void scheduleNavigationBlur() {
        mainHandler.removeCallbacks(navigationBlurUpdate);
        mainHandler.postDelayed(navigationBlurUpdate, 65);
    }

    private GridLayout.LayoutParams gridCellParams(int row, int column) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row), GridLayout.spec(column, 1f));
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.setMargins(dp(this, 2), dp(this, 2), dp(this, 2), dp(this, 2));
        return params;
    }

    private View createGameCell(GameLibrary.Game game, int iconSize, int row, int column) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(this, 4), dp(this, 3), dp(this, 4), dp(this, 3));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(game.title + ", chạy game");

        GameIconView icon = new GameIconView(this);
        icon.setArtwork(game.icon, game.title);
        icon.setContentDescription(game.title + " icon");
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        cell.addView(icon, iconParams);

        TextView title = new TextView(this);
        title.setText(game.title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        title.setMaxLines(2);
        title.setMinLines(2);
        title.setEllipsize(null);
        title.setPadding(dp(this, 1), dp(this, 7), dp(this, 1), 0);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(this, 1);
        cell.addView(title, titleParams);
        title.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int availableWidth = title.getWidth()
                        - title.getTotalPaddingLeft() - title.getTotalPaddingRight();
                if (availableWidth > 0) {
                    title.removeOnLayoutChangeListener(this);
                    title.setText(fitTitleWithLiteralDots(
                            game.title, title.getPaint(), availableWidth));
                }
            }
        });
        cell.setOnClickListener(v -> openGame(game));
        cell.setOnLongClickListener(v -> {
            showGameMenu(v, game);
            return true;
        });
        return cell;
    }

    private String fitTitleWithLiteralDots(String value, TextPaint paint, int width) {
        if (value == null || value.isEmpty() || width <= 0
                || titleLineCount(value, paint, width) <= 2) {
            return value;
        }

        int codePointCount = value.codePointCount(0, value.length());
        int low = 0;
        int high = codePointCount;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String prefix = value.substring(0, value.offsetByCodePoints(0, middle));
            String candidate = trimTitleEnd(prefix) + "...";
            if (titleLineCount(candidate, paint, width) <= 2) low = middle;
            else high = middle - 1;
        }

        String prefix = value.substring(0, value.offsetByCodePoints(0, low));
        int wordBoundary = Math.max(prefix.lastIndexOf(' '), prefix.lastIndexOf('\n'));
        if (wordBoundary >= prefix.length() * 0.55f) {
            prefix = prefix.substring(0, wordBoundary);
        }
        prefix = trimTitleEnd(prefix);
        String fitted = prefix + "...";
        while (!prefix.isEmpty() && titleLineCount(fitted, paint, width) > 2) {
            int end = prefix.offsetByCodePoints(prefix.length(), -1);
            prefix = trimTitleEnd(prefix.substring(0, end));
            fitted = prefix + "...";
        }
        return fitted;
    }

    private int titleLineCount(String value, TextPaint paint, int width) {
        StaticLayout layout = new StaticLayout(value, paint, width,
                Layout.Alignment.ALIGN_CENTER, 1f, 0f, true);
        return layout.getLineCount();
    }

    private String trimTitleEnd(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private void addEmptyState(String query, boolean landscape) {
        boolean waitingForSearchQuery = currentScreen == TAB_SEARCH && query.isEmpty();
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        int verticalPadding = landscape ? 0 : 72;
        int bottomPadding = landscape ? 0 : 48;
        empty.setPadding(dp(this, 24), dp(this, verticalPadding),
                dp(this, 24), dp(this, bottomPadding));
        TextView title = new TextView(this);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setText(AppLocalization.text(this, waitingForSearchQuery ? "Tìm ứng dụng"
                : !query.isEmpty() ? "Không tìm thấy ứng dụng"
                : games.isEmpty() ? "Thư viện đang trống" : "Không có game phù hợp"));
        empty.addView(title);
        TextView description = new TextView(this);
        description.setGravity(Gravity.CENTER);
        description.setTextColor(COLOR_MUTED);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        description.setText(AppLocalization.text(this, waitingForSearchQuery
                ? "Nhập tên game để tìm kiếm."
                : !query.isEmpty()
                ? "Thử một tên khác hoặc xoá nội dung tìm kiếm."
                : games.isEmpty()
                    ? "Thêm game từ nhiều tệp cùng lúc."
                    : "Chưa có game nào trong danh sách này."));
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(this, 8);
        empty.addView(description, descriptionParams);
        if (games.isEmpty() && query.isEmpty() && currentScreen != TAB_SEARCH) {
            TextView add = new TextView(this);
            add.setText(AppLocalization.text(this, "Thêm game"));
            add.setTextColor(COLOR_BACKGROUND);
            add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            add.setGravity(Gravity.CENTER);
            add.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            add.setBackground(rounded(COLOR_ACCENT, 22, 0));
            add.setPadding(dp(this, 22), dp(this, 12), dp(this, 22), dp(this, 12));
            add.setOnClickListener(v -> showAddSourcePopup(add, true));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            addParams.topMargin = dp(this, 20);
            empty.addView(add, addParams);
        }
        contentColumn.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void setContentColumnLayout(boolean centered) {
        if (contentColumn == null) return;
        boolean centerInLandscape = centered && isLandscape();
        int topPadding = headerOverlayMode ? 78 : 8;
        contentColumn.setPadding(dp(this, 12), dp(this, topPadding), dp(this, 12),
                centerInLandscape ? dp(this, 72) : dp(this, 100));
        contentColumn.setGravity(centerInLandscape ? Gravity.CENTER : Gravity.TOP);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    private void showSortMenu(View anchor) {
        showStyledPopup(anchor, new String[]{"Mới thêm trước", "Tên A–Z"},
                true, true, position -> {
            alphabetical = position == 1;
            getPreferences(0).edit().putBoolean("sortAlphabetical", alphabetical).apply();
            renderGames(contentScroll.getScrollY());
        });
    }

    private void showGameMenu(View anchor, GameLibrary.Game game) {
        showStyledPopup(anchor, new String[]{
                "Chạy game", "Thiết lập", "Thêm vào bộ sưu tập",
                "Đặt lại dữ liệu", "Gỡ cài đặt game"},
                false, false, position -> {
            if (position == 0) {
                openGame(game);
            } else if (position == 1) {
                openGameSettings(game.jar, game.title, false);
            } else if (position == 2) {
                showAddGameToCollectionPopup(anchor, game);
            } else if (position == 3) {
                confirmResetGameData(game);
            } else {
                confirmUninstallGame(game);
            }
        });
    }

    private interface StyledPopupListener {
        void onSelected(int position);
    }

    private interface StyledPopupDismissListener {
        void onDismiss(boolean itemSelected);
    }

    private void showStyledPopup(View anchor, String[] labels,
            boolean belowAnchor, boolean alignRight, StyledPopupListener listener) {
        showStyledPopup(anchor, labels, belowAnchor, alignRight, listener, null);
    }

    private void showStyledPopup(View anchor, String[] labels,
            boolean belowAnchor, boolean alignRight, StyledPopupListener listener,
            StyledPopupDismissListener dismissListener) {
        if (anchor == null || labels == null || labels.length == 0) return;
        final String[] displayedLabels = AppLocalization.texts(this, labels);
        if (activeStyledPopup != null) activeStyledPopup.dismiss();
        final boolean[] itemSelected = {false};

        Rect visibleFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int viewportWidth = visibleFrame.width() > 0
                ? visibleFrame.width() : getResources().getDisplayMetrics().widthPixels;
        int viewportHeight = visibleFrame.height() > 0
                ? visibleFrame.height() : getResources().getDisplayMetrics().heightPixels;
        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);

        Paint textMeasure = new Paint(Paint.ANTI_ALIAS_FLAG);
        textMeasure.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
                getResources().getDisplayMetrics()));
        textMeasure.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        float widest = 0f;
        for (String label : displayedLabels) {
            if (label != null) widest = Math.max(widest, textMeasure.measureText(label));
        }
        int minimumWidth = Math.round(viewportWidth * 0.6f);
        int maximumWidth = Math.max(minimumWidth, viewportWidth - dp(this, 24));
        int desiredWidth = (int) Math.ceil(widest) + dp(this, 38);
        int width = Math.min(maximumWidth, Math.max(minimumWidth, desiredWidth));
        int rowHeight = dp(this, 52);
        int maximumHeight = Math.max(rowHeight + dp(this, 2), viewportHeight - dp(this, 16));
        int height = Math.min(displayedLabels.length * rowHeight + dp(this, 2), maximumHeight);

        ListView menu = new ListView(this);
        menu.setBackground(rounded(COLOR_SURFACE, 14, 0xFF3D4A5E));
        menu.setClipToOutline(true);
        menu.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        menu.setPadding(dp(this, 1), dp(this, 1), dp(this, 1), dp(this, 1));
        menu.setDivider(new ColorDrawable(Color.TRANSPARENT));
        menu.setDividerHeight(0);
        menu.setSelector(new ColorDrawable(Color.TRANSPARENT));
        menu.setVerticalScrollBarEnabled(false);
        menu.setFocusable(true);
        menu.setFocusableInTouchMode(true);

        final int[] focusedPosition = {0};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, displayedLabels) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setText(displayedLabels[position]);
                row.setTextColor(COLOR_TEXT);
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                row.setSingleLine(true);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.setMinHeight(rowHeight);
                row.setPadding(dp(MainActivity.this, 18), dp(MainActivity.this, 10),
                        dp(MainActivity.this, 18), dp(MainActivity.this, 10));
                row.setBackground(commandMenuRowBackground());
                row.setSelected(position == focusedPosition[0]);
                return row;
            }
        };
        menu.setAdapter(adapter);
        menu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (position >= 0 && focusedPosition[0] != position) {
                    focusedPosition[0] = position;
                    adapter.notifyDataSetChanged();
                }
            }
        });

        PopupWindow popup = new PopupWindow(menu, width, height, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(this, 8));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setClippingEnabled(true);

        FrameLayout contentRoot = findViewById(android.R.id.content);
        View dim = new View(this);
        dim.setBackgroundColor(0x80000000);
        dim.setClickable(true);
        contentRoot.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim.setOnClickListener(view -> popup.dismiss());
        activeStyledPopup = popup;
        activePopupDim = dim;
        popup.setOnDismissListener(() -> {
            if (dim.getParent() instanceof ViewGroup) {
                ((ViewGroup) dim.getParent()).removeView(dim);
            }
            if (activeStyledPopup == popup) {
                activeStyledPopup = null;
                activePopupDim = null;
            }
            if (dismissListener != null) dismissListener.onDismiss(itemSelected[0]);
        });
        menu.setSelection(0);

        int horizontalOffset;
        if (alignRight) {
            horizontalOffset = visibleFrame.right - anchorLocation[0]
                    - width - dp(this, 12);
        } else {
            int leftInset = Math.max(dp(this, 8), anchorLocation[0] - visibleFrame.left);
            horizontalOffset = visibleFrame.left + leftInset - anchorLocation[0];
        }
        if (belowAnchor) {
            popup.showAsDropDown(anchor, horizontalOffset, dp(this, 8));
        } else {
            popup.showAsDropDown(anchor, horizontalOffset,
                    -height - anchor.getHeight() - dp(this, 24));
        }
        menu.requestFocus();
        menu.setSelection(0);
        menu.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= displayedLabels.length) return;
            itemSelected[0] = true;
            popup.dismiss();
            if (listener != null) listener.onSelected(position);
        });
    }

    private android.graphics.drawable.Drawable commandMenuRowBackground() {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        int[][] activeStates = {
                {android.R.attr.state_focused},
                {android.R.attr.state_activated},
                {android.R.attr.state_selected},
                {android.R.attr.state_pressed}
        };
        for (int[] state : activeStates) {
            states.addState(state, rounded(COLOR_SURFACE_SELECTED, 0, 0));
        }
        states.addState(new int[0], rounded(Color.TRANSPARENT, 0, 0));
        return states;
    }

    private void showUpdateCheck() {
        boolean english = AppLocalization.isEnglish(this);
        showInfoPopup(english ? "Updates" : "Cập nhật",
                AppLocalization.text(this,
                        "Chưa có nguồn cập nhật được cấu hình. Thông tin phát hành sẽ xuất hiện sau khi dự án có kho công khai."),
                false);
    }

    private void showIntroduction() {
        boolean english = AppLocalization.isEnglish(this);
        try {
            showInfoPopup(english ? "About" : "Giới thiệu",
                    ProjectInformation.introduction(this), true);
        } catch (IOException error) {
            showError(error);
        }
    }

    private void showInfoPopup(String title, String message, boolean scrollable) {
        LinearLayout panel = collectionModalPanel(title);
        TextView body = new TextView(this);
        body.setText(AppLocalization.text(this, message));
        body.setTextColor(COLOR_TEXT);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setTextIsSelectable(true);
        if (message.contains("https://github.com/huongdino443/J2MEX")) {
            Linkify.addLinks(body, Linkify.WEB_URLS);
            body.setLinkTextColor(COLOR_ACCENT);
            body.setMovementMethod(LinkMovementMethod.getInstance());
        }
        body.setLineSpacing(dp(this, 2), 1f);
        if (scrollable) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(false);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(body, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 360));
            scrollParams.topMargin = dp(this, 12);
            panel.addView(scroll, scrollParams);
            if (currentScreen == TAB_MORE) {
                ScrollCueOverlay.install(this, scroll, 8);
            }
        } else {
            body.setPadding(0, dp(this, 12), 0, 0);
            panel.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView close = styledActionButton(
                "Tiếng Việt".equals(AppPreferences.automaticLanguage(this)) ? "Đóng" : "Close",
                true);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = dp(this, 16);
        actions.addView(close, closeParams);
        panel.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        PopupWindow popup = showModalPopup(panel, false);
        close.setOnClickListener(view -> popup.dismiss());
    }

    private void requestStoragePermissionOnFirstLaunch() {
        if (isFinishing() || isDestroyed() || hasStorageAccess()) return;
        ensureStorageAccess(false);
    }

    private void showAddSourcePopup(View anchor, boolean belowAnchor) {
        if (busy) return;
        if (activeStyledPopup != null) activeStyledPopup.dismiss();
        int previousNavigationTab = activeNavigationTab;
        showStyledPopup(anchor, new String[]{"Chọn tệp", "Chọn thư mục"},
                belowAnchor, true, position -> openGameFileManager(position == 1),
                itemSelected -> {
                    if (!itemSelected) setActiveNavigation(previousNavigationTab);
                });
        setActiveNavigation(TAB_ADD);
    }

    private void openGameFileManager(boolean folderPicker) {
        if (busy) return;
        ensureStorageAccess(true, folderPicker);
    }

    private void ensureStorageAccess(boolean openManagerAfterGrant) {
        ensureStorageAccess(openManagerAfterGrant, false);
    }

    private void ensureStorageAccess(boolean openManagerAfterGrant, boolean folderPicker) {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) {
                if (!interfaceInitialized) initializeApplicationInterface();
                if (openManagerAfterGrant) launchGameFileManager(folderPicker);
                return;
            }
            openManagerAfterStorageGrant = openManagerAfterGrant;
            folderPickerAfterStorageGrant = openManagerAfterGrant && folderPicker;
            waitingForAllFilesSettings = true;
            try {
                Intent settings = new Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                settings.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(settings, REQUEST_ALL_FILES_SETTINGS);
            } catch (android.content.ActivityNotFoundException error) {
                try {
                    startActivityForResult(new Intent(
                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQUEST_ALL_FILES_SETTINGS);
                } catch (android.content.ActivityNotFoundException unavailable) {
                    waitingForAllFilesSettings = false;
                    openManagerAfterStorageGrant = false;
                    folderPickerAfterStorageGrant = false;
                    showError(new IOException(
                            "Thiết bị không hỗ trợ trang cấp quyền truy cập bộ nhớ.", unavailable));
                }
            }
            return;
        }

        if (hasLegacyStoragePermission() && !interfaceInitialized) {
            initializeApplicationInterface();
        }
        if (!hasLegacyStoragePermission()) {
            openManagerAfterStorageGrant = openManagerAfterGrant;
            folderPickerAfterStorageGrant = openManagerAfterGrant && folderPicker;
            ArrayList<String> permissions = new ArrayList<>();
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= 28) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            requestPermissions(permissions.toArray(new String[0]),
                    REQUEST_STORAGE_PERMISSION);
            return;
        }
        if (openManagerAfterGrant) launchGameFileManager(folderPicker);
    }

    private boolean hasLegacyStoragePermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasStorageAccess() {
        return Build.VERSION.SDK_INT >= 30
                ? Environment.isExternalStorageManager() : hasLegacyStoragePermission();
    }

    private void launchGameFileManager(boolean folderPicker) {
        File storageRoot = Environment.getExternalStorageDirectory();
        Intent intent = new Intent(this, GameFileManagerActivity.class);
        intent.putExtra(GameFileManagerActivity.EXTRA_ROOT_PATH,
                storageRoot.getAbsolutePath());
        intent.putExtra(GameFileManagerActivity.EXTRA_PICK_FOLDER, folderPicker);
        startActivityForResult(intent, PICK_FILE_MANAGER);
    }

    private void chooseStorageLocation() {
        if (busy) return;
        if (!hasStorageAccess()) {
            ensureStorageAccess(false);
            return;
        }
        Intent intent = new Intent(this, GameFileManagerActivity.class);
        intent.putExtra(GameFileManagerActivity.EXTRA_ROOT_PATH,
                Environment.getExternalStorageDirectory().getAbsolutePath());
        intent.putExtra(GameFileManagerActivity.EXTRA_PICK_FOLDER, true);
        intent.putExtra(GameFileManagerActivity.EXTRA_PICK_STORAGE, true);
        startActivityForResult(intent, PICK_STORAGE_FOLDER);
    }

    private void applyStorageLocation(String directory) {
        if (busy || directory == null || directory.trim().isEmpty()) return;
        setBusy(true);
        worker.execute(() -> {
            String failure = null;
            ArrayList<GameCollectionStore.Collection> loadedCollections = null;
            try {
                GameStorage.setRoot(this, new File(directory));
                loadedCollections = GameCollectionStore.load(this);
            } catch (Exception error) {
                failure = error.getMessage() == null
                        ? "Không thể đổi thư mục lưu game." : error.getMessage();
            }
            final String result = failure;
            final ArrayList<GameCollectionStore.Collection> restored = loadedCollections;
            runOnUiThread(() -> {
                setBusy(false);
                if (result != null) {
                    showError(new IOException(result));
                    return;
                }
                collections.clear();
                if (restored != null) collections.addAll(restored);
                libraryScanComplete = false;
                if (currentScreen == TAB_MORE) {
                    renderMore(contentScroll == null ? 0 : contentScroll.getScrollY());
                }
                refreshLibrary();
                Toast.makeText(this, AppLocalization.text(this,
                        "Đã đổi thư mục lưu game."), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void refreshLibrary() {
        worker.execute(() -> {
            List<GameLibrary.Game> found;
            try {
                found = GameLibrary.scan(this);
            } catch (IOException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) showError(error);
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (remapCollectionPaths(found)) saveCollections();
                games.clear();
                games.addAll(found);
                libraryScanComplete = true;
                renderGames(contentScroll.getScrollY());
            });
        });
    }

    private boolean remapCollectionPaths(List<GameLibrary.Game> found) {
        HashMap<String, String> movedPaths = new HashMap<>();
        for (GameLibrary.Game game : found) {
            if (game.previousJarPath != null) {
                movedPaths.put(game.previousJarPath, game.jar.getAbsolutePath());
            }
        }
        if (movedPaths.isEmpty()) return false;
        boolean changed = false;
        for (GameCollectionStore.Collection collection : collections) {
            for (int index = collection.gamePaths.size() - 1; index >= 0; index--) {
                String replacement = movedPaths.get(collection.gamePaths.get(index));
                if (replacement == null) continue;
                if (collection.gamePaths.contains(replacement)) {
                    collection.gamePaths.remove(index);
                } else {
                    collection.gamePaths.set(index, replacement);
                }
                changed = true;
            }
        }
        return changed;
    }

    private void prepareAndLaunch(File jar, String name) {
        if (busy) return;
        setBusy(true);
        worker.execute(() -> {
            try {
                if (!runtimeReady) {
                    HostFiles.prepare(this);
                    runtimeReady = true;
                }
                File selected = jar == null ? new File(getFilesDir(), "sample.jar") : jar;
                if (!selected.isFile()) throw new IOException("Không còn tìm thấy JAR đã chọn");
                getPreferences(0).edit()
                        .putString("recentPath", selected.getAbsolutePath())
                        .putString("recentName", name).apply();
                runOnUiThread(() -> {
                    setBusy(false);
                    if (isFinishing() || isDestroyed()) return;
                    Intent intent = new Intent(this, RuntimeActivity.class);
                    intent.putExtra("jar", selected.getAbsolutePath());
                    intent.putExtra("title", name);
                    AppPreferences.addRuntimeSnapshotExtras(this, intent);
                    boolean launchStateSaved = jar == null
                            || GameSettings.markLaunched(this, selected);
                    startActivity(intent);
                    if (!launchStateSaved) {
                        Toast.makeText(this, AppLocalization.text(this,
                                "Không thể lưu trạng thái cài đặt game."),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(error);
                });
            }
        });
    }

    private void openGame(GameLibrary.Game game) {
        if (game == null || game.jar == null || !game.jar.isFile() || busy) return;
        if (GameSettings.hasLaunched(this, game.jar)) {
            prepareAndLaunch(game.jar, game.title);
        } else {
            openGameSettings(game.jar, game.title, true);
        }
    }

    private void openGameSettings(File jar, String name, boolean launchAfterSave) {
        if (busy || jar == null || !jar.isFile()) return;
        Intent intent = new Intent(this, GameSettingsActivity.class);
        intent.putExtra(GameSettingsActivity.EXTRA_JAR, jar.getAbsolutePath());
        intent.putExtra(GameSettingsActivity.EXTRA_TITLE, name);
        intent.putExtra(GameSettingsActivity.EXTRA_LAUNCH_AFTER_SAVE, launchAfterSave);
        startActivityForResult(intent, REQUEST_GAME_SETTINGS);
    }

    private void confirmResetGameData(GameLibrary.Game game) {
        LinearLayout panel = collectionModalPanel("Đặt lại dữ liệu game?");
        TextView message = new TextView(this);
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        message.setText(vietnamese
                ? "Dữ liệu lưu của “" + game.title
                        + "” (RMS và FileConnection) sẽ bị xóa vĩnh viễn. "
                        + "Tệp game và thiết lập điều khiển vẫn được giữ."
                : "Saved data for “" + game.title
                        + "” (RMS and FileConnection files) will be permanently deleted. "
                        + "The game JAR and control settings will remain.");
        message.setTextColor(COLOR_MUTED);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setPadding(0, dp(this, 12), 0, 0);
        panel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 18);
        panel.addView(actions, actionsParams);
        TextView cancel = styledActionButton("Hủy", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView reset = styledActionButton("Đặt lại dữ liệu", true);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.leftMargin = dp(this, 10);
        actions.addView(reset, resetParams);

        PopupWindow popup = showModalPopup(panel, false);
        cancel.setOnClickListener(view -> popup.dismiss());
        reset.setOnClickListener(view -> {
            popup.dismiss();
            resetGameData(game);
        });
    }

    private void resetGameData(GameLibrary.Game game) {
        if (busy || game == null || game.jar == null) return;
        setBusy(true);
        worker.execute(() -> {
            String failure = null;
            try {
                GameStorage.resetGameData(this, game.jar);
            } catch (Exception error) {
                failure = error.getMessage() == null
                        ? "Không thể đặt lại dữ liệu game." : error.getMessage();
            }
            final String result = failure;
            runOnUiThread(() -> {
                setBusy(false);
                if (result != null) {
                    showError(new IOException(result));
                } else {
                    Toast.makeText(this, AppLocalization.text(this,
                            "Đã đặt lại dữ liệu game."), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void confirmUninstallGame(GameLibrary.Game game) {
        LinearLayout panel = collectionModalPanel("Gỡ cài đặt game?");
        TextView message = new TextView(this);
        boolean vietnamese = "Tiếng Việt".equals(AppPreferences.automaticLanguage(this));
        message.setText(vietnamese
                ? "“" + game.title + "” sẽ bị gỡ khỏi thư viện và các bộ sưu tập. Toàn bộ thư mục "
                        + "game trong J2MEX/games sẽ bị xóa, gồm JAR, dữ liệu RMS/FileConnection "
                        + "và thiết lập. Tệp JAR nguồn ban đầu không bị xóa."
                : "“" + game.title + "” will be removed from the library and collections. Its "
                        + "entire J2MEX/games folder will be deleted, including the JAR, "
                        + "RMS/FileConnection data, and settings. The original source JAR "
                        + "will not be deleted.");
        message.setTextColor(COLOR_MUTED);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setPadding(0, dp(this, 12), 0, 0);
        panel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 18);
        panel.addView(actions, actionsParams);
        TextView cancel = styledActionButton("Hủy", false);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView remove = styledActionButton("Gỡ cài đặt", true);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeParams.leftMargin = dp(this, 10);
        actions.addView(remove, removeParams);

        PopupWindow popup = showModalPopup(panel, false);
        cancel.setOnClickListener(view -> popup.dismiss());
        remove.setOnClickListener(view -> {
            popup.dismiss();
            uninstallGame(game);
        });
    }

    private void uninstallGame(GameLibrary.Game game) {
        if (busy || game == null || game.jar == null) return;
        File jar = game.jar;
        setBusy(true);
        worker.execute(() -> {
            String failure = null;
            try {
                GameStorage.deleteGameDirectory(this, jar);
            } catch (Exception error) {
                failure = error.getMessage() == null
                        ? "Không thể gỡ cài đặt game." : error.getMessage();
            }
            final String result = failure;
            runOnUiThread(() -> {
                setBusy(false);
                if (result != null) {
                    Toast.makeText(this, AppLocalization.text(this, result),
                            Toast.LENGTH_LONG).show();
                    refreshLibrary();
                    return;
                }
                String path = gamePath(game);
                games.remove(game);
                for (GameCollectionStore.Collection collection : collections) {
                    collection.gamePaths.remove(path);
                }
                saveCollections();
                renderGames(contentScroll == null ? 0 : contentScroll.getScrollY());
                Toast.makeText(this, AppLocalization.text(this,
                        "Đã gỡ game và xóa toàn bộ thư mục dữ liệu."),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void prepareRuntimeOnStartup() {
        try {
            if (HostFiles.isPrepared(this)) {
                runtimeReady = true;
                refreshLibrary();
                return;
            }
        } catch (IOException error) {
            showError(error);
            return;
        }
        setBusy(true);
        worker.execute(() -> {
            try {
                HostFiles.prepare(this);
                runtimeReady = true;
                runOnUiThread(() -> {
                    setBusy(false);
                    refreshLibrary();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(error);
                });
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        if (sortButton != null) sortButton.setEnabled(!value);
        if (addNavigationItem != null) addNavigationItem.setEnabled(!value);
    }

    private void showError(Throwable error) {
        if (!isFinishing() && !isDestroyed()) {
            String message = error == null ? null : error.getMessage();
            if (message == null || message.trim().isEmpty())
                message = error == null ? "Không thể thực hiện." : error.toString();
            new AlertDialog.Builder(this)
                    .setTitle(AppLocalization.text(this, "Không thể thực hiện"))
                    .setMessage(AppLocalization.text(this, message))
                    .setPositiveButton(AppLocalization.text(this, "Đóng"), null).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        completeStorageSettingsReturn();
        if (contentScroll != null) {
            if (currentScreen == TAB_MORE) renderMore(contentScroll.getScrollY());
            refreshLibrary();
        }
    }

    private void completeStorageSettingsReturn() {
        if (!waitingForAllFilesSettings) return;
        waitingForAllFilesSettings = false;
        boolean granted = hasStorageAccess();
        boolean openManager = openManagerAfterStorageGrant;
        boolean folderPicker = folderPickerAfterStorageGrant;
        openManagerAfterStorageGrant = false;
        folderPickerAfterStorageGrant = false;
        if (granted) {
            if (!interfaceInitialized) initializeApplicationInterface();
            if (openManager) launchGameFileManager(folderPicker);
        } else {
            if (!interfaceInitialized) initializeApplicationInterface();
            showStorageAccessRequiredPopup();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSION) return;
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        boolean openManager = openManagerAfterStorageGrant;
        boolean folderPicker = folderPickerAfterStorageGrant;
        openManagerAfterStorageGrant = false;
        folderPickerAfterStorageGrant = false;
        if (granted) {
            if (!interfaceInitialized) initializeApplicationInterface();
            if (openManager) launchGameFileManager(folderPicker);
        } else {
            if (!interfaceInitialized) initializeApplicationInterface();
            showStorageAccessRequiredPopup();
        }
    }

    @Override public void onBackPressed() {
        if (interfaceInitialized && !hasStorageAccess()) {
            if (activeStyledPopup == null) showStorageAccessRequiredPopup();
            return;
        }
        if (currentScreen == TAB_SEARCH) {
            changeScreen(TAB_HOME);
        } else if (currentScreen == TAB_COLLECTION || currentScreen == TAB_MORE) {
            changeScreen(TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }

    private void showStorageAccessRequiredPopup() {
        if (activeStyledPopup != null || hasStorageAccess()) return;
        LinearLayout panel = collectionModalPanel("Cần quyền truy cập bộ nhớ");
        TextView message = new TextView(this);
        message.setText(AppLocalization.text(this,
                "J2MEX cần quyền truy cập bộ nhớ để duyệt thư mục và tìm game. Hãy cấp quyền để tiếp tục, hoặc thoát ứng dụng."));
        message.setTextColor(COLOR_MUTED);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(this, 10);
        panel.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(this, 18);
        panel.addView(actions, actionsParams);
        TextView exit = styledActionButton("Thoát", false);
        actions.addView(exit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView grant = styledActionButton("Cấp quyền", true);
        LinearLayout.LayoutParams grantParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        grantParams.leftMargin = dp(this, 10);
        actions.addView(grant, grantParams);

        PopupWindow popup = showModalPopup(panel, false);
        popup.setFocusable(false);
        popup.setOutsideTouchable(false);
        if (activePopupDim != null) activePopupDim.setOnClickListener(null);
        grant.setOnClickListener(view -> {
            popup.dismiss();
            ensureStorageAccess(false);
        });
        exit.setOnClickListener(view -> {
            popup.dismiss();
            finish();
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_GAME_SETTINGS) {
            if (result == RESULT_OK && data != null && !busy) {
                String jarPath = data.getStringExtra(GameSettingsActivity.EXTRA_JAR);
                String title = data.getStringExtra(GameSettingsActivity.EXTRA_TITLE);
                boolean launch = data.getBooleanExtra(
                        GameSettingsActivity.EXTRA_LAUNCH_AFTER_SAVE, false);
                if (launch && jarPath != null) prepareAndLaunch(new File(jarPath), title);
            }
            return;
        }
        if (request == REQUEST_ALL_FILES_SETTINGS) {
            completeStorageSettingsReturn();
            return;
        }
        if (request == PICK_STORAGE_FOLDER) {
            if (result != RESULT_OK || data == null || busy) return;
            String directory = data.getStringExtra(
                    GameFileManagerActivity.EXTRA_SELECTED_DIRECTORY);
            if (directory != null) applyStorageLocation(directory);
            return;
        }
        if (request == PICK_FILE_MANAGER) {
            if (interfaceInitialized) setActiveNavigation(currentScreen);
            if (result != RESULT_OK || data == null || busy) return;
            String directory = data.getStringExtra(
                    GameFileManagerActivity.EXTRA_SELECTED_DIRECTORY);
            if (directory != null) {
                importDirectory(directory);
                return;
            }
            ArrayList<String> selected = data.getStringArrayListExtra(
                    GameFileManagerActivity.EXTRA_SELECTED_FILES);
            if (selected != null && !selected.isEmpty()) importFiles(selected);
            return;
        }
        if (request == HostFiles.EXPORT_LOG) {
            if (result != RESULT_OK || data == null || data.getData() == null) return;
            try {
                HostFiles.exportLog(this, data.getData());
                Toast.makeText(this, AppLocalization.text(this, "Đã lưu log."),
                        Toast.LENGTH_SHORT).show();
            } catch (IOException error) {
                showError(error);
            }
            return;
        }
        if (result != RESULT_OK || data == null || busy) return;
    }

    private void importFiles(List<String> paths) {
        ArrayList<File> files = new ArrayList<>();
        for (String path : paths) files.add(new File(path));
        startImportTask(() -> GameImportManager.importFiles(this, files));
    }

    private void importDirectory(String path) {
        File directory = new File(path);
        startImportTask(() -> GameImportManager.importDirectory(this, directory));
    }

    private interface ImportTask {
        GameImportManager.Result run();
    }

    private void startImportTask(ImportTask task) {
        setBusy(true);
        worker.execute(() -> {
            GameImportManager.Result imported;
            try {
                imported = task.run();
            } catch (Throwable error) {
                imported = new GameImportManager.Result();
                imported.errors.add(error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage());
            }
            GameImportManager.Result finalResult = imported;
            runOnUiThread(() -> {
                setBusy(false);
                if (isFinishing() || isDestroyed()) return;
                changeScreen(TAB_HOME);
                refreshLibrary();
                if (finalResult.errors.isEmpty()) {
                    Toast.makeText(this, finalResult.summary(this), Toast.LENGTH_LONG).show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(AppLocalization.text(this, "Hoàn tất thêm game"))
                            .setMessage(finalResult.summary(this))
                            .setPositiveButton(AppLocalization.text(this, "Đóng"), null).show();
                }
            });
        });
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(this, radiusDp));
        if (stroke != 0) drawable.setStroke(dp(this, 1), stroke);
        return drawable;
    }

    private void refreshNavigationBlur() {
        if (Build.VERSION.SDK_INT < 31 || navigationBackdrop == null || contentScroll == null
                || navigationBackdrop.getWidth() <= 0 || navigationBackdrop.getHeight() <= 0) {
            return;
        }
        Bitmap next;
        try {
            next = Bitmap.createBitmap(navigationBackdrop.getWidth(),
                    navigationBackdrop.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError ignored) {
            return;
        }
        Canvas canvas = new Canvas(next);
        canvas.translate(-navigationBackdrop.getLeft(), -navigationBackdrop.getTop());
        contentScroll.draw(canvas);
        Bitmap previous = navigationBackdropBitmap;
        navigationBackdropBitmap = next;
        navigationBackdrop.setImageBitmap(next);
        if (previous != null && !previous.isRecycled()) previous.recycle();
    }

    private static final class NavigationState {
        final IconView icon;
        final TextView label;
        NavigationState(IconView icon, TextView label) {
            this.icon = icon;
            this.label = label;
        }
    }

    private final class GlowView extends View {
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int cornerRadiusDp;
        private final int insetDp;
        private final int blurRadiusDp;

        GlowView(Context context, int cornerRadiusDp, int insetDp, int blurRadiusDp) {
            super(context);
            this.cornerRadiusDp = cornerRadiusDp;
            this.insetDp = insetDp;
            this.blurRadiusDp = blurRadiusDp;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(MainActivity.this, insetDp);
            float left = inset;
            float top = inset;
            float right = Math.max(left, getWidth() - inset);
            float bottom = Math.max(top, getHeight() - inset);
            float radius = Math.min(dp(MainActivity.this, cornerRadiusDp),
                    Math.min((right - left) / 2f, (bottom - top) / 2f));
            glowPaint.setColor(0x0AFFFFFF);
            glowPaint.setShadowLayer(dp(MainActivity.this, blurRadiusDp), 0f, 0f,
                    0x42FFFFFF);
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, glowPaint);
            glowPaint.clearShadowLayer();
        }
    }

    private final class IconView extends View {
        static final int HOME = 1;
        static final int FOLDER = 2;
        static final int ADD = 3;
        static final int SEARCH = 4;
        static final int MORE = 5;
        static final int FILTER = 6;
        static final int CLEAR = 7;
        static final int EDIT = 8;
        static final int CHEVRON = 9;

        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        IconView(Context context, int type) {
            super(context);
            this.type = type;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setActive(boolean selected) {
            active = selected;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float unit = Math.min(getWidth(), getHeight());
            float scale = unit / 24f;
            canvas.save();
            canvas.translate((getWidth() - 24 * scale) / 2f,
                    (getHeight() - 24 * scale) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(active ? COLOR_ACCENT : COLOR_TEXT);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            switch (type) {
                case HOME:
                    path.moveTo(3, 10.5f); path.lineTo(12, 3); path.lineTo(21, 10.5f);
                    path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 21); path.lineTo(18.5f, 21);
                    path.lineTo(18.5f, 9.5f);
                    canvas.drawPath(path, paint);
                    canvas.drawLine(10, 21, 10, 14, paint);
                    canvas.drawLine(10, 14, 14, 14, paint);
                    canvas.drawLine(14, 14, 14, 21, paint);
                    break;
                case FOLDER:
                    path.moveTo(3, 6); path.lineTo(9.3f, 6); path.lineTo(11.4f, 8.5f);
                    path.lineTo(21, 8.5f); path.lineTo(21, 19); path.lineTo(3, 19);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawLine(3.5f, 10.5f, 20.5f, 10.5f, paint);
                    break;
                case ADD:
                    canvas.drawLine(12, 4, 12, 20, paint);
                    canvas.drawLine(4, 12, 20, 12, paint);
                    break;
                case SEARCH:
                    canvas.drawCircle(10.5f, 10.5f, 6.5f, paint);
                    canvas.drawLine(15.5f, 15.5f, 21, 21, paint);
                    break;
                case CLEAR:
                    paint.setColor(Color.rgb(190, 201, 215));
                    paint.setStrokeWidth(2.5f);
                    canvas.drawLine(6, 6, 18, 18, paint);
                    canvas.drawLine(18, 6, 6, 18, paint);
                    break;
                case MORE:
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(5, 12, 1.6f, paint);
                    canvas.drawCircle(12, 12, 1.6f, paint);
                    canvas.drawCircle(19, 12, 1.6f, paint);
                    break;
                case FILTER:
                    paint.setStrokeWidth(2.1f);
                    canvas.drawLine(3, 5, 21, 5, paint);
                    canvas.drawLine(6, 12, 18, 12, paint);
                    canvas.drawLine(9, 19, 15, 19, paint);
                    break;
                case EDIT:
                    path.moveTo(3.5f, 20.5f);
                    path.lineTo(7.8f, 19.4f);
                    path.lineTo(19.4f, 7.8f);
                    path.lineTo(16.2f, 4.6f);
                    path.lineTo(4.6f, 16.2f);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawLine(14.9f, 5.9f, 18.1f, 9.1f, paint);
                    break;
                case CHEVRON:
                    canvas.drawLine(5, 9, 12, 16, paint);
                    canvas.drawLine(12, 16, 19, 9, paint);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }
    }

    @Override protected void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        mainHandler.removeCallbacks(navigationBlurUpdate);
        if (activeStyledPopup != null) activeStyledPopup.dismiss();
        if (activePopupDim != null && activePopupDim.getParent() instanceof ViewGroup) {
            ((ViewGroup) activePopupDim.getParent()).removeView(activePopupDim);
            activePopupDim = null;
        }
        if (navigationBackdropBitmap != null && !navigationBackdropBitmap.isRecycled()) {
            navigationBackdropBitmap.recycle();
            navigationBackdropBitmap = null;
        }
        worker.shutdown();
        super.onDestroy();
    }
}
