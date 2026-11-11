/*
This file is part of FreeJ2ME.

FreeJ2ME is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package javax.bluetooth;

import java.util.Vector;
import java.util.Hashtable;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.recompile.freej2me.protocol.BluetoothProtocol;
import org.recompile.mobile.BluetoothBridge;

/**
 * JSR-82 discovery entry point. Android owns permissions, adapter activation,
 * and device discovery; a rejected system request is reported as an inquiry
 * error rather than leaving the game thread waiting for a callback.
 */
public final class DiscoveryAgent {
    public static final int NOT_DISCOVERABLE = 0;
    public static final int GIAC = 0x9E8B33;
    public static final int LIAC = 0x9E8B00;
    public static final int CACHED = 0x00;
    public static final int PREKNOWN = 0x01;

    private static final Vector<RemoteDevice> CACHED_DEVICES = new Vector<RemoteDevice>();
    private static final Hashtable<Integer, ServiceSearch> SERVICE_SEARCHES =
            new Hashtable<Integer, ServiceSearch>();
    private static final AtomicInteger NEXT_TRANSACTION = new AtomicInteger(1);
    private static final Timer SEARCH_TIMEOUTS = new Timer("BluetoothServiceSearchTimeout", true);
    private static volatile DiscoveryListener activeListener;
    private static volatile boolean inquiryActive;

    private static final class ServiceSearch {
        final int id;
        final RemoteDevice device;
        final UUID[] requested;
        final DiscoveryListener listener;
        ServiceSearch(int id, RemoteDevice device, UUID[] requested, DiscoveryListener listener) {
            this.id = id;
            this.device = device;
            this.requested = requested;
            this.listener = listener;
        }
    }

    DiscoveryAgent() { }

    public RemoteDevice[] retrieveDevices(int option) {
        if (option != CACHED && option != PREKNOWN) throw new IllegalArgumentException("Unknown device list");
        if (option == PREKNOWN) {
            BluetoothBridge.Reply reply = BluetoothBridge.request(
                    BluetoothProtocol.GET_BONDED, BluetoothProtocol.NEED_CONNECT, "");
            if (!reply.accepted) return new RemoteDevice[0];
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(reply.data))) {
                int count = input.readInt();
                if (count < 0 || count > 256) return new RemoteDevice[0];
                RemoteDevice[] devices = new RemoteDevice[count];
                for (int i = 0; i < count; i++)
                    devices[i] = new RemoteDevice(input.readUTF(), input.readUTF());
                return devices;
            } catch (IOException error) {
                return new RemoteDevice[0];
            }
        }
        synchronized (CACHED_DEVICES) {
            RemoteDevice[] result = new RemoteDevice[CACHED_DEVICES.size()];
            CACHED_DEVICES.copyInto(result);
            return result;
        }
    }

    public boolean startInquiry(int accessCode, DiscoveryListener listener) throws BluetoothStateException {
        if (listener == null) throw new NullPointerException("DiscoveryListener is null");
        if (accessCode != GIAC && accessCode != LIAC && (accessCode < 0x9E8B00 || accessCode > 0x9E8B3F))
            throw new IllegalArgumentException("Invalid access code");
        if (inquiryActive) return false;

        activeListener = listener;
        inquiryActive = true;
        synchronized (CACHED_DEVICES) { CACHED_DEVICES.clear(); }
        BluetoothBridge.Reply reply = BluetoothBridge.request(
                BluetoothProtocol.START_INQUIRY,
                BluetoothProtocol.NEED_SCAN | BluetoothProtocol.NEED_CONNECT, "");
        if (!reply.accepted) {
            activeListener = null;
            inquiryActive = false;
            listener.inquiryCompleted(DiscoveryListener.INQUIRY_ERROR);
            return false;
        }
        return true;
    }

    public boolean cancelInquiry(DiscoveryListener listener) {
        if (listener == null) throw new NullPointerException("DiscoveryListener is null");
        BluetoothBridge.request(BluetoothProtocol.CANCEL_INQUIRY, 0, "");
        if (inquiryActive) inquiryFinished(DiscoveryListener.INQUIRY_TERMINATED);
        return true;
    }

    public int searchServices(int[] attributes, UUID[] uuids, RemoteDevice device,
            DiscoveryListener listener) throws BluetoothStateException {
        if (uuids == null || device == null || listener == null)
            throw new NullPointerException();
        if (uuids.length == 0) throw new IllegalArgumentException("uuidSet is empty");
        for (int i = 0; i < uuids.length; i++) {
            if (uuids[i] == null) throw new NullPointerException("uuidSet contains null");
            for (int j = i + 1; j < uuids.length; j++)
                if (uuids[i].equals(uuids[j])) throw new IllegalArgumentException("duplicate UUID");
        }
        if (attributes != null) for (int attribute : attributes)
            if (attribute < 0 || attribute > 0xffff) throw new IllegalArgumentException("Invalid attribute ID");

        int id = NEXT_TRANSACTION.getAndIncrement();
        ServiceSearch search = new ServiceSearch(id, device, uuids.clone(), listener);
        synchronized (SERVICE_SEARCHES) { SERVICE_SEARCHES.put(Integer.valueOf(id), search); }
        SEARCH_TIMEOUTS.schedule(new TimerTask() {
            public void run() { completeSearch(id, DiscoveryListener.SERVICE_SEARCH_NO_RECORDS, null); }
        }, 30000L);

        BluetoothBridge.Reply reply = BluetoothBridge.request(
                BluetoothProtocol.FETCH_UUIDS, BluetoothProtocol.NEED_CONNECT,
                device.getBluetoothAddress());
        if (!reply.accepted)
            completeSearch(id, DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE, null);
        return id;
    }

    public boolean cancelServiceSearch(int transactionId) {
        return completeSearch(transactionId, DiscoveryListener.SERVICE_SEARCH_TERMINATED, null);
    }

    public static void deviceFound(String address, String name, int classOfDevice) {
        DiscoveryListener listener = activeListener;
        if (listener == null || address == null || address.length() == 0) return;
        RemoteDevice device = new RemoteDevice(address, name);
        synchronized (CACHED_DEVICES) {
            if (!CACHED_DEVICES.contains(device)) CACHED_DEVICES.addElement(device);
        }
        listener.deviceDiscovered(device, new DeviceClass(classOfDevice));
    }

    public static void inquiryFinished(int result) {
        DiscoveryListener listener = activeListener;
        activeListener = null;
        inquiryActive = false;
        if (listener != null) listener.inquiryCompleted(result);
    }

    public static void servicesFound(String address, String uuidList) {
        Vector<ServiceSearch> matches = new Vector<ServiceSearch>();
        synchronized (SERVICE_SEARCHES) {
            for (ServiceSearch search : SERVICE_SEARCHES.values())
                if (search.device.getBluetoothAddress().equalsIgnoreCase(address))
                    matches.addElement(search);
        }
        for (ServiceSearch search : matches) {
            Vector<ServiceRecord> records = new Vector<ServiceRecord>();
            if (uuidList != null && !uuidList.isEmpty()) {
                String[] found = uuidList.split(",");
                for (String value : found) {
                    try {
                        UUID candidate = new UUID(value, false);
                        for (UUID requested : search.requested) {
                            if (candidate.equals(requested)) {
                                records.addElement(new J2MEServiceRecord(search.device, candidate, false));
                                records.addElement(new J2MEServiceRecord(search.device, candidate, true));
                                break;
                            }
                        }
                    } catch (IllegalArgumentException ignored) { }
                }
            }
            completeSearch(search.id,
                    records.isEmpty() ? DiscoveryListener.SERVICE_SEARCH_NO_RECORDS
                            : DiscoveryListener.SERVICE_SEARCH_COMPLETED,
                    records);
        }
    }

    private static boolean completeSearch(int id, int result, Vector<ServiceRecord> records) {
        ServiceSearch search;
        synchronized (SERVICE_SEARCHES) { search = SERVICE_SEARCHES.remove(Integer.valueOf(id)); }
        if (search == null) return false;
        if (records != null && !records.isEmpty()) {
            ServiceRecord[] resultRecords = new ServiceRecord[records.size()];
            records.copyInto(resultRecords);
            search.listener.servicesDiscovered(id, resultRecords);
        }
        search.listener.serviceSearchCompleted(id, result);
        return true;
    }
}
