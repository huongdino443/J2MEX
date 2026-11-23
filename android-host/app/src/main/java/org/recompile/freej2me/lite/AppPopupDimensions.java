package org.recompile.freej2me.lite;

/**
 * Shared dp dimensions for app-owned popup and dialog surfaces.
 *
 * Keep action placement semantic rather than uniform: information popups use
 * one end-aligned Close action, confirmations keep Cancel and the primary action
 * paired, and selection popups keep their own list/footer structure.
 */
final class AppPopupDimensions {
    static final int GRID_DP = 4;

    static final int SPACE_1_DP = 4;
    static final int SPACE_2_DP = 8;
    static final int SPACE_3_DP = 12;
    static final int SPACE_4_DP = 16;
    static final int SPACE_5_DP = 20;
    static final int SPACE_6_DP = 24;
    static final int SPACE_8_DP = 32;

    static final int PANEL_HORIZONTAL_PADDING_DP = SPACE_5_DP;
    static final int PANEL_TOP_PADDING_DP = SPACE_5_DP;
    static final int PANEL_BOTTOM_PADDING_DP = SPACE_4_DP;
    static final int UPDATE_PANEL_TOP_PADDING_DP = 18;
    static final int UPDATE_PANEL_BOTTOM_PADDING_DP = 8;
    static final int PANEL_CORNER_RADIUS_DP = SPACE_6_DP;
    static final int UPDATE_PANEL_CORNER_RADIUS_DP = 20;
    static final int PANEL_ELEVATION_DP = 8;
    static final int POPUP_ELEVATION_DP = 10;

    static final int MAX_POPUP_WIDTH_DP = 400;
    static final int MIN_POPUP_WIDTH_DP = 260;
    static final int SCREEN_EDGE_GUTTER_DP = SPACE_4_DP;
    static final int MAX_SCROLL_CONTENT_HEIGHT_DP = 360;

    static final int STANDARD_ACTION_MIN_HEIGHT_DP = 48;
    static final int COMPACT_CLOSE_MIN_HEIGHT_DP = 36;
    static final int UPDATE_ACTION_MIN_HEIGHT_DP = 44;
    static final int UPDATE_ACTION_TEXT_SIZE_SP = 13;
    static final int UPDATE_ACTION_PADDING_DP = 4;
    static final int UPDATE_FOOTER_GAP_DP = 6;
    static final int CHOICE_ROW_HEIGHT_DP = 52;
    static final int LIST_ITEM_MIN_HEIGHT_DP = 56;
    static final int ACTION_HORIZONTAL_PADDING_DP = SPACE_4_DP;
    static final int INFO_CLOSE_OPTICAL_OFFSET_DP = SPACE_1_DP;
    static final int SECTION_GAP_DP = SPACE_3_DP;
    static final int FOOTER_GAP_DP = SPACE_2_DP;

    static final int PORTRAIT_DIALOG_SIDE_MARGIN_DP = 36;
    static final int LANDSCAPE_DIALOG_SIDE_MARGIN_DP = 20;
    static final int RUNTIME_DIALOG_SIDE_MARGIN_DP = 28;
    static final int COMPACT_DIALOG_SIDE_MARGIN_DP = 40;
    static final int COMPACT_DIALOG_MAX_WIDTH_DP = 480;
    static final int WIDE_DIALOG_MAX_WIDTH_DP = 520;

    private AppPopupDimensions() {}
}
