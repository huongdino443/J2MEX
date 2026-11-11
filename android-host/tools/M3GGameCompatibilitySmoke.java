package javax.microedition.m3g;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.microedition.lcdui.Image;
import org.recompile.mobile.MIDletLoader;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Exercises the M3G assets and morphing path used by City Bloxx and Snake III. */
public final class M3GGameCompatibilitySmoke {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("City JAR, Snake JAR and output directory required");
        File output = new File(args[2]);
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Could not create output directory");
        cityBloxx(new File(args[0]), output);
        snake(new File(args[1]));
        polygonModeDuplicate();
        syntheticMorphRender();
        rootNodeTransformSemantics();
        System.out.println("M3GGameCompatibilitySmoke: " + checks
                + " checks passed; City Bloxx world and Snake III morph meshes loaded");
    }

    private static void cityBloxx(File jar, File output) throws Exception {
        installLoader(jar);
        Object3D[] roots = Loader.load("/90");
        World world = findWorld(roots);
        check(world != null, "City Bloxx /90 resource has no World");
        check(world.getActiveCamera() != null, "City Bloxx world has no active camera");
        int meshes = count(world, Mesh.class);
        check(meshes >= 10, "City Bloxx world did not expose its model meshes: " + meshes);
        check(world.find(31) instanceof Mesh, "City Bloxx named mesh lookup failed");

        Image target = Image.createImage(240, 320);
        Graphics3D graphics = new Graphics3D();
        graphics.bindTarget(target.getGraphics(), true, Graphics3D.OVERWRITE);
        graphics.setViewport(0, 0, 240, 320);
        graphics.render(world);
        graphics.releaseTarget();
        int[] pixels = new int[240 * 320];
        target.getRGB(pixels, 0, 240, 0, 0, 240, 320);
        Set<Integer> colors = new java.util.HashSet<Integer>();
        for (int pixel : pixels) colors.add(pixel);
        check(colors.size() > 50, "City Bloxx M3G world rendered too few colors: " + colors.size());

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                240, 320, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 240, 320, pixels, 0, 240);
        javax.imageio.ImageIO.write(image, "png", new File(output, "citybloxx-m3g-world.png"));
        System.out.println("City Bloxx M3G: " + meshes + " meshes, " + colors.size() + " rendered colors");
    }

    private static void snake(File jar) throws Exception {
        installLoader(jar);
        Object3D[] snakeRoots = Loader.load("/snake.m3g");
        Object3D[] obstacleRoots = Loader.load("/obstacles.m3g");
        Object3D[] wallRoots = Loader.load("/walls.m3g");
        check(findWorld(snakeRoots) != null, "Snake III snake.m3g has no World");
        check(findWorld(obstacleRoots) != null, "Snake III obstacles.m3g has no World");
        check(findWorld(wallRoots) != null, "Snake III walls.m3g has no World");

        MorphingMesh morph = findMorph(snakeRoots);
        check(morph != null, "Snake III snake.m3g has no MorphingMesh");
        check(morph.getMorphTargetCount() > 0, "Snake III morph mesh has no targets");
        for (int i = 0; i < morph.getMorphTargetCount(); i++)
            check(morph.getMorphTarget(i) != null, "Snake III morph target " + i + " is missing");
        for (Object3D root : snakeRoots)
            for (int time = 0; time <= 5000; time += 250) root.animate(time);

        float[] weights = new float[morph.getMorphTargetCount()];
        morph.getWeights(weights);
        MorphingMesh copy = (MorphingMesh)morph.duplicate();
        check(copy.getMorphTargetCount() == morph.getMorphTargetCount(),
                "MorphingMesh duplication lost its targets");
        float[] copiedWeights = new float[weights.length];
        copy.getWeights(copiedWeights);
        for (int i = 0; i < weights.length; i++)
            check(Float.floatToIntBits(weights[i]) == Float.floatToIntBits(copiedWeights[i]),
                    "MorphingMesh duplication lost weight " + i);

        Texture2D texture = findTexture(snakeRoots);
        check(texture != null, "Snake III snake.m3g has no Texture2D");
        texture.setBlendColor(0x123456);
        texture.setBlending(Texture2D.FUNC_BLEND);
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
        texture.setWrapping(Texture2D.WRAP_REPEAT, Texture2D.WRAP_CLAMP);
        texture.setTranslation(3f, 4f, 5f);
        texture.setScale(2f, 3f, 4f);
        Texture2D textureCopy = (Texture2D)texture.duplicate();
        check(textureCopy != texture, "Texture2D duplicate returned the original object");
        check(textureCopy.getImage() == texture.getImage(), "Texture2D duplicate did not retain its shared Image2D");
        check(textureCopy.getBlendColor() == texture.getBlendColor(), "Texture2D duplicate lost blend color");
        check(textureCopy.getBlending() == texture.getBlending(), "Texture2D duplicate lost blending mode");
        check(textureCopy.getLevelFilter() == texture.getLevelFilter()
                        && textureCopy.getImageFilter() == texture.getImageFilter(),
                "Texture2D duplicate lost filtering");
        check(textureCopy.getWrappingS() == texture.getWrappingS()
                        && textureCopy.getWrappingT() == texture.getWrappingT(),
                "Texture2D duplicate lost wrapping");
        float[] sourceTranslation = new float[3], copiedTranslation = new float[3];
        float[] sourceScale = new float[3], copiedScale = new float[3];
        texture.getTranslation(sourceTranslation); textureCopy.getTranslation(copiedTranslation);
        texture.getScale(sourceScale); textureCopy.getScale(copiedScale);
        for (int i = 0; i < 3; i++) {
            check(Float.floatToIntBits(sourceTranslation[i]) == Float.floatToIntBits(copiedTranslation[i]),
                    "Texture2D duplicate lost translation component " + i);
            check(Float.floatToIntBits(sourceScale[i]) == Float.floatToIntBits(copiedScale[i]),
                    "Texture2D duplicate lost scale component " + i);
        }
        System.out.println("Snake III M3G: " + morph.getMorphTargetCount()
                + " morph targets; texture duplication and snake, obstacles, walls loaded");
    }

    private static void syntheticMorphRender() throws Exception {
        VertexArray basePositions = new VertexArray(3, 3, VertexArray.SHORT);
        basePositions.set(0, 3, new short[]{-1, -1, 0, 1, -1, 0, 0, 1, 0});
        VertexArray targetPositions = new VertexArray(3, 3, VertexArray.SHORT);
        targetPositions.set(0, 3, new short[]{0, -1, 0, 2, -1, 0, 1, 1, 0});
        VertexBuffer base = new VertexBuffer();
        base.setPositions(basePositions, 1, null);
        VertexBuffer target = new VertexBuffer();
        target.setPositions(targetPositions, 1, null);
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon);
        Material material = new Material();
        material.setColor(Material.DIFFUSE, 0xff40a0ff);
        appearance.setMaterial(material);
        MorphingMesh mesh = new MorphingMesh(base, new VertexBuffer[]{target},
                new TriangleStripArray(0, new int[]{3}), appearance);

        mesh.setWeights(new float[]{0.5f});
        check(Math.abs(mesh.positionForRendering(0, 0) + 0.5f) < 0.001f,
                "Morph weights did not interpolate base and target positions");
        int[] before = renderTriangle(mesh);
        mesh.setWeights(new float[]{1});
        int[] after = renderTriangle(mesh);
        long beforeX = centroidX(before);
        long afterX = centroidX(after);
        check(afterX > beforeX + 10, "Morph weight did not move rendered geometry: " + beforeX + " -> " + afterX);

        try {
            mesh.setWeights(new float[0]);
            throw new AssertionError("Wrong morph weight count was accepted");
        } catch (IllegalArgumentException expected) { checks++; }
    }

    private static void rootNodeTransformSemantics() throws Exception {
        Transform external = new Transform();
        external.postTranslate(0.5f, 0, 0);

        Mesh rootMesh = createTriangleMesh();
        rootMesh.setTranslation(1, 0, 0);
        long rootCenter = centroidX(renderNode(rootMesh, external));
        check(rootCenter >= 73 && rootCenter <= 83,
                "render(Node, Transform) combined the root mesh transform with the supplied transform: "
                        + rootCenter);

        Group rootGroup = new Group();
        rootGroup.setTranslation(1, 0, 0);
        Mesh childMesh = createTriangleMesh();
        childMesh.setTranslation(1, 0, 0);
        rootGroup.addChild(childMesh);
        long childCenter = centroidX(renderNode(rootGroup, external));
        check(childCenter > rootCenter + 25,
                "render(Node, Transform) failed to retain child transforms: "
                        + rootCenter + " -> " + childCenter);
    }

    private static Mesh createTriangleMesh() {
        VertexArray positions = new VertexArray(3, 3, VertexArray.SHORT);
        positions.set(0, 3, new short[]{-1, -1, 0, 1, -1, 0, 0, 1, 0});
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 0.3f, null);
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon);
        Material material = new Material();
        material.setColor(Material.DIFFUSE, 0xff40a0ff);
        appearance.setMaterial(material);
        return new Mesh(vertices, new TriangleStripArray(0, new int[]{3}), appearance);
    }

    private static int[] renderNode(Node node, Transform external) throws Exception {
        Image target = Image.createImage(120, 120);
        Graphics3D graphics = new Graphics3D();
        graphics.bindTarget(target.getGraphics(), true, Graphics3D.OVERWRITE);
        graphics.setViewport(0, 0, 120, 120);
        Camera camera = new Camera();
        camera.setPerspective(45, 1, 0.1f, 20);
        Transform cameraWorld = new Transform();
        cameraWorld.postTranslate(0, 0, 4);
        graphics.setCamera(camera, cameraWorld);
        Background background = new Background();
        background.setColor(0xff000000);
        graphics.clear(background);
        graphics.render(node, external);
        graphics.releaseTarget();
        int[] pixels = new int[120 * 120];
        target.getRGB(pixels, 0, 120, 0, 0, 120, 120);
        return pixels;
    }

    private static int[] renderTriangle(MorphingMesh mesh) throws Exception {
        Image target = Image.createImage(120, 120);
        Graphics3D graphics = new Graphics3D();
        graphics.bindTarget(target.getGraphics(), true, Graphics3D.OVERWRITE);
        graphics.setViewport(0, 0, 120, 120);
        Camera camera = new Camera();
        camera.setPerspective(45, 1, 0.1f, 20);
        Transform cameraWorld = new Transform();
        cameraWorld.postTranslate(0, 0, 4);
        graphics.setCamera(camera, cameraWorld);
        Background background = new Background();
        background.setColor(0xff000000);
        graphics.clear(background);
        graphics.render(mesh, new Transform());
        graphics.releaseTarget();
        int[] pixels = new int[120 * 120];
        target.getRGB(pixels, 0, 120, 0, 0, 120, 120);
        return pixels;
    }

    private static long centroidX(int[] pixels) {
        long sum = 0, count = 0;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] & 0x00ffffff) == 0) continue;
            sum += i % 120;
            count++;
        }
        if (count < 3) throw new AssertionError("Morph triangle did not rasterize");
        return sum / count;
    }

    private static void installLoader(File jar) throws Exception {
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        platform.dataPath = Files.createTempDirectory("m3g-game-smoke-").toFile()
                .getCanonicalPath() + File.separator;
        platform.loader = new MIDletLoader(new URL[]{jar.toURI().toURL()});
    }

    private static World findWorld(Object3D[] roots) {
        for (Object3D root : roots) if (root instanceof World) return (World)root;
        return null;
    }

    private static MorphingMesh findMorph(Object3D[] roots) {
        Set<Object3D> visited = Collections.newSetFromMap(new IdentityHashMap<Object3D, Boolean>());
        Deque<Object3D> pending = new ArrayDeque<Object3D>();
        Collections.addAll(pending, roots);
        while (!pending.isEmpty()) {
            Object3D value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (value instanceof MorphingMesh) return (MorphingMesh)value;
            pending.addAll(M3GGraph.references(value));
        }
        return null;
    }

    private static Texture2D findTexture(Object3D[] roots) {
        Set<Object3D> visited = Collections.newSetFromMap(new IdentityHashMap<Object3D, Boolean>());
        Deque<Object3D> pending = new ArrayDeque<Object3D>();
        Collections.addAll(pending, roots);
        while (!pending.isEmpty()) {
            Object3D value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (value instanceof Texture2D) return (Texture2D)value;
            pending.addAll(M3GGraph.references(value));
        }
        return null;
    }

    private static void polygonModeDuplicate() {
        PolygonMode source = new PolygonMode();
        source.setCulling(PolygonMode.CULL_FRONT);
        source.setShading(PolygonMode.SHADE_SMOOTH);
        source.setWinding(PolygonMode.WINDING_CW);
        source.setLocalCameraLightingEnable(true);
        source.setPerspectiveCorrectionEnable(false);
        source.setTwoSidedLightingEnable(true);
        PolygonMode copy = (PolygonMode)source.duplicate();
        check(copy != source, "PolygonMode duplicate returned the original object");
        check(copy.getCulling() == source.getCulling(), "PolygonMode duplicate lost culling");
        check(copy.getShading() == source.getShading(), "PolygonMode duplicate lost shading");
        check(copy.getWinding() == source.getWinding(), "PolygonMode duplicate lost winding");
        check(copy.isLocalCameraLightingEnabled() == source.isLocalCameraLightingEnabled(),
                "PolygonMode duplicate lost local camera lighting");
        check(copy.isPerspectiveCorrectionEnabled() == source.isPerspectiveCorrectionEnabled(),
                "PolygonMode duplicate lost perspective correction");
        check(copy.isTwoSidedLightingEnabled() == source.isTwoSidedLightingEnabled(),
                "PolygonMode duplicate lost two-sided lighting");
    }

    private static int count(Object3D root, Class<?> type) {
        Set<Object3D> visited = Collections.newSetFromMap(new IdentityHashMap<Object3D, Boolean>());
        Deque<Object3D> pending = new ArrayDeque<Object3D>();
        pending.add(root);
        int result = 0;
        while (!pending.isEmpty()) {
            Object3D value = pending.removeFirst();
            if (!visited.add(value)) continue;
            if (type.isInstance(value)) result++;
            pending.addAll(M3GGraph.references(value));
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
