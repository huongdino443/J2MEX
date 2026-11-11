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

public class VertexArray extends Object3D
{

	public static final int BYTE = 1;
	public static final int SHORT = 2;

	private final int vertexCount;
	private final int componentCount;
	private final int componentSize;
	private final short[] values;

	public VertexArray(int numVertices, int numComponents, int componentSize)
	{
		if (numVertices < 1 || numComponents < 2 || numComponents > 4
				|| (componentSize != BYTE && componentSize != SHORT))
			throw new IllegalArgumentException("Invalid vertex array dimensions or component size");
		vertexCount = numVertices;
		componentCount = numComponents;
		this.componentSize = componentSize;
		values = new short[numVertices * numComponents];
	}

	public void get(int firstVertex, int numVertices, byte[] destination)
	{
		checkRange(firstVertex, numVertices);
		int count = numVertices * componentCount;
		if (destination == null || destination.length < count || componentSize != BYTE)
			throw new IllegalArgumentException("Invalid byte destination");
		for (int i = 0; i < count; i++) destination[i] = (byte) values[firstVertex * componentCount + i];
	}

	public void get(int firstVertex, int numVertices, short[] destination)
	{
		checkRange(firstVertex, numVertices);
		int count = numVertices * componentCount;
		if (destination == null || destination.length < count || componentSize != SHORT)
			throw new IllegalArgumentException("Invalid short destination");
		System.arraycopy(values, firstVertex * componentCount, destination, 0, count);
	}

	public int getComponentCount() { return componentCount; }

	public int getComponentType() { return componentSize; }

	public int getVertexCount() { return vertexCount; }

	public void set(int firstVertex, int numVertices, byte[] source)
	{
		checkRange(firstVertex, numVertices);
		int count = numVertices * componentCount;
		if (source == null || source.length < count || componentSize != BYTE)
			throw new IllegalArgumentException("Invalid byte source");
		for (int i = 0; i < count; i++) values[firstVertex * componentCount + i] = source[i];
	}

	public void set(int firstVertex, int numVertices, short[] source)
	{
		checkRange(firstVertex, numVertices);
		int count = numVertices * componentCount;
		if (source == null || source.length < count || componentSize != SHORT)
			throw new IllegalArgumentException("Invalid short source");
		System.arraycopy(source, 0, values, firstVertex * componentCount, count);
	}

	float component(int vertex, int component)
	{
		return values[vertex * componentCount + component];
	}

	float unsignedComponent(int vertex, int component)
	{
		int value = values[vertex * componentCount + component];
		return componentSize == BYTE ? value & 0xff : value & 0xffff;
	}

	private void checkRange(int firstVertex, int numVertices)
	{
		if (firstVertex < 0 || numVertices < 0 || firstVertex + numVertices > vertexCount)
			throw new IndexOutOfBoundsException("Vertex range outside array");
	}

}
