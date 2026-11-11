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

public class MorphingMesh extends Mesh
{

private final VertexBuffer[] morphTargets;
private final float[] weights;


public MorphingMesh(VertexBuffer base, VertexBuffer[] targets, IndexBuffer[] submeshes, Appearance[] appearances)
{
super(base, submeshes, appearances);
if (targets == null) throw new NullPointerException("targets");
VertexArray basePositions = base.getPositions(null);
if (basePositions == null) throw new IllegalArgumentException("Base vertex buffer has no positions");
morphTargets = targets.clone();
for (VertexBuffer target : morphTargets)
{
if (target == null) throw new NullPointerException("morph target");
VertexArray positions = target.getPositions(null);
if (positions == null || positions.getVertexCount() != basePositions.getVertexCount()
|| positions.getComponentCount() != basePositions.getComponentCount())
throw new IllegalArgumentException("Morph target vertex layout does not match the base");
}
weights = new float[morphTargets.length];
}

public MorphingMesh(VertexBuffer base, VertexBuffer[] targets, IndexBuffer submesh, Appearance appearance)
{
this(base, targets, new IndexBuffer[]{submesh}, new Appearance[]{appearance});
}


public VertexBuffer getMorphTarget(int index) { return morphTargets[index]; }

public int getMorphTargetCount() { return morphTargets.length; }

	public void getWeights(float[] store)
	{
if (store == null) throw new NullPointerException("store");
if (store.length < weights.length) throw new IllegalArgumentException("Weights destination is too small");
		for (int i=0; i<weights.length; i++)
		{
			store[i]=weights[i];
		}
	}

public void setWeights(float[] values)
{
if (values == null) throw new NullPointerException("values");
if (values.length != weights.length) throw new IllegalArgumentException("Expected one weight per morph target");
for (int i=0; i<weights.length; i++)
{
if (Float.isNaN(values[i]) || Float.isInfinite(values[i]))
throw new IllegalArgumentException("Morph weights must be finite");
weights[i]=values[i];
}
}

float positionForRendering(int vertex, int component)
{
VertexBuffer base = getVertexBuffer();
float value = base.position(vertex, component);
for (int i=0; i<morphTargets.length; i++)
{
float weight = weights[i];
if (weight != 0)
value += (morphTargets[i].position(vertex, component) - base.position(vertex, component)) * weight;
}
return value;
}

}
