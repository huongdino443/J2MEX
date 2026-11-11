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

public class TriangleStripArray extends IndexBuffer
{

	private final int[] stripLengths;

	public TriangleStripArray(int[] indices, int[] stripLengths)
	{
		if (indices == null || stripLengths == null) throw new NullPointerException();
		this.indices = indices.clone();
		this.stripLengths = stripLengths.clone();
		validateStrips();
	}

	public TriangleStripArray(int firstIndex, int[] stripLengths)
	{
		if (stripLengths == null) throw new NullPointerException("stripLengths");
		this.stripLengths = stripLengths.clone();
		int count = validateStrips();
		this.indices = new int[count];
		for (int i = 0; i < count; i++) this.indices[i] = firstIndex + i;
	}

	int[] stripLengths()
	{
		return stripLengths;
	}

	private int validateStrips()
	{
		int count = 0;
		for (int length : stripLengths) {
			if (length < 3) throw new IllegalArgumentException("Triangle strips need at least three indices");
			count += length;
		}
		if (count != indices.length && indices.length != 0)
			throw new IllegalArgumentException("Strip lengths do not match index count");
		return count;
	}

}
