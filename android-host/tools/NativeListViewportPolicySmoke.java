package org.recompile.freej2me.lite.nativeui;

public final class NativeListViewportPolicySmoke {
    public static void main(String[] args) {
        check(NativeListViewportPolicy.shouldScrollTo(6, 0, 9, true),
                "initial List focus should be positioned");
        check(!NativeListViewportPolicy.shouldScrollTo(5, 0, 9, false),
                "selection inside the viewport must not change the scroll position");
        check(NativeListViewportPolicy.shouldScrollTo(10, 0, 9, false),
                "selection below the viewport must be brought into view");
        check(NativeListViewportPolicy.shouldScrollTo(0, 1, 9, false),
                "selection above the viewport must be brought into view");
        check(NativeListViewportPolicy.shouldScrollTo(4, -1, -1, false),
                "unlaid-out lists must position their initial visible focus");
        check(!NativeListViewportPolicy.shouldScrollTo(-1, 0, 9, true),
                "missing focus IDs must not scroll");
        System.out.println("NativeListViewportPolicySmoke: focus viewport behavior passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
