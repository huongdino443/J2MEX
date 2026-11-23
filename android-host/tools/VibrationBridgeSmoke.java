import com.nokia.mid.ui.DeviceControl;
import com.samsung.util.Vibration;
import com.siemens.mp.game.Vibrator;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.lcdui.Display;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

public final class VibrationBridgeSmoke {
    private static final class VibrationCall {
        final long duration;
        final int strength;
        VibrationCall(long duration, int strength) {
            this.duration = duration;
            this.strength = strength;
        }
    }

    public static void main(String[] args) {
        MobilePlatform platform = new MobilePlatform(8, 8);
        List<VibrationCall> calls = new ArrayList<>();
        int[] stops = {0};
        platform.setVibrationHandler(new MobilePlatform.VibrationHandler() {
            @Override public void vibrate(long duration, int strength) {
                calls.add(new VibrationCall(duration, strength));
            }
            @Override public void stop() { stops[0]++; }
        });
        Mobile.setPlatform(platform);

        if (!new Display().vibrate(40)) throw new AssertionError("Display.vibrate rejected");
        assertCall(calls, 0, 40, 100);
        DeviceControl.startVibra(65, 750);
        assertCall(calls, 1, 750, 65);
        DeviceControl.stopVibra();
        if (stops[0] != 1) throw new AssertionError("Nokia stop was not forwarded");
        Vibrator.startVibrator();
        assertCall(calls, 2, -1, 100);
        Vibrator.stopVibrator();
        if (stops[0] != 2) throw new AssertionError("Siemens stop was not forwarded");
        if (!Vibration.isSupported()) throw new AssertionError("Samsung support was not reported");
        Vibration.start(260, 35);
        assertCall(calls, 3, 260, 35);
        Vibration.stop();
        if (stops[0] != 3) throw new AssertionError("Samsung stop was not forwarded");
        System.out.println("PASS: MIDP, Nokia, Siemens, and Samsung vibration APIs reach the host");
    }

    private static void assertCall(List<VibrationCall> calls, int index,
            long duration, int strength) {
        if (calls.size() <= index || calls.get(index).duration != duration
                || calls.get(index).strength != strength)
            throw new AssertionError("Unexpected vibration request at index " + index);
    }
}
