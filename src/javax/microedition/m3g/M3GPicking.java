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

import java.util.Vector;

/** Shared M3G ray picking used by terrain queries, aiming, and object selection. */
final class M3GPicking
{
	private static final float EPSILON = 0.000001f;

	private M3GPicking() { }

	static boolean pick(Group root, int scope, float ox, float oy, float oz,
			float dx, float dy, float dz, RayIntersection result)
	{
		if (root == null || result == null) throw new NullPointerException();
		result.reset(ox, oy, oz, dx, dy, dz);
		float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!(length > EPSILON) || Float.isInfinite(length) || Float.isNaN(length)) return false;
		dx /= length; dy /= length; dz /= length;
		result.reset(ox, oy, oz, dx, dy, dz);
		if (!root.isPickingEnabled()) return false;
		PickState state = new PickState(root, scope, ox, oy, oz, dx, dy, dz, result);
		visit(root, state);
		return result.getIntersected() != null;
	}

	static boolean pick(Group root, int scope, float x, float y, Camera camera,
			RayIntersection result)
	{
		if (root == null || camera == null || result == null) throw new NullPointerException();
		result.reset(0f, 0f, 0f, 0f, 0f, 0f);
		if (!root.isPickingEnabled() || !camera.isPickingEnabled()) return false;

		Transform projection = new Transform();
		camera.getProjection(projection);
		try {
			projection.invert();
		} catch (ArithmeticException singular) {
			return false;
		}

		float ndcX = 2f * x - 1f;
		float ndcY = 1f - 2f * y;
		float[] nearPoint = unproject(projection, ndcX, ndcY, -1f);
		float[] farPoint = unproject(projection, ndcX, ndcY, 1f);
		if (nearPoint == null || farPoint == null) return false;

		float[] origin = new float[4];
		float[] direction = new float[4];
		if (camera.projectionType() == Camera.PERSPECTIVE) {
			origin[3] = 1f;
			direction[0] = farPoint[0];
			direction[1] = farPoint[1];
			direction[2] = farPoint[2];
		} else {
			origin[0] = nearPoint[0];
			origin[1] = nearPoint[1];
			origin[2] = nearPoint[2];
			origin[3] = 1f;
			direction[0] = farPoint[0] - nearPoint[0];
			direction[1] = farPoint[1] - nearPoint[1];
			direction[2] = farPoint[2] - nearPoint[2];
		}

		Transform cameraToRoot = new Transform();
		if (!camera.getTransformTo(root, cameraToRoot)) return false;
		cameraToRoot.transform(origin);
		cameraToRoot.transform(direction);
		if (Math.abs(origin[3]) > EPSILON && Math.abs(origin[3] - 1f) > EPSILON) {
			origin[0] /= origin[3]; origin[1] /= origin[3]; origin[2] /= origin[3];
		}
		float length = (float)Math.sqrt(direction[0] * direction[0]
				+ direction[1] * direction[1] + direction[2] * direction[2]);
		if (!(length > EPSILON) || Float.isInfinite(length) || Float.isNaN(length)) return false;
		return pick(root, scope, origin[0], origin[1], origin[2],
				direction[0] / length, direction[1] / length, direction[2] / length, result);
	}

	private static float[] unproject(Transform inverseProjection, float x, float y, float z)
	{
		float[] point = new float[]{x, y, z, 1f};
		inverseProjection.transform(point);
		if (Math.abs(point[3]) < EPSILON || Float.isNaN(point[3])) return null;
		return new float[]{point[0] / point[3], point[1] / point[3], point[2] / point[3]};
	}

	private static void visit(Node node, PickState state)
	{
		if (node == null || !node.isPickingEnabled()) return;
		if (node instanceof Mesh && matchesScope(node.getScope(), state.scope))
			pickMesh((Mesh)node, state);
		if (node instanceof Group) {
			Vector<Node> children = ((Group)node).children();
			for (int i = 0; i < children.size(); i++) visit(children.get(i), state);
		}
	}

	private static boolean matchesScope(int nodeScope, int queryScope)
	{
		return (nodeScope & queryScope) != 0;
	}

	private static void pickMesh(Mesh mesh, PickState state)
	{
		VertexBuffer vertices = mesh.getVertexBuffer();
		if (vertices == null || vertices.getVertexCount() < 3) return;
		float[] scaleBias = new float[4];
		VertexArray positions = vertices.getPositions(scaleBias);
		if (positions == null || positions.getVertexCount() < 3) return;

		Transform meshToRoot = new Transform();
		if (!mesh.getTransformTo(state.root, meshToRoot)) return;
		int vertexCount = vertices.getVertexCount();
		float[] points = new float[vertexCount * 4];
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < vertexCount; i++) {
			int p = i * 4;
			points[p] = vertices.position(i, 0);
			points[p + 1] = vertices.position(i, 1);
			points[p + 2] = vertices.position(i, 2);
			points[p + 3] = 1f;
		}
		meshToRoot.transform(points);
		for (int i = 0; i < vertexCount; i++) {
			int p = i * 4;
			if (Math.abs(points[p + 3]) < EPSILON || Float.isNaN(points[p + 3])) continue;
			if (Math.abs(points[p + 3] - 1f) > EPSILON) {
				points[p] /= points[p + 3];
				points[p + 1] /= points[p + 3];
				points[p + 2] /= points[p + 3];
			}
			points[p + 3] = 1f;
			minX = Math.min(minX, points[p]); minY = Math.min(minY, points[p + 1]);
			minZ = Math.min(minZ, points[p + 2]); maxX = Math.max(maxX, points[p]);
			maxY = Math.max(maxY, points[p + 1]); maxZ = Math.max(maxZ, points[p + 2]);
		}
		if (minX == Float.POSITIVE_INFINITY
				|| !intersectsBounds(state, minX, minY, minZ, maxX, maxY, maxZ)) return;

		VertexArray[] textureCoords = new VertexArray[RayIntersection.TEXTURE_COORDINATE_SETS];
		for (int set = 0; set < textureCoords.length; set++)
			textureCoords[set] = vertices.getTexCoords(set, new float[3]);

		for (int submesh = 0; submesh < mesh.getSubmeshCount(); submesh++) {
			IndexBuffer indexBuffer = mesh.getIndexBuffer(submesh);
			if (indexBuffer == null || indexBuffer.getIndexCount() < 3) continue;
			int[] indices = new int[indexBuffer.getIndexCount()];
			indexBuffer.getIndices(indices);
			int[] strips = indexBuffer instanceof TriangleStripArray
					? ((TriangleStripArray)indexBuffer).stripLengths()
					: new int[]{indices.length};
			int offset = 0;
			for (int stripLength : strips) {
				for (int j = 0; j + 2 < stripLength; j++) {
					int i0 = indices[offset + j], i1 = indices[offset + j + 1],
							i2 = indices[offset + j + 2];
					if ((j & 1) != 0) { int swap = i0; i0 = i1; i1 = swap; }
					if (i0 < 0 || i1 < 0 || i2 < 0
							|| i0 >= vertexCount || i1 >= vertexCount || i2 >= vertexCount) continue;
					intersectTriangle(mesh, vertices, textureCoords, points, i0, i1, i2,
							submesh, state);
				}
				offset += stripLength;
			}
		}
	}

	private static boolean intersectsBounds(PickState state, float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ)
	{
		float near = 0f, far = state.result.getDistance();
		if (Math.abs(state.dx) < EPSILON) {
			if (state.ox < minX || state.ox > maxX) return false;
		} else {
			float a = (minX - state.ox) / state.dx, b = (maxX - state.ox) / state.dx;
			if (a > b) { float swap = a; a = b; b = swap; }
			near = Math.max(near, a); far = Math.min(far, b);
			if (far < near) return false;
		}
		if (Math.abs(state.dy) < EPSILON) {
			if (state.oy < minY || state.oy > maxY) return false;
		} else {
			float a = (minY - state.oy) / state.dy, b = (maxY - state.oy) / state.dy;
			if (a > b) { float swap = a; a = b; b = swap; }
			near = Math.max(near, a); far = Math.min(far, b);
			if (far < near) return false;
		}
		if (Math.abs(state.dz) < EPSILON) {
			if (state.oz < minZ || state.oz > maxZ) return false;
		} else {
			float a = (minZ - state.oz) / state.dz, b = (maxZ - state.oz) / state.dz;
			if (a > b) { float swap = a; a = b; b = swap; }
			near = Math.max(near, a); far = Math.min(far, b);
		}
		return far >= near && far >= 0f;
	}

	private static void intersectTriangle(Mesh mesh, VertexBuffer vertices,
			VertexArray[] textureCoords, float[] points, int i0, int i1, int i2,
			int submesh, PickState state)
	{
		int p0 = i0 * 4, p1 = i1 * 4, p2 = i2 * 4;
		float ax = points[p0], ay = points[p0 + 1], az = points[p0 + 2];
		float e1x = points[p1] - ax, e1y = points[p1 + 1] - ay, e1z = points[p1 + 2] - az;
		float e2x = points[p2] - ax, e2y = points[p2 + 1] - ay, e2z = points[p2 + 2] - az;
		float px = state.dy * e2z - state.dz * e2y;
		float py = state.dz * e2x - state.dx * e2z;
		float pz = state.dx * e2y - state.dy * e2x;
		float determinant = e1x * px + e1y * py + e1z * pz;
		if (Math.abs(determinant) < EPSILON) return;
		float inverseDeterminant = 1f / determinant;
		float tx = state.ox - ax, ty = state.oy - ay, tz = state.oz - az;
		float baryU = (tx * px + ty * py + tz * pz) * inverseDeterminant;
		if (baryU < -EPSILON || baryU > 1f + EPSILON) return;
		float qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
		float baryV = (state.dx * qx + state.dy * qy + state.dz * qz) * inverseDeterminant;
		if (baryV < -EPSILON || baryU + baryV > 1f + EPSILON) return;
		float distance = (e2x * qx + e2y * qy + e2z * qz) * inverseDeterminant;
		if (distance < 0f || distance >= state.result.getDistance()) return;

		float nx = e1y * e2z - e1z * e2y;
		float ny = e1z * e2x - e1x * e2z;
		float nz = e1x * e2y - e1y * e2x;
		float normalLength = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (normalLength > EPSILON) { nx /= normalLength; ny /= normalLength; nz /= normalLength; }

		float[] s = new float[RayIntersection.TEXTURE_COORDINATE_SETS];
		float[] t = new float[RayIntersection.TEXTURE_COORDINATE_SETS];
		float baryW = 1f - baryU - baryV;
		for (int set = 0; set < textureCoords.length; set++) {
			VertexArray coords = textureCoords[set];
			if (coords == null || coords.getComponentCount() < 2
					|| coords.getVertexCount() <= Math.max(i0, Math.max(i1, i2))) continue;
			s[set] = baryW * vertices.texCoord(set, i0, 0)
					+ baryU * vertices.texCoord(set, i1, 0)
					+ baryV * vertices.texCoord(set, i2, 0);
			t[set] = baryW * vertices.texCoord(set, i0, 1)
					+ baryU * vertices.texCoord(set, i1, 1)
					+ baryV * vertices.texCoord(set, i2, 1);
		}
		state.result.setHit(mesh, distance, nx, ny, nz, submesh, s, t);
	}

	private static final class PickState
	{
		final Group root;
		final int scope;
		final float ox, oy, oz, dx, dy, dz;
		final RayIntersection result;

		PickState(Group root, int scope, float ox, float oy, float oz,
				float dx, float dy, float dz, RayIntersection result)
		{
			this.root = root; this.scope = scope;
			this.ox = ox; this.oy = oy; this.oz = oz;
			this.dx = dx; this.dy = dy; this.dz = dz;
			this.result = result;
		}
	}
}
