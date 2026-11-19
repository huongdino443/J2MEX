package org.recompile.freej2me.lite;

/** Foreground service isolated with the second experimental runtime process. */
public final class BackgroundGameService2 extends BackgroundGameService {
    @Override protected int defaultSessionSlot() {
        return 1;
    }
}
