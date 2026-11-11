import java.util.Date;
import javax.microedition.lcdui.DateField;

public final class DateFieldModelSmoke {
    public static void main(String[] args) {
        DateField field = new DateField("Date", DateField.DATE);
        if (field.getDate() != null) throw new AssertionError("new DateField must be empty");

        Date input = new Date(1_700_000_000_000L);
        field.setDate(input);
        input.setTime(0L);
        Date stored = field.getDate();
        if (stored == null || stored.getTime() != 1_700_000_000_000L)
            throw new AssertionError("setDate must store the supplied instant");

        stored.setTime(0L);
        if (field.getDate().getTime() != 1_700_000_000_000L)
            throw new AssertionError("getDate must return a defensive copy");

        field.setDate(null);
        if (field.getDate() != null) throw new AssertionError("setDate(null) must clear the value");

        boolean rejected = false;
        try { field.setInputMode(0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("invalid DateField mode was accepted");

        System.out.println("DateFieldModelSmoke PASS");
    }
}
