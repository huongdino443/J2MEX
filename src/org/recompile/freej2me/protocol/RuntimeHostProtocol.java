package org.recompile.freej2me.protocol;

/** Android-to-runtime control messages that are not game input or audio events. */
public final class RuntimeHostProtocol {
    public static final int SET_RENDERING_ENABLED = 20;
    public static final int REQUEST_HOTSPOT_MEMORY = 21;
    public static final int HOTSPOT_MEMORY_RESPONSE = 11;
    public static final int VIBRATION_REQUEST = 12;
    public static final int EXTERNAL_URL_REQUEST = 22;

    private RuntimeHostProtocol() { }
}
