package fixture;

public final class MemoryEditorFixture {
    public static int wallet = 10000;
    public int currentValue = 10000;
    public int[] counters = { 10000 };
    public short unsignedValue = (short) 60000;
    public char characterValue = (char) 60000;
    public short[] unsignedCounters = { (short) 60000 };
    public int[] manyCounters = new int[205];

    public MemoryEditorFixture() {
        java.util.Arrays.fill(manyCounters, 7777);
    }
}
