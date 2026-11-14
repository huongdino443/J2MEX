package javax.microedition.m3g;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

public final class M3GSplineInterpolationSmoke {
    private static int checks;

    public static void main(String[] args) throws Exception {
        openSplineUsesHermiteTangents();
        nonUniformTimesScaleTangents();
        loopSplineInterpolatesAcrossSeam();
        if (args.length > 0) inspectNfsAssets(new File(args[0]));
        System.out.println("M3GSplineInterpolationSmoke: " + checks + " checks passed");
    }

    private static void openSplineUsesHermiteTangents() {
        KeyframeSequence sequence = sequence(
                new int[]{0, 10, 20}, new float[]{0, 10, 20}, 20, KeyframeSequence.CONSTANT);
        close(sequence.sample(5)[0], 3.75f, "first open segment");
        close(sequence.sample(15)[0], 16.25f, "second open segment");
        close(sequence.sample(0)[0], 0f, "first keyframe");
        close(sequence.sample(20)[0], 20f, "last keyframe");
    }

    private static void nonUniformTimesScaleTangents() {
        KeyframeSequence sequence = sequence(
                new int[]{0, 10, 30}, new float[]{0, 10, 30}, 30, KeyframeSequence.CONSTANT);
        close(sequence.sample(20)[0], 22.5f, "nonuniform keyframe timing");
    }

    private static void loopSplineInterpolatesAcrossSeam() {
        KeyframeSequence sequence = sequence(
                new int[]{0, 10, 20}, new float[]{0, 10, 0}, 30, KeyframeSequence.LOOP);
        close(sequence.sample(5)[0], 5.625f, "loop first segment tangent");
        close(sequence.sample(25)[0], -1.25f, "loop closing segment");
        close(sequence.sample(30)[0], 0f, "loop duration wraps to first keyframe");
    }

    private static KeyframeSequence sequence(
            int[] times, float[] values, int duration, int repeatMode) {
        KeyframeSequence result = new KeyframeSequence(times.length, 1, KeyframeSequence.SPLINE);
        result.setDuration(duration);
        result.setRepeatMode(repeatMode);
        for (int i = 0; i < times.length; i++)
            result.setKeyframe(i, times[i], new float[]{values[i]});
        return result;
    }

    private static void inspectNfsAssets(File jar) throws Exception {
        check(jar.isFile(), "NFS test JAR is missing: " + jar);
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        platform.dataPath = Files.createTempDirectory("m3g-nfs-spline-").toFile()
                .getCanonicalPath() + File.separator;
        platform.loader = new MIDletLoader(new URL[]{jar.toURI().toURL()});

        int splineSequences = 0;
        int sampledSegments = 0;
        for (String resource : new String[]{"animrig_car.m3g", "animrig_camera.m3g"}) {
            Object3D[] roots = Loader.load("/" + resource);
            Set<Object3D> seen =
                    Collections.newSetFromMap(new IdentityHashMap<Object3D, Boolean>());
            Deque<Object3D> pending = new ArrayDeque<Object3D>();
            Collections.addAll(pending, roots);
            while (!pending.isEmpty()) {
                Object3D object = pending.removeFirst();
                if (!seen.add(object)) continue;
                if (object instanceof KeyframeSequence) {
                    KeyframeSequence sequence = (KeyframeSequence)object;
                    if (sequence.getInterpolationType() == KeyframeSequence.SPLINE) {
                        splineSequences++;
                        int first = sequence.getValidRangeFirst();
                        int last = sequence.getValidRangeLast();
                        for (int i = first; i < last; i++) {
                            int start = sequence.getKeyframe(i, null);
                            int end = sequence.getKeyframe(i + 1, null);
                            assertFinite(sequence.sample((start + end) / 2f), resource);
                            sampledSegments++;
                        }
                        if (sequence.getRepeatMode() == KeyframeSequence.LOOP && last >= first) {
                            int start = sequence.getKeyframe(last, null);
                            int end = sequence.getKeyframe(first, null) + sequence.getDuration();
                            assertFinite(sequence.sample((start + end) / 2f), resource);
                            sampledSegments++;
                        }
                    }
                }
                pending.addAll(M3GGraph.references(object));
            }
        }
        check(splineSequences > 0, "NFS animation rigs contain no SPLINE keyframe sequence");
        check(sampledSegments > 0, "NFS SPLINE sequences contain no sampleable segments");
        inspectNfsTrackCutoutTexture();
    }

    private static void inspectNfsTrackCutoutTexture() throws Exception {
        Object3D[] roots = Loader.load("/texture_tracktex_city.m3g");
        Set<Object3D> seen =
                Collections.newSetFromMap(new IdentityHashMap<Object3D, Boolean>());
        Deque<Object3D> pending = new ArrayDeque<Object3D>();
        Collections.addAll(pending, roots);
        while (!pending.isEmpty()) {
            Object3D object = pending.removeFirst();
            if (!seen.add(object)) continue;
            if (object instanceof Image2D) {
                Image2D image = (Image2D)object;
                check(image.getFormat() == Image2D.RGBA,
                        "NFS city track texture is not RGBA");
                int transparent = 0, opaque = 0;
                for (int pixel : image.pixels()) {
                    int alpha = (pixel >>> 24) & 255;
                    if (alpha == 0) transparent++;
                    else if (alpha == 255) opaque++;
                    else throw new AssertionError("NFS city track texture unexpectedly has partial alpha");
                }
                check(transparent > 0 && opaque > 0,
                        "NFS city track texture lost its binary-alpha cutout");
                return;
            }
            pending.addAll(M3GGraph.references(object));
        }
        throw new AssertionError("NFS city track texture contains no Image2D");
    }

    private static void assertFinite(float[] values, String resource) {
        check(values.length > 0, resource + ": empty spline sample");
        for (float value : values)
            check(Float.isFinite(value), resource + ": non-finite spline sample");
    }

    private static void close(float actual, float expected, String label) {
        check(Math.abs(actual - expected) < 0.0001f,
                label + " expected " + expected + " but got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
