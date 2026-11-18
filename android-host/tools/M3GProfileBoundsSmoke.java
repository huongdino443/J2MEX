package javax.microedition.m3g;

import javax.microedition.lcdui.Graphics;

public final class M3GProfileBoundsSmoke
{
	public static void main(String[] args)
	{
		Graphics3D graphics = new Graphics3D();
		CaptureGraphics target = new CaptureGraphics(16, 16);
		Camera camera = new Camera();
		camera.setPerspective(90f, 1f, 0.1f, 100f);
		Appearance appearance = new Appearance();
		PolygonMode polygon = new PolygonMode();
		polygon.setCulling(PolygonMode.CULL_NONE);
		appearance.setPolygonMode(polygon);

		VertexArray positions = new VertexArray(3, 3, VertexArray.SHORT);
		positions.set(0, 3, new short[] { -1, -1, -2, 1, -1, -2, 0, 1, -2 });
		VertexBuffer vertices = new VertexBuffer();
		vertices.setPositions(positions, 1f, null);
		vertices.setDefaultColor(0xffffffff);
		IndexBuffer indices = new TriangleStripArray(
				new int[] { 0, 1, 2 }, new int[] { 3 });

		for (int frame = 0; frame < 240; frame++) {
			graphics.bindTarget(target, true, Graphics3D.OVERWRITE);
			graphics.setViewport(0, 0, target.width, target.height);
			graphics.setCamera(camera, new Transform());
			graphics.render(vertices, indices, appearance, new Transform());
			graphics.releaseTarget();
		}

		int center = target.pixels[8 * target.width + 8];
		if ((center & 0x00ffffff) == 0)
			throw new AssertionError("Profile smoke lost the rendered triangle");
		System.out.println("M3GProfileBoundsSmoke: 240 profiled target releases passed");
	}

	private static final class CaptureGraphics extends Graphics
	{
		final int width;
		final int height;
		final int[] pixels;

		CaptureGraphics(int width, int height)
		{
			this.width = width;
			this.height = height;
			this.pixels = new int[width * height];
		}

		@Override public int getClipX() { return 0; }
		@Override public int getClipY() { return 0; }
		@Override public int getClipWidth() { return width; }
		@Override public int getClipHeight() { return height; }

		@Override public void drawRGB(int[] rgbData, int offset, int scanlength,
				int x, int y, int drawWidth, int drawHeight, boolean processAlpha)
		{
			for (int row = 0; row < drawHeight; row++)
				System.arraycopy(rgbData, offset + row * scanlength, pixels,
						(y + row) * width + x, drawWidth);
		}
	}
}
