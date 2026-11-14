package org.recompile.freej2me.lite;

import java.util.Locale;

/** Compares stable dotted release versions such as v1.2.3. */
final class ReleaseVersionPolicy {
    private ReleaseVersionPolicy() {}

    static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int[] parse(String value) {
        if (value == null) return new int[0];
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) normalized = normalized.substring(1);
        int suffix = normalized.indexOf('-');
        if (suffix >= 0) normalized = normalized.substring(0, suffix);
        suffix = normalized.indexOf('+');
        if (suffix >= 0) normalized = normalized.substring(0, suffix);
        String[] parts = normalized.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceFirst("[^0-9].*$", "");
            if (digits.isEmpty()) return new int[0];
            try {
                result[i] = Integer.parseInt(digits);
            } catch (NumberFormatException error) {
                return new int[0];
            }
        }
        return result;
    }
}
