package org.recompile.freej2me.lite.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Small software note/tone synthesizer for MMAPI MIDIControl and playTone. */
final class MidiToneSynth {
    private static final String TAG = "FreeJ2MEAudio";
    private static final int RATE = 22050;
    private final Map<Long, Voice> voices = new HashMap<>();
    private final Map<Long, Float> channelVolumes = new HashMap<>();
    private final Map<Integer, Float> ownerVolumes = new HashMap<>();
    private AudioTrack track;
    private Thread worker;
    private volatile boolean stopping;

    synchronized void noteOn(int owner, int channel, int note, int velocity) {
        if (note < 0 || note > 127 || velocity <= 0) return;
        long key = key(owner, channel, note);
        Voice voice = voices.get(key);
        if (voice == null) {
            voice = new Voice(note);
            voices.put(key, voice);
        }
        voice.velocity = Math.min(127, velocity) / 127.0;
        voice.releasing = false;
        if (worker == null || !worker.isAlive()) startWorker();
    }

    synchronized void noteOff(int owner, int channel, int note) {
        Voice voice = voices.get(key(owner, channel, note));
        if (voice != null) voice.releasing = true;
    }

    synchronized void setChannelVolume(int owner, int channel, int volume) {
        channelVolumes.put(channelKey(owner, channel), Math.max(0, Math.min(127, volume)) / 127.0f);
    }

    synchronized void setOwnerVolume(int owner, float volume) {
        ownerVolumes.put(owner, Math.max(0.0f, Math.min(1.0f, volume)));
    }

    synchronized void close() {
        stopping = true;
        voices.clear();
    }

    synchronized void stopOwner(int owner) {
        Iterator<Map.Entry<Long, Voice>> iterator = voices.entrySet().iterator();
        while (iterator.hasNext()) {
            if ((int)(iterator.next().getKey() >> 11) == owner) iterator.remove();
        }
        ownerVolumes.remove(owner);
        Iterator<Long> channels = channelVolumes.keySet().iterator();
        while (channels.hasNext()) {
            if ((int)(channels.next().longValue() >> 4) == owner) channels.remove();
        }
    }

    synchronized void allNotesOff(int owner, int channel) {
        long ownerChannel = channelKey(owner, channel);
        for (Map.Entry<Long, Voice> entry : voices.entrySet()) {
            if ((entry.getKey() >> 7) == ownerChannel) entry.getValue().releasing = true;
        }
    }

    private void startWorker() {
        stopping = false;
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int buffer = Math.max(RATE / 5, min > 0 ? min * 2 : RATE / 5);
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    buffer, AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                track = null;
                return;
            }
            track.play();
            worker = new Thread(this::render, "FreeJ2ME-MidiTone");
            worker.setPriority(Thread.NORM_PRIORITY + 2);
            worker.start();
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot start MIDI tone output", error);
            if (track != null) track.release();
            track = null;
            worker = null;
        }
    }

    private void render() {
        short[] output = new short[512];
        AudioTrack outputTrack;
        synchronized (this) { outputTrack = track; }
        int silentBlocks = 0;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            while (!stopping && outputTrack != null) {
                int active;
                synchronized (this) {
                    active = renderBlock(output);
                }
                if (active == 0) {
                    if (++silentBlocks > 24) break;
                } else {
                    silentBlocks = 0;
                }
                int written = outputTrack.write(output, 0, output.length);
                if (written < 0) break;
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "MIDI tone output stopped", error);
        } finally {
            synchronized (this) {
                if (track == outputTrack) {
                    track = null;
                    worker = null;
                }
            }
            try { outputTrack.stop(); } catch (RuntimeException ignored) { }
            outputTrack.release();
        }
    }

    private int renderBlock(short[] output) {
        int active = 0;
        for (int frame = 0; frame < output.length; frame++) {
            double sample = 0.0;
            int count = voices.size();
            Iterator<Map.Entry<Long, Voice>> iterator = voices.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Voice> entry = iterator.next();
                Voice voice = entry.getValue();
                if (voice.releasing) voice.level = Math.max(0.0, voice.level - 0.004);
                else voice.level = Math.min(1.0, voice.level + 0.004);
                if (voice.releasing && voice.level < 0.002) {
                    iterator.remove();
                    continue;
                }
                Float selectedVolume = channelVolumes.get(
                        channelKey((int)(entry.getKey() >> 11), (int)(entry.getKey() >> 7 & 15)));
                float channelVolume = selectedVolume == null ? 1.0f : selectedVolume.floatValue();
                Float selectedOwnerVolume = ownerVolumes.get((int)(entry.getKey() >> 11));
                float ownerVolume = selectedOwnerVolume == null ? 1.0f : selectedOwnerVolume.floatValue();
                sample += Math.sin(voice.phase) * voice.level * voice.velocity * channelVolume * ownerVolume;
                voice.phase += voice.increment;
                if (voice.phase > Math.PI * 2.0) voice.phase -= Math.PI * 2.0;
            }
            if (count > 0) active++;
            output[frame] = (short)(Math.max(-1.0, Math.min(1.0, sample * 0.18)) * 32767);
        }
        return active;
    }

    private static long channelKey(int owner, int channel) {
        return (((long)owner) << 4) | (channel & 15);
    }

    private static long key(int owner, int channel, int note) {
        return ((((long)owner) << 4) | (channel & 15)) << 7 | (note & 127);
    }

    private static final class Voice {
        final double increment;
        double phase;
        double level;
        double velocity;
        boolean releasing;
        Voice(int note) {
            increment = 2.0 * Math.PI * 440.0 * Math.pow(2.0, (note - 69) / 12.0) / RATE;
        }
    }
}
