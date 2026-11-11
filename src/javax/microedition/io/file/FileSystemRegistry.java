/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
*/
package javax.microedition.io.file;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

/** Exposes only this MIDlet's private persistent file area. */
public final class FileSystemRegistry
{
	private FileSystemRegistry() {}

	public static Enumeration listRoots()
	{
		Vector roots = new Vector(1);
		try
		{
			FileConnectionImpl.storageRoot();
			roots.addElement(FileConnectionImpl.ROOT_NAME + "/");
		}
		catch (IOException error)
		{
			System.err.println("FileSystemRegistry: private file root unavailable: " + error);
		}
		return roots.elements();
	}
}
