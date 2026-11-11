import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.Transform;

public final class M3GTransformableSemanticsSmoke {
    public static void main(String[] args) {
        Camera camera = new Camera();
        camera.setTranslation(12, 5, 16);

        // Battle Boats sets the camera position, then installs a rotation-only
        // matrix each frame. JSR-184 keeps those states independent.
        Transform rotation = new Transform();
        rotation.postRotate(27, 0, 1, 0);
        camera.setTransform(rotation);
        assertTranslation(camera, 12, 5, 16, "setTransform must preserve camera translation");

        float[] matrix = new float[16];
        Transform composite = new Transform();
        camera.getCompositeTransform(composite);
        composite.get(matrix);
        check(Math.abs(matrix[0] - 1f) > 0.01f, "rotation matrix was lost from camera composite");

        float[] raw = new float[16];
        Transform generic = new Transform();
        camera.getTransform(generic);
        generic.get(raw);
        check(Math.abs(raw[3]) < 0.0001f && Math.abs(raw[7]) < 0.0001f
                && Math.abs(raw[11]) < 0.0001f,
                "getTransform should return the generic matrix, not component translation");

        camera.setTranslation(-8, 3, 21);
        camera.setTransform(rotation);
        assertTranslation(camera, -8, 3, 21, "camera translation must follow successive game positions");

        camera.setScale(2, 3, 4);
        float[] scale = new float[3];
        camera.getScale(scale);
        check(close(scale[0], 2) && close(scale[1], 3) && close(scale[2], 4),
                "scale components must remain independent from generic transform");

        Group parent = new Group();
        parent.setTranslation(100, 1, -4);
        parent.addChild(camera);
        camera.getCompositeTransform(composite);
        composite.get(matrix);
        check(close(matrix[3], 92) && close(matrix[7], 4) && close(matrix[11], 17),
                "parent, component translation, and generic transform did not compose");

        camera.setTransform(null);
        camera.getCompositeTransform(composite);
        composite.get(matrix);
        check(close(matrix[3], 92) && close(matrix[7], 4) && close(matrix[11], 17),
                "null generic transform must not clear parent or component translation");
        System.out.println("M3GTransformableSemanticsSmoke: component and generic transforms remain independent");
    }

    private static void assertTranslation(Camera camera, float x, float y, float z, String message) {
        Transform transform = new Transform();
        camera.getCompositeTransform(transform);
        float[] matrix = new float[16];
        transform.get(matrix);
        check(close(matrix[3], x) && close(matrix[7], y) && close(matrix[11], z), message);
    }

    private static boolean close(float a, float b) { return Math.abs(a - b) < 0.0001f; }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
