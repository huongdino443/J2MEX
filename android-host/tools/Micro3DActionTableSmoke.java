package com.mascotcapsule.micro3d.v3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Exercises MTRA parsing and verifies that Figure.setPosture deforms a bone. */
public final class Micro3DActionTableSmoke {
    public static void main(String[] args) throws Exception {
        ActionTable table = new ActionTable(animatedTranslationFixture());
        check(table.getNumActions() == 1, "action count was not parsed");
        check(table.getNumFrames(0) == (2 << 16), "frame count was not parsed as 16.16");

        int[] identity = {4096, 0, 0, 0, 0, 4096, 0, 0, 0, 0, 4096, 0};
        Micro3DModel model = new Micro3DModel(5, 0, 1,
                new int[]{1, 2, 3}, new Micro3DModel.Triangle[0],
                new Micro3DModel.Bone[]{new Micro3DModel.Bone(1, -1, identity)});
        Figure figure = new Figure(model);
        figure.setPosture(table, 0, 0);
        check(model.vertices[0] == 1, "frame zero did not preserve the bind pose");
        figure.setPosture(table, 0, 32768);
        check(model.vertices[0] == 6, "half-frame translation was not interpolated");
        figure.setPosture(table, 0, 65536);
        check(model.vertices[0] == 11, "end-frame translation was not applied");

        expectIllegalArgument(new Runnable() {
            public void run() { table.getNumFrames(-1); }
        }, "invalid action index should fail");
        table.dispose();
        expectIllegalState(new Runnable() {
            public void run() { table.getNumActions(); }
        }, "disposed action table should fail");
        verifyStaticMatrixAction();
        verifyPatternTrack();
        System.out.println("Micro3DActionTableSmoke: MTRA parsing and skeletal animation passed");
    }

    private static void verifyPatternTrack() throws Exception {
        ByteArrayOutputStream out = header(5, 1, 0);
        u16(out, 3); // action length
        u16(out, 1); // pattern keys
        u16(out, 1);
        i32(out, 4);
        for (int i = 0; i < 20; i++) out.write(0); // version 4+ trailer
        ActionTable table = new ActionTable(out.toByteArray());
        Figure figure = new Figure(new Micro3DModel(5, 0, 1, new int[0],
                new Micro3DModel.Triangle[0]));
        figure.setPosture(table, 0, 65536);
        check(figure.getPatternMask() == 4, "dynamic pattern key was not applied");
    }

    private static void verifyStaticMatrixAction() throws Exception {
        ByteArrayOutputStream out = header(4, 1, 1);
        u16(out, 1);
        out.write(0); // constant affine transform
        int[] transform = {4096, 0, 0, 7, 0, 4096, 0, 0, 0, 0, 4096, 0};
        for (int value : transform) s16(out, value);
        for (int i = 0; i < 20; i++) out.write(0);
        ActionTable table = new ActionTable(out.toByteArray());
        int[] identity = {4096, 0, 0, 0, 0, 4096, 0, 0, 0, 0, 4096, 0};
        Micro3DModel model = new Micro3DModel(5, 0, 1,
                new int[]{1, 2, 3}, new Micro3DModel.Triangle[0],
                new Micro3DModel.Bone[]{new Micro3DModel.Bone(1, -1, identity)});
        Figure figure = new Figure(model);
        figure.setPosture(table, 0, 0);
        check(model.vertices[0] == 8, "constant action matrix translation was not applied");
    }

    private static byte[] animatedTranslationFixture() throws IOException {
        ByteArrayOutputStream out = header(2, 1, 1);
        u16(out, 2); // action duration in whole key frames
        out.write(2); // animated translate/scale/rotate/roll bone
        u16(out, 2); // translation keys
        u16(out, 0); s16(out, 0); s16(out, 0); s16(out, 0);
        u16(out, 1); s16(out, 10); s16(out, 0); s16(out, 0);
        u16(out, 1); // scale keys
        u16(out, 0); s16(out, 4096); s16(out, 4096); s16(out, 4096);
        u16(out, 1); // direction keys
        u16(out, 0); s16(out, 0); s16(out, 0); s16(out, 4096);
        u16(out, 1); // roll keys
        u16(out, 0); s16(out, 0);
        return out.toByteArray();
    }

    private static ByteArrayOutputStream header(int version, int actionCount, int boneCount)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('M'); out.write('T'); out.write(version); out.write(0);
        u16(out, actionCount); u16(out, boneCount);
        for (int i = 0; i < 8; i++) u16(out, 0);
        i32(out, 0);
        return out;
    }

    private static void u16(ByteArrayOutputStream out, int value) {
        out.write(value & 255); out.write((value >>> 8) & 255);
    }

    private static void s16(ByteArrayOutputStream out, int value) {
        u16(out, value);
    }

    private static void i32(ByteArrayOutputStream out, int value) {
        out.write(value & 255); out.write((value >>> 8) & 255);
        out.write((value >>> 16) & 255); out.write((value >>> 24) & 255);
    }

    private static void expectIllegalArgument(Runnable test, String message) {
        try { test.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void expectIllegalState(Runnable test, String message) {
        try { test.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
