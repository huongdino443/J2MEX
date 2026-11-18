package javax.microedition.m3g;

import java.lang.reflect.Field;

public final class M3GGpuPayloadSmoke {
    public static void main(String[] args) throws Exception {
        M3GSoftwareRenderer renderer = new M3GSoftwareRenderer();
        Field stateField = M3GSoftwareRenderer.class.getDeclaredField("gpuState");
        Field parameterField = M3GSoftwareRenderer.class.getDeclaredField("gpuParameters");
        stateField.setAccessible(true);
        parameterField.setAccessible(true);
        int[] state = (int[]) stateField.get(renderer);
        float[] parameters = (float[]) parameterField.get(renderer);
        if (state.length != M3GNativeGpu.STATE_INT_COUNT || state.length < 17)
            throw new AssertionError("Native M3G state payload is shorter than its indexed fields");
        if (parameters.length != M3GNativeGpu.PARAMETER_FLOAT_COUNT || parameters.length < 46)
            throw new AssertionError("Native M3G payload is shorter than camera and transform matrices");

        short[] packed = new short[6];
        int count = M3GSoftwareRenderer.packTriangleIndices(
                4, new int[] {0, 1, 2, 3}, new int[] {4}, packed);
        int[] expected = {0, 1, 2, 2, 1, 3};
        if (count != expected.length) throw new AssertionError("Strip triangle count changed");
        for (int i = 0; i < count; i++)
            if ((packed[i] & 0xffff) != expected[i])
                throw new AssertionError("Strip winding changed at index " + i);

        short[] unsigned = new short[3];
        count = M3GSoftwareRenderer.packTriangleIndices(
                40002, new int[] {32768, 32769, 40001}, new int[] {3}, unsigned);
        int[] expectedUnsigned = {32768, 32769, 40001};
        if (count != expectedUnsigned.length) throw new AssertionError("Unsigned index count changed");
        for (int i = 0; i < count; i++)
            if ((unsigned[i] & 0xffff) != expectedUnsigned[i])
                throw new AssertionError("Unsigned 16-bit index changed at " + i);

        System.out.println("M3GGpuPayloadSmoke: camera matrices and unsigned 16-bit triangle indices passed");
    }
}
