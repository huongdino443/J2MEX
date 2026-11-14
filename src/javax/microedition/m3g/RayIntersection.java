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
package javax.microedition.m3g;

public class RayIntersection
{

	static final int TEXTURE_COORDINATE_SETS = 8;
	private Node intersected;
	private final float[] ray = new float[6];
	private float distance = Float.POSITIVE_INFINITY;
	private float normalX, normalY, normalZ;
	private int submeshIndex;
	private final float[] textureS = new float[TEXTURE_COORDINATE_SETS];
	private final float[] textureT = new float[TEXTURE_COORDINATE_SETS];

	public RayIntersection() {  }


	public float getDistance() { return distance; }

	public Node getIntersected() { return intersected; }

	public float getNormalX() { return normalX; }

	public float getNormalY() { return normalY; }

	public float getNormalZ() { return normalZ; }

	public void getRay(float[] destination)
	{
		if (destination == null || destination.length < ray.length)
			throw new IllegalArgumentException("Ray destination needs six values");
		System.arraycopy(ray, 0, destination, 0, ray.length);
	}

	public int getSubmeshIndex() { return submeshIndex; }

	public float getTextureS(int index) { return textureS[index]; }

	public float getTextureT(int index) { return textureT[index]; }

	void reset(float ox, float oy, float oz, float dx, float dy, float dz)
	{
		intersected = null;
		distance = Float.POSITIVE_INFINITY;
		normalX = normalY = normalZ = 0;
		submeshIndex = 0;
		ray[0] = ox; ray[1] = oy; ray[2] = oz;
		ray[3] = dx; ray[4] = dy; ray[5] = dz;
		java.util.Arrays.fill(textureS, 0f);
		java.util.Arrays.fill(textureT, 0f);
	}

	void setHit(Node node, float hitDistance, float nx, float ny, float nz,
			int submesh, float[] s, float[] t)
	{
		intersected = node;
		distance = hitDistance;
		normalX = nx; normalY = ny; normalZ = nz;
		submeshIndex = submesh;
		System.arraycopy(s, 0, textureS, 0, textureS.length);
		System.arraycopy(t, 0, textureT, 0, textureT.length);
	}

}
