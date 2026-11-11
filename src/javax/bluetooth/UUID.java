package javax.bluetooth;

import java.util.Locale;

/** JSR-82 UUID value with the Bluetooth base UUID applied to short values. */
public final class UUID {
    private static final String BASE_SUFFIX = "00001000800000805F9B34FB";
    private final String value;

    public UUID(long shortValue) {
        if (shortValue < 0 || shortValue > 0xffffffffL)
            throw new IllegalArgumentException("UUID value must fit in 32 bits");
        this.value = expand(Long.toHexString(shortValue), true);
    }

    public UUID(String value, boolean shortUUID) {
        if (value == null) throw new NullPointerException("UUID value");
        if (value.length() == 0) throw new IllegalArgumentException("UUID value is empty");
        this.value = expand(value, shortUUID);
    }

    private static String expand(String input, boolean shortUUID) {
        String hex = input.toUpperCase(Locale.US);
        if (!hex.matches("[0-9A-F]+")) throw new IllegalArgumentException("Invalid UUID");
        if (shortUUID) {
            if (hex.length() > 8) throw new IllegalArgumentException("Short UUID exceeds 32 bits");
            hex = String.format(Locale.US, "%8s", hex).replace(' ', '0') + BASE_SUFFIX;
        } else {
            if (hex.length() > 32) throw new IllegalArgumentException("UUID exceeds 128 bits");
            hex = String.format(Locale.US, "%32s", hex).replace(' ', '0');
        }
        return hex;
    }

    public String toString() { return value; }
    public boolean equals(Object other) {
        return other instanceof UUID && value.equals(((UUID)other).value);
    }
    public int hashCode() { return value.hashCode(); }
}
