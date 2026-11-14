/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package com.mascotcapsule.micro3d.v3;

import javax.microedition.lcdui.Graphics;

public class Graphics3D
{
	private static long lastPointSpriteDiagnosticMs;

	public static final int COMMAND_AFFINE_INDEX = 0x87000000; // -2030043136
	public static final int COMMAND_AMBIENT_LIGHT = 0xA0000000; // -1610612736
	public static final int COMMAND_ATTRIBUTE = 0x83000000; // -2097152000
	public static final int COMMAND_CENTER = 0x85000000; // -2063597568
	public static final int COMMAND_CLIP = 0x84000000; // -2080374784
	public static final int COMMAND_DIRECTION_LIGHT = 0xA1000000; // -1593835520
	public static final int COMMAND_END = 0x80000000; // -2147483648;
	public static final int COMMAND_FLUSH = 0x82000000; // -2113929216
	public static final int COMMAND_LIST_VERSION_1_0 = 0x1FFFFFF; //-33554431
	public static final int COMMAND_NOP = 0x81000000; // -2130706432
	public static final int COMMAND_PARALLEL_SCALE = 0x90000000; // -1879048192
	public static final int COMMAND_PARALLEL_SIZE = 0x91000000; // -1862270976
	public static final int COMMAND_PERSPECTIVE_FOV = 0x92000000; // -1845493760
	public static final int COMMAND_PERSPECTIVE_WH = 0x93000000; // -1828716544
	public static final int COMMAND_TEXTURE_INDEX = 0x86000000; // -2046820352
	public static final int COMMAND_THRESHOLD = 0xAF000000; // -1358954496
	public static final int ENV_ATTR_LIGHTING = 1;
	public static final int ENV_ATTR_SEMI_TRANSPARENT = 8;
	public static final int ENV_ATTR_SPHERE_MAP = 2;
	public static final int ENV_ATTR_TOON_SHADING = 4;
	public static final int PATTR_BLEND_ADD = 64;
	public static final int PATTR_BLEND_HALF = 32;
	public static final int PATTR_BLEND_NORMAL = 0;
	public static final int PATTR_BLEND_SUB = 96;
	public static final int PATTR_COLORKEY = 16;
	public static final int PATTR_LIGHTING = 1;
	public static final int PATTR_SPHERE_MAP = 2;
	public static final int PDATA_COLOR_NONE = 0;
	public static final int PDATA_COLOR_PER_COMMAND = 0x400; // 1024
	public static final int PDATA_COLOR_PER_FACE = 0x800; // 2048
	public static final int PDATA_NORMAL_NONE = 0;
	public static final int PDATA_NORMAL_PER_FACE = 0x200; // 512
	public static final int PDATA_NORMAL_PER_VERTEX = 0x300; // 768
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_CMD = 0x1000; // 4096
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_FACE = 0x2000; // 8192
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_VERTEX = 0x3000; // 12288
	public static final int PDATA_TEXURE_COORD = 0x3000; // 12288
	public static final int PDATA_TEXURE_COORD_NONE = 0;
	public static final int POINT_SPRITE_LOCAL_SIZE = 0;
	public static final int POINT_SPRITE_NO_PERS = 2;
	public static final int POINT_SPRITE_PERSPECTIVE = 0;
	public static final int POINT_SPRITE_PIXEL_SIZE = 1;
	public static final int PRIMITVE_POINTS = 0x1000000; // 16777216
	public static final int PRIMITVE_LINES = 0x2000000; // 33554432;
	public static final int PRIMITVE_TRIANGLES = 0x3000000; // 50331648;
	public static final int PRIMITVE_QUADS = 0x4000000; // 67108864;
	public static final int PRIMITVE_POINT_SPRITES = 0x5000000; // 83886080


	protected Graphics gc;
private Micro3DSoftwareRenderer softwareRenderer;


	public Graphics3D()
	{
		gc = null;
	}


public void bind(Graphics g)
{
if(g == null) throw new NullPointerException("graphics");
if(softwareRenderer != null) throw new IllegalStateException("A graphics target is already bound");
gc = g;
softwareRenderer = new Micro3DSoftwareRenderer(g);
}

public void release(Graphics g)
{
if(g == null) throw new NullPointerException("graphics");
if(g != gc) throw new IllegalArgumentException("Graphics target does not match the bound target");
flush();
softwareRenderer = null;
gc = null;
}

public void dispose()
{
if(softwareRenderer != null) flush();
softwareRenderer = null;
gc = null;
}

public void flush()
{
if(softwareRenderer != null) softwareRenderer.flush();
}

	public void renderPrimitives(Texture texture, int x, int y, FigureLayout layout, Effect3D effect, int command, int numPrimitives, int[] vertexCoords, int[] normals, int[] textureCoords, int[] colors)
	{
		checkBound();
		if(layout == null || effect == null || vertexCoords == null
				|| normals == null || textureCoords == null || colors == null)
			throw new NullPointerException();
		if(command < 0 || numPrimitives <= 0 || numPrimitives >= 256)
			throw new IllegalArgumentException();

		int primitiveType = command & 0x07000000;
		int verticesPerPrimitive;
		if(primitiveType == PRIMITVE_POINTS || primitiveType == PRIMITVE_POINT_SPRITES)
			verticesPerPrimitive = 1;
		else if(primitiveType == PRIMITVE_LINES) verticesPerPrimitive = 2;
		else if(primitiveType == PRIMITVE_TRIANGLES) verticesPerPrimitive = 3;
		else if(primitiveType == PRIMITVE_QUADS) verticesPerPrimitive = 4;
		else throw new IllegalArgumentException("Unsupported Micro3D primitive type");

		int vertexCount = verticesPerPrimitive * numPrimitives;
		if(vertexCoords.length < vertexCount * 3)
			throw new IllegalArgumentException("Not enough vertex coordinates");
		boolean pointSprites = primitiveType == PRIMITVE_POINT_SPRITES;
		boolean hasTextureCoordinates = pointSprites
				? (command & 0x3000) != PDATA_TEXURE_COORD_NONE
				: (command & 0x3000) == PDATA_TEXURE_COORD;
		if(pointSprites && hasTextureCoordinates && texture == null)
			return;
		if(hasTextureCoordinates && textureCoords.length < vertexCount * 2)
		{
			if(pointSprites)
			{
				int params = (command & 0x3000) == PDATA_POINT_SPRITE_PARAMS_PER_CMD
						? 8 : numPrimitives * 8;
				if(textureCoords.length < params)
					throw new IllegalArgumentException("Not enough point sprite parameters");
			}
			else throw new IllegalArgumentException("Not enough texture coordinates");
		}

		int colorMode = command & 0x0c00;
		int requiredColors = colorMode == PDATA_COLOR_PER_COMMAND ? 1
				: colorMode == PDATA_COLOR_PER_FACE ? numPrimitives
				: colorMode == 0x0c00 ? vertexCount : 0;
		if(colors.length < requiredColors)
			throw new IllegalArgumentException("Not enough colors");

// J2ME Loader renders primitives with face culling disabled. Keep that
// behavior separate from model DOUBLE_FACE semantics.
int material = 16;
		if((command & PATTR_COLORKEY) != 0) material |= 1;
		if((command & PATTR_LIGHTING) != 0) material |= 32;
		int blendMode = command & (PATTR_BLEND_HALF | PATTR_BLEND_ADD);
		if(blendMode == PATTR_BLEND_HALF) material |= 2;
		else if(blendMode == PATTR_BLEND_ADD) material |= 4;
		else if(blendMode == PATTR_BLEND_SUB) material |= 6;

		if(pointSprites)
		{
			if(!hasTextureCoordinates) return;
			renderPointSprites(texture, x, y, layout, effect, command,
					numPrimitives, vertexCoords, textureCoords, material);
			return;
		}
		if(primitiveType == PRIMITVE_POINTS || primitiveType == PRIMITVE_LINES)
		{
			if(colorMode == 0) return;
			renderPointsAndLines(x, y, layout, effect, command, primitiveType,
					numPrimitives, vertexCoords, colors, colorMode, material);
			return;
		}

		int[] vertices = new int[vertexCount * 3];
		System.arraycopy(vertexCoords, 0, vertices, 0, vertices.length);
		java.util.ArrayList<Micro3DModel.Triangle> triangles =
				new java.util.ArrayList<Micro3DModel.Triangle>(numPrimitives * 2);
		int textureCount = texture != null && hasTextureCoordinates ? 1 : 0;
		for(int primitive = 0; primitive < numPrimitives; primitive++)
		{
			int first = primitive * verticesPerPrimitive;
			int color = primitiveColor(colors, colorMode, primitive, first, verticesPerPrimitive);
			if(textureCount == 0) color = 0xff000000 | color;
			if(verticesPerPrimitive == 3)
			{
				triangles.add(makePrimitiveTriangle(first, first + 1, first + 2,
						textureCount, material, color, textureCoords, hasTextureCoordinates));
			}
else if(verticesPerPrimitive == 4)
{
// Match the reference renderer's A-B-C / A-C-D split. The previous
// B-C-D triangle had the opposite winding and was culled, leaving half
// of colored quads (including ground markers) missing.
triangles.add(makePrimitiveTriangle(first, first + 1, first + 2,
textureCount, material, color, textureCoords, hasTextureCoordinates));
triangles.add(makePrimitiveTriangle(first, first + 2, first + 3,
textureCount, material, color, textureCoords, hasTextureCoordinates));
}
			else
			{
				triangles.add(makePrimitiveTriangle(first, first + 1, first + 2,
						textureCount, material, color, textureCoords, hasTextureCoordinates));
				triangles.add(makePrimitiveTriangle(first + 2, first + 1, first + 3,
						textureCount, material, color, textureCoords, hasTextureCoordinates));
			}
		}
 Figure figure = new Figure(new Micro3DModel(5, textureCount, 1,
				vertices, triangles.toArray(new Micro3DModel.Triangle[triangles.size()])));
		if(textureCount != 0) figure.setTexture(texture);
		softwareRenderer.renderPrimitiveFigure(figure, x, y, layout, effect);
	}

	private void renderPointsAndLines(int x, int y, FigureLayout layout, Effect3D effect,
			int command, int primitiveType, int numPrimitives, int[] source,
			int[] colors, int colorMode, int material)
	{
		int verticesPerPrimitive = primitiveType == PRIMITVE_POINTS ? 1 : 2;
		int perPrimitiveVertices = verticesPerPrimitive * 3;
		int[] vertices = new int[numPrimitives * 4 * 3];
		java.util.ArrayList<Micro3DModel.Triangle> triangles =
				new java.util.ArrayList<Micro3DModel.Triangle>(numPrimitives * 2);
		AffineTrans view = layout.getAffineTrans();
		int cursor = 0;
		for(int primitive = 0; primitive < numPrimitives; primitive++)
		{
			int color = primitiveColor(colors, colorMode, primitive,
					primitive * verticesPerPrimitive, verticesPerPrimitive) | 0xff000000;
			float cx, cy, cz, halfWidth;
			float x0, y0, z0, x1, y1, z1;
			if(primitiveType == PRIMITVE_POINTS)
			{
				int sourceOffset = primitive * 3;
				Vector3D center = view.transform(new Vector3D(source[sourceOffset],
						source[sourceOffset + 1], source[sourceOffset + 2]));
				cx = center.x; cy = center.y; cz = center.z;
				halfWidth = (float)pixelsToViewUnits(1.0, cz, layout, false);
				x0 = cx - halfWidth; y0 = cy - halfWidth;
				x1 = cx + halfWidth; y1 = cy + halfWidth;
				z0 = z1 = cz;
			}
			else
			{
				int sourceOffset = primitive * perPrimitiveVertices;
				Vector3D first = view.transform(new Vector3D(source[sourceOffset],
						source[sourceOffset + 1], source[sourceOffset + 2]));
				Vector3D second = view.transform(new Vector3D(source[sourceOffset + 3],
						source[sourceOffset + 4], source[sourceOffset + 5]));
				double dx = second.x - first.x;
				double dy = second.y - first.y;
				double length = Math.sqrt(dx * dx + dy * dy);
				if(length == 0) { dx = 1; dy = 0; length = 1; }
				halfWidth = (float)pixelsToViewUnits(1.0,
						(first.z + second.z) * 0.5, layout, false);
				float px = (float)(-dy / length * halfWidth);
				float py = (float)(dx / length * halfWidth);
				x0 = first.x + px; y0 = first.y + py; z0 = first.z;
				x1 = first.x - px; y1 = first.y - py; z1 = first.z;
				cx = second.x; cy = second.y; cz = second.z;
			}

			Vector3D[] corners;
			if(primitiveType == PRIMITVE_POINTS)
			{
				corners = new Vector3D[]{
						cameraToWorld(view, x0, y0, z0),
						cameraToWorld(view, x1, y0, z1),
						cameraToWorld(view, x0, y1, z0),
						cameraToWorld(view, x1, y1, z1)};
			}
			else
			{
				int sourceOffset = primitive * perPrimitiveVertices;
				Vector3D first = view.transform(new Vector3D(source[sourceOffset],
						source[sourceOffset + 1], source[sourceOffset + 2]));
				Vector3D second = view.transform(new Vector3D(source[sourceOffset + 3],
						source[sourceOffset + 4], source[sourceOffset + 5]));
				double dx = second.x - first.x;
				double dy = second.y - first.y;
				double length = Math.sqrt(dx * dx + dy * dy);
				if(length == 0) { dx = 1; dy = 0; length = 1; }
				float px = (float)(-dy / length * halfWidth);
				float py = (float)(dx / length * halfWidth);
				corners = new Vector3D[]{
						cameraToWorld(view, first.x + px, first.y + py, first.z),
						cameraToWorld(view, first.x - px, first.y - py, first.z),
						cameraToWorld(view, second.x + px, second.y + py, second.z),
						cameraToWorld(view, second.x - px, second.y - py, second.z)};
			}
			int firstVertex = cursor / 3;
			for(Vector3D corner : corners)
			{
				vertices[cursor++] = corner.x;
				vertices[cursor++] = corner.y;
				vertices[cursor++] = corner.z;
			}
			triangles.add(makePrimitiveTriangle(firstVertex, firstVertex + 1, firstVertex + 2,
					0, material, color, new int[0], false));
			triangles.add(makePrimitiveTriangle(firstVertex + 2, firstVertex + 1, firstVertex + 3,
					0, material, color, new int[0], false));
		}
 Figure figure = new Figure(new Micro3DModel(5, 0, 1, vertices,
				triangles.toArray(new Micro3DModel.Triangle[triangles.size()])));
		softwareRenderer.renderPrimitiveFigure(figure, x, y, layout, effect);
	}

	private void renderPointSprites(Texture texture, int x, int y, FigureLayout layout,
			Effect3D effect, int command, int count, int[] source, int[] params, int material)
	{
		int paramMode = command & 0x3000;
		if(paramMode == PDATA_TEXURE_COORD_NONE) return;
		AffineTrans view = layout.getAffineTrans();
		int[] modelVertices = new int[count * 4 * 3];
		float[] cameraVertices = new float[count * 4 * 3];
		java.util.ArrayList<Micro3DModel.Triangle> triangles =
				new java.util.ArrayList<Micro3DModel.Triangle>(count * 2);
		int spriteMaterial = material | Micro3DSoftwareRenderer.MATERIAL_POINT_SPRITE_COMMAND;
		int cursor = 0;
		for(int i = 0; i < count; i++)
		{
			int p = paramMode == PDATA_POINT_SPRITE_PARAMS_PER_CMD ? 0 : i * 8;
float width = Math.abs(params[p]);
float height = Math.abs(params[p + 1]);
			double angle = params[p + 2] * Math.PI / 2048.0;
int u0 = params[p + 3] & 0xff, v0 = params[p + 4] & 0xff;
int u1 = (params[p + 5] - 1) & 0xff;
int v1 = (params[p + 6] - 1) & 0xff;
			int sizeMode = params[p + 7];
			int sourceOffset = i * 3;
			float[] camera = transformToCamera(view, source[sourceOffset],
					source[sourceOffset + 1], source[sourceOffset + 2]);
			float halfWidth;
			float halfHeight;
			boolean perspective = layout.getProjection() == COMMAND_PERSPECTIVE_FOV
					|| layout.getProjection() == COMMAND_PERSPECTIVE_WH;
			switch(sizeMode)
			{
				case POINT_SPRITE_LOCAL_SIZE | POINT_SPRITE_PERSPECTIVE:
					halfWidth = width * 0.5f;
					halfHeight = height * 0.5f;
					break;
				case POINT_SPRITE_PIXEL_SIZE | POINT_SPRITE_PERSPECTIVE:
					if(perspective)
					{
						halfWidth = (float)(width * layout.getNear()
								/ (2.0 * perspectiveFocalPixels(layout, true)));
						halfHeight = (float)(height * layout.getNear()
								/ (2.0 * perspectiveFocalPixels(layout, false)));
					}
					else
					{
						halfWidth = (float)pixelsToViewUnits(width * 0.5, camera[2], layout, true);
						halfHeight = (float)pixelsToViewUnits(height * 0.5, camera[2], layout, false);
					}
					break;
				case POINT_SPRITE_LOCAL_SIZE | POINT_SPRITE_NO_PERS:
					if(perspective)
					{
						float near = Math.max(1, layout.getNear());
						halfWidth = width * camera[2] / near * 0.5f;
						halfHeight = height * camera[2] / near * 0.5f;
					}
					else { halfWidth = width * 0.5f; halfHeight = height * 0.5f; }
					break;
				case POINT_SPRITE_PIXEL_SIZE | POINT_SPRITE_NO_PERS:
					if(perspective)
					{
						halfWidth = (float)(width * camera[2]
								/ (2.0 * perspectiveFocalPixels(layout, true)));
						halfHeight = (float)(height * camera[2]
								/ (2.0 * perspectiveFocalPixels(layout, false)));
					}
					else
					{
						halfWidth = (float)pixelsToViewUnits(width * 0.5, camera[2], layout, true);
						halfHeight = (float)pixelsToViewUnits(height * 0.5, camera[2], layout, false);
					}
					break;
				default:
					throw new IllegalArgumentException("Invalid point sprite size mode");
			}
logPointSpriteSample(sizeMode, width, height, u0, v0, u1, v1,
 camera, halfWidth, halfHeight, layout,
softwareRenderer.getWidth(), softwareRenderer.getHeight());
			float cos = (float)Math.cos(angle), sin = (float)Math.sin(angle);
			float[][] offsets = {
					{-halfWidth * cos + halfHeight * sin, -halfWidth * sin - halfHeight * cos},
					{ halfWidth * cos + halfHeight * sin,  halfWidth * sin - halfHeight * cos},
					{-halfWidth * cos - halfHeight * sin, -halfWidth * sin + halfHeight * cos},
					{ halfWidth * cos - halfHeight * sin,  halfWidth * sin + halfHeight * cos}
			};
			int firstVertex = cursor / 3;
			for(float[] offset : offsets)
			{
				cameraVertices[cursor++] = camera[0] + offset[0];
				cameraVertices[cursor++] = camera[1] + offset[1];
				cameraVertices[cursor++] = camera[2];
			}
			// J2ME Loader builds point sprites in clip space with the top edge
			// mapped to v1 and the bottom edge mapped to v0. Our camera-space
			// rasterizer has screen Y increasing downward, so preserve that exact
			// texture orientation when mapping the same four corners.
			triangles.add(new Micro3DModel.Triangle(firstVertex, firstVertex + 1,
					firstVertex + 2, 0, 0, spriteMaterial, 0xffffffff,
					u0, v1, u1, v1, u0, v0));
			triangles.add(new Micro3DModel.Triangle(firstVertex + 2, firstVertex + 1,
					firstVertex + 3, 0, 0, spriteMaterial, 0xffffffff,
					u0, v0, u1, v1, u1, v0));
		}
		Figure figure = new Figure(new Micro3DModel(5, 1, 1, modelVertices,
				triangles.toArray(new Micro3DModel.Triangle[triangles.size()])));
		figure.setTexture(texture);
softwareRenderer.renderFigureInCameraSpace(figure, cameraVertices, x, y, layout, effect);
	}

private void logPointSpriteSample(int sizeMode,
float spriteWidth, float spriteHeight, int u0, int v0, int u1, int v1,
float[] camera, float halfWidth, float halfHeight, FigureLayout layout,
int targetWidth, int targetHeight)
{
if(!shouldLogPointSpriteSample()) return;
double screenWidth = 2.0 * halfWidth;
double screenHeight = 2.0 * halfHeight;
if(layout.getProjection() == COMMAND_PERSPECTIVE_FOV
|| layout.getProjection() == COMMAND_PERSPECTIVE_WH)
{
screenWidth *= perspectiveFocalPixels(layout, true) / Math.max(1.0, camera[2]);
screenHeight *= perspectiveFocalPixels(layout, false) / Math.max(1.0, camera[2]);
}
else if(layout.getProjection() == COMMAND_PARALLEL_SIZE)
{
screenWidth *= targetWidth
/ (double)Math.max(1, layout.getParallelWidth());
screenHeight *= targetHeight
/ (double)Math.max(1, layout.getParallelHeight());
}
else
{
screenWidth *= layout.getScaleX() / 4096.0;
screenHeight *= layout.getScaleY() / 4096.0;
}
System.out.println("[micro3d-sprite] mode=" + sizeMode
+ " input=" + spriteWidth + "x" + spriteHeight
+ " uv=" + u0 + "," + v0 + ".." + u1 + "," + v1
+ " camera=" + camera[0] + "," + camera[1] + "," + camera[2]
+ " half=" + halfWidth + "x" + halfHeight
+ " projected=" + screenWidth + "x" + screenHeight
+ " projection=" + layout.getProjection()
+ " angle=" + layout.getAngle());
}

private static synchronized boolean shouldLogPointSpriteSample()
{
long now = System.currentTimeMillis();
if(now - lastPointSpriteDiagnosticMs < 1500) return false;
lastPointSpriteDiagnosticMs = now;
return true;
}

	private double pixelsToViewUnits(double pixels, double depth, FigureLayout layout, boolean horizontal)
	{
		if(layout.getProjection() == COMMAND_PERSPECTIVE_FOV
				|| layout.getProjection() == COMMAND_PERSPECTIVE_WH)
		{
			double focal = perspectiveFocalPixels(layout, horizontal);
			return pixels * Math.max(1, depth) / Math.max(1.0, focal);
		}
		double worldSize = horizontal ? layout.getParallelWidth() : layout.getParallelHeight();
		if(layout.getProjection() == COMMAND_PARALLEL_SIZE && worldSize > 0)
		{
			return pixels * worldSize / Math.max(1,
					horizontal ? softwareRenderer.getWidth() : softwareRenderer.getHeight());
		}
		return pixels * 4096.0 / Math.max(1,
				horizontal ? layout.getScaleX() : layout.getScaleY());
	}

	private double perspectiveFocalPixels(FigureLayout layout, boolean horizontal)
	{
		if(layout.getProjection() == COMMAND_PERSPECTIVE_FOV)
		{
			double halfAngle = layout.getAngle() * Math.PI / 4096.0;
			return softwareRenderer.getWidth() / (2.0 * Math.tan(halfAngle));
		}
		int dimension = horizontal ? layout.getPerspectiveWidth() : layout.getPerspectiveHeight();
		return dimension == 0 ? 1.0
				: (double)layout.getNear() * (horizontal
						? softwareRenderer.getWidth() : softwareRenderer.getHeight()) * 4096.0 / dimension;
	}

	private static float[] transformToCamera(AffineTrans transform, int x, int y, int z)
	{
		return new float[]{
				(float)(((long)transform.m00 * x + (long)transform.m01 * y
						+ (long)transform.m02 * z) / 4096.0 + transform.m03),
				(float)(((long)transform.m10 * x + (long)transform.m11 * y
						+ (long)transform.m12 * z) / 4096.0 + transform.m13),
				(float)(((long)transform.m20 * x + (long)transform.m21 * y
						+ (long)transform.m22 * z) / 4096.0 + transform.m23)
		};
	}

	private static Vector3D cameraToWorld(AffineTrans view, float x, float y, float z)
	{
		double dx = x - view.m03, dy = y - view.m13, dz = z - view.m23;
		double m00 = view.m00 / 4096.0, m01 = view.m01 / 4096.0, m02 = view.m02 / 4096.0;
		double m10 = view.m10 / 4096.0, m11 = view.m11 / 4096.0, m12 = view.m12 / 4096.0;
		double m20 = view.m20 / 4096.0, m21 = view.m21 / 4096.0, m22 = view.m22 / 4096.0;
		double determinant = m00 * (m11 * m22 - m12 * m21)
				- m01 * (m10 * m22 - m12 * m20)
				+ m02 * (m10 * m21 - m11 * m20);
		if(Math.abs(determinant) < 1e-12)
			throw new IllegalArgumentException("Singular Micro3D view transform");
		double inverse = 1.0 / determinant;
		double wx = ((m11 * m22 - m12 * m21) * dx
				+ (m02 * m21 - m01 * m22) * dy
				+ (m01 * m12 - m02 * m11) * dz) * inverse;
		double wy = ((m12 * m20 - m10 * m22) * dx
				+ (m00 * m22 - m02 * m20) * dy
				+ (m02 * m10 - m00 * m12) * dz) * inverse;
		double wz = ((m10 * m21 - m11 * m20) * dx
				+ (m01 * m20 - m00 * m21) * dy
				+ (m00 * m11 - m01 * m10) * dz) * inverse;
		return new Vector3D(
				(int)Math.round(wx), (int)Math.round(wy), (int)Math.round(wz));
	}

	private static int primitiveColor(int[] colors, int colorMode, int primitive,
			int firstVertex, int verticesPerPrimitive)
	{
		if(colorMode == PDATA_COLOR_PER_COMMAND) return colors[0] & 0x00ffffff;
		if(colorMode == PDATA_COLOR_PER_FACE) return colors[primitive] & 0x00ffffff;
		if(colorMode == 0x0c00)
		{
			long red = 0, green = 0, blue = 0;
			for(int i = 0; i < verticesPerPrimitive; i++)
			{
				int color = colors[firstVertex + i];
				red += (color >> 16) & 255;
				green += (color >> 8) & 255;
				blue += color & 255;
			}
			return (int)(red / verticesPerPrimitive) << 16
					| (int)(green / verticesPerPrimitive) << 8
					| (int)(blue / verticesPerPrimitive);
		}
		return 0x00ffffff;
	}

	private static Micro3DModel.Triangle makePrimitiveTriangle(int a, int b, int c,
			int textureCount, int material, int color, int[] textureCoords,
			boolean hasTextureCoordinates)
	{
		int face = textureCount == 0 ? -1 : 0;
		int u0 = 0, v0 = 0, u1 = 0, v1 = 0, u2 = 0, v2 = 0;
		if(hasTextureCoordinates)
		{
			u0 = textureCoords[a * 2]; v0 = textureCoords[a * 2 + 1];
			u1 = textureCoords[b * 2]; v1 = textureCoords[b * 2 + 1];
			u2 = textureCoords[c * 2]; v2 = textureCoords[c * 2 + 1];
		}
		return new Micro3DModel.Triangle(a, b, c, face, 0, material, color,
				u0, v0, u1, v1, u2, v2);
	}

	public void renderFigure(Figure figure, int x, int y, FigureLayout layout, Effect3D effect)
	{
checkBound();
if(figure == null || layout == null || effect == null) throw new NullPointerException();
		softwareRenderer.renderFigure(figure, x, y, layout, effect);
	}

	public void drawCommandList(Texture[] textures, int x, int y, FigureLayout layout, Effect3D effect, int[] commandList)
	{

	}

	public void drawCommandList(Texture texture, int x, int y, FigureLayout layout, Effect3D effect, int[] commandList)
	{

	}

	public void drawFigure(Figure figure, int x, int y, FigureLayout layout, Effect3D effect)
	{
checkBound();
if(figure == null || layout == null || effect == null) throw new NullPointerException();
		softwareRenderer.renderFigure(figure, x, y, layout, effect);
flush();
	}

private void checkBound()
{
if(softwareRenderer == null) throw new IllegalStateException("No graphics target is bound");
}

}
