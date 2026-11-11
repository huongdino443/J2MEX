package org.recompile.freej2me.protocol;

/** Wire messages shared by the Java ME engine and Android Bluetooth host. */
public final class BluetoothProtocol {
    private BluetoothProtocol() { }

    /** Frame type written by the engine to the Android activity. */
    public static final int HOST_REQUEST = 9;
    /** Commands written by Android to the engine. */
    public static final int ENGINE_RESPONSE = 15;
    public static final int ENGINE_EVENT = 16;

    public static final int ACCESS = 1;
    public static final int START_INQUIRY = 2;
    public static final int CANCEL_INQUIRY = 3;
    public static final int SET_DISCOVERABLE = 4;
    public static final int GET_LOCAL_NAME = 5;
    public static final int FETCH_UUIDS = 6;
    public static final int OPEN_SERVER = 7;
    public static final int OPEN_CLIENT = 8;
    public static final int ACCEPT = 9;
    public static final int READ = 10;
    public static final int WRITE = 11;
    public static final int AVAILABLE = 12;
    public static final int CLOSE = 13;
    public static final int GET_BONDED = 14;

    public static final int NEED_SCAN = 1;
    public static final int NEED_CONNECT = 2;
    public static final int NEED_ADVERTISE = 4;

    public static final int DEVICE_FOUND = 1;
    public static final int INQUIRY_FINISHED = 2;
    public static final int UUIDS_FOUND = 3;
    public static final int INQUIRY_COMPLETED = 0;
    public static final int INQUIRY_TERMINATED = 5;
}
