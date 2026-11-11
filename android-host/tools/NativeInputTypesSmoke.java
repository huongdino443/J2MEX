import android.text.InputType;
import org.recompile.freej2me.lite.nativeui.NativeInputTypes;

public final class NativeInputTypesSmoke {
    public static void main(String[] args) {
        int textPassword = NativeInputTypes.of(0x10000);
        int expectedTextPassword = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        require(textPassword == expectedTextPassword, "text password input type");
        require(NativeInputTypes.isPassword(0x10000), "password flag detection");
        require(!NativeInputTypes.isPassword(0), "non-password flag detection");

        int numericPassword = NativeInputTypes.of(2 | 0x10000);
        int expectedNumericPassword = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        require(numericPassword == expectedNumericPassword, "numeric password input type");

        int decimalPassword = NativeInputTypes.of(5 | 0x10000);
        int expectedDecimalPassword = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        require(decimalPassword == expectedDecimalPassword, "decimal password input type");

        System.out.println("NativeInputTypesSmoke: 5 password mapping checks passed");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
