package org.recompile.freej2me.lite;

import android.graphics.RectF;

interface FramePresentation {
    void frame(int width, int height, int[] pixels);
    boolean getCanvasBounds(RectF output);
    void setCanvasPresentation(int scalePercent, int position);
    void setPointerInputEnabled(boolean enabled);
    void setRefreshRateHz(int refreshRateHz);
    String drainFrameStats();
}
