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

import java.util.HashMap;
import java.util.Map;

public class VertexBuffer extends Object3D
{

	private static final class TexCoords
	{
		final VertexArray array;
		final float scale;
		final float biasU;
		final float biasV;
		TexCoords(VertexArray array, float scale, float biasU, float biasV)
		{
			this.array = array; this.scale = scale; this.biasU = biasU; this.biasV = biasV;
		}
	}

	private VertexArray positions;
	private VertexArray normals;
	private VertexArray colors;
	private final Map<Integer, TexCoords> texCoords = new HashMap<Integer, TexCoords>();
	private float positionScale = 1f, positionBiasX, positionBiasY, positionBiasZ;
	private int defaultColor = 0xffffffff;

	public VertexBuffer() {  }

	public VertexArray getColors() { return colors; }

	public int getDefaultColor() { return defaultColor; }

	public VertexArray getNormals() { return normals; }

	public VertexArray getPositions(float[] scaleBias)
	{
		if (scaleBias != null && scaleBias.length >= 4) {
			scaleBias[0] = positionScale;
			scaleBias[1] = positionBiasX;
			scaleBias[2] = positionBiasY;
			scaleBias[3] = positionBiasZ;
		}
		return positions;
	}

	public VertexArray getTexCoords(int index, float[] scaleBias)
	{
		TexCoords tc = texCoords.get(index);
		if (tc == null) return null;
		if (scaleBias != null && scaleBias.length >= 3) {
			scaleBias[0] = tc.scale;
			scaleBias[1] = tc.biasU;
			scaleBias[2] = tc.biasV;
		}
		return tc.array;
	}

	public int getVertexCount() { return positions == null ? 0 : positions.getVertexCount(); }

	public void setColors(VertexArray colors) { this.colors = colors; }

	public void setDefaultColor(int ARGB) { defaultColor = ARGB; }

	public void setNormals(VertexArray normals) { this.normals = normals; }

	public void setPositions(VertexArray positions, float scale, float[] bias)
	{
		this.positions = positions;
		positionScale = scale;
		positionBiasX = bias != null && bias.length > 0 ? bias[0] : 0;
		positionBiasY = bias != null && bias.length > 1 ? bias[1] : 0;
		positionBiasZ = bias != null && bias.length > 2 ? bias[2] : 0;
	}

	public void setTexCoords(int index, VertexArray coords, float scale, float[] bias)
	{
		if (index < 0) throw new IllegalArgumentException("Negative texture coordinate index");
		texCoords.put(index, coords == null ? null : new TexCoords(coords, scale,
				bias != null && bias.length > 0 ? bias[0] : 0,
				bias != null && bias.length > 1 ? bias[1] : 0));
	}

	float position(int vertex, int component)
	{
		if (positions == null || component >= positions.getComponentCount()) return 0;
		float bias = component == 0 ? positionBiasX : component == 1 ? positionBiasY : positionBiasZ;
		return positions.component(vertex, component) * positionScale + bias;
	}

	float color(int vertex, int component)
	{
		if (colors == null || component >= colors.getComponentCount()) return -1;
		return colors.unsignedComponent(vertex, component);
	}

	float texCoord(int index, int vertex, int component)
	{
		TexCoords tc = texCoords.get(index);
		if (tc == null || component >= tc.array.getComponentCount()) return 0;
		return tc.array.component(vertex, component) * tc.scale + (component == 0 ? tc.biasU : tc.biasV);
	}

	int textureComponentCount(int index)
	{
		TexCoords tc = texCoords.get(index);
		return tc == null ? 0 : tc.array.getComponentCount();
	}

}
