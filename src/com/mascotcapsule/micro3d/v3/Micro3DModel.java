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
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed, immutable geometry from a Mascot Capsule MBAC figure.
 *
 * The reader deliberately lives beside the Micro3D API rather than in the
 * shared MIDP graphics path, so games which do not use this API are unaffected.
 */
final class Micro3DModel {
    final int version;
    final int textureCount;
    final int patternCount;
    final int[] vertices;
    final int[] normals;
    final int[] originalVertices;
    final int[] originalNormals;
    final Triangle[] triangles;
    final Bone[] bones;

    Micro3DModel(int version, int textureCount, int patternCount,
                 int[] vertices, Triangle[] triangles) {
        this(version, textureCount, patternCount, vertices, triangles, new Bone[0]);
    }

    Micro3DModel(int version, int textureCount, int patternCount,
                 int[] vertices, Triangle[] triangles, Bone[] bones) {
        this(version, textureCount, patternCount, vertices, triangles, bones, null);
    }

    Micro3DModel(int version, int textureCount, int patternCount,
                 int[] vertices, Triangle[] triangles, Bone[] bones, int[] normals) {
        this.version = version;
        this.textureCount = textureCount;
        this.patternCount = patternCount;
        this.originalVertices = vertices.clone();
        this.bones = bones == null ? new Bone[0] : bones.clone();
        this.originalNormals = normals == null ? null : normals.clone();
        this.vertices = applyBindPose(this.originalVertices, this.bones);
        this.normals = applyBindPoseNormals(normals, this.bones);
        this.triangles = triangles;
    }

    private static int[] applyBindPose(int[] source, Bone[] bones) {
        if (bones.length == 0) return source.clone();
        int[] output = new int[source.length];
        AffineTrans[] transforms = new AffineTrans[bones.length];
        int vertex = 0;
        for (int i = 0; i < bones.length; i++) {
            Bone bone = bones[i];
            AffineTrans local = new AffineTrans(bone.transform);
            if (bone.parent < 0) {
                transforms[i] = local;
            } else {
                if (bone.parent >= i || transforms[bone.parent] == null) {
                    throw new IllegalArgumentException("Invalid MBAC bone hierarchy");
                }
                transforms[i] = new AffineTrans();
                transforms[i].mul(transforms[bone.parent], local);
            }
            for (int j = 0; j < bone.vertexCount; j++, vertex++) {
                int offset = vertex * 3;
                Vector3D transformed = transforms[i].transform(
                        new Vector3D(source[offset], source[offset + 1], source[offset + 2]));
                output[offset] = transformed.x;
                output[offset + 1] = transformed.y;
                output[offset + 2] = transformed.z;
            }
        }
        if (vertex * 3 != source.length) {
            throw new IllegalArgumentException("MBAC bone vertex counts do not match model");
        }
        return output;
    }

    private static int[] applyBindPoseNormals(int[] source, Bone[] bones) {
        if (source == null) return null;
        if (bones.length == 0) return source.clone();

        int[] output = new int[source.length];
        AffineTrans[] transforms = new AffineTrans[bones.length];
        int vertex = 0;
        for (int i = 0; i < bones.length; i++) {
            Bone bone = bones[i];
            AffineTrans local = new AffineTrans(bone.transform);
            if (bone.parent < 0) {
                transforms[i] = local;
            } else {
                transforms[i] = new AffineTrans();
                transforms[i].mul(transforms[bone.parent], local);
            }

            AffineTrans transform = transforms[i];
            for (int j = 0; j < bone.vertexCount; j++, vertex++) {
                int offset = vertex * 3;
                long x = (long) transform.m00 * source[offset]
                        + (long) transform.m01 * source[offset + 1]
                        + (long) transform.m02 * source[offset + 2];
                long y = (long) transform.m10 * source[offset]
                        + (long) transform.m11 * source[offset + 1]
                        + (long) transform.m12 * source[offset + 2];
                long z = (long) transform.m20 * source[offset]
                        + (long) transform.m21 * source[offset + 1]
                        + (long) transform.m22 * source[offset + 2];
                output[offset] = (int) ((x + 2048) >> 12);
                output[offset + 1] = (int) ((y + 2048) >> 12);
                output[offset + 2] = (int) ((z + 2048) >> 12);
            }
        }
        if (vertex * 3 != source.length) {
            throw new IllegalArgumentException("MBAC bone normal counts do not match model");
        }
        return output;
    }

    static final class Bone {
        final int vertexCount;
        final int parent;
        final int[] transform;

        Bone(int vertexCount, int parent, int[] transform) {
            this.vertexCount = vertexCount;
            this.parent = parent;
            this.transform = transform;
        }
    }

    static Micro3DModel parse(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("figure");
        }
        try {
            return new Reader(bytes).read();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid Mascot Capsule figure: " + e.getMessage(), e);
        }
    }

    static final class Triangle {
        final int a;
        final int b;
        final int c;
        final int face;
        final int pattern;
        final int material;
        final int color;
        final int u0;
        final int v0;
        final int u1;
        final int v1;
        final int u2;
        final int v2;

        Triangle(int a, int b, int c, int face, int pattern, int material,
                 int color, int u0, int v0, int u1, int v1, int u2, int v2) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.face = face;
            this.pattern = pattern;
            this.material = material;
            this.color = color;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;
        }

        Triangle withPattern(int value) {
            return new Triangle(a, b, c, face, value, material, color,
                    u0, v0, u1, v1, u2, v2);
        }
    }

    private static final class Polygon {
        int a, b, c, d;
        int face;
        int material;
        int color;
        int[] uv;

        List<Triangle> triangles(int pattern) {
            ArrayList<Triangle> result = new ArrayList<Triangle>(d < 0 ? 1 : 2);
            if (uv == null) {
                result.add(new Triangle(a, b, c, -1, pattern, material, color,
                        0, 0, 0, 0, 0, 0));
                if (d >= 0) {
                    result.add(new Triangle(c, b, d, -1, pattern, material, color,
                            0, 0, 0, 0, 0, 0));
                }
            } else {
                result.add(new Triangle(a, b, c, face, pattern, material, color,
                        uv[0], uv[1], uv[2], uv[3], uv[4], uv[5]));
                if (d >= 0) {
                    result.add(new Triangle(c, b, d, face, pattern, material, color,
                            uv[4], uv[5], uv[2], uv[3], uv[6], uv[7]));
                }
            }
            return result;
        }
    }

    private static final class Reader {
        private static final int[] VERTEX_BITS = {8, 10, 13, 16};
        private static final int[] POOL_NORMALS = {0, 0, 4096, 0, 0, -4096, 0, 0};

        private final byte[] data;
        private int position;
        private int bitCache;
        private int cachedBits;

        private int version;
        private int vertexFormat;
        private int normalFormat;
        private int polygonFormat;
        private int boneFormat;
        private int vertexCount;
        private int texturedTriangles;
        private int texturedQuads;
        private int colorTriangles;
        private int colorQuads;
        private int textureCount;
        private int patternCount;
        private int colorCount;
        private int[][][] patternPolygons;
        private int[] vertices;
        private int[] normals;
        private Bone[] bones;
        private final ArrayList<Polygon> colored = new ArrayList<Polygon>();
        private final ArrayList<Polygon> textured = new ArrayList<Polygon>();

        Reader(byte[] data) {
            this.data = data;
        }

        Micro3DModel read() throws IOException {
            if (readUByte() != 'M' || readUByte() != 'B') {
                throw new IOException("missing MB signature");
            }
            version = readUByte();
            if (readUByte() != 0 || version < 2 || version > 5) {
                throw new IOException("unsupported version " + version);
            }

            if (version >= 4) {
                vertexFormat = readUByte();
                normalFormat = readUByte();
                polygonFormat = readUByte();
                boneFormat = readUByte();
            } else {
                vertexFormat = 1;
                normalFormat = 0;
                polygonFormat = 1;
                boneFormat = 1;
            }
            if (boneFormat != 1) {
                throw new IOException("unsupported bone format " + boneFormat);
            }

            vertexCount = readUShort();
            texturedTriangles = readUShort();
            texturedQuads = readUShort();
            int boneCount = readUShort();
            if (polygonFormat < 3) {
                textureCount = 1;
                colorTriangles = 0;
                colorQuads = 0;
                patternCount = 1;
                colorCount = 0;
            } else {
                colorTriangles = readUShort();
                colorQuads = readUShort();
                textureCount = readUShort();
                patternCount = readUShort();
                colorCount = readUShort();
            }
            if (vertexCount > 21845 || textureCount > 16 || patternCount < 1
                    || patternCount > 33 || colorCount > 256) {
                throw new IOException("invalid model counts");
            }

            readPatternCounts();
            readVertices();
            readNormals();
            if (colorTriangles + colorQuads != 0) {
                readColoredPolygons();
            }
            if (texturedTriangles + texturedQuads != 0) {
                readTexturedPolygons();
            }
            readBones(boneCount);

            ArrayList<Triangle> output = new ArrayList<Triangle>();
            appendPatternGroup(output, colored, 0, colorTriangles, colorQuads, new int[]{0});
            int[] textureCursor = {0};
            for (int face = 0; face < textureCount; face++) {
                appendPatternGroup(output, textured, face + 1,
                        texturedTriangles, texturedQuads, textureCursor);
            }
            return new Micro3DModel(version, textureCount, patternCount,
                    vertices, output.toArray(new Triangle[output.size()]), bones, normals);
        }

        private void readPatternCounts() throws IOException {
            patternPolygons = new int[patternCount][textureCount + 1][2];
            if (version == 5) {
                for (int pattern = 0; pattern < patternCount; pattern++) {
                    for (int type = 0; type <= textureCount; type++) {
                        patternPolygons[pattern][type][0] = readUShort();
                        patternPolygons[pattern][type][1] = readUShort();
                    }
                }
            } else {
                patternPolygons[0][0][0] = colorTriangles;
                patternPolygons[0][0][1] = colorQuads;
                if (textureCount > 0) {
                    patternPolygons[0][1][0] = texturedTriangles;
                    patternPolygons[0][1][1] = texturedQuads;
                }
            }
        }

        private void readVertices() throws IOException {
            vertices = new int[vertexCount * 3];
            if (vertexFormat == 1) {
                for (int i = 0; i < vertices.length; i++) {
                    vertices[i] = readShort();
                }
            } else if (vertexFormat == 2) {
                int at = 0;
                while (at < vertices.length) {
                    int chunk = readUBits(8);
                    int size = VERTEX_BITS[chunk >>> 6];
                    int count = (chunk & 63) + 1;
                    if (count * 3 > vertices.length - at) {
                        throw new IOException("vertex chunk exceeds vertex count");
                    }
                    for (int i = 0; i < count; i++) {
                        vertices[at++] = readSignedBits(size);
                        vertices[at++] = readSignedBits(size);
                        vertices[at++] = readSignedBits(size);
                    }
                }
            } else {
                throw new IOException("unsupported vertex format " + vertexFormat);
            }
            clearBits();
        }

        private void readNormals() throws IOException {
            if (normalFormat == 0) {
                return;
            }
            normals = new int[vertexCount * 3];
            if (normalFormat == 1) {
                for (int i = 0; i < normals.length; i++) {
                    normals[i] = readShort();
                }
            } else if (normalFormat == 2) {
                for (int i = 0; i < vertexCount; i++) {
                    int x = readUBits(7);
                    int y;
                    int z;
                    if (x == 64) {
                        int pool = readUBits(3);
                        if (pool > 5) {
                            throw new IOException("invalid normal pool index");
                        }
                        z = POOL_NORMALS[pool++];
                        y = POOL_NORMALS[pool++];
                        x = POOL_NORMALS[pool];
                    } else {
                        x = (x << 25) >> 19;
                        y = (readUBits(7) << 25) >> 19;
                        int sign = readUBits(1);
                        int remaining = 4096 * 4096 - x * x - y * y;
                        z = remaining > 0 ? (int) Math.round(Math.sqrt(remaining)) : 0;
                        if (sign == 1) z = -z;
                    }
                    int offset = i * 3;
                    normals[offset] = x;
                    normals[offset + 1] = y;
                    normals[offset + 2] = z;
                }
                clearBits();
            } else {
                throw new IOException("unsupported normal format " + normalFormat);
            }
        }

        private void readPoolNormal(int pool) {
            int unusedZ = POOL_NORMALS[pool++];
            int unusedY = POOL_NORMALS[pool++];
            int unusedX = POOL_NORMALS[pool];
        }

        private void readColoredPolygons() throws IOException {
            int materialBits = readUByte();
            int vertexBits = readUByte();
            int colorBits = readUByte();
            int colorIdBits = readUByte();
            readUByte(); // reserved
            int[] palette = new int[colorCount];
            for (int i = 0; i < colorCount; i++) {
                palette[i] = (readUBits(colorBits) << 16)
                        | (readUBits(colorBits) << 8)
                        | readUBits(colorBits);
            }
            for (int i = 0; i < colorTriangles + colorQuads; i++) {
                int material = readUBits(materialBits) << 1;
                int a = readIndex(vertexBits);
                int b = readIndex(vertexBits);
                int c = readIndex(vertexBits);
                int d = i < colorTriangles ? -1 : readIndex(vertexBits);
                int colorId = readUBits(colorIdBits);
                if (colorId >= palette.length) {
                    throw new IOException("color index outside palette");
                }
                Polygon polygon = new Polygon();
                polygon.a = a;
                polygon.b = b;
                polygon.c = c;
                polygon.d = d;
                polygon.material = material;
                polygon.color = 0xff000000 | palette[colorId];
                colored.add(polygon);
            }
            clearBits();
        }

        private void readTexturedPolygons() throws IOException {
            if (polygonFormat == 1) {
                readTexturedV1();
            } else if (polygonFormat == 2) {
                readTexturedV2();
            } else if (polygonFormat == 3) {
                readTexturedV3();
            } else {
                throw new IOException("unsupported polygon format " + polygonFormat);
            }
            clearBits();
        }

        private void readTexturedV1() throws IOException {
            for (int i = 0; i < texturedTriangles + texturedQuads; i++) {
                int material = readUShort();
                Polygon polygon = new Polygon();
                polygon.material = (material & 4) << 2 | (material & 2) >> 1;
                polygon.a = checkedIndex(readUShort());
                polygon.b = checkedIndex(readUShort());
                polygon.c = checkedIndex(readUShort());
                boolean quad = i >= texturedTriangles;
                if (quad) {
                    polygon.d = checkedIndex(readUShort());
                } else {
                    polygon.d = -1;
                }
                int uvCount = quad ? 4 : 3;
                polygon.uv = new int[uvCount * 2];
                for (int j = 0; j < polygon.uv.length; j++) {
                    polygon.uv[j] = (readByte() & 255) >>> 1;
                }
                textured.add(polygon);
            }
        }

        private void readTexturedV2() throws IOException {
            int materialBits = readUByte();
            int vertexBits = readUByte();
            for (int i = 0; i < texturedTriangles + texturedQuads; i++) {
                Polygon polygon = new Polygon();
                polygon.material = readUBits(materialBits);
                polygon.a = readIndex(vertexBits);
                polygon.b = readIndex(vertexBits);
                polygon.c = readIndex(vertexBits);
                boolean quad = i >= texturedTriangles;
                polygon.d = quad ? readIndex(vertexBits) : -1;
                polygon.uv = new int[(quad ? 4 : 3) * 2];
                for (int j = 0; j < polygon.uv.length; j++) {
                    polygon.uv[j] = readUBits(7);
                }
                textured.add(polygon);
            }
        }

        private void readTexturedV3() throws IOException {
            int materialBits = readUBits(8);
            int vertexBits = readUBits(8);
            int uvBits = readUBits(8);
            readUBits(8); // reserved
            for (int i = 0; i < texturedTriangles + texturedQuads; i++) {
                Polygon polygon = new Polygon();
                polygon.material = readUBits(materialBits);
                polygon.a = readIndex(vertexBits);
                polygon.b = readIndex(vertexBits);
                polygon.c = readIndex(vertexBits);
                boolean quad = i >= texturedTriangles;
                polygon.d = quad ? readIndex(vertexBits) : -1;
                polygon.uv = new int[(quad ? 4 : 3) * 2];
                for (int j = 0; j < polygon.uv.length; j++) {
                    polygon.uv[j] = normalizeUv(readUBits(uvBits), uvBits);
                }
                textured.add(polygon);
            }
        }

        private int normalizeUv(int value, int bits) {
            return value;
        }

        private void appendPatternGroup(ArrayList<Triangle> output, List<Polygon> polygons,
                                        int group, int triCount, int quadCount,
                                        int[] polygonCursor) throws IOException {
            for (int p = 0; p < patternCount; p++) {
                int triangles = patternPolygons[p][group][0];
                int quads = patternPolygons[p][group][1];
                int count = triangles + quads;
                if (polygonCursor[0] + count > polygons.size()) {
                    throw new IOException("pattern polygon counts exceed model data");
                }
                int pattern = p == 0 ? 0 : 1 << p;
                for (int i = 0; i < count; i++) {
                    Polygon polygon = polygons.get(polygonCursor[0]++);
                    polygon.face = group - 1;
                    for (Triangle triangle : polygon.triangles(pattern)) {
                        output.add(triangle);
                    }
                }
            }
            if (group == 0 && polygonCursor[0] != polygons.size()
                    || group == textureCount && polygonCursor[0] != polygons.size()
                    || (triCount + quadCount != 0 && polygonCursor[0] > polygons.size())) {
                throw new IOException("pattern polygon counts do not match model");
            }
        }

        private int readIndex(int bits) throws IOException {
            return checkedIndex(readUBits(bits));
        }

        private int checkedIndex(int index) throws IOException {
            if (index < 0 || index >= vertexCount) {
                throw new IOException("vertex index outside model");
            }
            return index;
        }

        private void readBones(int count) throws IOException {
            bones = new Bone[count];
            int totalVertices = 0;
            for (int i = 0; i < count; i++) {
                int boneVertices = readUShort();
                int parent = readShort();
                if (parent < -1 || parent >= i || boneVertices > vertexCount - totalVertices) {
                    throw new IOException("invalid bone record");
                }
                int[] transform = new int[12];
                for (int j = 0; j < transform.length; j++) {
                    transform[j] = readShort();
                }
                bones[i] = new Bone(boneVertices, parent, transform);
                totalVertices += boneVertices;
            }
            if (count > 0 && totalVertices != vertexCount) {
                throw new IOException("bone vertex counts do not match vertex count");
            }
        }

        private int readByte() throws IOException {
            require(1);
            return data[position++];
        }

        private int readUByte() throws IOException {
            require(1);
            return data[position++] & 255;
        }

        private int readShort() throws IOException {
            require(2);
            return (short) ((data[position++] & 255) | (data[position++] << 8));
        }

        private int readUShort() throws IOException {
            require(2);
            return (data[position++] & 255) | ((data[position++] & 255) << 8);
        }

        private int readUBits(int size) throws IOException {
            if (size < 0 || size > 24) {
                throw new IOException("invalid bit field width " + size);
            }
            while (cachedBits < size) {
                bitCache |= readUByte() << cachedBits;
                cachedBits += 8;
            }
            int result = size == 0 ? 0 : bitCache & ((1 << size) - 1);
            bitCache >>>= size;
            cachedBits -= size;
            return result;
        }

        private int readSignedBits(int size) throws IOException {
            int value = readUBits(size);
            int shift = 32 - size;
            return value << shift >> shift;
        }

        private void clearBits() {
            bitCache = 0;
            cachedBits = 0;
        }

        private void skip(int count) throws IOException {
            require(count);
            position += count;
        }

        private void require(int count) throws IOException {
            if (count < 0 || position + count > data.length) {
                throw new IOException("unexpected end of file");
            }
        }
    }
}
