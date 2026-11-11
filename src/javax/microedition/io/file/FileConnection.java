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
import javax.microedition.io.StreamConnection;

/** JSR-75 file connection surface used by MIDlets. */
public interface FileConnection extends StreamConnection
{
	boolean canRead() throws IOException;
	boolean canWrite() throws IOException;
	void create() throws IOException;
	void delete() throws IOException;
	long directorySize(boolean includeSubDirs) throws IOException;
	boolean exists() throws IOException;
	long fileSize() throws IOException;
	String getName();
	String getPath();
	String getURL();
	boolean isDirectory() throws IOException;
	boolean isHidden() throws IOException;
	boolean isOpen();
	boolean isRoot();
	long lastModified() throws IOException;
	Enumeration list() throws IOException;
	Enumeration list(String filter, boolean includeHidden) throws IOException;
	void mkdir() throws IOException;
	void rename(String newName) throws IOException;
	void setFileConnection(String fileName) throws IOException;
	void setHidden(boolean hidden) throws IOException;
	void setReadable(boolean readable) throws IOException;
	void setWritable(boolean writable) throws IOException;
	void truncate(long byteOffset) throws IOException;
	long totalSize();
	long usedSize();
	long availableSize();
	java.io.OutputStream openOutputStream(long byteOffset) throws IOException;
}
