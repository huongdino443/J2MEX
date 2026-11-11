package javax.bluetooth;

import javax.microedition.io.Connection;

/** Internal hook for Bluetooth server notifiers to expose their SDP record. */
public interface BluetoothServiceNotifier extends Connection {
    ServiceRecord getServiceRecord();
}
