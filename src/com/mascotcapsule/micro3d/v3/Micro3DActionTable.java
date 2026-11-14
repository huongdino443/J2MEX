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
package com.mascotcapsule.micro3d.v3;

import java.io.IOException;
/** Parsed MTRA actions used to animate MBAC figure bones. */
final class Micro3DActionTable {
    final int version;
    final Action[] actions;
    final int boneCount;

    private Micro3DActionTable(int version, Action[] actions, int boneCount) {
        this.version = version;
        this.actions = actions;
        this.boneCount = boneCount;
    }

    static Micro3DActionTable parse(byte[] data) throws IOException {
        return new Reader(data).read();
    }

    static final class Action {
        final int keyframes;
        final BoneAction[] bones;
        final int[] patternFrames;
        final int[] patterns;

        Action(int keyframes, BoneAction[] bones, int[] patternFrames, int[] patterns) {
            this.keyframes = keyframes;
            this.bones = bones;
            this.patternFrames = patternFrames;
            this.patterns = patterns;
        }

        int getPattern(int frame, int fallback) {
            int wholeFrame = frame < 0 ? 0 : frame >>> 16;
            for (int i = patternFrames.length - 1; i >= 0; i--) {
                if (patternFrames[i] <= wholeFrame) {
                    return patterns[i];
                }
            }
            return fallback;
        }
    }

    static final class BoneAction {
        private static final double FIXED = 4096.0;
        final int type;
        final int[] fixedMatrix;
        final Track translate;
        final Track scale;
        final Track rotate;
        final ScalarTrack roll;

        BoneAction(int type, int[] fixedMatrix, Track translate,
                   Track scale, Track rotate, ScalarTrack roll) {
            this.type = type;
            this.fixedMatrix = fixedMatrix;
            this.translate = translate;
            this.scale = scale;
            this.rotate = rotate;
            this.roll = roll;
        }

        AffineTrans at(int frame) {
            if (type == 0 || type == 1) {
                return new AffineTrans(fixedMatrix);
            }
            double keyFrame = (frame < 0 ? 0 : frame) / 65536.0;
            double[] translation = translate == null
                    ? new double[]{0, 0, 0} : translate.at(keyFrame);
            double[] direction = rotate.at(keyFrame);
            double angle = roll == null ? 0 : roll.at(keyFrame);
            double[][] matrix = directionMatrix(direction[0], direction[1], direction[2]);
            applyRoll(matrix, angle);
            if (scale != null) {
                double[] values = scale.at(keyFrame);
                for (int row = 0; row < 3; row++) {
                    matrix[row][0] *= values[0];
                    matrix[row][1] *= values[1];
                    matrix[row][2] *= values[2];
                }
            }
            return new AffineTrans(
                    fixed(matrix[0][0]), fixed(matrix[0][1]), fixed(matrix[0][2]), rounded(translation[0]),
                    fixed(matrix[1][0]), fixed(matrix[1][1]), fixed(matrix[1][2]), rounded(translation[1]),
                    fixed(matrix[2][0]), fixed(matrix[2][1]), fixed(matrix[2][2]), rounded(translation[2]));
        }

        private static double[][] directionMatrix(double x, double y, double z) {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length == 0) {
                return new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            }
            x /= length;
            y /= length;
            z /= length;
            double xx = x * x;
            double yy = y * y;
            double denominator = xx + yy;
            if (denominator == 0) {
                return new double[][]{{1, 0, 0}, {0, z, 0}, {0, 0, z}};
            }
            double a = (1.0 - z) / denominator;
            double b = -x * y * a;
            return new double[][]{
                    {z + yy * a, b, x},
                    {b, z + xx * a, y},
                    {-x, -y, z}
            };
        }

        private static void applyRoll(double[][] matrix, double angle) {
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            for (int row = 0; row < 3; row++) {
                double x = matrix[row][0];
                double y = matrix[row][1];
                matrix[row][0] = x * cos + y * sin;
                matrix[row][1] = y * cos - x * sin;
            }
        }

        private static int fixed(double value) {
            return (int) Math.round(value * FIXED);
        }

        private static int rounded(double value) {
            return (int) Math.round(value);
        }
    }

    private static final class Track {
        final int[] frames;
        final double[][] values;

        Track(int[] frames, double[][] values) {
            this.frames = frames;
            this.values = values;
        }

        double[] at(double frame) {
            int last = frames.length - 1;
            if (last < 0) return new double[]{0, 0, 0};
            if (frame >= frames[last]) return values[last].clone();
            for (int i = last - 1; i >= 0; i--) {
                if (frames[i] > frame) continue;
                if (frames[i] == frame) return values[i].clone();
                int nextFrame = frames[i + 1];
                if (nextFrame <= frames[i]) return values[i + 1].clone();
                double amount = (frame - frames[i]) / (nextFrame - frames[i]);
                return new double[]{
                        values[i][0] + (values[i + 1][0] - values[i][0]) * amount,
                        values[i][1] + (values[i + 1][1] - values[i][1]) * amount,
                        values[i][2] + (values[i + 1][2] - values[i][2]) * amount
                };
            }
            return values[0].clone();
        }
    }

    private static final class ScalarTrack {
        final int[] frames;
        final double[] values;

        ScalarTrack(int[] frames, double[] values) {
            this.frames = frames;
            this.values = values;
        }

        double at(double frame) {
            int last = frames.length - 1;
            if (last < 0) return 0;
            if (frame >= frames[last]) return values[last];
            for (int i = last - 1; i >= 0; i--) {
                if (frames[i] > frame) continue;
                if (frames[i] == frame) return values[i];
                int nextFrame = frames[i + 1];
                if (nextFrame <= frames[i]) return values[i + 1];
                double amount = (frame - frames[i]) / (nextFrame - frames[i]);
                return values[i] + (values[i + 1] - values[i]) * amount;
            }
            return values[0];
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int offset;

        Reader(byte[] data) throws IOException {
            if (data == null) throw new NullPointerException("action table");
            this.data = data;
        }

        Micro3DActionTable read() throws IOException {
            if (u8() != 'M' || u8() != 'T') throw new IOException("missing MTRA signature");
            int version = u8();
            if (u8() != 0 || version < 2 || version > 5) {
                throw new IOException("unsupported MTRA version " + version);
            }
            int actionCount = u16();
            int boneCount = u16();
            if (actionCount > 4096 || boneCount > 1024
                    || (long) actionCount * boneCount > 262144) {
                throw new IOException("invalid MTRA action/bone counts");
            }
            for (int i = 0; i < 8; i++) u16(); // transform-type counts
            i32(); // data size

            Action[] actions = new Action[actionCount];
            for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                int frameCount = u16();
                BoneAction[] boneActions = new BoneAction[boneCount];
                for (int bone = 0; bone < boneCount; bone++) {
                    boneActions[bone] = readBoneAction();
                }
                int patternCount = version >= 5 ? u16() : 0;
                int[] frames = new int[patternCount];
                int[] patterns = new int[patternCount];
                for (int i = 0; i < patternCount; i++) {
                    frames[i] = u16();
                    patterns[i] = i32();
                }
                actions[actionIndex] = new Action(frameCount, boneActions, frames, patterns);
            }
            if (version >= 4) {
                require(20);
                offset += 20;
            }
            return new Micro3DActionTable(version, actions, boneCount);
        }

        private BoneAction readBoneAction() throws IOException {
            int type = u8();
            int[] matrix;
            Track translate = null;
            Track scale = null;
            Track rotate = null;
            ScalarTrack roll = null;
            switch (type) {
                case 0:
                    matrix = new int[12];
                    for (int i = 0; i < 12; i++) {
                        int value = s16();
                        matrix[i] = value;
                    }
                    break;
                case 1:
                    matrix = identity();
                    break;
                case 2:
                    translate = readTrack(false);
                    scale = readTrack(true);
                    rotate = readTrack(false);
                    roll = readScalarTrack(true);
                    matrix = identity();
                    break;
                case 3:
                    translate = new Track(new int[]{0},
                            new double[][]{{s16(), s16(), s16()}});
                    rotate = readTrack(false);
                    roll = new ScalarTrack(new int[]{0},
                            new double[]{s16() * Math.PI / 2048.0});
                    matrix = identity();
                    break;
                case 4:
                    rotate = readTrack(false);
                    roll = readScalarTrack(true);
                    matrix = identity();
                    break;
                case 5:
                    rotate = readTrack(false);
                    matrix = identity();
                    break;
                case 6:
                    translate = readTrack(false);
                    rotate = readTrack(false);
                    roll = readScalarTrack(true);
                    matrix = identity();
                    break;
                default:
                    throw new IOException("unsupported MTRA bone transform type " + type);
            }
            return new BoneAction(type, matrix, translate, scale, rotate, roll);
        }

        private Track readTrack(boolean fixedScale) throws IOException {
            int count = u16();
            if (count == 0) throw new IOException("invalid MTRA key count");
            int[] frames = new int[count];
            double[][] values = new double[count][3];
            for (int i = 0; i < count; i++) {
                frames[i] = u16();
                for (int axis = 0; axis < 3; axis++) {
                    int value = s16();
                    values[i][axis] = fixedScale ? value / 4096.0 : value;
                }
            }
            return new Track(frames, values);
        }

        private ScalarTrack readScalarTrack(boolean radians) throws IOException {
            int count = u16();
            if (count == 0) throw new IOException("invalid MTRA roll key count");
            int[] frames = new int[count];
            double[] values = new double[count];
            for (int i = 0; i < count; i++) {
                frames[i] = u16();
                int value = s16();
                values[i] = radians ? value * Math.PI / 2048.0 : value;
            }
            return new ScalarTrack(frames, values);
        }

        private static int[] identity() {
            return new int[]{4096, 0, 0, 0, 0, 4096, 0, 0, 0, 0, 4096, 0};
        }

        private int u8() throws IOException {
            require(1);
            return data[offset++] & 255;
        }

        private int u16() throws IOException {
            require(2);
            return (data[offset++] & 255) | ((data[offset++] & 255) << 8);
        }

        private int s16() throws IOException {
            return (short) u16();
        }

        private int i32() throws IOException {
            require(4);
            return (data[offset++] & 255)
                    | ((data[offset++] & 255) << 8)
                    | ((data[offset++] & 255) << 16)
                    | (data[offset++] << 24);
        }

        private void require(int length) throws IOException {
            if (length < 0 || offset > data.length - length) {
                throw new IOException("truncated MTRA data at byte " + offset);
            }
        }
    }
}
