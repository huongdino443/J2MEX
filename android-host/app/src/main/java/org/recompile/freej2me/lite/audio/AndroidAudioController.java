package org.recompile.freej2me.lite.audio;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.recompile.freej2me.protocol.AudioCachePaths;
import org.recompile.freej2me.protocol.AudioProtocol;

/** Owns Android decoders and serializes their lifecycle away from the UI thread. */
public final class AndroidAudioController {
    private static final String TAG = "FreeJ2MEAudio";
    private static final int TONE_OWNER = Integer.MIN_VALUE;
    /** Local aliases keep the engine API off Android's application classpath. */
    private static final class AudioBridge {
        static final int CREATE_STREAM = AudioProtocol.CREATE_STREAM;
        static final int CREATE_LOCATOR = AudioProtocol.CREATE_LOCATOR;
        static final int START = AudioProtocol.START;
        static final int STOP = AudioProtocol.STOP;
        static final int RELEASE = AudioProtocol.RELEASE;
        static final int SEEK = AudioProtocol.SEEK;
        static final int LOOP = AudioProtocol.LOOP;
        static final int VOLUME = AudioProtocol.VOLUME;
        static final int MIDI_EVENT = AudioProtocol.MIDI_EVENT;
        static final int TONE = AudioProtocol.TONE;
        static final int MIDI_CHANNEL_VOLUME = AudioProtocol.MIDI_CHANNEL_VOLUME;
        static final int TONE_SEQUENCE = AudioProtocol.TONE_SEQUENCE;
        static final int PREPARED = AudioProtocol.PREPARED;
        static final int STARTED = AudioProtocol.STARTED;
        static final int STOPPED = AudioProtocol.STOPPED;
        static final int COMPLETED = AudioProtocol.COMPLETED;
        static final int ERROR = AudioProtocol.ERROR;
        static final int SEEK_COMPLETE = AudioProtocol.SEEK_COMPLETE;
    }

    public interface EventSink {
        void send(int event, int playerId, long value, String detail);
    }

    private final File cacheDirectory;
    private final EventSink events;
    private final HandlerThread audioThread;
    private final Handler handler;
    private final HashMap<Integer, Entry> players = new HashMap<>();
    private final MidiToneSynth synthesizer = new MidiToneSynth();

    public AndroidAudioController(File cacheDirectory, EventSink events) {
        if (cacheDirectory == null || events == null) throw new IllegalArgumentException("Audio host configuration is missing");
        this.cacheDirectory = cacheDirectory.getAbsoluteFile();
        this.events = events;
        audioThread = new HandlerThread("FreeJ2ME-Audio");
        audioThread.start();
        handler = new Handler(audioThread.getLooper());
    }

    public void handle(int action, int playerId, String text, String extra,
            long value, int number, int flags) {
        handler.post(() -> {
            try {
                handleOnAudioThread(action, playerId, text, extra, value, number, flags);
            } catch (RuntimeException error) {
                Log.e(TAG, "Audio action failed: " + action, error);
                send(AudioBridge.ERROR, playerId, 0L, error.toString());
            }
        });
    }

    private void handleOnAudioThread(int action, int id, String text, String extra,
            long value, int number, int flags) {
        switch (action) {
            case AudioBridge.CREATE_STREAM:
                createStream(id, text, extra);
                break;
            case AudioBridge.CREATE_LOCATOR:
                createLocator(id, text);
                break;
            case AudioBridge.START:
                start(id);
                break;
            case AudioBridge.STOP:
                stop(id);
                break;
            case AudioBridge.RELEASE:
                release(id);
                break;
            case AudioBridge.SEEK:
                seek(id, value);
                break;
            case AudioBridge.LOOP:
                setLoopCount(id, number);
                break;
            case AudioBridge.VOLUME:
                setVolume(id, number, flags != 0);
                break;
            case AudioBridge.MIDI_EVENT:
                midiEvent(id, number, flags & 0xffff, (flags >>> 16) & 0xffff);
                break;
            case AudioBridge.TONE:
                playTone((int)value, number, flags);
                break;
            case AudioBridge.MIDI_CHANNEL_VOLUME:
                synthesizer.setChannelVolume(id, number, flags);
                break;
            case AudioBridge.TONE_SEQUENCE:
                setToneSequence(id, extra);
                break;
            default:
                Log.w(TAG, "Ignoring unknown audio action " + action);
        }
    }

    private void createStream(int id, String type, String path) {
        File file;
        try {
            file = new File(path).getCanonicalFile();
            if (!AudioCachePaths.isPrivateFile(cacheDirectory, file) || !file.isFile())
                throw new IOException("Audio resource is outside the private cache");
        } catch (IOException error) {
            send(AudioBridge.ERROR, id, 0L, error.toString());
            return;
        }
        createMediaPlayer(id, file.getPath(), false);
    }

    private void createLocator(int id, String locator) {
        if ("device://tone".equalsIgnoreCase(locator)) {
            Entry entry = new Entry(id, null);
            entry.toneDevice = true;
            entry.prepared = true;
            players.put(id, entry);
            send(AudioBridge.PREPARED, id, -1L, "");
            return;
        }
        try {
            String scheme = new URI(locator).getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                throw new IOException("Android audio locators support HTTP and HTTPS");
        } catch (Exception error) {
            send(AudioBridge.ERROR, id, 0L, "Invalid audio locator: " + error);
            return;
        }
        createMediaPlayer(id, locator, true);
    }

    private void createMediaPlayer(int id, String source, boolean locator) {
        release(id);
        MediaPlayer media = new MediaPlayer();
        Entry entry = new Entry(id, media);
        players.put(id, entry);
        try {
            media.setAudioStreamType(AudioManager.STREAM_MUSIC);
            media.setOnPreparedListener(player -> {
                Entry current = players.get(id);
                if (current != entry) return;
                entry.prepared = true;
                long duration = -1L;
                try {
                    int milliseconds = player.getDuration();
                    if (milliseconds >= 0) duration = milliseconds * 1000L;
                } catch (RuntimeException ignored) { }
                send(AudioBridge.PREPARED, id, duration, "");
                applyVolume(entry);
                try { player.setLooping(entry.loopCount == -1); }
                catch (RuntimeException error) { Log.w(TAG, "Cannot configure media looping", error); }
                if (entry.pendingSeek >= 0L) {
                    player.seekTo((int)Math.min(Integer.MAX_VALUE, entry.pendingSeek / 1000L));
                    entry.pendingSeek = -1L;
                }
                if (entry.startRequested) startPrepared(entry);
            });
            media.setOnCompletionListener(player -> onCompletion(entry));
            media.setOnSeekCompleteListener(player -> {
                if (players.get(id) != entry) return;
                long position = 0L;
                try { position = Math.max(0, player.getCurrentPosition()) * 1000L; }
                catch (RuntimeException ignored) { }
                send(AudioBridge.SEEK_COMPLETE, id, position, "");
            });
            media.setOnErrorListener((player, what, extra) -> {
                if (players.get(id) == entry) {
                    send(AudioBridge.ERROR, id, 0L, "Android media decoder error " + what + "/" + extra);
                    release(id);
                }
                return true;
            });
            if (locator) media.setDataSource(source);
            else media.setDataSource(source);
            media.prepareAsync();
        } catch (Exception error) {
            Log.e(TAG, "Cannot prepare " + (locator ? "locator" : "resource"), error);
            send(AudioBridge.ERROR, id, 0L, "Cannot prepare media: " + error);
            release(id);
        }
    }

    private void start(int id) {
        Entry entry = players.get(id);
        if (entry == null) {
            send(AudioBridge.ERROR, id, 0L, "Audio player is not available");
            return;
        }
        if (entry.playing || entry.startRequested) return;
        entry.startRequested = true;
        if (entry.prepared) {
            if (entry.completed && entry.media != null) {
                try {
                    entry.media.seekTo(0);
                    entry.completed = false;
                } catch (RuntimeException error) {
                    entry.startRequested = false;
                    send(AudioBridge.ERROR, id, 0L, "Cannot rewind completed media: " + error);
                    return;
                }
            }
            startPrepared(entry);
        }
    }

    private void startPrepared(Entry entry) {
        if (players.get(entry.id) != entry || !entry.startRequested) return;
        try {
            if (entry.media != null) {
                entry.media.start();
                entry.playing = true;
                entry.startRequested = false;
                send(AudioBridge.STARTED, entry.id, getPosition(entry), "");
            } else if (entry.toneDevice) {
                entry.playing = true;
                entry.startRequested = false;
                send(AudioBridge.STARTED, entry.id, 0L, "");
                if (entry.sequence != null) playToneSequence(entry);
            }
        } catch (RuntimeException error) {
            entry.startRequested = false;
            send(AudioBridge.ERROR, entry.id, 0L, "Cannot start media: " + error);
        }
    }

    private void stop(int id) {
        Entry entry = players.get(id);
        if (entry == null) return;
        entry.startRequested = false;
        boolean wasPlaying = entry.playing;
        if (entry.media != null && entry.prepared) {
            try {
                if (wasPlaying) entry.media.pause();
            } catch (RuntimeException error) {
                Log.w(TAG, "Media pause failed", error);
            }
        }
        long stoppedPosition = getPosition(entry);
        entry.playing = false;
        entry.completed = false;
        synthesizer.stopOwner(id);
        if (wasPlaying) send(AudioBridge.STOPPED, id, stoppedPosition, "");
    }

    private void seek(int id, long microseconds) {
        Entry entry = players.get(id);
        if (entry == null || entry.toneDevice) return;
        long position = Math.max(0L, microseconds);
        if (!entry.prepared) {
            entry.pendingSeek = position;
            return;
        }
        try {
            entry.media.seekTo((int)Math.min(Integer.MAX_VALUE, position / 1000L));
        } catch (RuntimeException error) {
            send(AudioBridge.ERROR, id, 0L, "Cannot seek media: " + error);
        }
    }

    private void setLoopCount(int id, int count) {
        Entry entry = players.get(id);
        if (entry == null) return;
        entry.loopCount = count < 0 ? -1 : Math.max(1, count);
        entry.loopsRemaining = entry.loopCount;
        if (entry.media != null && entry.prepared) {
            try { entry.media.setLooping(entry.loopCount == -1); }
            catch (RuntimeException error) { Log.w(TAG, "Cannot set infinite looping", error); }
        }
    }

    private void setVolume(int id, int level, boolean muted) {
        Entry entry = players.get(id);
        synthesizer.setOwnerVolume(id, muted ? 0.0f : Math.max(0, Math.min(100, level)) / 100.0f);
        if (entry == null) return;
        entry.volume = Math.max(0, Math.min(100, level));
        entry.muted = muted;
        applyVolume(entry);
    }

    private void applyVolume(Entry entry) {
        if (entry.media == null) return;
        float level = entry.muted ? 0.0f : entry.volume / 100.0f;
        try { entry.media.setVolume(level, level); }
        catch (RuntimeException error) { Log.w(TAG, "Cannot set media volume", error); }
    }

    private void onCompletion(Entry entry) {
        if (players.get(entry.id) != entry) return;
        entry.playing = false;
        if (entry.loopCount == -1) {
            entry.startRequested = true;
            startPrepared(entry);
            return;
        }
        if (entry.loopsRemaining > 1) {
            entry.loopsRemaining--;
            try {
                entry.media.seekTo(0);
                entry.completed = false;
                entry.startRequested = true;
                entry.media.start();
                entry.playing = true;
                entry.startRequested = false;
                send(AudioBridge.STARTED, entry.id, 0L, "");
                return;
            } catch (RuntimeException error) {
                send(AudioBridge.ERROR, entry.id, 0L, "Cannot restart looping media: " + error);
                return;
            }
        }
        entry.startRequested = false;
        long completedPosition = getPosition(entry);
        entry.completed = true;
        try { entry.media.seekTo(0); }
        catch (RuntimeException error) { Log.w(TAG, "Cannot rewind completed media", error); }
        send(AudioBridge.COMPLETED, entry.id, completedPosition, "");
    }

    private long getPosition(Entry entry) {
        if (entry.media == null || !entry.prepared) return 0L;
        try { return Math.max(0, entry.media.getCurrentPosition()) * 1000L; }
        catch (RuntimeException ignored) { return 0L; }
    }

    private void midiEvent(int id, int status, int data1, int data2) {
        int command = status & 0xf0;
        int channel = status & 15;
        if (command == 0x90 && data2 > 0) synthesizer.noteOn(id, channel, data1, data2);
        else if (command == 0x80 || command == 0x90) synthesizer.noteOff(id, channel, data1);
        else if (command == 0xb0 && (data1 == 120 || data1 == 123))
            synthesizer.allNotesOff(id, channel);
        else if (command == 0xb0 && data1 == 7)
            synthesizer.setChannelVolume(id, channel, data2);
    }

    private void playTone(int note, int duration, int volume) {
        if (duration <= 0 || volume <= 0 || note < 0 || note > 127) return;
        long token = System.nanoTime() & 0x7fffffffL;
        int owner = (int)token;
        synthesizer.noteOn(owner, 15, note, Math.max(0, Math.min(127, volume * 127 / 100)));
        handler.postDelayed(() -> synthesizer.noteOff(owner, 15, note), Math.min(duration, 60000));
    }

    private void setToneSequence(int id, String path) {
        Entry entry = players.get(id);
        if (entry == null) {
            send(AudioBridge.ERROR, id, 0L, "ToneControl player is not available");
            return;
        }
        try {
            File sequenceFile = new File(path).getCanonicalFile();
            if (!AudioCachePaths.isPrivateFile(cacheDirectory, sequenceFile) || !sequenceFile.isFile()
                    || sequenceFile.length() < 1 || sequenceFile.length() > 1024L * 1024L)
                throw new IOException("Tone sequence is outside the private cache or too large");
            byte[] sequence = new byte[(int)sequenceFile.length()];
            try (java.io.FileInputStream input = new java.io.FileInputStream(sequenceFile)) {
                int offset = 0;
                while (offset < sequence.length) {
                    int count = input.read(sequence, offset, sequence.length - offset);
                    if (count < 0) throw new IOException("Incomplete tone sequence");
                    offset += count;
                }
            }
            entry.sequence = sequence;
            if (entry.playing) playToneSequence(entry);
        } catch (IOException error) {
            send(AudioBridge.ERROR, id, 0L, error.toString());
        }
    }

    private void playToneSequence(Entry entry) {
        final int token = ++entry.sequenceToken;
        final List<ToneEvent> sequence;
        try { sequence = parseToneSequence(entry.sequence); }
        catch (IOException error) {
            entry.playing = false;
            send(AudioBridge.ERROR, entry.id, 0L, "Invalid ToneControl sequence: " + error.getMessage());
            return;
        }
        long startAt = SystemClock.uptimeMillis();
        long endAt = startAt;
        for (ToneEvent tone : sequence) {
            long noteAt = startAt + tone.startMs;
            long offAt = noteAt + tone.durationMs;
            endAt = Math.max(endAt, offAt);
            handler.postAtTime(() -> {
                if (players.get(entry.id) == entry && entry.playing && entry.sequenceToken == token)
                    synthesizer.noteOn(entry.id, 15, tone.note, tone.volume * 127 / 100);
            }, noteAt);
            handler.postAtTime(() -> {
                if (players.get(entry.id) == entry && entry.playing && entry.sequenceToken == token)
                    synthesizer.noteOff(entry.id, 15, tone.note);
            }, offAt);
        }
        handler.postAtTime(() -> {
            if (players.get(entry.id) == entry && entry.playing && entry.sequenceToken == token)
                onToneSequenceComplete(entry);
        }, endAt);
    }

    private void onToneSequenceComplete(Entry entry) {
        if (entry.loopCount == -1 || entry.loopsRemaining > 1) {
            if (entry.loopsRemaining > 1) entry.loopsRemaining--;
            playToneSequence(entry);
            return;
        }
        entry.playing = false;
        send(AudioBridge.COMPLETED, entry.id, 0L, "");
    }

    private static List<ToneEvent> parseToneSequence(byte[] sequence) throws IOException {
        List<ToneEvent> events = new ArrayList<>();
        int tempo = 30, resolution = 64, volume = 100;
        long cursor = 0L;
        for (int index = 0; index < sequence.length;) {
            int item = sequence[index++];
            if (item >= 0) {
                if (index >= sequence.length) throw new IOException("Note duration is missing");
                int ticks = sequence[index++] & 0xff;
                long duration = Math.max(1L, (long)ticks * 60000L / ((long)tempo * resolution));
                events.add(new ToneEvent(cursor, duration, item, volume));
                cursor += duration;
                continue;
            }
            switch (item) {
                case -1: // SILENCE
                    if (index >= sequence.length) throw new IOException("Silence duration is missing");
                    cursor += Math.max(0L, (long)(sequence[index++] & 0xff) * 60000L /
                            ((long)tempo * resolution));
                    break;
                case -2: // VERSION
                    if (index >= sequence.length) throw new IOException("VERSION value is missing");
                    index++;
                    break;
                case -3: // TEMPO
                    if (index >= sequence.length) throw new IOException("TEMPO value is missing");
                    tempo = sequence[index++] & 0xff;
                    if (tempo == 0) throw new IOException("TEMPO must be positive");
                    break;
                case -4: // RESOLUTION
                    if (index >= sequence.length) throw new IOException("RESOLUTION value is missing");
                    resolution = sequence[index++] & 0xff;
                    if (resolution == 0) throw new IOException("RESOLUTION must be positive");
                    break;
                case -8: // SET_VOLUME
                    if (index >= sequence.length) throw new IOException("SET_VOLUME value is missing");
                    volume = Math.min(100, sequence[index++] & 0xff);
                    break;
                case -5: case -6: case -7: case -9:
                    throw new IOException("ToneControl block/repeat commands are not supported yet");
                default:
                    throw new IOException("Unknown command " + item);
            }
        }
        if (cursor > 10L * 60L * 1000L) throw new IOException("Tone sequence exceeds ten minutes");
        return events;
    }

    private void release(int id) {
        Entry entry = players.remove(id);
        if (entry == null) return;
        entry.playing = false;
        entry.startRequested = false;
        synthesizer.stopOwner(id);
        entry.sequenceToken++;
        if (entry.media != null) {
            entry.media.setOnPreparedListener(null);
            entry.media.setOnCompletionListener(null);
            entry.media.setOnSeekCompleteListener(null);
            entry.media.setOnErrorListener(null);
            try { entry.media.stop(); } catch (RuntimeException ignored) { }
            try { entry.media.release(); } catch (RuntimeException ignored) { }
        }
    }

    private void send(int event, int id, long value, String detail) {
        try { events.send(event, id, value, detail); }
        catch (RuntimeException error) { Log.e(TAG, "Cannot report media state", error); }
    }

    public void close() {
        handler.post(() -> {
            Integer[] ids = players.keySet().toArray(new Integer[0]);
            for (Integer id : ids) release(id.intValue());
            synthesizer.close();
        });
        audioThread.quitSafely();
    }

    private static final class Entry {
        final int id;
        final MediaPlayer media;
        boolean prepared;
        boolean playing;
        boolean startRequested;
        boolean toneDevice;
        boolean completed;
        int loopCount = 1;
        int loopsRemaining = 1;
        int volume = 100;
        boolean muted;
        long pendingSeek = -1L;
        byte[] sequence;
        int sequenceToken;
        Entry(int id, MediaPlayer media) { this.id = id; this.media = media; }
    }

    private static final class ToneEvent {
        final long startMs;
        final long durationMs;
        final int note;
        final int volume;
        ToneEvent(long startMs, long durationMs, int note, int volume) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.note = note;
            this.volume = volume;
        }
    }
}
