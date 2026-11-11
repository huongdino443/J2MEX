package javax.microedition.m3g;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.microedition.lcdui.Image;
import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Loads a real JAR's M3G resources and checks skinned-mesh graph and pose behavior. */
public final class M3GSkinnedMeshSmoke {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Game JAR required");
        File jarFile = new File(args[0]);
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        platform.dataPath = Files.createTempDirectory("m3g-skinned-smoke-").toFile()
                .getCanonicalPath() + File.separator;
        platform.loader = new MIDletLoader(new URL[]{jarFile.toURI().toURL()});

        List<String> resources = new ArrayList<String>();
        try (ZipFile jar = new ZipFile(jarFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".m3g"))
                    resources.add(name);
            }
        }
        Collections.sort(resources);
        if (resources.contains("menuPlayer0.m3g"))
            verifyAnimatedTransformComponentSeparation("/menuPlayer0.m3g");
        if (resources.contains("menuPlayer0.m3g"))
            verifyFightFaceAttachment("/menuPlayer0.m3g", platform);
        int loadedSkins = 0, loadedBindings = 0;
        for (String resource : resources) {
            Object3D[] roots = Loader.load("/" + resource);
            Set<Object3D> seen = Collections.newSetFromMap(
                    new IdentityHashMap<Object3D, Boolean>());
            Deque<Object3D> pending = new ArrayDeque<Object3D>();
            Collections.addAll(pending, roots);
            while (!pending.isEmpty()) {
                Object3D value = pending.removeFirst();
                if (!seen.add(value)) continue;
                if (value instanceof SkinnedMesh) {
                    SkinnedMesh mesh = (SkinnedMesh)value;
                    check(mesh.getSkeleton() != null, resource + ": missing skeleton");
                    check(mesh.getSubmeshCount() > 0, resource + ": missing skinned submeshes");
                    float[] bindPose = mesh.positionsForRendering();
                    VertexBuffer vertices = mesh.getVertexBuffer();
                    for (int i = 0; i < vertices.getVertexCount(); i++) {
                        for (int axis = 0; axis < 3; axis++) {
                            float valueAtPose = bindPose[i * 3 + axis];
                            float original = vertices.position(i, axis);
                            check(Float.isFinite(valueAtPose), resource + ": non-finite skinned position");
                            check(Math.abs(valueAtPose - original) < 0.02f,
                                    resource + ": bind-pose skinning moved vertex " + i);
                        }
                    }
                    renderSkinnedMesh(mesh);

                    List<SkinnedMesh.BoneBinding> bindings = mesh.boneBindings();
                    check(mesh.getMaxBoneInfluencesPerVertex() <= 4,
                            resource + ": vertex has more than four bone influences");
                    IdentityHashMap<Node, Boolean> checkedBones =
                            new IdentityHashMap<Node, Boolean>();
                    SkinnedMesh.BoneBinding animatedBinding = null;
                    int animatedVertex = -1;
                    int count = mesh.getWeightedVertexCount();
                    for (SkinnedMesh.BoneBinding binding : bindings) {
                        if (checkedBones.put(binding.bone, Boolean.TRUE) != null) continue;
                        int[] indices = new int[count];
                        float[] weights = new float[count];
                        int influenced = mesh.getBoneVertices(binding.bone, indices, weights);
                        check(influenced >= 0 && influenced <= count,
                                resource + ": invalid bone vertex count");
                        for (int i = 0; i < influenced; i++)
                            check(weights[i] >= 0f && weights[i] <= 1f,
                                    resource + ": bone weight outside normalized range");
                        if (animatedBinding == null) {
                            for (int i = 0; i < influenced; i++) {
                                if (indices[i] < vertices.getVertexCount()) {
                                    animatedBinding = binding;
                                    animatedVertex = indices[i];
                                    break;
                                }
                            }
                        }
                        Transform bind = new Transform();
                        mesh.getBoneTransform(binding.bone, bind);
                        loadedBindings++;
                    }
                    if (animatedBinding != null) {
                        float[] translation = new float[3];
                        animatedBinding.bone.getTranslation(translation);
                        animatedBinding.bone.setTranslation(translation[0] + 0.5f,
                                translation[1], translation[2]);
                        float[] posed = mesh.positionsForRendering();
                        animatedBinding.bone.setTranslation(translation[0],
                                translation[1], translation[2]);
                        boolean moved = false;
                        for (int axis = 0; axis < 3; axis++)
                            moved |= Math.abs(posed[animatedVertex * 3 + axis]
                                    - bindPose[animatedVertex * 3 + axis]) > 0.001f;
                        check(moved, resource + ": animated bone did not deform its vertices");
                    }

                    SkinnedMesh copy = (SkinnedMesh)mesh.duplicate();
                    check(copy != mesh && copy.getSkeleton() != mesh.getSkeleton(),
                            resource + ": SkinnedMesh duplicate aliased its skeleton");
                    check(copy.getBoneBindingCount() == mesh.getBoneBindingCount(),
                            resource + ": duplicate lost bone bindings");

                    loadedBindings += bindings.size() - checkedBones.size();
                    loadedSkins++;
                }
                pending.addAll(M3GGraph.references(value));
            }
        }
        check(loadedSkins > 0, "JAR has no reachable SkinnedMesh resources");
        verifyInfluenceLimitAndMerging();
        verifyLinearOrientationNormalization();
        System.out.println("M3GSkinnedMeshSmoke: " + checks + " checks passed; "
                + resources.size() + " resources, " + loadedSkins + " skinned meshes, "
                + loadedBindings + " bone bindings");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void verifyAnimatedTransformComponentSeparation(String resource)
            throws Exception {
        Object3D[] roots = Loader.load(resource);
        World world = null;
        SkinnedMesh mesh = null;
        Set<Object3D> seen = Collections.newSetFromMap(
                new IdentityHashMap<Object3D, Boolean>());
        Deque<Object3D> pending = new ArrayDeque<Object3D>();
        Collections.addAll(pending, roots);
        while (!pending.isEmpty()) {
            Object3D value = pending.removeFirst();
            if (!seen.add(value)) continue;
            if (value instanceof World) world = (World)value;
            if (value instanceof SkinnedMesh) mesh = (SkinnedMesh)value;
            pending.addAll(M3GGraph.references(value));
        }
        check(world != null && mesh != null, resource + ": missing world or skinned mesh");

        Node animatedRoot = mesh.getSkeleton().getChild(0);
        check(animatedRoot.getAnimationTrackCount() > 0,
                resource + ": expected an animated skeleton root");
        float[] translation = new float[3];
        animatedRoot.getTranslation(translation);
        check(Math.abs(translation[1] - 3.5f) < 0.02f,
                resource + ": serialized root translation was not kept as a component");
        Transform general = new Transform();
        animatedRoot.getTransform(general);
        float[] matrix = new float[16];
        general.get(matrix);
        check(Math.abs(matrix[7]) < 0.02f,
                resource + ": component translation leaked into the generic matrix");

        world.animate(0);
        animatedRoot.getTranslation(translation);
        check(translation[1] > 3.4f && translation[1] < 3.6f,
                resource + ": animation replaced root translation incorrectly");
        Transform composite = new Transform();
        animatedRoot.getCompositeTransform(composite);
        composite.get(matrix);
        check(matrix[7] > 3.4f && matrix[7] < 3.6f,
                resource + ": root translation was applied twice");

        float[] posed = mesh.positionsForRendering();
        VertexBuffer vertices = mesh.getVertexBuffer();
        float maxDelta = 0f;
        for (int i = 0; i < vertices.getVertexCount(); i++) {
            for (int axis = 0; axis < 3; axis++)
                maxDelta = Math.max(maxDelta,
                        Math.abs(posed[i * 3 + axis] - vertices.position(i, axis)));
        }
        check(maxDelta < 0.05f,
                resource + ": rest animation moved the mesh by " + maxDelta);
    }

    private static void verifyFightFaceAttachment(String resource, MobilePlatform platform)
            throws Exception {
        Object3D[] roots = Loader.load(resource);
        World world = null;
        Group model = null;
        for (Object3D value : roots) {
            if (value instanceof World) {
                world = (World)value;
                if (world.getChildCount() > 0 && world.getChild(0) instanceof Group)
                    model = (Group)world.getChild(0);
            }
        }
        check(world != null && model != null, resource + ": missing model scene graph");
        Object3D faceObject = model.find(3);
        check(faceObject instanceof Mesh, resource + ": face attachment mesh ID 3 is missing");
        Mesh face = (Mesh)faceObject;
        check(face.getParent() != null, resource + ": face mesh is not attached to a bone");

        Class<?> helper = platform.loader.loadClass("h");
        java.lang.reflect.Method applyTextures = helper.getMethod("a",
                Node.class, Image.class, Image.class);
        applyTextures.invoke(null, model, Image.createImage("/face0.png"),
                Image.createImage("/body0.png"));
        world.animate(1200);

        int[] withFace = renderFightModel(model);
        face.setRenderingEnable(false);
        int[] withoutFace;
        try {
            withoutFace = renderFightModel(model);
        } finally {
            face.setRenderingEnable(true);
        }
        int changedPixels = 0;
        for (int i = 0; i < withFace.length; i++)
            if (withFace[i] != withoutFace[i]) changedPixels++;
        check(changedPixels > 0,
                resource + ": rendering the scene did not include its face-bone attachment");
    }

    private static int[] renderFightModel(Group model) {
        Image target = Image.createImage(240, 320);
        Graphics3D graphics = new Graphics3D();
        graphics.bindTarget(target.getGraphics(), true, Graphics3D.OVERWRITE);
        try {
            graphics.setViewport(0, 0, 240, 320);
            Camera camera = new Camera();
            camera.setPerspective(60f, 0.75f, 1f, 100000f);
            graphics.setCamera(camera, new Transform());
            Transform modelTransform = new Transform();
            modelTransform.postTranslate(2f, -4f, -9f);
            graphics.render(model, modelTransform);
        } finally {
            graphics.releaseTarget();
        }
        int[] pixels = new int[240 * 320];
        target.getRGB(pixels, 0, 240, 0, 0, 240, 320);
        return pixels;
    }

    private static void renderSkinnedMesh(SkinnedMesh mesh) {
        Image target = Image.createImage(64, 64);
        Graphics3D graphics = new Graphics3D();
        graphics.bindTarget(target.getGraphics(), true, Graphics3D.OVERWRITE);
        try {
            graphics.setViewport(0, 0, 64, 64);
            Camera camera = new Camera();
            camera.setPerspective(45f, 1f, 0.1f, 10000f);
            graphics.setCamera(camera, new Transform());
            graphics.render(mesh, new Transform());
        } finally {
            graphics.releaseTarget();
        }
        check(true, "SkinnedMesh renderer path completed");
    }

    private static void verifyInfluenceLimitAndMerging() {
        VertexArray positions = new VertexArray(3, 3, VertexArray.SHORT);
        positions.set(0, 3, new short[]{0, 0, 0, 100, 0, 0, 0, 100, 0});
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 1f, new float[]{0f, 0f, 0f});
        IndexBuffer triangles = new TriangleStripArray(new int[]{0, 1, 2}, new int[]{3});
        Group skeleton = new Group();
        Group[] bones = new Group[5];
        for (int i = 0; i < bones.length; i++) {
            bones[i] = new Group();
            skeleton.addChild(bones[i]);
        }
        SkinnedMesh mesh = new SkinnedMesh(vertices, triangles, new Appearance(), skeleton);
        mesh.addTransform(bones[0], 100, 0, 3);
        mesh.addTransform(bones[1], 90, 0, 3);
        mesh.addTransform(bones[2], 80, 0, 3);
        mesh.addTransform(bones[3], 70, 0, 3);
        mesh.addTransform(bones[4], 10, 0, 3);
        check(mesh.getMaxBoneInfluencesPerVertex() == 4,
                "M3G must retain at most four influences per vertex");
        int[] indices = new int[3];
        float[] weights = new float[3];
        check(mesh.getBoneVertices(bones[4], indices, weights) == 0,
                "M3G must discard a weaker fifth influence");
        mesh.addTransform(bones[0], 50, 0, 3);
        int count = mesh.getBoneVertices(bones[0], indices, weights);
        check(count == 3, "Repeated ranges for one bone must merge");
        check(Math.abs(weights[0] - (150f / 390f)) < 0.01f,
                "Merged bone weights must be normalized against retained influences");
    }

    private static void verifyLinearOrientationNormalization() {
        Group target = new Group();
        KeyframeSequence sequence = new KeyframeSequence(2, 4, KeyframeSequence.LINEAR);
        sequence.setDuration(100);
        sequence.setKeyframe(0, 0, new float[]{0f, 0f, 0f, 1f});
        sequence.setKeyframe(1, 100, new float[]{0f, 0f, 1f, 0f});
        AnimationController controller = new AnimationController();
        controller.setPosition(0f, 0);
        controller.setActiveInterval(0, 100);
        AnimationTrack track = new AnimationTrack(sequence, AnimationTrack.ORIENTATION);
        track.setController(controller);
        target.addAnimationTrack(track);

        target.animate(50);
        float[] angleAxis = new float[4];
        target.getOrientation(angleAxis);
        check(Math.abs(angleAxis[0] - 90f) < 0.5f
                        && Math.abs(Math.abs(angleAxis[3]) - 1f) < 0.01f,
                "LINEAR quaternion samples must be normalized before applying orientation");
    }
}
