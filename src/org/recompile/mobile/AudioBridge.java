/*
This file is part of FreeJ2ME.

FreeJ2ME is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

FreeJ2ME is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package org.recompile.mobile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.recompile.freej2me.protocol.AudioCachePaths;
import org.recompile.freej2me.protocol.AudioProtocol;

/** Small transport boundary between MMAPI and the native Android audio host. */
public final class AudioBridge {
    public static final int CREATE_STREAM = AudioProtocol.CREATE_STREAM;
    public static final int CREATE_LOCATOR = AudioProtocol.CREATE_LOCATOR;
    public static final int START = AudioProtocol.START;
    public static final int STOP = AudioProtocol.STOP;
    public static final int RELEASE = AudioProtocol.RELEASE;
    public static final int SEEK = AudioProtocol.SEEK;
    public static final int LOOP = AudioProtocol.LOOP;
    public static final int VOLUME = AudioProtocol.VOLUME;
    public static final int MIDI_EVENT = AudioProtocol.MIDI_EVENT;
    public static final int TONE = AudioProtocol.TONE;
    public static final int MIDI_CHANNEL_VOLUME = AudioProtocol.MIDI_CHANNEL_VOLUME;
    public static final int TONE_SEQUENCE = AudioProtocol.TONE_SEQUENCE;

    public static final int PREPARED = AudioProtocol.PREPARED;
    public static final int STARTED = AudioProtocol.STARTED;
    public static final int STOPPED = AudioProtocol.STOPPED;
    public static final int COMPLETED = AudioProtocol.COMPLETED;
    public static final int ERROR = AudioProtocol.ERROR;
    public static final int SEEK_COMPLETE = AudioProtocol.SEEK_COMPLETE;

    private static final long MAX_AUDIO_BYTES = 32L * 1024L * 1024L;
    private static final AtomicInteger nextId = new AtomicInteger(1);
    private static final AtomicInteger nextSequence = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Listener> listeners =
            new ConcurrentHashMap<Integer, Listener>();
    private static final ConcurrentHashMap<Integer, PlayerSource> sources =
            new ConcurrentHashMap<Integer, PlayerSource>();
    private static volatile Transport transport;
    private static volatile File cacheDirectory;

    private AudioBridge() { }

    public interface Listener {
        void onAudioEvent(int event, long value, String detail);
    }

    public interface Transport {
        void send(int action, int playerId, String text, String extra,
                long value, int number, int flags) throws IOException;
    }

    public static void configure(File directory, Transport host) throws IOException {
        if (directory == null || host == null) throw new IOException("Android audio host is unavailable");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create audio cache");
        if (!directory.isDirectory() || !directory.canWrite()) throw new IOException("Audio cache is not writable");
        cacheDirectory = directory.getCanonicalFile();
        transport = host;
    }

    public static boolean isAvailable() {
        return transport != null && cacheDirectory != null;
    }

    public static int createPlayer(InputStream input, String contentType, Listener listener) throws IOException {
        if (input == null) throw new IOException("Audio stream is null");
        int id = allocateId();
        File directory = cacheDirectory;
        Transport host = transport;
        if (directory == null || host == null) throw new IOException("Android audio host is unavailable");

        String extension = extensionFor(contentType);
        File partial = new File(directory, "player-" + id + extension + ".part");
        File complete = new File(directory, "player-" + id + extension);
        long total = 0;
        try (InputStream source = new BufferedInputStream(input);
             BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(partial))) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > MAX_AUDIO_BYTES) throw new IOException("Audio resource exceeds 32 MiB");
                output.write(buffer, 0, count);
            }
        } catch (IOException error) {
            partial.delete();
            throw error;
        }
        if (total == 0) {
            partial.delete();
            throw new IOException("Audio resource is empty");
        }
        if (!partial.renameTo(complete)) {
            partial.delete();
            throw new IOException("Cannot finalize temporary audio resource");
        }

        sources.put(id, new PlayerSource(false, contentType == null ? "" : contentType,
                complete.getCanonicalPath()));
        if (listener != null) listeners.put(id, listener);
        try {
            host.send(CREATE_STREAM, id, contentType == null ? "" : contentType,
                    complete.getCanonicalPath(), 0L, 0, 0);
        } catch (IOException error) {
            listeners.remove(id);
            sources.remove(id);
            complete.delete();
            throw error;
        }
        return id;
    }

    public static int createPlayer(String locator, Listener listener) throws IOException {
        if (locator == null || locator.length() == 0) throw new IOException("Audio locator is empty");
        Transport host = transport;
        if (host == null) throw new IOException("Android audio host is unavailable");
        int id = allocateId();
        sources.put(id, new PlayerSource(true, locator, ""));
        if (listener != null) listeners.put(id, listener);
        try {
            host.send(CREATE_LOCATOR, id, locator, "", 0L, 0, 0);
        } catch (IOException error) {
            listeners.remove(id);
            sources.remove(id);
            throw error;
        }
        return id;
    }

    /** Recreates a released Android decoder while retaining this MIDlet Player. */
    public static void recreatePlayer(int playerId, Listener listener) throws IOException {
        PlayerSource source = sources.get(playerId);
        Transport host = transport;
        if (source == null || host == null) throw new IOException("Audio source is no longer available");
        if (!source.locator) {
            File directory = cacheDirectory;
            File file = new File(source.path).getCanonicalFile();
            if (directory == null || !AudioCachePaths.isPrivateFile(directory, file) || !file.isFile())
                throw new IOException("Cached audio source is unavailable");
        }
        if (listener != null) listeners.put(playerId, listener);
        host.send(source.locator ? CREATE_LOCATOR : CREATE_STREAM, playerId,
                source.typeOrLocator, source.path, 0L, 0, 0);
    }

    private static final class PlayerSource {
        final boolean locator;
        final String typeOrLocator;
        final String path;
        PlayerSource(boolean locator, String typeOrLocator, String path) {
            this.locator = locator;
            this.typeOrLocator = typeOrLocator;
            this.path = path;
        }
    }

    private static int allocateId() {
        int id = nextId.getAndIncrement();
        if (id <= 0) {
            nextId.compareAndSet(id + 1, 1);
            id = nextId.getAndIncrement();
        }
        return id;
    }

    private static String extensionFor(String type) {
        if (type == null) return ".media";
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("midi") || normalized.contains("sp-midi")) return ".mid";
        if (normalized.contains("wav")) return ".wav";
        if (normalized.contains("amr")) return ".amr";
        if (normalized.contains("mpeg") || normalized.contains("mp3")) return ".mp3";
        if (normalized.contains("3gpp")) return ".3gp";
        if (normalized.contains("mp4")) return ".m4a";
        if (normalized.contains("ogg")) return ".ogg";
        return ".media";
    }

    public static void command(int action, int playerId) {
        send(action, playerId, "", "", 0L, 0, 0);
    }

    public static void seek(int playerId, long microseconds) {
        send(SEEK, playerId, "", "", microseconds, 0, 0);
    }

    public static void loop(int playerId, int count) {
        send(LOOP, playerId, "", "", 0L, count, 0);
    }

    public static void volume(int playerId, int level, boolean muted) {
        send(VOLUME, playerId, "", "", 0L, level, muted ? 1 : 0);
    }

    public static void midiEvent(int playerId, int status, int data1, int data2) {
        send(MIDI_EVENT, playerId, "", "", 0L, status, (data1 & 0xffff) | ((data2 & 0xffff) << 16));
    }

    public static void midiChannelVolume(int playerId, int channel, int volume) {
        send(MIDI_CHANNEL_VOLUME, playerId, "", "", 0L, channel, volume);
    }

    public static void playTone(int note, int duration, int volume) {
        send(TONE, 0, "", "", note, duration, volume);
    }

    public static void toneSequence(int playerId, byte[] sequence) {
        if (sequence == null || sequence.length == 0 || sequence.length > 1024 * 1024) {
            System.err.println("Ignoring invalid MMAPI tone sequence");
            return;
        }
        File directory = cacheDirectory;
        Transport host = transport;
        if (directory == null || host == null) return;
        int version = nextSequence.getAndIncrement();
        File partial = new File(directory, "player-" + playerId + "-tone-" + version + ".part");
        File complete = new File(directory, "player-" + playerId + "-tone-" + version + ".seq");
        try {
            try (FileOutputStream output = new FileOutputStream(partial)) {
                output.write(sequence);
            }
            if (!partial.renameTo(complete)) throw new IOException("Cannot finalize tone sequence");
            host.send(TONE_SEQUENCE, playerId, "", complete.getCanonicalPath(), 0L, 0, 0);
        } catch (IOException error) {
            partial.delete();
            complete.delete();
            System.err.println("Cannot send MMAPI tone sequence: " + error);
        }
    }

    private static void send(int action, int playerId, String text, String extra,
            long value, int number, int flags) {
        Transport host = transport;
        if (host == null) return;
        try {
            host.send(action, playerId, text, extra, value, number, flags);
        } catch (IOException error) {
            System.err.println("Android audio command failed: " + error);
        }
    }

    public static void dispatchEvent(int event, int playerId, long value, String detail) {
        Listener listener = listeners.get(playerId);
        if (listener != null) listener.onAudioEvent(event, value, detail == null ? "" : detail);
    }

    public static void forget(int playerId) {
        listeners.remove(playerId);
        sources.remove(playerId);
    }

    public static void closeAll() {
        Set<Integer> ids = new HashSet<Integer>(listeners.keySet());
        ids.addAll(sources.keySet());
        for (Integer id : ids) command(RELEASE, id.intValue());
        listeners.clear();
        sources.clear();
    }
}
