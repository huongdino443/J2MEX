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

public class Mesh extends Node
{

	private Appearance[] appearances = new Appearance[0];
	private IndexBuffer[] indexbuffers = new IndexBuffer[0];
	private VertexBuffer vertexbuffer;


	public Mesh() {  }

	public Mesh(VertexBuffer vertices, IndexBuffer[] submeshes, Appearance[] appearances)
	{
		if (vertices == null || submeshes == null || appearances == null || submeshes.length == 0
				|| submeshes.length != appearances.length) throw new IllegalArgumentException("Invalid mesh");
		this.vertexbuffer = vertices;
		this.indexbuffers = submeshes.clone();
		this.appearances = appearances.clone();
	}

	public Mesh(VertexBuffer vertices, IndexBuffer submesh, Appearance appearance)
	{
		this(vertices, new IndexBuffer[]{submesh}, new Appearance[]{appearance});
	}


	public Appearance getAppearance(int index) { return appearances[index]; }

	public IndexBuffer getIndexBuffer(int index) { return indexbuffers[index]; }

	public int getSubmeshCount() { return indexbuffers.length; }

	public VertexBuffer getVertexBuffer() { return vertexbuffer; }

	public void setAppearance(int index, Appearance a) { appearances[index] = a; }

}
