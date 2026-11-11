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

public abstract class Transformable extends Object3D
{

// JSR-184 keeps the component translation/orientation/scale separate from
// the optional generic matrix supplied through setTransform().
private final Transform staticTransform = new Transform();
private final Transform orientation = new Transform();
private float tx, ty, tz;
private float sx = 1f, sy = 1f, sz = 1f;

public void getCompositeTransform(Transform result) { getLocalCompositeTransform(result); }

	public void getOrientation(float[] angleAxis)
	{
		if (angleAxis == null || angleAxis.length < 4) throw new IllegalArgumentException("Need angle and axis");
float[] m = new float[16]; orientation.getColumnMajor(m);
float r00 = m[0], r11 = m[5], r22 = m[10];
		float angle = (float)Math.acos(Math.max(-1, Math.min(1, (r00+r11+r22-1)*0.5)));
		float s = (float)Math.sin(angle);
		angleAxis[0] = (float)Math.toDegrees(angle);
		if (Math.abs(s) < 1e-6) { angleAxis[1]=0; angleAxis[2]=1; angleAxis[3]=0; }
		else {
angleAxis[1] = (m[6] - m[9]) / (2*s);
angleAxis[2] = (m[8] - m[2]) / (2*s);
angleAxis[3] = (m[1] - m[4]) / (2*s);
		}
	}

	public void getScale(float[] xyz)
	{
		if (xyz == null || xyz.length < 3) throw new IllegalArgumentException("Need xyz scale");
xyz[0]=sx; xyz[1]=sy; xyz[2]=sz;
	}

public void getTransform(Transform result)
{
if (result == null) throw new NullPointerException("transform");
result.set(staticTransform);
}

	public void getTranslation(float[] xyz)
	{
		if (xyz == null || xyz.length < 3) throw new IllegalArgumentException("Need xyz translation");
xyz[0]=tx; xyz[1]=ty; xyz[2]=tz;
	}

public void postRotate(float angle, float ax, float ay, float az) { orientation.postRotate(angle, ax, ay, az); }

	public void preRotate(float angle, float ax, float ay, float az)
	{
		Transform rotation = new Transform();
		rotation.postRotate(angle, ax, ay, az);
rotation.postMultiply(orientation);
orientation.set(rotation);
	}

public void scale(float sx, float sy, float sz)
{
this.sx *= sx; this.sy *= sy; this.sz *= sz;
}

	public void setOrientation(float angle, float ax, float ay, float az)
	{
orientation.setIdentity();
orientation.postRotate(angle, ax, ay, az);
	}

	public void setScale(float sx, float sy, float sz)
	{
this.sx=sx; this.sy=sy; this.sz=sz;
	}

public void setTransform(Transform value)
{
if (value == null) staticTransform.setIdentity();
else staticTransform.set(value);
}

	public void setTranslation(float tx, float ty, float tz)
	{
this.tx=tx; this.ty=ty; this.tz=tz;
	}

public void translate(float tx, float ty, float tz)
{
this.tx += tx; this.ty += ty; this.tz += tz;
}

void getLocalCompositeTransform(Transform result)
{
if (result == null) throw new NullPointerException("transform");
result.setIdentity();
result.postTranslate(tx,ty,tz);
result.postMultiply(orientation);
result.postScale(sx,sy,sz);
result.postMultiply(staticTransform);
}

void copyTransformableStateTo(Transformable target)
{
if (target == null) throw new NullPointerException("target");
target.tx=tx; target.ty=ty; target.tz=tz;
target.sx=sx; target.sy=sy; target.sz=sz;
target.orientation.set(orientation);
target.staticTransform.set(staticTransform);
}

Transform localTransform()
{
Transform result = new Transform();
getLocalCompositeTransform(result);
return result;
}

}
