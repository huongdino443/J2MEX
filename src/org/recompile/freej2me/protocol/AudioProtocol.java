package org.recompile.freej2me.protocol;

/** Stable audio messages shared by the Java 8 engine and Android host. */
public final class AudioProtocol {
    private AudioProtocol() { }

    public static final int CREATE_STREAM = 1;
    public static final int CREATE_LOCATOR = 2;
    public static final int START = 3;
    public static final int STOP = 4;
    public static final int RELEASE = 5;
    public static final int SEEK = 6;
    public static final int LOOP = 7;
    public static final int VOLUME = 8;
    public static final int MIDI_EVENT = 9;
    public static final int TONE = 10;
    public static final int MIDI_CHANNEL_VOLUME = 11;
    public static final int TONE_SEQUENCE = 12;

    public static final int PREPARED = 1;
    public static final int STARTED = 2;
    public static final int STOPPED = 3;
    public static final int COMPLETED = 4;
    public static final int ERROR = 5;
    public static final int SEEK_COMPLETE = 6;
}
