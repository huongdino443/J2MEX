package org.recompile.freej2me.lite.audio;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

public final class MidiToneSynthMuteSmoke {
    public static void main(String[] args) throws Exception {
        MidiToneSynth synth = new MidiToneSynth();
        synth.setOutputEnabled(false);

        Class<?> voiceType = Class.forName(
                "org.recompile.freej2me.lite.audio.MidiToneSynth$Voice");
        Constructor<?> constructor = voiceType.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Object voice = constructor.newInstance(69);
        setDouble(voiceType, voice, "velocity", 1.0);
        setDouble(voiceType, voice, "level", 0.25);

        Field voicesField = MidiToneSynth.class.getDeclaredField("voices");
        voicesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Object> voices = (Map<Long, Object>) voicesField.get(synth);
        voices.put(69L, voice);

        short[] output = new short[512];
        double phaseBefore = doubleField(voiceType, voice, "phase");
        int active = synth.renderBlock(output, false);
        require(active > 0, "muting must not discard active voices");
        require(allZero(output), "muted MIDI blocks must contain only silence");
        require(doubleField(voiceType, voice, "phase") != phaseBefore,
                "muted playback must keep advancing the MIDI timeline");
        require(doubleField(voiceType, voice, "level") > 0.25,
                "muted playback must continue envelope updates");

        synth.renderBlock(output, true);
        require(!allZero(output), "unmuting must restore generated audio");
        System.out.println("MidiToneSynthMuteSmoke: muted audio advances voices and writes silence");
    }

    private static void setDouble(Class<?> type, Object instance, String name, double value)
            throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(instance, value);
    }

    private static double doubleField(Class<?> type, Object instance, String name)
            throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(instance);
    }

    private static boolean allZero(short[] values) {
        for (short value : values) if (value != 0) return false;
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
