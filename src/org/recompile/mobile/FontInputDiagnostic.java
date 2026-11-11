package org.recompile.mobile;

import java.util.HashSet;
import java.util.Set;

/**
 * Bounded, content-redacted diagnostics for system-font and startup input paths.
 * This class records shapes and metrics, never the actual string contents.
 */
public final class FontInputDiagnostic {
    private static final int MAX_FONT_EVENTS = 120;
    private static final int MAX_INPUT_EVENTS = 40;
    private static final Set<String> FONT_EVENTS = new HashSet<String>();
    private static int fontEventCount;
    private static int inputEventCount;

    private FontInputDiagnostic() { }

    public static synchronized void fontMetrics(int face, int style, int pointSize,
            int ascent, int descent, int leading, int ascentOffset) {
        String key = "metrics:" + face + ":" + (style & 7) + ":" + pointSize;
        if (!recordFontEvent(key)) return;
        System.out.println("DIAG_FONT op=metrics face=" + face
                + " style=" + (style & 7) + " size=" + pointSize
                + " ascent=" + ascent + " descent=" + descent
                + " leading=" + leading + " ascentOffset=" + ascentOffset);
    }

    public static synchronized void fontText(String operation, int face, int style,
            int pointSize, String text, float advance) {
        if (text == null) return;
        TextShape shape = TextShape.of(text);
        String key = operation + ":" + face + ":" + (style & 7) + ":"
                + pointSize + ":" + shape.signature();
        if (!recordFontEvent(key)) return;
        System.out.println("DIAG_FONT op=" + operation + " face=" + face
                + " style=" + (style & 7) + " size=" + pointSize
                + " chars=" + shape.length + " unique=" + shape.uniqueCount
                + " repeatedCode=" + shape.repeatedCode
                + " upperAscii=" + shape.upperAscii
                + " advance=" + advance);
    }

    public static synchronized void input(String event, int value1, int value2,
            String displayable) {
        if (inputEventCount >= MAX_INPUT_EVENTS) return;
        int sequence = ++inputEventCount;
        System.out.println("DIAG_INPUT #" + sequence + " event=" + event
                + " value1=" + value1 + " value2=" + value2
                + " display=" + (displayable == null ? "none" : displayable));
    }

    private static boolean recordFontEvent(String key) {
        if (fontEventCount >= MAX_FONT_EVENTS || !FONT_EVENTS.add(key)) return false;
        fontEventCount++;
        return true;
    }

    private static final class TextShape {
        final int length;
        final int uniqueCount;
        final int repeatedCode;
        final boolean upperAscii;

        private TextShape(int length, int uniqueCount, int repeatedCode, boolean upperAscii) {
            this.length = length;
            this.uniqueCount = uniqueCount;
            this.repeatedCode = repeatedCode;
            this.upperAscii = upperAscii;
        }

        static TextShape of(String text) {
            boolean upperAscii = true;
            boolean repeated = text.length() > 1;
            char first = text.length() == 0 ? 0 : text.charAt(0);
            Set<Character> unique = new HashSet<Character>();
            for (int i = 0; i < text.length(); i++) {
                char value = text.charAt(i);
                unique.add(Character.valueOf(value));
                if (value != first) repeated = false;
                if (value < 'A' || value > 'Z') upperAscii = false;
            }
            return new TextShape(text.length(), unique.size(),
                    repeated ? (int)first : -1, upperAscii);
        }

        String signature() {
            return length + ":" + uniqueCount + ":" + repeatedCode + ":" + upperAscii;
        }
    }
}
