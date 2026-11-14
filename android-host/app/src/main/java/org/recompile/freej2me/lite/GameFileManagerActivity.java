package org.recompile.freej2me.lite;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.animation.DecelerateInterpolator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Shared-storage browser for selecting multiple MIDlet archives. */
public final class GameFileManagerActivity extends LocalizedActivity {
    static final String EXTRA_ROOT_PATH = "org.recompile.freej2me.lite.extra.ROOT_PATH";
    static final String EXTRA_SELECTED_FILES =
            "org.recompile.freej2me.lite.extra.SELECTED_GAME_FILES";
    static final String EXTRA_PICK_FOLDER =
            "org.recompile.freej2me.lite.extra.PICK_FOLDER";
    static final String EXTRA_PICK_STORAGE =
            "org.recompile.freej2me.lite.extra.PICK_STORAGE";
    static final String EXTRA_SELECTED_DIRECTORY =
            "org.recompile.freej2me.lite.extra.SELECTED_DIRECTORY";

    private static final int COLOR_BACKGROUND = 0xFF0E1826;
    private static final int COLOR_SURFACE = 0xFF192434;
    private static final int COLOR_SURFACE_SELECTED = 0xFF2B3548;
    private static final int COLOR_TEXT = 0xFFE8EFF7;
    private static final int COLOR_MUTED = 0xFF9DAABB;
    private static final int COLOR_ACCENT = 0xFFB9C3FF;
    private static final int COLOR_BORDER = 0xFF34465A;
    private File storageRoot;
    private File currentDirectory;
    private boolean newestFirst;
    private final LinkedHashMap<String, File> selectedFiles = new LinkedHashMap<>();
    private final ArrayList<FileEntry> visibleEntries = new ArrayList<>();
    private final ArrayList<CheckBox> renderedChecks = new ArrayList<>();
    private final ExecutorService directoryWorker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Future<?> directoryTask;
    private int directoryGeneration;
    private int visibleFileCount;
    private int selectedVisibleFileCount;
    private boolean loadingDirectory;
    private LinearLayout heading;
    private ScrollView directoryScroll;
    private LinearLayout rows;
    private LinearLayout[] landscapeColumns;
    private TextView pathView;
    private FrameLayout selectAllButton;
    private TextView sortButton;
    private FrameLayout searchButton;
    private LinearLayout searchPanel;
    private EditText searchField;
    private BrowserIconView selectAllGlyph;
    private boolean searchVisible;
    private String restoredSearchQuery = "";
    private int renderedEntryIndex;
    private int pendingDirectoryScrollY = -1;
    private boolean folderPicker;
    private boolean storagePicker;
    private TextView addButton;
    private TextView selectionCount;
    private TextView emptyView;
    private TextView backCancelButton;

    private static final class FileEntry {
        final File file;
        final String name;
        final boolean directory;
        final long modified;
        final String path;

        FileEntry(File file) {
            this.file = file;
            this.name = file.getName();
            this.directory = file.isDirectory();
            this.modified = file.lastModified();
            this.path = file.getAbsolutePath();
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        String rootPath = getIntent().getStringExtra(EXTRA_ROOT_PATH);
        if (rootPath == null || rootPath.trim().isEmpty()) {
            finish();
            return;
        }
        storageRoot = canonical(new File(rootPath));
        currentDirectory = storageRoot;
        folderPicker = getIntent().getBooleanExtra(EXTRA_PICK_FOLDER, false);
        storagePicker = getIntent().getBooleanExtra(EXTRA_PICK_STORAGE, false);
        if (state != null) {
            newestFirst = state.getBoolean("file_manager.newest_first", false);
            searchVisible = state.getBoolean("file_manager.search_visible", false);
            restoredSearchQuery = state.getString("file_manager.search_query", "");
            pendingDirectoryScrollY = state.getInt("file_manager.scroll_y", 0);
            String directoryPath = state.getString("file_manager.directory");
            if (directoryPath != null) {
                File restoredDirectory = canonical(new File(directoryPath));
                if (restoredDirectory.isDirectory() && isInsideRoot(restoredDirectory))
                    currentDirectory = restoredDirectory;
            }
            ArrayList<String> selectedPaths =
                    state.getStringArrayList("file_manager.selected_paths");
            if (!folderPicker && selectedPaths != null) {
                for (String path : selectedPaths) {
                    File selected = canonical(new File(path));
                    if (selected.isFile() && selected.canRead()
                            && isSupported(selected.getName()) && isInsideRoot(selected))
                        selectedFiles.put(selected.getAbsolutePath(), selected);
                }
            }
        }
        buildInterface();
        if (searchVisible) {
            heading.setVisibility(View.GONE);
            searchPanel.setVisibility(View.VISIBLE);
            searchField.setText(restoredSearchQuery);
        }
        refreshDirectory();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("file_manager.directory", currentDirectory.getAbsolutePath());
        outState.putBoolean("file_manager.newest_first", newestFirst);
        outState.putBoolean("file_manager.search_visible", searchVisible);
        outState.putString("file_manager.search_query",
                searchField == null ? restoredSearchQuery : searchField.getText().toString());
        outState.putStringArrayList("file_manager.selected_paths",
                new ArrayList<>(selectedFiles.keySet()));
        outState.putInt("file_manager.scroll_y",
                directoryScroll == null ? 0 : directoryScroll.getScrollY());
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setPadding(dp(16), dp(8), dp(16), dp(12));

        FrameLayout headerContainer = new FrameLayout(this);
        root.addView(headerContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        headerContainer.addView(heading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(titleBlock, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView title = new TextView(this);
        title.setText(AppLocalization.text(this, storagePicker
                ? "Chọn thư mục lưu game" : folderPicker ? "Chọn thư mục" : "Chọn tệp"));
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        titleBlock.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pathView = new TextView(this);
        pathView.setTextColor(COLOR_TEXT);
        pathView.setTextSize(13);
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        pathView.setPadding(0, dp(1), 0, 0);
        pathView.setContentDescription(AppLocalization.text(this,
                "Đường dẫn hiện tại, chạm để lên thư mục cha"));
        pathView.setOnClickListener(view -> goToParent());
        titleBlock.addView(pathView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        selectAllButton = createHeaderButton("Chọn tất cả");
        selectAllGlyph = new BrowserIconView(this, BrowserIconView.SELECT_ALL);
        selectAllButton.addView(selectAllGlyph, centeredIconParams());
        heading.addView(selectAllButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        selectAllButton.setOnClickListener(view -> toggleVisibleSelection());
        if (folderPicker) selectAllButton.setVisibility(View.GONE);

        sortButton = new TextView(this);
        sortButton.setText("A-Z");
        sortButton.setTextColor(COLOR_TEXT);
        sortButton.setTextSize(13);
        sortButton.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        sortButton.setGravity(Gravity.CENTER);
        sortButton.setSingleLine(true);
        sortButton.setPadding(dp(7), 0, dp(7), 0);
        sortButton.setBackground(selectableBackground(20));
        sortButton.setFocusable(true);
        sortButton.setClickable(true);
        int sortButtonWidth = Math.max(dp(44), (int) Math.ceil(Math.max(
                sortButton.getPaint().measureText("A-Z"),
                sortButton.getPaint().measureText("Mới"))) + dp(14));
        LinearLayout.LayoutParams sortParams = new LinearLayout.LayoutParams(
                sortButtonWidth, dp(40));
        sortParams.leftMargin = dp(2);
        heading.addView(sortButton, sortParams);
        sortButton.setOnClickListener(view -> {
            newestFirst = !newestFirst;
            sortButton.setText(AppLocalization.text(this, newestFirst ? "Mới" : "A-Z"));
        sortButton.setContentDescription(AppLocalization.text(this, newestFirst
                ? "Sắp xếp mới nhất trước. Chạm để sắp xếp A–Z."
                : "Sắp xếp A–Z. Chạm để sắp xếp mới nhất trước."));
            refreshDirectory();
        });

        searchButton = createHeaderButton("Tìm tệp");
        BrowserIconView searchGlyph = new BrowserIconView(this, BrowserIconView.SEARCH);
        searchButton.addView(searchGlyph, centeredIconParams());
        LinearLayout.LayoutParams searchButtonParams =
                new LinearLayout.LayoutParams(dp(40), dp(40));
        searchButtonParams.leftMargin = dp(2);
        heading.addView(searchButton, searchButtonParams);
        searchButton.setOnClickListener(view -> openSearch());

        searchPanel = new LinearLayout(this);
        searchPanel.setOrientation(LinearLayout.HORIZONTAL);
        searchPanel.setGravity(Gravity.CENTER_VERTICAL);
        searchPanel.setPadding(dp(14), 0, dp(8), 0);
        searchPanel.setBackground(rounded(COLOR_SURFACE_SELECTED, 24, COLOR_BORDER));
        searchPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams searchPanelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46), Gravity.CENTER_VERTICAL);
        headerContainer.addView(searchPanel, searchPanelParams);

        BrowserIconView inputSearchGlyph =
                new BrowserIconView(this, BrowserIconView.SEARCH);
        searchPanel.addView(inputSearchGlyph, new LinearLayout.LayoutParams(dp(22), dp(22)));
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setTextColor(COLOR_TEXT);
        searchField.setHintTextColor(COLOR_MUTED);
        searchField.setHint(AppLocalization.text(this, "Tìm tệp..."));
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setPadding(dp(10), 0, dp(4), 0);
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshDirectory();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        searchPanel.addView(searchField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        FrameLayout closeSearch = createHeaderButton("Đóng tìm kiếm");
        BrowserIconView closeGlyph = new BrowserIconView(this, BrowserIconView.CLOSE);
        closeSearch.addView(closeGlyph, centeredIconParams());
        searchPanel.addView(closeSearch, new LinearLayout.LayoutParams(dp(40), dp(40)));
        closeSearch.setOnClickListener(view -> closeSearch());

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(5);
        root.addView(divider, dividerParams);

        FrameLayout listFrame = new FrameLayout(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(4);
        root.addView(listFrame, listParams);

        directoryScroll = new ScrollView(this);
        directoryScroll.setFillViewport(true);
        directoryScroll.setVerticalScrollBarEnabled(false);
        listFrame.addView(directoryScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        directoryScroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        emptyView = new TextView(this);
        emptyView.setTextColor(COLOR_MUTED);
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(24), dp(24), dp(24), dp(24));
        emptyView.setVisibility(View.GONE);
        listFrame.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View footerDivider = new View(this);
        footerDivider.setBackgroundColor(COLOR_BORDER);
        LinearLayout.LayoutParams footerDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        footerDividerParams.topMargin = dp(8);
        root.addView(footerDivider, footerDividerParams);

        selectionCount = new TextView(this);
        selectionCount.setTextColor(COLOR_MUTED);
        selectionCount.setTextSize(12);
        selectionCount.setGravity(Gravity.CENTER_VERTICAL);
        selectionCount.setSingleLine(true);
        selectionCount.setEllipsize(TextUtils.TruncateAt.END);
        selectionCount.setPadding(dp(4), dp(6), dp(4), dp(2));
        root.addView(selectionCount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        addButton = new TextView(this);
        addButton.setText(AppLocalization.text(this, "Thêm (0)"));
        addButton.setTextColor(COLOR_BACKGROUND);
        addButton.setTextSize(15);
        addButton.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        addButton.setGravity(Gravity.CENTER);
        addButton.setBackground(rounded(COLOR_ACCENT, 28, 0));
        addButton.setFocusable(true);
        addButton.setClickable(true);
        actions.addView(addButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        addButton.setOnClickListener(view -> {
            if (folderPicker) finishWithDirectory();
            else finishWithSelection();
        });

        backCancelButton = new TextView(this);
        backCancelButton.setText(AppLocalization.text(this, "Trở về"));
        backCancelButton.setTextColor(COLOR_TEXT);
        backCancelButton.setTextSize(15);
        backCancelButton.setGravity(Gravity.CENTER);
        backCancelButton.setBackground(rounded(COLOR_SURFACE, 28, COLOR_BORDER));
        backCancelButton.setFocusable(true);
        backCancelButton.setClickable(true);
        LinearLayout.LayoutParams backCancelParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        backCancelParams.leftMargin = dp(10);
        actions.addView(backCancelButton, backCancelParams);
        backCancelButton.setOnClickListener(view -> handleBackCancel());

        setContentView(root);
    }

    private FrameLayout createHeaderButton(String description) {
        FrameLayout button = new FrameLayout(this);
        button.setContentDescription(description);
        button.setBackground(selectableBackground(20));
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private FrameLayout.LayoutParams centeredIconParams() {
        return new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
    }

    private void openSearch() {
        if (searchVisible) {
            searchField.requestFocus();
            showSearchKeyboard();
            return;
        }
        searchVisible = true;
        heading.setVisibility(View.VISIBLE);
        heading.setAlpha(1f);
        heading.setTranslationY(0f);
        heading.animate().cancel();
        heading.animate().alpha(0f).translationY(-dp(10))
                .setDuration(190)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (searchVisible) heading.setVisibility(View.GONE);
                })
                .start();
        searchPanel.setVisibility(View.VISIBLE);
        searchPanel.setAlpha(0f);
        searchPanel.setTranslationY(-dp(12));
        searchPanel.animate().cancel();
        searchPanel.animate().alpha(1f).translationY(0f)
                .setDuration(280)
                .setStartDelay(24)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    searchField.requestFocus();
                    showSearchKeyboard();
                })
                .start();
        refreshDirectory();
    }

    private void closeSearch() {
        if (!searchVisible) return;
        InputMethodManager input = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        input.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        searchField.clearFocus();
        searchField.setText("");
        searchVisible = false;
        searchPanel.animate().cancel();
        searchPanel.animate().alpha(0f).translationY(-dp(12))
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    searchPanel.setVisibility(View.GONE);
                    searchPanel.setAlpha(1f);
                    searchPanel.setTranslationY(0f);
                })
                .start();
        heading.setVisibility(View.VISIBLE);
        heading.setAlpha(0f);
        heading.setTranslationY(-dp(10));
        heading.animate().cancel();
        heading.animate().alpha(1f).translationY(0f)
                .setDuration(240)
                .setStartDelay(24)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        refreshDirectory();
    }

    private void showSearchKeyboard() {
        searchField.postDelayed(() -> {
            if (!searchVisible || isFinishing()) return;
            InputMethodManager input = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            input.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        }, 80);
    }

    private void refreshDirectory() {
        currentDirectory = canonical(currentDirectory);
        final File requestedDirectory = currentDirectory;
        final String query = searchVisible && searchField != null
                ? searchField.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        final boolean sortNewest = newestFirst;
        final boolean foldersOnly = folderPicker;
        final int generation = ++directoryGeneration;
        pathView.setText(requestedDirectory.getAbsolutePath());
        if (directoryTask != null) directoryTask.cancel(true);
        rows.removeAllViews();
        renderedChecks.clear();
        landscapeColumns = null;
        renderedEntryIndex = 0;
        visibleEntries.clear();
        visibleFileCount = 0;
        selectedVisibleFileCount = 0;

        loadingDirectory = true;
        showEmpty("Đang đọc thư mục...");
        updateSelection();
        final HashSet<String> selectedSnapshot = new HashSet<>(selectedFiles.keySet());
        directoryTask = directoryWorker.submit(() -> {
            ArrayList<FileEntry> result = new ArrayList<>();
            String error = null;
            try {
                if (!requestedDirectory.isDirectory() || !requestedDirectory.canRead()
                        || !isInsideRoot(requestedDirectory)) {
                    error = "Không thể đọc thư mục này. Hãy cấp quyền truy cập bộ nhớ trong Cài đặt.";
                } else {
                    File[] children = requestedDirectory.listFiles();
                    if (children == null) {
                        error = "Không thể liệt kê nội dung thư mục này.";
                    } else {
                        for (File child : children) {
                            if (Thread.currentThread().isInterrupted()) return;
                            File canonicalChild = canonical(child);
                            if (child.isDirectory()) {
                                if (!child.getName().startsWith(".") && child.canRead()
                                        && isInsideRoot(canonicalChild)
                                        && matchesQuery(child.getName(), query)) {
                                    result.add(new FileEntry(canonicalChild));
                                }
                            } else if (!foldersOnly && child.isFile()
                                    && isSupported(child.getName())
                                    && child.canRead() && isInsideRoot(canonicalChild)
                                    && matchesQuery(child.getName(), query)) {
                                result.add(new FileEntry(canonicalChild));
                            }
                        }
                    }
                }
            } catch (SecurityException errorReading) {
                error = "Không thể đọc thư mục này. Hãy kiểm tra quyền truy cập bộ nhớ.";
            }
            if (Thread.currentThread().isInterrupted()) return;
            Collections.sort(result, new Comparator<FileEntry>() {
                @Override public int compare(FileEntry left, FileEntry right) {
                    if (left.directory != right.directory) return left.directory ? -1 : 1;
                    if (sortNewest && left.modified != right.modified) {
                        return left.modified > right.modified ? -1 : 1;
                    }
                    return left.name.compareToIgnoreCase(right.name);
                }
            });
            int files = 0;
            int selected = 0;
            for (FileEntry entry : result) {
                if (entry.directory) continue;
                files++;
                if (selectedSnapshot.contains(entry.path)) selected++;
            }
            final int resultFileCount = files;
            final int resultSelectedCount = selected;
            final String emptyMessage = error != null ? error
                    : query.isEmpty()
                    ? foldersOnly ? "Thư mục này chưa có thư mục con."
                    : "Thư mục này chưa có thư mục con hoặc tệp JAR, ZIP."
                    : "Không tìm thấy tệp khớp với nội dung tìm kiếm.";
            mainHandler.post(() -> {
                if (generation != directoryGeneration || isFinishing() || isDestroyed()) return;
                loadingDirectory = false;
                visibleEntries.clear();
                visibleEntries.addAll(result);
                visibleFileCount = resultFileCount;
                selectedVisibleFileCount = resultSelectedCount;
                if (visibleEntries.isEmpty()) {
                    showEmpty(emptyMessage);
                } else {
                    emptyView.setVisibility(View.GONE);
                    rows.setVisibility(View.VISIBLE);
                    prepareEntryLayout();
                    for (FileEntry entry : visibleEntries) addEntryRow(entry);
                }
                updateSelection();
                if (pendingDirectoryScrollY >= 0) {
                    int restoreY = pendingDirectoryScrollY;
                    pendingDirectoryScrollY = -1;
                    directoryScroll.post(() -> directoryScroll.scrollTo(0, restoreY));
                }
            });
        });
    }

    private void prepareEntryLayout() {
        renderedEntryIndex = 0;
        renderedChecks.clear();
        landscapeColumns = null;
        if (!isLandscape()) return;

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        rows.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        landscapeColumns = new LinearLayout[]{new LinearLayout(this), new LinearLayout(this)};
        for (int index = 0; index < landscapeColumns.length; index++) {
            LinearLayout column = landscapeColumns[index];
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) columnParams.leftMargin = dp(12);
            grid.addView(column, columnParams);
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean matchesQuery(String name, String query) {
        return query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query);
    }

    private void showEmpty(String message) {
        rows.setVisibility(View.GONE);
        emptyView.setText(AppLocalization.text(this, message));
        emptyView.setVisibility(View.VISIBLE);
    }

    private void addEntryRow(final FileEntry entry) {
        int entryIndex = renderedEntryIndex++;
        renderedChecks.add(null);
        LinearLayout rowContainer = landscapeColumns == null
                ? rows : landscapeColumns[entryIndex % landscapeColumns.length];
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.setBackground(selectableBackground(0));
        row.setFocusable(true);
        row.setClickable(true);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        rowContainer.addView(row, rowParams);

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(rounded(COLOR_SURFACE, 12, 0));
        row.addView(iconSurface, new LinearLayout.LayoutParams(dp(42), dp(42)));
        FileGlyph glyph = new FileGlyph(this, entry.directory, entry.name);
        iconSurface.addView(glyph, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(COLOR_TEXT);
        name.setTextSize(15);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        nameParams.leftMargin = dp(12);
        row.addView(name, nameParams);

        if (entry.directory) {
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(COLOR_MUTED);
            arrow.setTextSize(26);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(32),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            row.setContentDescription(AppLocalization.text(this, "Mở thư mục " + entry.name));
            row.setOnClickListener(view -> {
                currentDirectory = entry.file;
                refreshDirectory();
            });
        } else {
            CheckBox check = new CheckBox(this);
            check.setButtonTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, new int[]{}},
                    new int[]{COLOR_ACCENT, COLOR_MUTED}));
            check.setFocusable(false);
            check.setClickable(false);
            check.setChecked(selectedFiles.containsKey(entry.path));
            renderedChecks.set(entryIndex, check);
            row.addView(check, new LinearLayout.LayoutParams(dp(42),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            row.setContentDescription(AppLocalization.text(this, "Chọn tệp " + entry.name));
            row.setOnClickListener(view -> toggleFile(entry, check));
        }

        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        rowContainer.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void toggleFile(FileEntry entry, CheckBox check) {
        String path = entry.path;
        if (selectedFiles.containsKey(path)) {
            selectedFiles.remove(path);
            if (selectedVisibleFileCount > 0) selectedVisibleFileCount--;
            check.setChecked(false);
        } else {
            selectedFiles.put(path, entry.file);
            selectedVisibleFileCount++;
            check.setChecked(true);
        }
        updateSelection();
    }

    private void toggleVisibleSelection() {
        boolean allSelected = visibleFileCount > 0
                && selectedVisibleFileCount == visibleFileCount;
        for (FileEntry entry : visibleEntries) {
            if (entry.directory) continue;
            String path = entry.path;
            if (allSelected) selectedFiles.remove(path);
            else selectedFiles.put(path, entry.file);
        }
        selectedVisibleFileCount = allSelected ? 0 : visibleFileCount;
        updateSelection();
        updateRenderedChecks();
    }

    private void updateSelection() {
        int count = selectedFiles.size();
        if (folderPicker) {
            selectionCount.setVisibility(View.VISIBLE);
            selectionCount.setText(AppLocalization.text(this,
                    storagePicker ? "Nơi lưu game" : "Chọn thư mục để quét"));
            addButton.setText(AppLocalization.text(this,
                    storagePicker ? "Dùng thư mục này" : "Chọn thư mục này"));
            boolean canChoose = !loadingDirectory && currentDirectory.isDirectory()
                    && currentDirectory.canRead();
            addButton.setEnabled(canChoose);
            addButton.setAlpha(canChoose ? 1f : 0.55f);
            backCancelButton.setText(AppLocalization.text(this, "Trở về"));
            backCancelButton.setContentDescription(currentDirectory.equals(storageRoot)
                    ? "Quay lại trang trước" : "Trở về thư mục cha");
            selectAllButton.setEnabled(false);
            return;
        }
        selectionCount.setVisibility(View.VISIBLE);
        addButton.setText(AppLocalization.text(this, "Thêm (" + count + ")"));
        addButton.setEnabled(count > 0);
        addButton.setAlpha(count > 0 ? 1f : 0.55f);
        selectionCount.setText(AppLocalization.text(this, count == 0 ? "Chưa chọn tệp nào"
                : "Đã chọn " + count + " tệp"));
        backCancelButton.setText(AppLocalization.text(this, count > 0 ? "Hủy" : "Trở về"));
        backCancelButton.setContentDescription(count > 0
                ? "Hủy chọn và quay lại"
                : currentDirectory.equals(storageRoot)
                ? "Quay lại trang trước" : "Trở về thư mục cha");
        boolean allVisibleSelected = visibleFileCount > 0
                && selectedVisibleFileCount == visibleFileCount;
        selectAllButton.setEnabled(visibleFileCount > 0 && !loadingDirectory);
        selectAllButton.setAlpha(visibleFileCount > 0 && !loadingDirectory ? 1f : 0.45f);
        selectAllButton.setContentDescription(allVisibleSelected
                ? "Bỏ chọn tất cả tệp đang hiển thị" : "Chọn tất cả tệp đang hiển thị");
        selectAllGlyph.setActive(allVisibleSelected);
    }

    private void updateRenderedChecks() {
        for (int index = 0; index < visibleEntries.size()
                && index < renderedChecks.size(); index++) {
            FileEntry entry = visibleEntries.get(index);
            CheckBox check = renderedChecks.get(index);
            if (!entry.directory && check != null)
                check.setChecked(selectedFiles.containsKey(entry.path));
        }
    }

    private void handleBackCancel() {
        if (!selectedFiles.isEmpty()) {
            finish();
        } else if (!currentDirectory.equals(storageRoot)) {
            goToParent();
        } else {
            finish();
        }
    }

    private void goToParent() {
        if (currentDirectory.equals(storageRoot)) return;
        File parent = currentDirectory.getParentFile();
        if (parent != null && isInsideRoot(parent)) {
            currentDirectory = parent;
            refreshDirectory();
        }
    }

    private void finishWithSelection() {
        if (selectedFiles.isEmpty()) return;
        Intent result = new Intent();
        result.putStringArrayListExtra(EXTRA_SELECTED_FILES,
                new ArrayList<>(selectedFiles.keySet()));
        setResult(RESULT_OK, result);
        finish();
    }

    private void finishWithDirectory() {
        if (!folderPicker || !currentDirectory.isDirectory() || !currentDirectory.canRead()) return;
        Intent result = new Intent();
        result.putExtra(EXTRA_SELECTED_DIRECTORY, currentDirectory.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    @Override public void onBackPressed() {
        if (searchVisible) {
            closeSearch();
            return;
        }
        if (!currentDirectory.equals(storageRoot)) goToParent();
        else super.onBackPressed();
    }

    private boolean isSupported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private boolean isInsideRoot(File candidate) {
        if (candidate == null) return false;
        try {
            String rootPath = storageRoot.getCanonicalPath();
            String candidatePath = candidate.getCanonicalPath();
            return candidatePath.equals(rootPath)
                    || candidatePath.startsWith(rootPath + File.separator);
        } catch (IOException error) {
            return false;
        }
    }

    private File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException error) {
            return file.getAbsoluteFile();
        }
    }

    private StateListDrawable selectableBackground(int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                rounded(COLOR_SURFACE_SELECTED, radius, 0));
        states.addState(new int[]{android.R.attr.state_pressed},
                rounded(COLOR_SURFACE_SELECTED, radius, 0));
        states.addState(new int[0], rounded(android.graphics.Color.TRANSPARENT, radius, 0));
        return states;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    @Override protected void onDestroy() {
        directoryGeneration++;
        if (directoryTask != null) directoryTask.cancel(true);
        directoryWorker.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class FileGlyph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final boolean directory;
        private final String extension;
        private final int iconColor;
        private final int labelColor;

        FileGlyph(Activity activity, boolean directory, String fileName) {
            super(activity);
            this.directory = directory;
            String lower = fileName.toLowerCase(Locale.ROOT);
            this.extension = lower.endsWith(".zip") ? "ZIP" : "JAR";
            this.iconColor = COLOR_ACCENT;
            this.labelColor = lower.endsWith(".zip") ? 0xFFFFC66E : 0xFF6ED6E8;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float s = Math.min(getWidth(), getHeight()) / 24f;
            canvas.save();
            canvas.translate((getWidth() - 24f * s) / 2f, (getHeight() - 24f * s) / 2f);
            canvas.scale(s, s);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(iconColor);
            if (directory) {
                path.reset();
                path.moveTo(3.5f, 7f);
                path.lineTo(9.5f, 7f);
                path.lineTo(11.5f, 9.5f);
                path.lineTo(20.5f, 9.5f);
                path.lineTo(20.5f, 18.5f);
                path.lineTo(3.5f, 18.5f);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(3.5f, 11f, 20.5f, 11f, paint);
            } else {
                path.reset();
                path.moveTo(6f, 3.5f);
                path.lineTo(14.5f, 3.5f);
                path.lineTo(19f, 8f);
                path.lineTo(19f, 20.5f);
                path.lineTo(5f, 20.5f);
                path.lineTo(5f, 3.5f);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(14.5f, 3.5f, 14.5f, 8f, paint);
                canvas.drawLine(14.5f, 8f, 19f, 8f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.BOLD));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(5.6f);
                paint.setColor(labelColor);
                canvas.drawText(extension, 12f, 17.2f, paint);
            }
            canvas.restore();
        }
    }

    private static final class BrowserIconView extends View {
        static final int SELECT_ALL = 1;
        static final int FILTER = 2;
        static final int SEARCH = 3;
        static final int CLOSE = 4;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int type;
        private boolean active;

        BrowserIconView(Activity activity, int type) {
            super(activity);
            this.type = type;
        }

        void setActive(boolean active) {
            if (this.active == active) return;
            this.active = active;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.save();
            canvas.translate((getWidth() - 24f * scale) / 2f,
                    (getHeight() - 24f * scale) / 2f);
            canvas.scale(scale, scale);
            paint.setColor(active ? COLOR_ACCENT : COLOR_TEXT);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            switch (type) {
                case SELECT_ALL:
                    paint.setStyle(Paint.Style.FILL);
                    for (int row = 0; row < 5; row++) {
                        for (int column = 0; column < 5; column++) {
                            if (row != 0 && row != 4 && column != 0 && column != 4) continue;
                            float centerX = 4f + column * 4f;
                            float centerY = 4f + row * 4f;
                            canvas.drawRoundRect(centerX - 0.9f, centerY - 0.9f,
                                    centerX + 0.9f, centerY + 0.9f,
                                    0.6f, 0.6f, paint);
                        }
                    }
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.8f);
                    canvas.drawRect(8.4f, 8.4f, 15.6f, 15.6f, paint);
                    break;
                case FILTER:
                    paint.setStrokeWidth(2.1f);
                    canvas.drawLine(3f, 5f, 21f, 5f, paint);
                    canvas.drawLine(6f, 12f, 18f, 12f, paint);
                    canvas.drawLine(9f, 19f, 15f, 19f, paint);
                    break;
                case SEARCH:
                    canvas.drawCircle(10f, 10f, 6.3f, paint);
                    canvas.drawLine(14.8f, 14.8f, 20.5f, 20.5f, paint);
                    break;
                case CLOSE:
                    canvas.drawLine(6f, 6f, 18f, 18f, paint);
                    canvas.drawLine(18f, 6f, 6f, 18f, paint);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }
    }
}
