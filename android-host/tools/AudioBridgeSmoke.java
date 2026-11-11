import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.MIDIControl;
import javax.microedition.media.control.ToneControl;
import javax.microedition.media.control.VolumeControl;
import org.recompile.mobile.AudioBridge;
import org.recompile.mobile.MIDletLoader;
import org.recompile.freej2me.protocol.AudioCachePaths;

/** Exercises MMAPI-to-Android audio transport and asynchronous state transitions without device audio. */
public final class AudioBridgeSmoke {
    private static int checks;
    private static final List<Record> messages = new ArrayList<>();
    private static final List<String> events = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        new MIDletLoader(new URL[0]);
        require("true".equalsIgnoreCase(System.getProperty("supports.mixing")),
                "advertise Android MMAPI mixing capability to MIDlets");

        File root = new File("build/audio-bridge-smoke").getCanonicalFile();
        delete(root);
        require(root.mkdirs(), "create isolated audio cache");
        File candidate = new File(root, "canonical-path-test.mid");
        try (FileOutputStream output = new FileOutputStream(candidate)) { output.write(1); }
        File alias = new File(root.getParentFile(), root.getName() + "-alias");
        Files.createSymbolicLink(alias.toPath(), root.toPath());
        require(AudioCachePaths.isPrivateFile(alias, candidate),
                "accept canonical cache path when app and engine use different symlink aliases");
        File outside = new File(root.getParentFile(), "outside-audio-smoke");
        require(outside.mkdir(), "create outside-path test directory");
        File outsideFile = new File(outside, "outside.mid");
        try (FileOutputStream output = new FileOutputStream(outsideFile)) { output.write(1); }
        require(!AudioCachePaths.isPrivateFile(root, outsideFile), "reject files outside the private audio cache");
        Files.delete(alias.toPath());
        delete(outside);
        AudioBridge.configure(root, (action, id, text, extra, value, number, flags) ->
                messages.add(new Record(action, id, text, extra, value, number, flags)));

        byte[] wav = wave();
        Player player = Manager.createPlayer(new ByteArrayInputStream(wav), "audio/x-wav");
        require(player.getControl("VolumeControl") instanceof VolumeControl, "volume control available");
        require(player.getControl("MIDIControl") instanceof MIDIControl, "MIDI control available");
        require(player.getControl("ToneControl") instanceof ToneControl, "ToneControl available");
        VolumeControl volume = (VolumeControl)player.getControl("VolumeControl");
        volume.setLevel(37);
        volume.setMute(true);
        MIDIControl midi = (MIDIControl)player.getControl("MIDIControl");
        midi.shortMidiEvent(0x90, 60, 100);
        ToneControl tone = (ToneControl)player.getControl("ToneControl");
        tone.setSequence(new byte[]{-2, 1, -3, 30, -4, 64, -8, 85, 60, 64, -1, 32, 62, 64});
        player.addPlayerListener((source, event, data) -> events.add(event));
        player.realize();
        player.prefetch();
        player.setLoopCount(2);
        player.start();
        int id = lastAction(AudioBridge.CREATE_STREAM).id;
        Record create = lastAction(AudioBridge.CREATE_STREAM);
        require(new File(create.extra).isFile(), "audio stream staged for Android decoder");
        require(java.util.Arrays.equals(wav, read(new File(create.extra))), "audio bytes preserved");
        require(lastAction(AudioBridge.TONE_SEQUENCE) != null, "ToneControl sequence forwarded");
        require(lastAction(AudioBridge.MIDI_EVENT) != null, "MIDI note event forwarded");
        require(lastAction(AudioBridge.TONE) == null, "playTone is separately addressable");
        AudioBridge.playTone(72, 120, 80);
        require(lastAction(AudioBridge.TONE) != null, "playTone forwarded");
        require(lastAction(AudioBridge.LOOP).number == 2, "loop count forwarded");
        require(lastAction(AudioBridge.VOLUME).number == 37 && lastAction(AudioBridge.VOLUME).flags == 1,
                "volume level and mute forwarded");

        AudioBridge.dispatchEvent(AudioBridge.PREPARED, id, 2500000L, "");
        AudioBridge.dispatchEvent(AudioBridge.STARTED, id, 0L, "");
        require(player.getState() == Player.STARTED, "prepared player enters started state");
        require(player.getDuration() == 2500000L, "duration event reaches MIDP player");
        player.setMediaTime(400000L);
        AudioBridge.dispatchEvent(AudioBridge.SEEK_COMPLETE, id, 400000L, "");
        require(player.getMediaTime() >= 400000L, "seek position reaches MIDP player");
        int seeksBeforePause = countAction(AudioBridge.SEEK, id);
        player.stop();
        require(lastAction(AudioBridge.STOP).id == id, "pause command reaches Android audio host");
        require(player.getMediaTime() >= 400000L, "stop preserves the current media position");
        AudioBridge.dispatchEvent(AudioBridge.STOPPED, id, 450000L, "");
        require(player.getState() == Player.PREFETCHED, "stop returns to prefetched state");
        require(player.getMediaTime() == 450000L, "stop event records the paused position");
        player.start();
        require(lastAction(AudioBridge.START).id == id, "resume starts the same MMAPI Player");
        require(countAction(AudioBridge.SEEK, id) == seeksBeforePause,
                "resume does not rewind or issue a zero-position seek");
        AudioBridge.dispatchEvent(AudioBridge.STARTED, id, 450000L, "");
        require(player.getMediaTime() >= 450000L, "resume continues from the paused position");
        AudioBridge.dispatchEvent(AudioBridge.COMPLETED, id, 2500000L, "");
        require(events.contains(PlayerListener.STARTED) && events.contains(PlayerListener.STOPPED)
                && events.contains(PlayerListener.END_OF_MEDIA), "asynchronous player events delivered");

        player.deallocate();
        require(player.getState() == Player.UNREALIZED, "deallocate returns remote player to unrealized state");
        require(lastAction(AudioBridge.RELEASE).id == id, "deallocate releases Android decoder");
        require(new File(create.extra).isFile(), "deallocate retains source needed to realize again");
        player.realize();
        player.prefetch();
        player.start();
        Record recreated = lastAction(AudioBridge.CREATE_STREAM);
        require(recreated.id == id && recreated.extra.equals(create.extra),
                "reuse recreates the same player from its cached source");
        require(lastAction(AudioBridge.START).id == id, "reused player starts after decoder recreation");
        AudioBridge.dispatchEvent(AudioBridge.PREPARED, id, 2500000L, "");
        AudioBridge.dispatchEvent(AudioBridge.STARTED, id, 0L, "");
        require(player.getState() == Player.STARTED, "recreated player returns to started state");

        int priorStopCount = countAction(AudioBridge.STOP, id);
        Player concurrentPlayer = Manager.createPlayer(new ByteArrayInputStream(wav), "audio/x-wav");
        concurrentPlayer.realize();
        concurrentPlayer.prefetch();
        concurrentPlayer.start();
        int concurrentId = lastAction(AudioBridge.CREATE_STREAM).id;
        require(concurrentId != id, "concurrent MMAPI Players receive separate host IDs");
        require(lastAction(AudioBridge.START).id == concurrentId,
                "second MMAPI Player starts independently");
        require(countAction(AudioBridge.STOP, id) == priorStopCount,
                "starting a sound effect does not stop the background Player");
        concurrentPlayer.close();

        player.close();
        require(lastAction(AudioBridge.RELEASE) != null, "close releases Android decoder");

        Player locatorPlayer = Manager.createPlayer("https://audio.invalid/smoke-track.mp3");
        require(locatorPlayer.getControl("VolumeControl") instanceof VolumeControl,
                "locator players expose VolumeControl");
        VolumeControl locatorVolume = (VolumeControl)locatorPlayer.getControl("VolumeControl");
        locatorVolume.setLevel(42);
        require(lastAction(AudioBridge.VOLUME).number == 42,
                "locator VolumeControl forwards its level to Android");
        locatorPlayer.start();
        require(lastAction(AudioBridge.START).id == lastAction(AudioBridge.CREATE_LOCATOR).id,
                "locator player start reaches its Android decoder");
        locatorPlayer.close();

        delete(root);
        System.out.println("AudioBridgeSmoke: " + checks
                + " checks passed; staged stream, Android command transport, MMAPI controls and async events");
    }

    private static Record lastAction(int action) {
        for (int index = messages.size() - 1; index >= 0; index--)
            if (messages.get(index).action == action) return messages.get(index);
        return null;
    }
    private static int countAction(int action, int id) {
        int count = 0;
        for (Record record : messages)
            if (record.action == action && record.id == id) count++;
        return count;
    }
    private static byte[] read(File file) throws Exception {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    private static byte[] wave() throws Exception {
        int samples = 800;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("RIFF"); little(out, 36 + samples * 2); out.writeBytes("WAVE");
        out.writeBytes("fmt "); little(out, 16); littleShort(out, 1); littleShort(out, 1);
        little(out, 8000); little(out, 16000); littleShort(out, 2); littleShort(out, 16);
        out.writeBytes("data"); little(out, samples * 2);
        for (int i = 0; i < samples; i++) littleShort(out, (int)(Math.sin(i * 0.15) * 5000));
        return bytes.toByteArray();
    }
    private static void little(DataOutputStream out, int value) throws Exception {
        out.writeByte(value); out.writeByte(value >>> 8); out.writeByte(value >>> 16); out.writeByte(value >>> 24);
    }
    private static void littleShort(DataOutputStream out, int value) throws Exception {
        out.writeByte(value); out.writeByte(value >>> 8);
    }
    private static void delete(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
    private static void require(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
    private static final class Record {
        final int action, id, number, flags;
        final String text, extra;
        final long value;
        Record(int action, int id, String text, String extra, long value, int number, int flags) {
            this.action = action; this.id = id; this.text = text; this.extra = extra;
            this.value = value; this.number = number; this.flags = flags;
        }
    }
}
