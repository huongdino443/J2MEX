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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.microedition.lcdui.Graphics;
import org.recompile.mobile.PlatformGraphics;

/**
 * CPU rasterizer for the Micro3D Figure path. It writes only through the
 * bound MIDP Graphics object and keeps its depth/color buffers private to one
 * Micro3D bind/flush cycle.
 */
final class Micro3DSoftwareRenderer {
    static final int MATERIAL_POINT_SPRITE_COMMAND = 128;

    private static final float DEPTH_CLEAR = Float.POSITIVE_INFINITY;
    private static long lastPrimitiveDiagnosticMs;
    private static long lastSubtractiveDiagnosticMs;

    private final Graphics target;
    private final int width;
    private final int height;
    private final int[] pixels;
    private final float[] depth;
    private final ArrayList<DrawCall> pending = new ArrayList<DrawCall>();
    private long currentDepthGroup = 1;
    private static final java.util.WeakHashMap<Micro3DModel, Long> lastTransformDiagnosticMs =
            new java.util.WeakHashMap<Micro3DModel, Long>();

    Micro3DSoftwareRenderer(Graphics target) {
        if (target == null) {
            throw new NullPointerException("graphics");
        }
        this.target = target;
        if (target instanceof PlatformGraphics) {
            BufferedImage canvas = ((PlatformGraphics) target).getCanvas();
            width = canvas.getWidth();
            height = canvas.getHeight();
        } else {
            width = Math.max(1, target.getClipWidth());
            height = Math.max(1, target.getClipHeight());
        }
        pixels = new int[width * height];
        depth = new float[width * height];
        clearBuffers();
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    void renderFigure(Figure figure, int offsetX, int offsetY,
                      FigureLayout layout, Effect3D effect) {
        Micro3DModel model = figure.getModel();
        AffineTrans transform = layout.getAffineTrans();
        float[] transformed = transformVertices(model.vertices, transform);
        float[] transformedNormals = transformNormals(model.normals, transform);
        enqueueFigure(figure, transformed, transformedNormals, offsetX, offsetY,
                layout, effect, currentDepthGroup);
    }

    void renderPrimitiveFigure(Figure figure, int offsetX, int offsetY,
                               FigureLayout layout, Effect3D effect) {
        Micro3DModel model = figure.getModel();
        AffineTrans transform = layout.getAffineTrans();
        float[] transformed = transformVertices(model.vertices, transform);
        float[] transformedNormals = transformNormals(model.normals, transform);
        enqueueFigure(figure, transformed, transformedNormals, offsetX, offsetY,
                layout, effect, currentDepthGroup);
    }

    void renderFigureInCameraSpace(Figure figure, float[] cameraVertices,
                                  int offsetX, int offsetY,
                                  FigureLayout layout, Effect3D effect) {
        if (figure == null || cameraVertices == null || layout == null || effect == null) {
            throw new NullPointerException();
        }
        Micro3DModel model = figure.getModel();
        if (cameraVertices.length != model.vertices.length) {
            throw new IllegalArgumentException("Camera-space vertices do not match figure");
        }
        float[] transformedNormals = transformNormals(model.normals, layout.getAffineTrans());
        enqueueFigure(figure, cameraVertices.clone(), transformedNormals, offsetX, offsetY,
                layout, effect, currentDepthGroup);
    }

    private void enqueueFigure(Figure figure, float[] transformed, float[] transformedNormals,
                               int offsetX, int offsetY,
                               FigureLayout layout, Effect3D effect, long depthGroup) {
        Micro3DModel model = figure.getModel();
        Texture[] textures = figure.getTextures();
        ArrayList<TriangleCall> triangles = new ArrayList<TriangleCall>();
        for (Micro3DModel.Triangle triangle : model.triangles) {
            if (triangle.pattern != 0
                    && (triangle.pattern & figure.getPatternMask()) != triangle.pattern) {
                continue;
            }
            BufferedImage texture = null;
            if (triangle.face >= 0 && triangle.face < textures.length) {
                texture = textures[triangle.face].getImage();
            }
            triangles.add(new TriangleCall(triangle, texture));
        }
        pending.add(new DrawCall(model, transformed, transformedNormals, triangles,
                snapshotLayout(layout), snapshotEffect(effect),
                layout.getCenterX() + offsetX, layout.getCenterY() + offsetY,
                depthGroup));
    }

    private void renderClippedTriangle(Micro3DModel.Triangle triangle, float[] vertices,
                                       float[] normals, BufferedImage texture,
                                       FigureLayout layout, Effect3D effect,
                                       int centerX, int centerY, int blendMode,
                                       boolean depthWrite) {
        ArrayList<CameraVertex> polygon = new ArrayList<CameraVertex>(4);
        polygon.add(vertex(vertices, normals, triangle.a, triangle.u0, triangle.v0));
        polygon.add(vertex(vertices, normals, triangle.b, triangle.u1, triangle.v1));
        polygon.add(vertex(vertices, normals, triangle.c, triangle.u2, triangle.v2));

        int projection = layout.getProjection();
        if (projection == Graphics3D.COMMAND_PERSPECTIVE_FOV
                || projection == Graphics3D.COMMAND_PERSPECTIVE_WH) {
            polygon = clipAgainstZ(polygon, layout.getNear(), true);
            polygon = clipAgainstZ(polygon, layout.getFar(), false);
        } else {
            // The parallel projection matrix clips camera-space Z to [-65536, 65536].
            // Clip triangles at those planes rather than dropping a whole triangle when
            // only one of its vertices falls outside the visible depth range.
            polygon = clipAgainstZ(polygon, -65536.0f, true);
            polygon = clipAgainstZ(polygon, 65536.0f, false);
        }
        if (polygon.size() < 3) {
            return;
        }

        ScreenVertex first = project(polygon.get(0), layout, centerX, centerY);
        for (int i = 1; i + 1 < polygon.size(); i++) {
            ScreenVertex second = project(polygon.get(i), layout, centerX, centerY);
            ScreenVertex third = project(polygon.get(i + 1), layout, centerX, centerY);
            rasterize(first, second, third, triangle, texture, effect,
                    blendMode, depthWrite);
        }
    }

    private CameraVertex vertex(float[] transformed, float[] normals,
                                int index, int u, int v) {
        int offset = index * 3;
        return new CameraVertex(transformed[offset], transformed[offset + 1],
                transformed[offset + 2],
                normals == null ? 0 : normals[offset],
                normals == null ? 0 : normals[offset + 1],
                normals == null ? 0 : normals[offset + 2], u, v);
    }

    private ArrayList<CameraVertex> clipAgainstZ(List<CameraVertex> input,
                                                 float plane, boolean keepGreater) {
        ArrayList<CameraVertex> output = new ArrayList<CameraVertex>(input.size() + 1);
        if (input.isEmpty()) {
            return output;
        }
        CameraVertex previous = input.get(input.size() - 1);
        boolean previousInside = keepGreater ? previous.z >= plane : previous.z <= plane;
        for (CameraVertex current : input) {
            boolean currentInside = keepGreater ? current.z >= plane : current.z <= plane;
            if (currentInside != previousInside) {
                float amount = (plane - previous.z) / (current.z - previous.z);
                output.add(previous.interpolateAtZ(current, amount, plane));
            }
            if (currentInside) {
                output.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    void flush() {
        if (pending.isEmpty()) {
            clearBuffers();
            return;
        }
        captureTargetPixels();
        logTransformSample();
        long renderedDepthGroup = -1;
        for (DrawCall call : pending) {
            // J2ME Loader renders queued primitives and the model together,
            // then clears depth at the end of drawFigure/flush. Keep all
            // commands accumulated in this batch on the same depth buffer.
            if (call.depthGroup != renderedDepthGroup) {
                Arrays.fill(depth, DEPTH_CLEAR);
                renderedDepthGroup = call.depthGroup;
            }
            if (!call.triangles.isEmpty()
                    && (call.triangles.get(0).triangle.material
                            & MATERIAL_POINT_SPRITE_COMMAND) != 0) {
                // Primitive point-sprite blend flags apply to the command itself;
                // J2ME Loader honors them only when Effect3D transparency is enabled.
                for (TriangleCall triangleCall : call.triangles) {
                    int materialBlend = call.effect.isTransparency()
                            ? triangleCall.triangle.material & 6 : 0;
                    renderClippedTriangle(triangleCall.triangle, call.vertices,
                            call.normals, triangleCall.texture, call.layout, call.effect,
                            call.centerX, call.centerY, materialBlend, materialBlend == 0);
                }
                continue;
            }
            for (int pass = 0; pass < 2; pass++) {
                boolean transparency = call.effect.isTransparency();
                for (TriangleCall triangleCall : call.triangles) {
                    int materialBlend = triangleCall.triangle.material & 6;
                    if (pass == 0 && transparency && materialBlend != 0) {
                        continue;
                    }
                    if (pass == 1 && (!transparency || materialBlend == 0)) {
                        continue;
                    }
                    int blendMode = pass == 1 ? materialBlend : 0;
                    renderClippedTriangle(triangleCall.triangle, call.vertices,
                            call.normals, triangleCall.texture, call.layout, call.effect,
                            call.centerX, call.centerY, blendMode, pass == 0);
                }
            }
        }
        boolean hasPixels = false;
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                hasPixels = true;
                break;
            }
        }
        if (hasPixels) {
            target.drawRGB(pixels, 0, width, 0, 0, width, height, true);
        }
        pending.clear();
        clearBuffers();
        currentDepthGroup++;
    }

    private void captureTargetPixels() {
        if (target instanceof PlatformGraphics) {
            BufferedImage canvas = ((PlatformGraphics) target).getCanvas();
            if (canvas.getWidth() == width && canvas.getHeight() == height) {
                canvas.getRGB(0, 0, width, height, pixels, 0, width);
                return;
            }
        }
        Arrays.fill(pixels, 0);
    }

    private void logTransformSample() {
        for (DrawCall call : pending) {
            if (!beginTransformDiagnosticWindow(call.model)) {
                continue;
            }

            AffineTrans transform = call.layout.getAffineTrans();
            String matrix = transform.m00 + "," + transform.m01 + "," + transform.m02
                    + "," + transform.m03 + ";" + transform.m10 + "," + transform.m11
                    + "," + transform.m12 + "," + transform.m13 + ";"
                    + transform.m20 + "," + transform.m21 + "," + transform.m22
                    + "," + transform.m23;

            java.util.TreeMap<Integer, Integer> materials =
                    new java.util.TreeMap<Integer, Integer>();
            java.util.HashSet<String> textures = new java.util.HashSet<String>();
            for (TriangleCall triangleCall : call.triangles) {
                int material = triangleCall.triangle.material;
                Integer count = materials.get(material);
                materials.put(material, count == null ? 1 : count + 1);
                if (triangleCall.texture != null) {
                    textures.add(triangleCall.texture.getWidth()
                            + "x" + triangleCall.texture.getHeight());
                }
            }
            Micro3DModel.Triangle primitiveSample =
                    call.triangles.isEmpty() ? null : call.triangles.get(0).triangle;
            if (call.vertices.length == 12 && call.triangles.size() == 2
                    && textures.isEmpty() && primitiveSample != null
                    && (primitiveSample.material & 6) == 6
                    && beginPrimitiveDiagnosticWindow()) {
                System.out.println("[micro3d-primitive] material="
                        + primitiveSample.material + " color=0x"
                        + Integer.toHexString(primitiveSample.color)
                        + " effectTransparency=" + call.effect.isTransparency()
                        + " center=" + call.centerX + "," + call.centerY
                        + " vertices=" + call.vertices.length / 3
                        + " triangles=" + call.triangles.size());
            }
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int i = 2; i < call.vertices.length; i += 3) {
                minZ = Math.min(minZ, call.vertices[i]);
                maxZ = Math.max(maxZ, call.vertices[i]);
            }

            boolean perspective = call.layout.getProjection()
                    == Graphics3D.COMMAND_PERSPECTIVE_FOV
                    || call.layout.getProjection() == Graphics3D.COMMAND_PERSPECTIVE_WH;
            float near = perspective ? call.layout.getNear() : -65536.0f;
            float far = perspective ? call.layout.getFar() : 65536.0f;
            int nearCrossings = countDepthPlaneCrossings(call, near);
            int farCrossings = countDepthPlaneCrossings(call, far);
            System.out.println("[micro3d-view] model="
                    + Integer.toHexString(System.identityHashCode(call.model))
                    + " target=" + width + "x" + height
                    + " p=" + call.layout.getProjection()
                    + " center=" + call.centerX + "," + call.centerY
                    + " v=" + call.vertices.length / 3
                    + " t=" + call.triangles.size()
                    + " tex=" + textures
                    + " mat=" + materials
                    + " z=" + minZ + ".." + maxZ
                    + " near=" + near + " far=" + far
                    + " nearCross=" + nearCrossings + " farCross=" + farCrossings
                    + " m=[" + matrix + "]");
            String markerColor = markerPaletteColor(call, textures);
            if (markerColor != null) {
                System.out.println("[micro3d-marker] model="
                        + Integer.toHexString(System.identityHashCode(call.model))
                        + " selected=" + markerColor
                        + " paletteU=" + markerPaletteU(call)
                        + " transform=" + transform.m03 + ","
                        + transform.m13 + "," + transform.m23
                        + " vertices=" + call.vertices.length / 3
                        + " triangles=" + call.triangles.size());
            }
        }
    }

    private static String markerPaletteColor(DrawCall call,
            java.util.Set<String> textures) {
        if (call.vertices.length != 27 || call.triangles.size() != 8
                || textures.size() != 1 || !textures.contains("256x256")) {
            return null;
        }
        int paletteU = -1;
        for (TriangleCall triangleCall : call.triangles) {
            Micro3DModel.Triangle triangle = triangleCall.triangle;
            if (triangleCall.texture == null || triangle.material != 48
                    || triangle.u0 != triangle.u1 || triangle.u1 != triangle.u2
                    || triangle.v0 < 219 || triangle.v0 > 238
                    || triangle.v1 < 219 || triangle.v1 > 238
                    || triangle.v2 < 219 || triangle.v2 > 238) {
                return null;
            }
            if (paletteU < 0) {
                paletteU = triangle.u0;
            } else if (paletteU != triangle.u0) {
                return null;
            }
        }
        if (paletteU == 250) return "blue";
        if (paletteU == 252) return "red";
        if (paletteU == 255) return "green";
        return null;
    }

    private static int markerPaletteU(DrawCall call) {
        return call.triangles.isEmpty() ? -1 : call.triangles.get(0).triangle.u0;
    }

    private int countDepthPlaneCrossings(DrawCall call, float plane) {
        int crossings = 0;
        for (TriangleCall triangleCall : call.triangles) {
            Micro3DModel.Triangle triangle = triangleCall.triangle;
            int a = triangle.a * 3 + 2;
            int b = triangle.b * 3 + 2;
            int c = triangle.c * 3 + 2;
            float min = Math.min(call.vertices[a],
                    Math.min(call.vertices[b], call.vertices[c]));
            float max = Math.max(call.vertices[a],
                    Math.max(call.vertices[b], call.vertices[c]));
            if (min < plane && max > plane) {
                crossings++;
            }
        }
        return crossings;
    }

    private static synchronized boolean beginTransformDiagnosticWindow(Micro3DModel model) {
        long now = System.currentTimeMillis();
        Long last = lastTransformDiagnosticMs.get(model);
        if (last != null && now - last < 3000) {
            return false;
        }
        lastTransformDiagnosticMs.put(model, now);
        return true;
    }

    private static synchronized boolean beginPrimitiveDiagnosticWindow() {
        long now = System.currentTimeMillis();
        if (now - lastPrimitiveDiagnosticMs < 1500) {
            return false;
        }
        lastPrimitiveDiagnosticMs = now;
        return true;
    }

    private static synchronized boolean beginSubtractiveDiagnosticWindow() {
        long now = System.currentTimeMillis();
        if (now - lastSubtractiveDiagnosticMs < 1500) {
            return false;
        }
        lastSubtractiveDiagnosticMs = now;
        return true;
    }

    private void rasterize(ScreenVertex a, ScreenVertex b, ScreenVertex c,
                           Micro3DModel.Triangle triangle, BufferedImage texture,
                           Effect3D effect, int blendMode, boolean depthWrite) {
        if (a == null || b == null || c == null) {
            return;
        }
        double area = edge(a.x, a.y, b.x, b.y, c.x, c.y);
        if (!Double.isFinite(area) || Math.abs(area) < 0.0001) {
            return;
        }
        // J2ME Loader culls back faces unless the MBAC DOUBLE_FACE flag is set.
        // `area` is the inverse of the usual signed screen-space cross product;
        // the projection coordinates retain the OpenGL clip-space Y direction.
        if ((triangle.material & 16) == 0 && area > 0) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        if (maxX < minX || maxY < minY) {
            return;
        }

        double abx = b.worldX - a.worldX;
        double aby = b.worldY - a.worldY;
        double abz = b.worldZ - a.worldZ;
        double acx = c.worldX - a.worldX;
        double acy = c.worldY - a.worldY;
        double acz = c.worldZ - a.worldZ;
        double fallbackNX = aby * acz - abz * acy;
        double fallbackNY = abz * acx - abx * acz;
        double fallbackNZ = abx * acy - aby * acx;
        for (int y = minY; y <= maxY; y++) {
            double py = y + 0.5;
            int row = y * width;
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double wa = edge(b.x, b.y, c.x, c.y, px, py) / area;
                double wb = edge(c.x, c.y, a.x, a.y, px, py) / area;
                double wc = 1.0 - wa - wb;
                if (wa < -1e-6 || wb < -1e-6 || wc < -1e-6) {
                    continue;
                }
                float z = (float) (wa * a.depth + wb * b.depth + wc * c.depth);
                int index = row + x;
                if (z >= depth[index]) {
                    continue;
                }

                int color = triangle.color;
                if (texture != null) {
                    double u;
                    double v;
                    if (a.reciprocalZ != 0 && b.reciprocalZ != 0 && c.reciprocalZ != 0) {
                        double invZ = wa * a.reciprocalZ + wb * b.reciprocalZ
                                + wc * c.reciprocalZ;
                        if (invZ == 0) {
                            continue;
                        }
                        u = (wa * a.uOverZ + wb * b.uOverZ + wc * c.uOverZ) / invZ;
                        v = (wa * a.vOverZ + wb * b.vOverZ + wc * c.vOverZ) / invZ;
                    } else {
                        u = wa * triangle.u0 + wb * triangle.u1 + wc * triangle.u2;
                        v = wa * triangle.v0 + wb * triangle.v1 + wc * triangle.v2;
                    }
                    // A constant texel coordinate is invariant under perspective
                    // interpolation. Reconstructing it through float reciprocal-Z
                    // values can drift just below an integer (for example U=250
                    // becoming 249.99998), which samples an adjacent atlas stripe.
                    if (triangle.u0 == triangle.u1 && triangle.u1 == triangle.u2) {
                        u = triangle.u0;
                    }
                    if (triangle.v0 == triangle.v1 && triangle.v1 == triangle.v2) {
                        v = triangle.v0;
                    }
                    color = applyTextureAlpha(sample(texture, u, v), triangle.material);
                    if (color == 0) continue;
                }
                float shade = shade(
                        wa * a.normalX + wb * b.normalX + wc * c.normalX,
                        wa * a.normalY + wb * b.normalY + wc * c.normalY,
                        wa * a.normalZ + wb * b.normalZ + wc * c.normalZ,
                        fallbackNX, fallbackNY, fallbackNZ, triangle.material, effect);
                color = modulate(color, shade);
                int alpha = color >>> 24;
                if (alpha == 0) {
                    continue;
                }
                int destination = pixels[index];
                int blended = blend(color, destination, blendMode);
                if (blendMode == 6 && beginSubtractiveDiagnosticWindow()) {
                    System.out.println("[micro3d-subtract] xy=" + x + "," + y
                            + " src=0x" + Integer.toHexString(color)
                            + " dst=0x" + Integer.toHexString(destination)
                            + " out=0x" + Integer.toHexString(blended));
                }
                pixels[index] = blended;
                if (depthWrite && alpha == 255) {
                    depth[index] = z;
                }
            }
        }
    }

    private ScreenVertex project(float x, float y, float z, FigureLayout layout,
                                 int centerX, int centerY) {
        ScreenVertex result = new ScreenVertex();
        int projection = layout.getProjection();
        if (projection == Graphics3D.COMMAND_PERSPECTIVE_FOV
                || projection == Graphics3D.COMMAND_PERSPECTIVE_WH) {
            if (z < layout.getNear() || z > layout.getFar() || z <= 0) {
                return null;
            }
            double focal;
            double focalY;
            if (projection == Graphics3D.COMMAND_PERSPECTIVE_FOV) {
                double halfAngle = layout.getAngle() * Math.PI / 4096.0;
                focal = width / (2.0 * Math.tan(halfAngle));
                focalY = focal;
            } else {
                int projectionWidth = layout.getPerspectiveWidth();
                int projectionHeight = layout.getPerspectiveHeight();
                double widthFocal = projectionWidth == 0
                        ? Double.POSITIVE_INFINITY
                        : (double) layout.getNear() * width * 4096.0 / projectionWidth;
                double heightFocal = projectionHeight == 0
                        ? Double.POSITIVE_INFINITY
                        : (double) layout.getNear() * height * 4096.0 / projectionHeight;
                focal = widthFocal;
                focalY = heightFocal;
            }
            result.x = (float) (centerX + x * focal / z);
            result.y = (float) (centerY + y * focalY / z);
            float near = layout.getNear();
            float far = layout.getFar();
            double normalizedDepth = ((double) far / (far - near))
                    * (1.0 - (double) near / z);
            result.depth = (float) Math.max(0.0, Math.min(1.0, normalizedDepth));
            result.reciprocalZ = 1.0f / z;
        } else {
            double worldWidth;
            double worldHeight;
            if (projection == Graphics3D.COMMAND_PARALLEL_SIZE
                    && layout.getParallelWidth() > 0 && layout.getParallelHeight() > 0) {
                worldWidth = layout.getParallelWidth();
                worldHeight = layout.getParallelHeight();
            } else {
                worldWidth = width * (4096.0 / Math.max(1, layout.getScaleX()));
                worldHeight = height * (4096.0 / Math.max(1, layout.getScaleY()));
            }
            result.x = (float) (centerX + x * width / worldWidth);
            result.y = (float) (centerY + y * height / worldHeight);
            result.depth = z / 131072.0f + 0.5f;
            result.reciprocalZ = 0;
        }
        result.worldX = x;
        result.worldY = y;
        result.worldZ = z;
        if (!Float.isFinite(result.x) || !Float.isFinite(result.y)
                || !Float.isFinite(result.depth) || result.depth < 0 || result.depth > 1) {
            return null;
        }
        return result;
    }

    private ScreenVertex project(CameraVertex vertex, FigureLayout layout,
                                 int centerX, int centerY) {
        ScreenVertex result = project(vertex.x, vertex.y, vertex.z, layout, centerX, centerY);
        if (result != null && result.reciprocalZ != 0) {
            result.uOverZ = vertex.u * result.reciprocalZ;
            result.vOverZ = vertex.v * result.reciprocalZ;
        } else if (result != null) {
            result.uOverZ = vertex.u;
            result.vOverZ = vertex.v;
        }
        if (result != null) {
            result.normalX = vertex.normalX;
            result.normalY = vertex.normalY;
            result.normalZ = vertex.normalZ;
        }
        return result;
    }

    private float[] transformVertices(int[] source, AffineTrans transform) {
        float[] result = new float[source.length];
        for (int i = 0; i < source.length; i += 3) {
            float x = source[i];
            float y = source[i + 1];
            float z = source[i + 2];
            result[i] = (float) (((long) transform.m00 * (long) x
                    + (long) transform.m01 * (long) y
                    + (long) transform.m02 * (long) z) / 4096.0 + transform.m03);
            result[i + 1] = (float) (((long) transform.m10 * (long) x
                    + (long) transform.m11 * (long) y
                    + (long) transform.m12 * (long) z) / 4096.0 + transform.m13);
            result[i + 2] = (float) (((long) transform.m20 * (long) x
                    + (long) transform.m21 * (long) y
                    + (long) transform.m22 * (long) z) / 4096.0 + transform.m23);
        }
        return result;
    }

    private float shade(double nx, double ny, double nz,
                        double fallbackNX, double fallbackNY, double fallbackNZ,
                        int material, Effect3D effect) {
        Light light = effect == null ? null : effect.getLight();
        if ((material & 32) == 0 || light == null || light.getDirection() == null) {
            return 1.0f;
        }
        if (nx == 0 && ny == 0 && nz == 0) {
            nx = fallbackNX;
            ny = fallbackNY;
            nz = fallbackNZ;
        }
        double normalLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
        Vector3D direction = light.getDirection();
        double lightLength = Math.sqrt((double) direction.x * direction.x
                + (double) direction.y * direction.y + (double) direction.z * direction.z);
        if (normalLength == 0 || lightLength == 0) {
            return Math.max(0, Math.min(1, light.getAmbIntensity() / 4096.0f));
        }
        double dot = -(nx * direction.x + ny * direction.y + nz * direction.z)
                / (normalLength * lightLength);
        float ambient = Math.max(0, Math.min(1, light.getAmbIntensity() / 4096.0f));
        float directional = Math.max(light.getDirIntensity(),
                light.getParallelLightIntensity()) / 4096.0f;
        directional = Math.max(0, Math.min(1, directional));
        return Math.min(1, ambient + directional * (float) Math.max(0, dot));
    }

    static int sample(BufferedImage image, double u, double v) {
        int x = Math.max(0, Math.min(image.getWidth() - 1,
                (int) Math.floor(u)));
        int y = Math.max(0, Math.min(image.getHeight() - 1,
                (int) Math.floor(v)));
        return image.getRGB(x, y);
    }

    static int applyTextureAlpha(int color, int material) {
        if ((material & 1) != 0 && (color >>> 24) < 128) {
            return 0;
        }
        return color | 0xff000000;
    }

    private static int modulate(int color, float intensity) {
        int alpha = color >>> 24;
        int red = Math.min(255, Math.round(((color >>> 16) & 255) * intensity));
        int green = Math.min(255, Math.round(((color >>> 8) & 255) * intensity));
        int blue = Math.min(255, Math.round((color & 255) * intensity));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int blend(int source, int destination, int mode) {
        int alpha = source >>> 24;
        if (alpha == 255 && mode == 0) {
            return source;
        }
        if (mode == 2) {
            int outAlpha = (alpha + (destination >>> 24) + 1) >> 1;
            return (outAlpha << 24)
                    | (((((source >>> 16) & 255) + ((destination >>> 16) & 255)) >> 1) << 16)
                    | (((((source >>> 8) & 255) + ((destination >>> 8) & 255)) >> 1) << 8)
                    | (((source & 255) + (destination & 255)) >> 1);
        }
        if (mode == 4) {
            int outAlpha = Math.min(255, alpha + (destination >>> 24));
            return (outAlpha << 24)
                    | (Math.min(255, ((source >>> 16) & 255) + ((destination >>> 16) & 255)) << 16)
                    | (Math.min(255, ((source >>> 8) & 255) + ((destination >>> 8) & 255)) << 8)
                    | Math.min(255, (source & 255) + (destination & 255));
        }
        if (mode == 6) {
            return (destination & 0xff000000)
                    | (Math.max(0, ((destination >>> 16) & 255) - ((source >>> 16) & 255)) << 16)
                    | (Math.max(0, ((destination >>> 8) & 255) - ((source >>> 8) & 255)) << 8)
                    | Math.max(0, (destination & 255) - (source & 255));
        }
        if (alpha == 255) {
            return source;
        }
        int inverse = 255 - alpha;
        int red = ((((source >>> 16) & 255) * alpha)
                + (((destination >>> 16) & 255) * inverse) + 127) / 255;
        int green = ((((source >>> 8) & 255) * alpha)
                + (((destination >>> 8) & 255) * inverse) + 127) / 255;
        int blue = (((source & 255) * alpha) + ((destination & 255) * inverse) + 127) / 255;
        int outAlpha = Math.min(255, alpha + ((destination >>> 24) * inverse + 127) / 255);
        return (outAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static double edge(double ax, double ay, double bx, double by,
                               double px, double py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private void clearBuffers() {
        Arrays.fill(pixels, 0);
        Arrays.fill(depth, DEPTH_CLEAR);
    }

    private static FigureLayout snapshotLayout(FigureLayout source) {
        FigureLayout copy = new FigureLayout(new AffineTrans(source.getAffineTrans()),
                source.getScaleX(), source.getScaleY(),
                source.getCenterX(), source.getCenterY());
        int projection = source.getProjection();
        if (projection == Graphics3D.COMMAND_PERSPECTIVE_FOV) {
            copy.setPerspective(source.getNear(), source.getFar(), source.getAngle());
        } else if (projection == Graphics3D.COMMAND_PERSPECTIVE_WH) {
            copy.setPerspective(source.getNear(), source.getFar(),
                    source.getPerspectiveWidth(), source.getPerspectiveHeight());
        } else if (projection == Graphics3D.COMMAND_PARALLEL_SIZE) {
            copy.setParallelSize(source.getParallelWidth(), source.getParallelHeight());
        } else {
            copy.setScale(source.getScaleX(), source.getScaleY());
        }
        return copy;
    }

    private static Effect3D snapshotEffect(Effect3D source) {
        Effect3D copy = new Effect3D();
        copy.setTransparency(source.isTransparency());
        copy.setShadingType(source.getShadingType());
        Light light = source.getLight();
        if (light != null) {
            Vector3D direction = light.getDirection();
            Light lightCopy = new Light(direction == null ? null
                    : new Vector3D(direction.x, direction.y, direction.z),
                    light.getDirIntensity(), light.getAmbIntensity());
            lightCopy.setParallelLightIntensity(light.getParallelLightIntensity());
            Vector3D parallelDirection = light.getParallelLightDirection();
            if (parallelDirection != null) {
                lightCopy.setParallelLightDirection(new Vector3D(
                        parallelDirection.x, parallelDirection.y, parallelDirection.z));
            }
            copy.setLight(lightCopy);
        }
        return copy;
    }

    private float[] transformNormals(int[] source, AffineTrans transform) {
        if (source == null) return null;
        float[] result = new float[source.length];
        for (int i = 0; i < source.length; i += 3) {
            double x = ((long) transform.m00 * source[i]
                    + (long) transform.m01 * source[i + 1]
                    + (long) transform.m02 * source[i + 2]) / 4096.0;
            double y = ((long) transform.m10 * source[i]
                    + (long) transform.m11 * source[i + 1]
                    + (long) transform.m12 * source[i + 2]) / 4096.0;
            double z = ((long) transform.m20 * source[i]
                    + (long) transform.m21 * source[i + 1]
                    + (long) transform.m22 * source[i + 2]) / 4096.0;
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length != 0) {
                result[i] = (float) (x / length);
                result[i + 1] = (float) (y / length);
                result[i + 2] = (float) (z / length);
            }
        }
        return result;
    }

    private static final class DrawCall {
        final Micro3DModel model;
        final float[] vertices;
        final float[] normals;
        final List<TriangleCall> triangles;
        final FigureLayout layout;
        final Effect3D effect;
        final int centerX;
        final int centerY;
        final long depthGroup;

        DrawCall(Micro3DModel model, float[] vertices, float[] normals, List<TriangleCall> triangles,
                 FigureLayout layout, Effect3D effect, int centerX, int centerY,
                 long depthGroup) {
            this.model = model;
            this.vertices = vertices;
            this.normals = normals;
            this.triangles = triangles;
            this.layout = layout;
            this.effect = effect;
            this.centerX = centerX;
            this.centerY = centerY;
            this.depthGroup = depthGroup;
        }
    }

    private static final class TriangleCall {
        final Micro3DModel.Triangle triangle;
        final BufferedImage texture;

        TriangleCall(Micro3DModel.Triangle triangle, BufferedImage texture) {
            this.triangle = triangle;
            this.texture = texture;
        }
    }

    private static final class ScreenVertex {
        float x;
        float y;
        float depth;
        float reciprocalZ;
        double uOverZ;
        double vOverZ;
        float worldX;
        float worldY;
        float worldZ;
        float normalX;
        float normalY;
        float normalZ;
    }

    private static final class CameraVertex {
        final float x;
        final float y;
        final float z;
        final float normalX;
        final float normalY;
        final float normalZ;
        final float u;
        final float v;

        CameraVertex(float x, float y, float z, float normalX, float normalY,
                     float normalZ, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.u = u;
            this.v = v;
        }

        CameraVertex interpolateAtZ(CameraVertex other, float amount, float clippedZ) {
            return new CameraVertex(
                    x + (other.x - x) * amount,
                    y + (other.y - y) * amount,
                    clippedZ,
                    normalX + (other.normalX - normalX) * amount,
                    normalY + (other.normalY - normalY) * amount,
                    normalZ + (other.normalZ - normalZ) * amount,
                    u + (other.u - u) * amount,
                    v + (other.v - v) * amount);
        }
    }
}
