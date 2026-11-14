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
package com.mascotcapsule.micro3d.v3;

import java.io.IOException;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import org.recompile.mobile.Mobile;

public class Figure
{

	private byte[] figure;

	private ArrayList<Texture> textures = new ArrayList<Texture>();

	private int selected = 0;

	private int pattern = 0;

private Micro3DModel model;

	public Figure(byte[] fig) { figure = fig; }

Figure(Micro3DModel parsedModel)
{
if(parsedModel == null) throw new NullPointerException("model");
model = parsedModel;
}

	public Figure(String name) throws IOException
	{
		InputStream stream = Mobile.getPlatform().loader.getResourceAsStream(name);
		try
		{
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			int count=0;
			byte[] data = new byte[4096];
			while (count!=-1)
			{
				count = stream.read(data);
				if(count!=-1) { buffer.write(data, 0, count); }
			}
			figure = buffer.toByteArray();
		}
		catch (Exception e) {  }
	}


	public final void dispose() {  }

	public final void setPosture(ActionTable actiontable, int action, int frame)
	{
		if(actiontable == null) throw new NullPointerException("actionTable");
		Micro3DActionTable table = actiontable.checked();
		if(action < 0 || action >= table.actions.length)
			throw new IllegalArgumentException("action index");
		Micro3DActionTable.Action pose = table.actions[action];
		Micro3DModel currentModel = getModel();
		if(pose.bones.length != 0 && currentModel.bones.length != 0)
		{
			int vertex = 0;
			AffineTrans[] transforms = new AffineTrans[currentModel.bones.length];
			for(int boneIndex = 0; boneIndex < currentModel.bones.length; boneIndex++)
			{
				Micro3DModel.Bone bone = currentModel.bones[boneIndex];
				AffineTrans local = new AffineTrans(bone.transform);
				AffineTrans bindWorld = local;
				if(bone.parent >= 0)
				{
					if(bone.parent >= boneIndex)
						throw new IllegalArgumentException("Invalid MBAC bone hierarchy");
					bindWorld = new AffineTrans();
					bindWorld.mul(transforms[bone.parent], local);
				}
				AffineTrans actionTransform = boneIndex < pose.bones.length
						? pose.bones[boneIndex].at(frame) : new AffineTrans();
				if(boneIndex >= pose.bones.length) actionTransform.setIdentity();
				transforms[boneIndex] = new AffineTrans();
				transforms[boneIndex].mul(bindWorld, actionTransform);
				AffineTrans transform = transforms[boneIndex];
				for(int j = 0; j < bone.vertexCount; j++, vertex++)
				{
					int offset = vertex * 3;
					Vector3D position = transform.transform(new Vector3D(
							currentModel.originalVertices[offset],
							currentModel.originalVertices[offset + 1],
							currentModel.originalVertices[offset + 2]));
					currentModel.vertices[offset] = position.x;
					currentModel.vertices[offset + 1] = position.y;
					currentModel.vertices[offset + 2] = position.z;
					if(currentModel.normals != null)
					{
						long nx = (long) transform.m00 * currentModel.originalNormals[offset]
								+ (long) transform.m01 * currentModel.originalNormals[offset + 1]
								+ (long) transform.m02 * currentModel.originalNormals[offset + 2];
						long ny = (long) transform.m10 * currentModel.originalNormals[offset]
								+ (long) transform.m11 * currentModel.originalNormals[offset + 1]
								+ (long) transform.m12 * currentModel.originalNormals[offset + 2];
						long nz = (long) transform.m20 * currentModel.originalNormals[offset]
								+ (long) transform.m21 * currentModel.originalNormals[offset + 1]
								+ (long) transform.m22 * currentModel.originalNormals[offset + 2];
						currentModel.normals[offset] = (int)((nx + 2048) >> 12);
						currentModel.normals[offset + 1] = (int)((ny + 2048) >> 12);
						currentModel.normals[offset + 2] = (int)((nz + 2048) >> 12);
					}
				}
			}
		}
		pattern = pose.getPattern(frame, pattern);
	}

public final Texture getTexture()
{
if(selected < 0 || selected >= textures.size()) return null;
return textures.get(selected);
}

public final void setTexture(Texture texture)
{
if(texture == null) throw new NullPointerException();
if(!texture.isForModel()) throw new IllegalArgumentException();
textures.clear();
textures.add(texture);
selected = 0;
}

public final void setTexture(Texture[] atexture)
{
if(atexture == null) throw new NullPointerException();
if(atexture.length == 0) throw new IllegalArgumentException();
textures.clear();
for(Texture texture : atexture)
{
if(texture == null) throw new NullPointerException();
if(!texture.isForModel()) throw new IllegalArgumentException();
textures.add(texture);
}
selected = -1;
}

	public final int getNumTextures() { return textures.size(); }

public final void selectTexture(int i)
{
if(i < 0 || i >= textures.size()) throw new IllegalArgumentException();
selected = i;
}

public final int getNumPattern() { return getModel().patternCount; }

public final void setPattern(int value)
{
pattern = value;
}

final Micro3DModel getModel()
{
if(model == null) model = Micro3DModel.parse(figure);
return model;
}

final int getPatternMask() { return pattern; }

final Texture[] getTextures()
{
return textures.toArray(new Texture[textures.size()]);
}
}
