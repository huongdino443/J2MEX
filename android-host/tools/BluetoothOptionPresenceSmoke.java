public final class BluetoothOptionPresenceSmoke {
    public static void main(String[] args) throws Exception {
        if (org.recompile.freej2me.protocol.BluetoothProtocol.HOST_REQUEST
                == org.recompile.freej2me.protocol.BluetoothProtocol.MEMORY_EDITOR_REPLY) {
            throw new AssertionError("Bluetooth requests must not share the memory-reply message id");
        }
        if (org.recompile.freej2me.protocol.BluetoothProtocol.ENGINE_RESPONSE <= 16
                || org.recompile.freej2me.protocol.BluetoothProtocol.ENGINE_EVENT <= 16
                || org.recompile.freej2me.protocol.BluetoothProtocol.ENGINE_RESPONSE
                        == org.recompile.freej2me.protocol.BluetoothProtocol.ENGINE_EVENT) {
            throw new AssertionError("Bluetooth response/event ids must not overlap runtime commands 1–16");
        }
        Class<?> discoveryAgent = Class.forName("javax.bluetooth.DiscoveryAgent");
        Class<?> discoveryListener = Class.forName("javax.bluetooth.DiscoveryListener");
        if (discoveryAgent.isInterface()) {
            throw new AssertionError("DiscoveryAgent must be a loadable class");
        }
        if (!discoveryListener.isInterface()) {
            throw new AssertionError("DiscoveryListener must be an interface");
        }
        if (discoveryAgent.getField("GIAC").getInt(null) != 0x9E8B33) {
            throw new AssertionError("Bluetooth capability constants are missing");
        }

        org.recompile.mobile.BluetoothBridge.setTransport((id, operation, flags, number, value, extra, data) ->
                org.recompile.mobile.BluetoothBridge.complete(id,
                        operation != org.recompile.freej2me.protocol.BluetoothProtocol.START_INQUIRY,
                        0, "simulated permission denial", new byte[0]));
        javax.bluetooth.LocalDevice local = javax.bluetooth.LocalDevice.getLocalDevice();
        final int[] completion = {-1};
        boolean started = local.getDiscoveryAgent().startInquiry(
                javax.bluetooth.DiscoveryAgent.GIAC, new javax.bluetooth.DiscoveryListener() {
                    public void deviceDiscovered(javax.bluetooth.RemoteDevice device, javax.bluetooth.DeviceClass type) { }
                    public void servicesDiscovered(int id, javax.bluetooth.ServiceRecord[] records) { }
                    public void serviceSearchCompleted(int id, int code) { }
                    public void inquiryCompleted(int type) { completion[0] = type; }
                });
        if (started || completion[0] != javax.bluetooth.DiscoveryListener.INQUIRY_ERROR) {
            throw new AssertionError("Denied Bluetooth access must return without leaving inquiry pending");
        }
        System.out.println("BluetoothOptionPresenceSmoke: JSR-82 classes load and denied access completes safely");
    }
}
