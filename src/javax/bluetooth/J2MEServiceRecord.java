package javax.bluetooth;

import java.util.Enumeration;
import java.util.Hashtable;

/** In-memory SDP record used by MIDlets to form JSR-82 connection URLs. */
final class J2MEServiceRecord implements ServiceRecord {
    private final RemoteDevice host;
    private final UUID uuid;
    private final boolean l2cap;
    private final Hashtable<Integer, DataElement> attributes = new Hashtable<Integer, DataElement>();

    J2MEServiceRecord(RemoteDevice host, UUID uuid, boolean l2cap) {
        if (uuid == null) throw new NullPointerException("uuid");
        this.host = host;
        this.uuid = uuid;
        this.l2cap = l2cap;
        attributes.put(Integer.valueOf(0x0001), new DataElement(DataElement.UUID, uuid));
        attributes.put(Integer.valueOf(0x0100), new DataElement(DataElement.STRING, "FreeJ2ME Bluetooth"));
    }

    public int[] getAttributeIDs() {
        int[] result = new int[attributes.size()];
        int index = 0;
        for (Enumeration<Integer> keys = attributes.keys(); keys.hasMoreElements();)
            result[index++] = keys.nextElement().intValue();
        return result;
    }

    public DataElement getAttributeValue(int attributeId) {
        return attributes.get(Integer.valueOf(attributeId));
    }

    public String getConnectionURL(int security, boolean master) {
        if (security < NOAUTHENTICATE_NOENCRYPT || security > AUTHENTICATE_ENCRYPT)
            throw new IllegalArgumentException("Invalid security level");
        StringBuilder url = new StringBuilder(l2cap ? "btl2cap://" : "btspp://");
        url.append(host == null ? "localhost" : host.getBluetoothAddress()).append(':').append(uuid);
        url.append(";authenticate=").append(security != NOAUTHENTICATE_NOENCRYPT);
        url.append(";encrypt=").append(security == AUTHENTICATE_ENCRYPT);
        url.append(";master=").append(master);
        return url.toString();
    }

    public RemoteDevice getHostDevice() { return host; }
    public boolean populateRecord(int[] ids) {
        if (ids == null) throw new NullPointerException("attribute IDs");
        for (int id : ids) if (attributes.containsKey(Integer.valueOf(id))) return true;
        return false;
    }
    public boolean setAttributeValue(int id, DataElement value) {
        if (id == 0) throw new IllegalArgumentException("Attribute 0 is reserved");
        if (value == null) return false;
        attributes.put(Integer.valueOf(id), value);
        return true;
    }
    public void setDeviceServiceClasses(int classes) { }
}
