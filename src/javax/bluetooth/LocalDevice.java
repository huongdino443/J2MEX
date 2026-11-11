package javax.bluetooth;

import javax.microedition.io.Connection;
import org.recompile.freej2me.protocol.BluetoothProtocol;
import org.recompile.mobile.BluetoothBridge;

/** JSR-82 local adapter facade backed by the Android host activity. */
public final class LocalDevice {
    private static final LocalDevice INSTANCE = new LocalDevice();
    private static final DiscoveryAgent AGENT = new DiscoveryAgent();

    private LocalDevice() { }

    public static LocalDevice getLocalDevice() throws BluetoothStateException {
        BluetoothBridge.Reply reply = BluetoothBridge.request(
                BluetoothProtocol.ACCESS, BluetoothProtocol.NEED_CONNECT, "");
        if (!reply.accepted) throw new BluetoothStateException(
                reply.detail.length() == 0 ? "Bluetooth permission or adapter unavailable" : reply.detail);
        return INSTANCE;
    }

    public DiscoveryAgent getDiscoveryAgent() { return AGENT; }

    public String getFriendlyName() {
        BluetoothBridge.Reply reply = BluetoothBridge.request(
                BluetoothProtocol.GET_LOCAL_NAME, BluetoothProtocol.NEED_CONNECT, "");
        return reply.accepted ? reply.detail : "";
    }

    public boolean setDiscoverable(int mode) throws BluetoothStateException {
        if (mode == DiscoveryAgent.NOT_DISCOVERABLE) return true;
        BluetoothBridge.Reply reply = BluetoothBridge.request(
                BluetoothProtocol.SET_DISCOVERABLE,
                BluetoothProtocol.NEED_CONNECT | BluetoothProtocol.NEED_ADVERTISE, "");
        if (!reply.accepted && reply.detail.length() != 0)
            throw new BluetoothStateException(reply.detail);
        return reply.accepted;
    }

    public static String getProperty(String property) {
        if ("bluetooth.connected.devices.max".equals(property)) return "7";
        if ("bluetooth.l2cap.receiveMTU.max".equals(property)) return "672";
        return null;
    }

    public ServiceRecord getRecord(Connection notifier) {
        if (notifier instanceof BluetoothServiceNotifier)
            return ((BluetoothServiceNotifier)notifier).getServiceRecord();
        return null;
    }
}
