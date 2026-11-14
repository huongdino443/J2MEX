package com.mascotcapsule.micro3d.v3;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads every MBAC model in a supplied game JAR and checks its geometry. */
public final class Micro3DModelSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Micro3DModelSmoke game.jar [--materials]");
        }
        boolean showMaterials = args.length == 2 && "--materials".equals(args[1]);
        int models = 0;
        int triangles = 0;
        try (JarFile jar = new JarFile(args[0])) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".mbac")) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
                Micro3DModel model = Micro3DModel.parse(bytes);
                if (model.vertices.length == 0 || model.triangles.length == 0) {
                    throw new AssertionError("Empty geometry: " + entry.getName());
                }
                if ((bytes.length > 5 && (bytes[5] & 255) != 0)
                        && (model.normals == null || model.normals.length != model.vertices.length)) {
                    throw new AssertionError("Invalid normal data: " + entry.getName());
                }
                if (model.normals != null) {
                    for (int i = 0; i < model.normals.length; i += 3) {
                        double length = Math.sqrt((double) model.normals[i] * model.normals[i]
                                + (double) model.normals[i + 1] * model.normals[i + 1]
                                + (double) model.normals[i + 2] * model.normals[i + 2]);
                        if (length > 0 && (length < 3500 || length > 4700)) {
                            throw new AssertionError("Invalid normal magnitude: "
                                    + entry.getName() + " normal " + (i / 3) + " = " + length);
                        }
                    }
                }
                for (Micro3DModel.Triangle triangle : model.triangles) {
                    checkIndex(model, triangle.a, entry.getName());
                    checkIndex(model, triangle.b, entry.getName());
                    checkIndex(model, triangle.c, entry.getName());
                    if (triangle.face >= model.textureCount) {
                        throw new AssertionError("Texture face outside model: " + entry.getName());
                    }
                }
                models++;
                triangles += model.triangles.length;
                System.out.println(entry.getName() + ": " + (model.vertices.length / 3)
                        + " vertices, " + model.triangles.length + " triangles, "
                        + model.textureCount + " texture faces");
                if (showMaterials && entry.getName().matches("(?i).*(skybox|circuit|straight).*")) {
                    HashMap<String, Integer> materialCounts = new HashMap<String, Integer>();
                    for (Micro3DModel.Triangle triangle : model.triangles) {
                        String key = "face=" + triangle.face + ",material=" + triangle.material
                                + ",pattern=" + triangle.pattern;
                        Integer count = materialCounts.get(key);
                        materialCounts.put(key, count == null ? 1 : count + 1);
                    }
                    System.out.println("  " + materialCounts);
                }
            }
        }
        if (models < 10 || triangles < 500) {
            throw new AssertionError("Unexpectedly little Micro3D test data: "
                    + models + " models, " + triangles + " triangles");
        }
        System.out.println("Micro3DModelSmoke: " + models + " models, "
                + triangles + " triangles passed");
    }

    private static void checkIndex(Micro3DModel model, int index, String name) {
        if (index < 0 || index * 3 + 2 >= model.vertices.length) {
            throw new AssertionError("Invalid vertex index in " + name + ": " + index);
        }
    }
}
