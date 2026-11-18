package javax.microedition.m3g;

public final class M3GGpuVertexPayloadSmoke
{
    public static void main(String[] args)
    {
        int[] payload = new int[9];
        M3GSoftwareRenderer.packGpuVertexWords(
                payload, 0,
                1.25f, -2.5f, 3.75f, 1f, 16.5f, -8.25f,
                0.5f, 0.25f, 1f, 0.75f, 123.5f);

        require(Float.intBitsToFloat(payload[0]) == 1.25f, "x position changed");
        require(Float.intBitsToFloat(payload[1]) == -2.5f, "y position changed");
        require(Float.intBitsToFloat(payload[2]) == 3.75f, "z position changed");
        require(Float.intBitsToFloat(payload[3]) == 1f, "clip w changed");
        require(Float.intBitsToFloat(payload[4]) == 16.5f, "u coordinate changed");
        require(Float.intBitsToFloat(payload[5]) == -8.25f, "v coordinate changed");
        require((payload[6] & 0xffff) == 32768, "red 16-bit normalization changed");
        require(((payload[6] >>> 16) & 0xffff) == 16384,
                "green 16-bit normalization changed");
        require((payload[7] & 0xffff) == 65535, "blue clamp changed");
        require(((payload[7] >>> 16) & 0xffff) == 49151,
                "alpha 16-bit normalization changed");
        require(Float.intBitsToFloat(payload[8]) == 123.5f, "fog distance changed");
        require(payload.length * Integer.BYTES == 36,
                "packed vertex stride should be 36 bytes");
        require(M3GSoftwareRenderer.packNormalizedColorPair(Float.NaN, 2f)
                == 0xffff0000, "non-finite and out-of-range colors were not clamped");

        System.out.println("M3GGpuVertexPayloadSmoke: 36-byte vertex payload and RGBA16 packing passed");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }
}
