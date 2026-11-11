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

public abstract class Node extends Transformable
{

	public static final int NONE  = 144;
	public static final int ORIGIN  = 145;
	public static final int X_AXIS  = 146;
	public static final int Y_AXIS  = 147;
	public static final int Z_AXIS  = 148;


	private Node alignRef;
	private Node parent;
	private float alphaFactor = 1f;
private boolean picking = true;
	private boolean rendering = true;
private int scope = -1;


	public void align(Node reference) {  }

	public Node getAlignmentReference(int axis) { return alignRef; }

	public int getAlignmentTarget(int axis) { return 0; }

	public float getAlphaFactor() { return alphaFactor; }

	public Node getParent() { return parent; }

	public int getScope() { return scope; }

public boolean getTransformTo(Node target, Transform transform) {
if(target==null||transform==null) throw new NullPointerException();
Node root=this, other=target;
while(root.parent!=null) root=root.parent;
while(other.parent!=null) other=other.parent;
if(root!=other) return false;
Transform source=new Transform(), destination=new Transform();
getCompositeTransform(source); target.getCompositeTransform(destination);
destination.invert(); destination.postMultiply(source); transform.set(destination);
return true;
}

	public boolean isPickingEnabled() { return picking; }

	public boolean isRenderingEnabled() { return rendering; }

	public void setAlignment(Node zRef, int zTarget, Node yRef, int yTarget) {  }

	public void setAlphaFactor(float value) { alphaFactor = value; }

	public void setPickingEnable(boolean enable) { picking = enable; }

	public void setRenderingEnable(boolean enable) { rendering = enable; }

	public void setScope(int value) { scope = value; }

	void setParent(Node value) { parent = value; }

	@Override public void getCompositeTransform(Transform result)
	{
		super.getCompositeTransform(result);
		if (parent != null) {
			Transform parentTransform = new Transform();
			parent.getCompositeTransform(parentTransform);
			parentTransform.postMultiply(result);
			result.set(parentTransform);
		}
	}

}
