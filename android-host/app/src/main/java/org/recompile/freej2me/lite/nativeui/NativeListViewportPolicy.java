package org.recompile.freej2me.lite.nativeui;

/** Keeps focus updates from realigning a List row that is already on screen. */
final class NativeListViewportPolicy {
    private NativeListViewportPolicy() { }

    static boolean shouldScrollTo(int position, int firstVisible, int lastVisible,
            boolean initialScreen) {
        if (position < 0) return false;
        return initialScreen || firstVisible < 0 || lastVisible < 0
                || position < firstVisible || position > lastVisible;
    }
}
