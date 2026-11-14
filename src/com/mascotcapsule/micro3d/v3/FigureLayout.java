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

import java.util.ArrayList;

public class FigureLayout
{
private int scalex=512;
private int scaley=512;
private int centerx=0;
private int centery=0;

private int pwidth=0;
private int pheight=0;
private int near=1;
private int far=32767;
private int angle=1024;
private int perspectiveWidth=0;
private int perspectiveHeight=0;
private int projection=Graphics3D.COMMAND_PARALLEL_SCALE;

	private int selected = 0;

	private ArrayList<AffineTrans> trans = new ArrayList<AffineTrans>();


public FigureLayout()
{
setAffineTrans((AffineTrans)null);
}

	public FigureLayout(AffineTrans atrans, int sx, int sy, int cx, int cy)
	{
		scalex = sx;
		scaley = sy;
		centerx = cx;
		centery = cy;
setAffineTrans(atrans);
	}


	public final AffineTrans getAffineTrans() { return trans.get(selected); }

	public final void setAffineTrans(AffineTrans atrans)
	{
if(atrans==null)
{
atrans = new AffineTrans(4096, 0, 0, 0, 0, 4096, 0, 0, 0, 0, 4096, 0);
}

		if(trans.size()==0)
		{
			trans.add(atrans);
		}
		else
		{
			trans.set(selected, atrans);
		}
	}

	public final void setAffineTransArray(AffineTrans[] atrans)
	{
		for(int i=0; i<atrans.length; i++)
		{
			trans.add(atrans[i]);
		}
	}

	public final void setAffineTrans(AffineTrans[] atrans) { setAffineTransArray(atrans); }

	public final void selectAffineTrans(int i) { selected = i; }

	public final int getScaleX() { return scalex; }

	public final int getScaleY() { return scaley; }

public final void setScale(int x, int y) { scalex=x; scaley=y; projection=Graphics3D.COMMAND_PARALLEL_SCALE; }


	public final int getParallelWidth() { return pwidth; }

	public final int getParallelHeight() { return pheight; }

public final void setParallelSize(int w, int h)
{
if(w < 0 || h < 0) throw new IllegalArgumentException();
pwidth=w; pheight=h; projection=Graphics3D.COMMAND_PARALLEL_SIZE;
}


	public final int getCenterX() { return centerx; }

	public final int getCenterY() { return centery; }

	public final void setCenter(int x, int y) { centerx=x; centery=y; }

	public final void setPerspective(int zNear, int zFar, int viewAngle)
	{
if(zNear >= zFar || zNear < 1 || zFar > 32767 || viewAngle < 1 || viewAngle > 2047)
throw new IllegalArgumentException();
near=zNear; far=zFar; angle=viewAngle; projection=Graphics3D.COMMAND_PERSPECTIVE_FOV;
	}

	public final void setPerspective(int zNear, int zFar, int width, int height)
	{
if(zNear >= zFar || zNear < 1 || zFar > 32767 || width < 0 || height < 0)
throw new IllegalArgumentException();
near=zNear; far=zFar; perspectiveWidth=width; perspectiveHeight=height;
projection=Graphics3D.COMMAND_PERSPECTIVE_WH;
	}

final int getProjection() { return projection; }
final int getNear() { return near; }
final int getFar() { return far; }
final int getAngle() { return angle; }
final int getPerspectiveWidth() { return perspectiveWidth; }
final int getPerspectiveHeight() { return perspectiveHeight; }
}
