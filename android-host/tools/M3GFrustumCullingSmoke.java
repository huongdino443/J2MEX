package javax.microedition.m3g;

public final class M3GFrustumCullingSmoke {
    public static void main(String[] args) {
        float[] identity = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
        check(!outside(identity, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f),
                "inside AABB was culled");
        check(outside(identity, 1.2f, -0.2f, -0.2f, 1.8f, 0.2f, 0.2f),
                "right-side AABB was not culled");
        check(!outside(identity, 0.9f, -0.2f, -0.2f, 1.1f, 0.2f, 0.2f),
                "AABB crossing the right plane was culled");
        check(outside(identity, -0.2f, -0.2f, -1.8f, 0.2f, 0.2f, -1.2f),
                "AABB beyond the near plane was not culled");
        check(!outside(identity, -0.2f, -0.2f, -1.1f, 0.2f, 0.2f, -0.9f),
                "AABB crossing the near plane was culled");

        float[] nonFinite = identity.clone();
        nonFinite[0] = Float.NaN;
        check(!outside(nonFinite, 2f, -0.2f, -0.2f, 3f, 0.2f, 0.2f),
                "non-finite transforms must conservatively remain visible");

        VertexArray positions = new VertexArray(2, 3, VertexArray.SHORT);
        positions.set(0, 2, new short[] { -1, -2, -3, 2, 3, 4 });
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 2f, new float[] { 1f, 2f, 3f });
        float[] bounds = new float[6];
        check(vertices.getPositionBounds(bounds), "missing position bounds");
        checkBounds(bounds, -1f, -2f, -3f, 5f, 8f, 11f);
        positions.set(0, 2, new short[] { 10, 10, 10, 12, 12, 12 });
        check(vertices.getPositionBounds(bounds), "missing updated position bounds");
        checkBounds(bounds, 21f, 22f, 23f, 25f, 26f, 27f);

        VertexArray triangle = new VertexArray(3, 3, VertexArray.SHORT);
        triangle.set(0, 3, new short[] { -1, -1, 0, 1, -1, 0, 0, 1, 0 });
        VertexBuffer visibleVertices = new VertexBuffer();
        visibleVertices.setPositions(triangle, 1f, null);
        Camera camera = new Camera();
        camera.setPerspective(60f, 640f / 360f, 0.1f, 100f);
        Transform cameraWorld = new Transform();
        Transform visibleModel = new Transform();
        visibleModel.postTranslate(0f, 0f, -5f);
        M3GFrustumCulling culling = new M3GFrustumCulling();
        check(!culling.shouldCull(visibleVertices, visibleModel, camera,
                cameraWorld, 640, 360), "visible mesh was culled");
        Transform outsideModel = new Transform();
        outsideModel.postTranslate(100f, 0f, -5f);
        check(culling.shouldCull(visibleVertices, outsideModel, camera,
                cameraWorld, 640, 360), "off-screen mesh was not culled");

        if (Boolean.getBoolean("j2mex.render.profile"))
            exerciseReuseProfile();

        System.out.println("M3GFrustumCullingSmoke: conservative clip tests and cached-bound invalidation passed");
    }

    private static void exerciseReuseProfile() {
        VertexArray positions = new VertexArray(3, 3, VertexArray.SHORT);
        short[] triangle = { -1, -1, 0, 1, -1, 0, 0, 1, 0 };
        positions.set(0, 3, triangle);
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 1f, null);
        Camera camera = new Camera();
        camera.setPerspective(60f, 640f / 360f, 0.1f, 100f);
        Transform cameraWorld = new Transform();
        Transform model = new Transform();
        model.postTranslate(0f, 0f, -5f);
        M3GFrustumCulling profiler = new M3GFrustumCulling();
        for (int target = 0; target < 120; target++) {
            profiler.beginTarget();
            if (target == 23) positions.set(0, 3, triangle);
            check(!profiler.shouldCull(vertices, model, camera,
                    cameraWorld, 640, 360), "reuse profiling changed visible output");
            profiler.endTarget();
        }
    }

    private static boolean outside(float[] matrix, float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        return M3GFrustumCulling.isAabbOutsideClipVolume(
                new float[] { minX, minY, minZ, maxX, maxY, maxZ }, matrix);
    }

    private static void checkBounds(float[] actual, float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        float[] expected = { minX, minY, minZ, maxX, maxY, maxZ };
        for (int i = 0; i < expected.length; i++)
            if (Math.abs(actual[i] - expected[i]) > 0.0001f)
                throw new AssertionError("Position bound " + i + ": expected "
                        + expected[i] + ", got " + actual[i]);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
