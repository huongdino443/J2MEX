import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

public final class M3GVertexBufferNullPositionsSmoke
{
    public static void main(String[] args)
    {
        VertexBuffer buffer = new VertexBuffer();
        VertexArray positions = new VertexArray(2, 3, 2);
        buffer.setPositions(positions, 0.5f, new float[] { 1f, 2f, 3f });
        if (buffer.getPositions(null) != positions || buffer.getVertexCount() != 2) {
            throw new AssertionError("setPositions did not retain the vertex array");
        }

        buffer.setPositions(null, 2f, new float[] { -1f, 4f, 0.25f });
        float[] scaleBias = new float[4];
        if (buffer.getPositions(scaleBias) != null || buffer.getVertexCount() != 0) {
            throw new AssertionError("setPositions(null) did not clear the vertex array");
        }
        if (scaleBias[0] != 2f || scaleBias[1] != -1f
                || scaleBias[2] != 4f || scaleBias[3] != 0.25f) {
            throw new AssertionError("setPositions(null) did not preserve scale and bias");
        }

        System.out.println("M3G_VERTEX_BUFFER_NULL_POSITIONS_PASS");
    }
}
