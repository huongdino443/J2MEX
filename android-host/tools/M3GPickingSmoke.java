import javax.microedition.m3g.*;

public final class M3GPickingSmoke
{
	public static void main(String[] args)
	{
		checkVerticalRayFindsRaisedSurface();
		checkNearestHitAndScope();
		checkCameraCenterRay();
		System.out.println("M3GPickingSmoke: terrain-height rays, nearest hit, scope and camera picking passed");
	}

	private static void checkVerticalRayFindsRaisedSurface()
	{
		Group world = new Group();
		Mesh ground = horizontalPlane(2f, -1f, 1f);
		world.addChild(ground);
		RayIntersection hit = new RayIntersection();
		if (!world.pick(-1, 0, 10, 0, 0, -1, 0, hit))
			throw new AssertionError("Downward terrain ray missed a raised surface");
		if (Math.abs((10f - hit.getDistance()) - 2f) > 0.0001f)
			throw new AssertionError("Terrain ray returned the wrong surface height: "
					+ (10f - hit.getDistance()));
		if (hit.getIntersected() != ground || hit.getSubmeshIndex() != 0)
			throw new AssertionError("Terrain ray returned the wrong mesh or submesh");
		float[] ray = new float[6];
		hit.getRay(ray);
		if (Math.abs(ray[1] - 10f) > 0.0001f || Math.abs(ray[4] + 1f) > 0.0001f)
			throw new AssertionError("RayIntersection did not retain its normalized input ray");
		if (Math.abs(Math.abs(hit.getNormalY()) - 1f) > 0.0001f)
			throw new AssertionError("Terrain hit returned an invalid surface normal");
		if (Math.abs(hit.getTextureS(0) - 0.5f) > 0.0001f
				|| Math.abs(hit.getTextureT(0) - 0.5f) > 0.0001f)
			throw new AssertionError("Terrain hit returned incorrect interpolated UV coordinates");
	}

	private static void checkNearestHitAndScope()
	{
		Group world = new Group();
		Mesh lower = horizontalPlane(2f, -1f, 1f);
		Mesh upper = horizontalPlane(5f, -1f, 1f);
		upper.setScope(2);
		world.addChild(lower);
		world.addChild(upper);
		RayIntersection hit = new RayIntersection();
		if (!world.pick(1, 0, 10, 0, 0, -1, 0, hit) || hit.getIntersected() != lower)
			throw new AssertionError("Ray picking ignored the requested node scope");
		if (!world.pick(-1, 0, 10, 0, 0, -1, 0, hit) || hit.getIntersected() != upper)
			throw new AssertionError("Ray picking did not return the nearest triangle");
		upper.setPickingEnable(false);
		if (!world.pick(-1, 0, 10, 0, 0, -1, 0, hit) || hit.getIntersected() != lower)
			throw new AssertionError("Ray picking ignored the node picking-enable flag");
	}

	private static void checkCameraCenterRay()
	{
		Group world = new Group();
		Mesh wall = verticalPlane(-5f);
		Camera camera = new Camera();
		camera.setPerspective(90f, 1f, 0.1f, 100f);
		world.addChild(wall);
		world.addChild(camera);
		RayIntersection hit = new RayIntersection();
		if (!world.pick(-1, 0.5f, 0.5f, camera, hit) || hit.getIntersected() != wall)
			throw new AssertionError("Camera-center pick missed the centered plane");
		if (Math.abs(hit.getDistance() - 5f) > 0.001f)
			throw new AssertionError("Camera-center pick returned an incorrect distance: "
					+ hit.getDistance());
	}

	private static Mesh horizontalPlane(float y, float min, float max)
	{
		VertexArray positions = new VertexArray(4, 3, VertexArray.SHORT);
		positions.set(0, 4, new short[]{
				(short)(min * 100), (short)(y * 100), (short)(min * 100),
				(short)(max * 100), (short)(y * 100), (short)(min * 100),
				(short)(min * 100), (short)(y * 100), (short)(max * 100),
				(short)(max * 100), (short)(y * 100), (short)(max * 100)});
		VertexBuffer vertices = new VertexBuffer();
		vertices.setPositions(positions, 0.01f, null);
		VertexArray uv = new VertexArray(4, 2, VertexArray.SHORT);
		uv.set(0, 4, new short[]{0, 0, 2, 0, 0, 2, 2, 2});
		vertices.setTexCoords(0, uv, 0.5f, null);
		return new Mesh(vertices, new TriangleStripArray(new int[]{0, 1, 2, 3},
				new int[]{4}), new Appearance());
	}

	private static Mesh verticalPlane(float z)
	{
		VertexArray positions = new VertexArray(4, 3, VertexArray.SHORT);
		positions.set(0, 4, new short[]{
				-100, -100, (short)(z * 100), 100, -100, (short)(z * 100),
				-100, 100, (short)(z * 100), 100, 100, (short)(z * 100)});
		VertexBuffer vertices = new VertexBuffer();
		vertices.setPositions(positions, 0.01f, null);
		return new Mesh(vertices, new TriangleStripArray(new int[]{0, 1, 2, 3},
				new int[]{4}), new Appearance());
	}
}
