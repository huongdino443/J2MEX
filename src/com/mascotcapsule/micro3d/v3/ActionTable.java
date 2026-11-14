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

import org.recompile.mobile.Mobile;

public class ActionTable
{

	private Micro3DActionTable parsed;

	public ActionTable(byte[] b)
	{
		if(b == null) throw new NullPointerException("action table");
		try { parsed = Micro3DActionTable.parse(b); }
		catch(IOException e) { throw new IllegalArgumentException("Invalid Mascot Capsule action table", e); }
	}

	public ActionTable(String name) throws IOException
	{
		byte[] bytes = Mobile.getPlatform().loader.getMIDletResourceAsByteArray(name);
		if(bytes == null) throw new IOException("Action table resource not found: " + name);
		parsed = Micro3DActionTable.parse(bytes);
	}

	public final void dispose() { parsed = null; }

	public final int getNumAction() { return getNumActions(); }

	public final int getNumActions() { return checked().actions.length; }

	public final int getNumFrame(int idx) { return getNumFrames(idx); }

	public final int getNumFrames(int idx)
	{
		Micro3DActionTable table = checked();
		if(idx < 0 || idx >= table.actions.length) throw new IllegalArgumentException("action index");
		return table.actions[idx].keyframes << 16;
	}

	final Micro3DActionTable checked()
	{
		if(parsed == null) throw new IllegalStateException("ActionTable disposed");
		return parsed;
	}

}
