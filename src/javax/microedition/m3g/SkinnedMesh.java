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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

public class SkinnedMesh extends Mesh
{
	private static final int MAX_BONES_PER_VERTEX = 4;

	private static final class VertexInfluence {
		Node bone;
		int weight;
		VertexInfluence(Node bone, int weight) { this.bone = bone; this.weight = weight; }
	}

	static final class BoneBinding {
		final Node bone;
		final int weight;
		final int firstVertex;
		final int numVertices;
		final Transform toBone;

		BoneBinding(Node bone, int weight, int firstVertex, int numVertices, Transform toBone) {
			this.bone = bone;
			this.weight = weight;
			this.firstVertex = firstVertex;
			this.numVertices = numVertices;
			this.toBone = new Transform();
			this.toBone.set(toBone);
		}
	}

	private final Group skeleton;
	private final List<BoneBinding> boneBindings = new ArrayList<BoneBinding>();
	private final IdentityHashMap<Node, Transform> boneBindTransforms =
			new IdentityHashMap<Node, Transform>();
	private List<VertexInfluence>[] vertexInfluences;
	private int weightedVertexCount;

	public SkinnedMesh(VertexBuffer vertices, IndexBuffer[] submeshes,
			Appearance[] appearances, Group skeleton) {
		super(vertices, submeshes, appearances);
		if (skeleton == null) throw new NullPointerException("skeleton");
		if (skeleton.getParent() != null || skeleton instanceof World)
			throw new IllegalArgumentException("Skeleton must be a detached Group");
		this.skeleton = skeleton;
		skeleton.setParent(this);
		weightedVertexCount = vertices.getVertexCount();
		vertexInfluences = newInfluenceRows(weightedVertexCount);
	}

	public SkinnedMesh(VertexBuffer vertices, IndexBuffer submesh,
			Appearance appearance, Group skeleton) {
		this(vertices, new IndexBuffer[]{submesh}, new Appearance[]{appearance}, skeleton);
	}

	public void addTransform(Node bone, int weight, int firstVertex, int numVertices) {
		if (bone == null) throw new NullPointerException("bone");
		if (weight <= 0 || numVertices <= 0)
			throw new IllegalArgumentException("Bone weight and vertex count must be positive");
		if (firstVertex < 0 || (long)firstVertex + numVertices > 65535L)
			throw new IndexOutOfBoundsException("Invalid skinned vertex range");
		requireBone(bone);
		Transform toBone = findBindTransform(bone);
		if (toBone == null) {
			toBone = new Transform();
			if (!getTransformTo(bone, toBone))
				throw new IllegalArgumentException("Bone is not connected to this SkinnedMesh");
		}
		addTransform(bone, weight, firstVertex, numVertices, toBone);
	}

	void addTransform(Node bone, int weight, int firstVertex, int numVertices, Transform toBone) {
		if (bone == null) throw new NullPointerException("bone");
		if (toBone == null) throw new NullPointerException("toBone");
		if (weight <= 0 || numVertices <= 0)
			throw new IllegalArgumentException("Bone weight and vertex count must be positive");
		if (firstVertex < 0 || (long)firstVertex + numVertices > 65535L)
			throw new IndexOutOfBoundsException("Invalid skinned vertex range");
		requireBone(bone);
		Transform firstBindTransform = boneBindTransforms.get(bone);
		if (firstBindTransform == null) {
			firstBindTransform = new Transform();
			firstBindTransform.set(toBone);
			boneBindTransforms.put(bone, firstBindTransform);
		}
		boneBindings.add(new BoneBinding(bone, weight, firstVertex, numVertices,
				firstBindTransform));
		addInfluenceRange(bone, weight, firstVertex, numVertices);
	}

	public void getBoneTransform(Node bone, Transform transform) {
		if (transform == null) throw new NullPointerException("transform");
		requireBone(bone);
		Transform bind = boneBindTransforms.get(bone);
		if (bind != null) transform.set(bind);
	}

	public int getBoneVertices(Node bone, int[] indices, float[] weights) {
		requireBone(bone);
		int vertexCount = weightedVertexCount;
		int[] boneWeights = new int[weightedVertexCount];
		int[] totalWeights = new int[weightedVertexCount];
		for (int i = 0; i < weightedVertexCount; i++) {
			List<VertexInfluence> row = vertexInfluences[i];
			if (row == null) continue;
			for (VertexInfluence influence : row) {
				totalWeights[i] += influence.weight;
				if (influence.bone == bone) boneWeights[i] = influence.weight;
			}
		}
		int count = 0;
		for (int i = 0; i < vertexCount; i++) if (boneWeights[i] > 0) count++;
		if (indices != null && weights != null) {
			if (indices.length < count || weights.length < count)
				throw new IllegalArgumentException("Bone vertex output arrays are too small");
			int output = 0;
			for (int i = 0; i < vertexCount; i++) {
				if (boneWeights[i] > 0) {
					indices[output] = i;
					weights[output] = totalWeights[i] == 0 ? 0f
							: boneWeights[i] / (float)totalWeights[i];
					output++;
				}
			}
		}
		return count;
	}

	public Group getSkeleton() { return skeleton; }

	List<BoneBinding> boneBindings() {
		return new ArrayList<BoneBinding>(boneBindings);
	}

	int getBoneBindingCount() { return boneBindings.size(); }
	int getWeightedVertexCount() { return weightedVertexCount; }

	int getMaxBoneInfluencesPerVertex() {
		int max = 0;
		for (List<VertexInfluence> row : vertexInfluences)
			if (row != null) max = Math.max(max, row.size());
		return max;
	}

	float[] positionsForRendering() {
		VertexBuffer vertices = getVertexBuffer();
		int vertexCount = vertices.getVertexCount();
		float[] positions = new float[vertexCount * 3];
		if (boneBindings.isEmpty()) {
			copyBasePositions(vertices, positions, vertexCount);
			return positions;
		}

		IdentityHashMap<Node, float[]> matrices = new IdentityHashMap<Node, float[]>();
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			List<VertexInfluence> row = vertexInfluences[vertex];
			if (row == null) continue;
			for (VertexInfluence influence : row) {
				if (matrices.containsKey(influence.bone)) continue;
			Transform current = new Transform();
				Transform bind = boneBindTransforms.get(influence.bone);
				if (bind != null && influence.bone.getTransformTo(this, current)) {
					current.postMultiply(bind);
					float[] matrix = new float[16];
					current.getColumnMajor(matrix);
					matrices.put(influence.bone, matrix);
				} else {
					matrices.put(influence.bone, null);
				}
			}
		}

		for (int vertex = 0; vertex < vertexCount; vertex++) {
			float x = vertices.position(vertex, 0);
			float y = vertices.position(vertex, 1);
			float z = vertices.position(vertex, 2);
			float totalWeight = 0f, px = 0f, py = 0f, pz = 0f;
			List<VertexInfluence> row = vertexInfluences[vertex];
			if (row != null) for (VertexInfluence influence : row) {
				float[] m = matrices.get(influence.bone);
				if (m == null) continue;
				float weight = influence.weight;
				float tx = m[0] * x + m[4] * y + m[8] * z + m[12];
				float ty = m[1] * x + m[5] * y + m[9] * z + m[13];
				float tz = m[2] * x + m[6] * y + m[10] * z + m[14];
				float tw = m[3] * x + m[7] * y + m[11] * z + m[15];
				if (tw != 0f && tw != 1f) { tx /= tw; ty /= tw; tz /= tw; }
				px += tx * weight; py += ty * weight; pz += tz * weight;
				totalWeight += weight;
			}
			int offset = vertex * 3;
			if (totalWeight == 0f) {
				positions[offset] = x; positions[offset + 1] = y; positions[offset + 2] = z;
			} else {
				positions[offset] = px / totalWeight;
				positions[offset + 1] = py / totalWeight;
				positions[offset + 2] = pz / totalWeight;
			}
		}
		return positions;
	}

	private Transform findBindTransform(Node bone) {
		Transform binding = boneBindTransforms.get(bone);
		if (binding == null) return null;
		Transform transform = new Transform();
		transform.set(binding);
		return transform;
	}

	private void addInfluenceRange(Node bone, int weight, int firstVertex, int numVertices) {
		int end = firstVertex + numVertices;
		ensureInfluenceRows(end);
		for (int vertex = firstVertex; vertex < end; vertex++) {
			List<VertexInfluence> row = vertexInfluences[vertex];
			if (row == null) vertexInfluences[vertex] = row = new ArrayList<VertexInfluence>(MAX_BONES_PER_VERTEX);
			int slot = -1;
			int smallestSlot = -1;
			int smallestWeight = weight;
			for (int i = 0; i < row.size(); i++) {
				VertexInfluence influence = row.get(i);
				if (influence.bone == bone) {
					slot = i;
					break;
				}
				if (influence.weight < smallestWeight) {
					smallestWeight = influence.weight;
					smallestSlot = i;
				}
			}
			int incomingWeight = weight;
			if (slot >= 0) incomingWeight += row.get(slot).weight;
			else if (row.size() < MAX_BONES_PER_VERTEX) {
				row.add(new VertexInfluence(bone, 0));
				slot = row.size() - 1;
			} else slot = smallestSlot;
			if (slot < 0) continue;

			while (incomingWeight >= 256) {
				incomingWeight >>= 1;
				for (VertexInfluence influence : row) influence.weight >>= 1;
			}
			VertexInfluence influence = row.get(slot);
			influence.bone = bone;
			influence.weight = incomingWeight;
		}
	}

	@SuppressWarnings("unchecked")
	private static List<VertexInfluence>[] newInfluenceRows(int count) {
		return (List<VertexInfluence>[])new List<?>[count];
	}

	private void ensureInfluenceRows(int count) {
		weightedVertexCount = Math.max(weightedVertexCount, count);
		if (count > vertexInfluences.length) {
			int newLength = Math.max(count, Math.max(8, vertexInfluences.length * 2));
			vertexInfluences = Arrays.copyOf(vertexInfluences, newLength);
		}
	}

	private void requireBone(Node bone) {
		if (bone == null) throw new NullPointerException("bone");
		for (Node current = bone; current != null; current = current.getParent())
			if (current == skeleton) return;
		throw new IllegalArgumentException("Node is outside this SkinnedMesh skeleton");
	}

	private static void copyBasePositions(VertexBuffer vertices, float[] target, int count) {
		for (int i = 0; i < count; i++) {
			target[i * 3] = vertices.position(i, 0);
			target[i * 3 + 1] = vertices.position(i, 1);
			target[i * 3 + 2] = vertices.position(i, 2);
		}
	}

}
