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

public class Transform
{

	private final float[] matrix = new float[16];

	public Transform() { setIdentity(); }

	public Transform(Transform transform)
	{
		if (transform == null) throw new NullPointerException("transform");
transform.getColumnMajor(matrix);
	}

	public void get(float[] destination)
	{
		if (destination == null || destination.length < 16) throw new IllegalArgumentException("Matrix needs 16 values");
// JSR-184 exposes row-major arrays; the rasterizer keeps column-major storage.
for (int row = 0; row < 4; row++)
for (int col = 0; col < 4; col++) destination[row * 4 + col] = matrix[col * 4 + row];
	}

	public void invert()
	{
		double[][] a = new double[4][8];
		for (int row = 0; row < 4; row++) {
			for (int col = 0; col < 4; col++) a[row][col] = matrix[col * 4 + row];
			a[row][row + 4] = 1.0;
		}
		for (int col = 0; col < 4; col++) {
			int pivot = col;
			for (int row = col + 1; row < 4; row++)
				if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) pivot = row;
			if (Math.abs(a[pivot][col]) < 1e-12) throw new ArithmeticException("Transform is singular");
			double[] swap = a[col]; a[col] = a[pivot]; a[pivot] = swap;
			double divisor = a[col][col];
			for (int j = 0; j < 8; j++) a[col][j] /= divisor;
			for (int row = 0; row < 4; row++) if (row != col) {
				double factor = a[row][col];
				for (int j = 0; j < 8; j++) a[row][j] -= factor * a[col][j];
			}
		}
		for (int row = 0; row < 4; row++)
			for (int col = 0; col < 4; col++) matrix[col * 4 + row] = (float) a[row][col + 4];
	}

	public void postMultiply(Transform transform)
	{
		if (transform == null) throw new NullPointerException("transform");
		multiply(matrix, transform.matrix);
	}

	public void postRotate(float angle, float ax, float ay, float az)
	{
if (angle % 360f == 0) return;
		float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		if (length == 0) throw new IllegalArgumentException("Rotation axis is zero");
		ax /= length; ay /= length; az /= length;
		float c = (float) Math.cos(Math.toRadians(angle));
		float s = (float) Math.sin(Math.toRadians(angle));
		float t = 1 - c;
		float[] r = identity();
		r[0] = t * ax * ax + c;       r[4] = t * ax * ay - s * az; r[8] = t * ax * az + s * ay;
		r[1] = t * ax * ay + s * az; r[5] = t * ay * ay + c;       r[9] = t * ay * az - s * ax;
		r[2] = t * ax * az - s * ay; r[6] = t * ay * az + s * ax; r[10] = t * az * az + c;
		multiply(matrix, r);
	}

	public void postRotateQuat(float qx, float qy, float qz, float qw)
	{
		float length = (float) Math.sqrt(qx*qx + qy*qy + qz*qz + qw*qw);
		if (length == 0) throw new IllegalArgumentException("Quaternion is zero");
		qx /= length; qy /= length; qz /= length; qw /= length;
		float[] r = identity();
		r[0] = 1 - 2*(qy*qy + qz*qz); r[4] = 2*(qx*qy - qz*qw);     r[8] = 2*(qx*qz + qy*qw);
		r[1] = 2*(qx*qy + qz*qw);     r[5] = 1 - 2*(qx*qx + qz*qz); r[9] = 2*(qy*qz - qx*qw);
		r[2] = 2*(qx*qz - qy*qw);     r[6] = 2*(qy*qz + qx*qw);     r[10] = 1 - 2*(qx*qx + qy*qy);
		multiply(matrix, r);
	}

	public void postScale(float sx, float sy, float sz)
	{
		matrix[0] *= sx; matrix[1] *= sx; matrix[2] *= sx; matrix[3] *= sx;
		matrix[4] *= sy; matrix[5] *= sy; matrix[6] *= sy; matrix[7] *= sy;
		matrix[8] *= sz; matrix[9] *= sz; matrix[10] *= sz; matrix[11] *= sz;
	}

	public void postTranslate(float tx, float ty, float tz)
	{
		matrix[12] += matrix[0] * tx + matrix[4] * ty + matrix[8] * tz;
		matrix[13] += matrix[1] * tx + matrix[5] * ty + matrix[9] * tz;
		matrix[14] += matrix[2] * tx + matrix[6] * ty + matrix[10] * tz;
		matrix[15] += matrix[3] * tx + matrix[7] * ty + matrix[11] * tz;
	}

	public void set(float[] source)
	{
		if (source == null || source.length < 16) throw new IllegalArgumentException("Matrix needs 16 values");
for (int row = 0; row < 4; row++)
for (int col = 0; col < 4; col++) matrix[col * 4 + row] = source[row * 4 + col];
	}

	public void set(Transform transform)
	{
		if (transform == null) throw new NullPointerException("transform");
transform.getColumnMajor(matrix);
	}

	public void setIdentity() { System.arraycopy(identity(), 0, matrix, 0, 16); }

	public void transform(float[] vectors)
	{
if (vectors == null || vectors.length % 4 != 0) throw new IllegalArgumentException("Expected homogeneous xyzw vectors");
for (int i = 0; i < vectors.length; i += 4) {
float x = vectors[i], y = vectors[i+1], z = vectors[i+2], w = vectors[i+3];
vectors[i] = matrix[0]*x + matrix[4]*y + matrix[8]*z + matrix[12]*w;
vectors[i+1] = matrix[1]*x + matrix[5]*y + matrix[9]*z + matrix[13]*w;
vectors[i+2] = matrix[2]*x + matrix[6]*y + matrix[10]*z + matrix[14]*w;
vectors[i+3] = matrix[3]*x + matrix[7]*y + matrix[11]*z + matrix[15]*w;
		}
	}

	public void transform(VertexArray in, float[] out, boolean W)
	{
		if (in == null || out == null) throw new NullPointerException();
		int stride = W ? 4 : 3;
		if (out.length < in.getVertexCount() * stride) throw new IllegalArgumentException("Output is too small");
		for (int i = 0; i < in.getVertexCount(); i++) {
			float x = in.getComponentCount() > 0 ? in.component(i, 0) : 0;
			float y = in.getComponentCount() > 1 ? in.component(i, 1) : 0;
			float z = in.getComponentCount() > 2 ? in.component(i, 2) : 0;
			float w = matrix[3]*x + matrix[7]*y + matrix[11]*z + matrix[15];
			float tx = matrix[0]*x + matrix[4]*y + matrix[8]*z + matrix[12];
			float ty = matrix[1]*x + matrix[5]*y + matrix[9]*z + matrix[13];
			float tz = matrix[2]*x + matrix[6]*y + matrix[10]*z + matrix[14];
			if (W) {
				out[i*4] = tx; out[i*4+1] = ty; out[i*4+2] = tz; out[i*4+3] = w;
			} else {
				float invW = w == 0 ? 1 : 1 / w;
				out[i*3] = tx * invW; out[i*3+1] = ty * invW; out[i*3+2] = tz * invW;
			}
		}
	}

	public void transpose()
	{
		for (int row = 0; row < 4; row++)
			for (int col = row + 1; col < 4; col++) {
				int a = col * 4 + row, b = row * 4 + col;
				float swap = matrix[a]; matrix[a] = matrix[b]; matrix[b] = swap;
			}
	}

	float[] values() { return matrix; }

void getColumnMajor(float[] destination) { System.arraycopy(matrix, 0, destination, 0, 16); }

void setColumnMajor(float[] source) { System.arraycopy(source, 0, matrix, 0, 16); }

	private void multiply(float[] left, float[] right)
	{
		float[] result = new float[16];
		for (int col = 0; col < 4; col++)
			for (int row = 0; row < 4; row++)
				for (int k = 0; k < 4; k++)
					result[col*4+row] += left[k*4+row] * right[col*4+k];
		System.arraycopy(result, 0, matrix, 0, 16);
	}

	private static float[] identity()
	{
		float[] result = new float[16];
		result[0] = result[5] = result[10] = result[15] = 1;
		return result;
	}

}
