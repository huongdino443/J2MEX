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

public abstract class Object3D
{

	private int userid;
private Object userObject;
final java.util.List<AnimationTrack> tracks = new java.util.ArrayList<AnimationTrack>();


public void addAnimationTrack(AnimationTrack animationTrack) {
if (animationTrack == null) throw new NullPointerException("animationTrack");
if (tracks.contains(animationTrack)) throw new IllegalArgumentException("Duplicate animation track");
tracks.add(animationTrack);
}

public int animate(int time) { return M3GAnimation.animate(this, time); }

public Object3D duplicate() { return M3GGraph.duplicate(this); }

public Object3D find(int userID) { return M3GGraph.find(this, userID); }

public AnimationTrack getAnimationTrack(int index) { return tracks.get(index); }

public int getAnimationTrackCount() { return tracks.size(); }

public int getReferences(Object3D[] references) {
java.util.List<Object3D> values = M3GGraph.references(this);
if (references != null) {
if (references.length < values.size()) throw new IllegalArgumentException("Reference array too small");
for (int i=0;i<values.size();i++) references[i]=values.get(i);
}
return values.size();
}

	public int getUserID() { return userid; }

public Object getUserObject() { return userObject; }

public void removeAnimationTrack(AnimationTrack animationTrack) { tracks.remove(animationTrack); }

	public void setUserID(int userID) { userid = userID; }

public void setUserObject(java.lang.Object userObject) { this.userObject = userObject; }

}
