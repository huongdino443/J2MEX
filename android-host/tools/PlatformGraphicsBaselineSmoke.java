package org.recompile.mobile;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public final class PlatformGraphicsBaselineSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int y = 28, ascent = 11, height = 14;
        require(PlatformGraphics.stringBaseline(y, ascent, height,
                        Graphics.LEFT | Graphics.BASELINE) == y,
                "MIDP BASELINE anchor must keep y as the AWT baseline");
        require(PlatformGraphics.stringBaseline(y, ascent, height,
                        Graphics.LEFT | Graphics.TOP) == y + ascent - 1,
                "TOP anchor behavior changed");
        require(PlatformGraphics.stringBaseline(y, ascent, height,
                        Graphics.LEFT | Graphics.VCENTER) == y + ascent - 1 - height / 2,
                "VCENTER anchor behavior changed");

        int ascentOffset = 6;
        require(PlatformGraphics.androidStringBaseline(y, ascent, height, ascentOffset,
                        Graphics.LEFT | Graphics.TOP)
                        == PlatformGraphics.stringBaseline(y, ascent, height,
                                Graphics.LEFT | Graphics.TOP),
                "Android ascent correction must not move a TOP anchor twice");
        require(PlatformGraphics.androidStringBaseline(y, ascent, height, ascentOffset,
                        Graphics.LEFT | Graphics.VCENTER)
                        == PlatformGraphics.stringBaseline(y, ascent, height,
                                Graphics.LEFT | Graphics.VCENTER),
                "Android ascent correction must preserve a VCENTER anchor");
        require(PlatformGraphics.androidStringBaseline(y, ascent, height, ascentOffset,
                        Graphics.LEFT | Graphics.BASELINE) == y - ascentOffset,
                "Android BASELINE anchor must retain Turbo's ascent correction");
        System.out.println("PlatformGraphicsBaselineSmoke: Android ascent correction respects TOP, VCENTER and BASELINE");
    }
}
