package javax.bluetooth;

/** Bluetooth class-of-device bit fields reported during discovery. */
public final class DeviceClass {
    private final int record;

    public DeviceClass(int record) {
        if ((record & 0xff000000) != 0) throw new IllegalArgumentException("Invalid class of device");
        this.record = record;
    }

    public int getServiceClasses() { return record & 0xffe000; }
    public int getMajorDeviceClass() { return record & 0x001f00; }
    public int getMinorDeviceClass() { return record & 0x0000fc; }
}
