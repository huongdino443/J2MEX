package javax.microedition.m3g;

public final class M3GGpuIndexCacheSmoke
{
    public static void main(String[] args)
    {
        M3GSoftwareRenderer renderer = new M3GSoftwareRenderer();
        int[] sourceIndices = {0, 1, 2, 3, 4, 5};
        int[] stripLengths = {3, 3};
        TriangleStripArray triangles =
                new TriangleStripArray(sourceIndices, stripLengths);
        int[] copiedIndices = new int[sourceIndices.length];
        triangles.getIndices(copiedIndices);
        int[] copiedStrips = triangles.stripLengths();

        M3GSoftwareRenderer.GpuIndexData first =
                renderer.cachedGpuTriangleIndices(
                        triangles, 6, copiedIndices, copiedStrips);
        require(first != null, "expected a cacheable triangle strip");
        require(first.indexCount == 6, "unexpected packed index count");
        require(first.indices[0] == 0 && first.indices[1] == 1
                && first.indices[2] == 2 && first.indices[3] == 3
                && first.indices[4] == 4 && first.indices[5] == 5,
                "packed strip indices changed");

        M3GSoftwareRenderer.GpuIndexData repeated =
                renderer.cachedGpuTriangleIndices(
                        triangles, 6, copiedIndices, copiedStrips);
        require(repeated == first, "same immutable buffer was not reused");

        M3GSoftwareRenderer.GpuIndexData differentVertexCount =
                renderer.cachedGpuTriangleIndices(
                        triangles, 5, copiedIndices, copiedStrips);
        require(differentVertexCount != first,
                "vertex-count-dependent packed data was reused");
        require(differentVertexCount.indexCount == 3,
                "invalid triangle indices were not filtered");

        int[] largeIndices = new int[2050];
        for (int i = 0; i < largeIndices.length; i++) largeIndices[i] = i;
        TriangleStripArray large =
                new TriangleStripArray(largeIndices, new int[] {largeIndices.length});
        M3GSoftwareRenderer.GpuIndexData oversized =
                renderer.cachedGpuTriangleIndices(
                        large, largeIndices.length, largeIndices,
                        new int[] {largeIndices.length});
        require(oversized == null, "oversized buffers should use the uncached path");

        System.out.println("M3G GPU index cache smoke passed");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
