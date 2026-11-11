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

public class Camera extends Node
{

	public static final int GENERIC = 48;
	public static final int PARALLEL = 49;
	public static final int PERSPECTIVE = 50;

	private int projection = PERSPECTIVE;
	private float fovy = 45f, aspect = 1f, near = 0.1f, far = 1000f;
	private final Transform generic = new Transform();

	public Camera() {  }


	public int getProjection(float[] params)
	{
		if (projection != GENERIC && params != null && params.length >= 4) {
			params[0] = fovy; params[1] = aspect; params[2] = near; params[3] = far;
		}
		return projection;
	}

	public int getProjection(Transform transform)
	{
		if (transform == null) throw new NullPointerException("transform");
		if (projection == GENERIC) transform.set(generic);
		else {
			float[] m = new float[16];
			if (projection == PERSPECTIVE) {
				float f = (float)(1.0 / Math.tan(Math.toRadians(fovy) * 0.5));
				m[0] = f / aspect; m[5] = f;
				m[10] = (far + near) / (near - far);
				m[11] = -1;
				m[14] = (2 * far * near) / (near - far);
			} else {
float top = fovy * 0.5f;
				float right = top * aspect;
				m[0] = 1 / right; m[5] = 1 / top;
				m[10] = 2 / (near - far);
				m[14] = (far + near) / (near - far);
				m[15] = 1;
			}
transform.setColumnMajor(m);
		}
		return projection;
	}

	public void setGeneric(Transform transform)
	{
		if (transform == null) throw new NullPointerException("transform");
		generic.set(transform); projection = GENERIC;
	}

	public void setParallel(float fovy, float aspectRatio, float near, float far)
	{
if (!(fovy>0) || !(aspectRatio>0) || !(far>near))
throw new IllegalArgumentException("Invalid parallel camera projection");
		this.fovy=fovy; this.aspect=aspectRatio; this.near=near; this.far=far; projection=PARALLEL;
	}

	public void setPerspective(float fovy, float aspectRatio, float near, float far)
	{
		checkProjection(fovy, aspectRatio, near, far);
		this.fovy=fovy; this.aspect=aspectRatio; this.near=near; this.far=far; projection=PERSPECTIVE;
	}

	int projectionType() { return projection; }
	float fieldOfView() { return fovy; }
	float aspectRatio() { return aspect; }
	float nearPlane() { return near; }
	float farPlane() { return far; }

	private static void checkProjection(float fovy, float aspect, float near, float far)
	{
		if (!(fovy > 0 && fovy < 180) || !(aspect > 0) || !(near > 0) || !(far > near))
			throw new IllegalArgumentException("Invalid camera projection");
	}

}
