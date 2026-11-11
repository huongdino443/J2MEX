/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
*/
package javax.microedition.io.file;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;
import java.util.regex.Pattern;
import javax.microedition.io.Connector;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/**
 * JSR-75 implementation constrained to a per-MIDlet directory inside the
 * app-private data area. No file URL can address shared phone storage.
 */
public final class FileConnectionImpl implements FileConnection
{
	static final String ROOT_NAME = "freej2me";
	private final File root;
	private final int mode;
	private File file;
	private boolean open = true;

	public FileConnectionImpl(String url, int mode) throws IOException
	{
		if (mode != Connector.READ && mode != Connector.WRITE && mode != Connector.READ_WRITE)
			throw new IllegalArgumentException("Invalid connection mode: " + mode);
		this.root = storageRoot();
		this.mode = mode;
		this.file = resolveUrl(url);
	}

	static File storageRoot() throws IOException
	{
		MobilePlatform platform = Mobile.getPlatform();
		if (platform == null || platform.dataPath == null || platform.dataPath.trim().isEmpty())
			throw new IOException("MIDlet private data directory is not configured");
		File data = new File(platform.dataPath).getCanonicalFile();
		File root = new File(data, "jsr75-files").getCanonicalFile();
		if (!root.equals(data) && !root.getPath().startsWith(data.getPath() + File.separator))
			throw new IOException("Invalid private file root");
		if (!root.isDirectory() && !root.mkdirs() && !root.isDirectory())
			throw new IOException("Cannot create private file root: " + root);
		return root;
	}

	private File resolveUrl(String value) throws IOException
	{
		if (value == null) throw new IllegalArgumentException("File URL must not be null");
		final URI uri;
// MIDP applications commonly pass unescaped spaces in file names.
// Preserve existing escapes and the normal URI/root containment checks.
try { uri = new URI(value.replace(" ", "%20")); }
		catch (URISyntaxException error) { throw new IOException("Invalid file URL", error); }
		if (!"file".equalsIgnoreCase(uri.getScheme()) || uri.getRawAuthority() != null
				&& !uri.getRawAuthority().isEmpty() || uri.getQuery() != null || uri.getFragment() != null)
			throw new IOException("Unsupported file URL: " + value);
		String path = uri.getPath();
		if (path == null || path.indexOf('\\') >= 0) throw new IOException("Invalid file path");
		while (path.startsWith("/")) path = path.substring(1);
		if (path.isEmpty()) return root;
		String prefix = ROOT_NAME;
		if (path.equals(prefix) || path.equals(prefix + "/")) return root;
		if (!path.startsWith(prefix + "/")) throw new IOException("Unknown private file volume");
		return resolveRelative(path.substring(prefix.length() + 1));
	}

	private File resolveRelative(String relative) throws IOException
	{
		File candidate = relative == null || relative.isEmpty()
				? root : new File(root, relative.replace('/', File.separatorChar));
		File canonical = candidate.getCanonicalFile();
		String rootPath = root.getCanonicalPath();
		if (!canonical.equals(root) && !canonical.getPath().startsWith(rootPath + File.separator))
			throw new IOException("File URL escapes the MIDlet's private directory");
		return canonical;
	}

	private void checkOpen() throws IOException
	{
		if (!open) throw new IOException("FileConnection is closed");
	}

	private void checkRead() throws IOException
	{
		checkOpen();
		if ((mode & Connector.READ) == 0) throw new IOException("FileConnection is not open for reading");
	}

	private void checkWrite() throws IOException
	{
		checkOpen();
		if ((mode & Connector.WRITE) == 0) throw new IOException("FileConnection is not open for writing");
		if (isRoot()) throw new IOException("Cannot modify the private file-system root");
	}

	private File requireExisting() throws IOException
	{
		checkOpen();
		if (!file.exists()) throw new FileNotFoundException(file.toString());
		return file;
	}

	private File requireRegularFile() throws IOException
	{
		File target = requireExisting();
		if (!target.isFile()) throw new IOException("Not a file: " + target);
		return target;
	}

	public void close() { open = false; }

	public boolean isOpen() { return open; }

	public boolean exists() throws IOException
	{
		checkOpen();
		return isRoot() || file.exists();
	}

	public boolean isRoot() { return file.equals(root); }

	public boolean isDirectory() throws IOException
	{
		checkOpen();
		return isRoot() || file.isDirectory();
	}

	public boolean canRead() throws IOException
	{
		checkOpen();
		return (mode & Connector.READ) != 0 && (isRoot() || file.canRead());
	}

	public boolean canWrite() throws IOException
	{
		checkOpen();
		if ((mode & Connector.WRITE) == 0 || isRoot()) return false;
		File target = file.exists() ? file : file.getParentFile();
		return target != null && target.canWrite();
	}

	public long fileSize() throws IOException
	{
		return requireRegularFile().length();
	}

	public long lastModified() throws IOException
	{
		return requireExisting().lastModified();
	}

	public boolean isHidden() throws IOException
	{
		return requireExisting().isHidden();
	}

	public String getName()
	{
		if (isRoot()) return "";
		String name = file.getName();
		return (file.isDirectory() || file.getPath().endsWith(File.separator)) ? name + "/" : name;
	}

	public String getPath()
	{
		String relative = root.toPath().relativize(file.toPath()).toString()
				.replace(File.separatorChar, '/');
		String path = relative.isEmpty() ? "/" : "/" + relative;
		if ((file.isDirectory() || isRoot()) && !path.endsWith("/")) path += "/";
		return path;
	}

	public String getURL()
	{
		String path = "/" + ROOT_NAME + getPath();
		try { return new URI("file", "", path, null, null).toASCIIString(); }
		catch (URISyntaxException impossible) { throw new IllegalStateException(impossible); }
	}

	public void create() throws IOException
	{
		checkWrite();
		if (file.exists()) throw new IOException("File already exists: " + file);
		File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory()) throw new IOException("Parent directory does not exist");
		try { java.nio.file.Files.createFile(file.toPath()); }
		catch (java.nio.file.FileAlreadyExistsException error) { throw new IOException("File already exists", error); }
	}

	public void mkdir() throws IOException
	{
		checkWrite();
		File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory()) throw new IOException("Parent directory does not exist");
		if (file.exists() || !file.mkdir()) throw new IOException("Cannot create directory: " + file);
	}

	public void delete() throws IOException
	{
		checkWrite();
		if (!file.exists()) throw new FileNotFoundException(file.toString());
		if (!file.delete()) throw new IOException("Cannot delete: " + file);
	}

	public void truncate(long byteOffset) throws IOException
	{
		checkWrite();
		if (byteOffset < 0) throw new IllegalArgumentException("Negative truncate offset");
		File target = requireRegularFile();
		try (RandomAccessFile stream = new RandomAccessFile(target, "rw"))
		{
			stream.setLength(byteOffset);
		}
	}

	public InputStream openInputStream() throws IOException
	{
		checkRead();
		return new FileInputStream(requireRegularFile());
	}

	public DataInputStream openDataInputStream() throws IOException
	{
		return new DataInputStream(openInputStream());
	}

	public OutputStream openOutputStream() throws IOException
	{
		checkWrite();
		File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory()) throw new IOException("Parent directory does not exist");
		if (file.isDirectory()) throw new IOException("Cannot write to a directory");
		return new FileOutputStream(file, false);
	}

	public OutputStream openOutputStream(long byteOffset) throws IOException
	{
		checkWrite();
		if (byteOffset < 0) throw new IllegalArgumentException("Negative output offset");
		File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory()) throw new IOException("Parent directory does not exist");
		if (file.isDirectory()) throw new IOException("Cannot write to a directory");
		final RandomAccessFile stream = new RandomAccessFile(file, "rw");
		if (byteOffset > stream.length())
		{
			stream.close();
			throw new IOException("Output offset is past end of file");
		}
		stream.seek(byteOffset);
		return new OutputStream()
		{
			public void write(int value) throws IOException { stream.write(value); }
			public void write(byte[] bytes, int offset, int length) throws IOException
			{ stream.write(bytes, offset, length); }
			public void flush() throws IOException { stream.getFD().sync(); }
			public void close() throws IOException { stream.close(); }
		};
	}

	public DataOutputStream openDataOutputStream() throws IOException
	{
		return new DataOutputStream(openOutputStream());
	}

	public Enumeration list() throws IOException { return list("*", false); }

	public Enumeration list(String filter, boolean includeHidden) throws IOException
	{
		File directory = requireExisting();
		if (!directory.isDirectory()) throw new IOException("Not a directory: " + directory);
		String expression = wildcardRegex(filter == null || filter.length() == 0 ? "*" : filter);
		Pattern pattern = Pattern.compile(expression);
		File[] entries = directory.listFiles();
		if (entries == null) throw new IOException("Cannot list directory: " + directory);
		Arrays.sort(entries, (left, right) -> left.getName().compareTo(right.getName()));
		Vector names = new Vector(entries.length);
		for (File entry : entries)
		{
			if (!includeHidden && entry.isHidden()) continue;
			if (!pattern.matcher(entry.getName()).matches()) continue;
			String name = entry.getName();
			if (entry.isDirectory()) name += "/";
			names.addElement(name);
		}
		return names.elements();
	}

	private static String wildcardRegex(String filter)
	{
		StringBuilder result = new StringBuilder("^");
		for (int i = 0; i < filter.length(); i++)
		{
			char character = filter.charAt(i);
			if (character == '*') result.append(".*");
			else if (character == '?') result.append('.');
			else result.append(Pattern.quote(String.valueOf(character)));
		}
		return result.append('$').toString();
	}

	public void rename(String newName) throws IOException
	{
		checkWrite();
		if (!file.exists()) throw new FileNotFoundException(file.toString());
		if (newName == null || newName.length() == 0 || newName.indexOf('/') >= 0
				|| newName.indexOf('\\') >= 0 || ".".equals(newName) || "..".equals(newName))
			throw new IOException("Invalid file name");
		File target = new File(file.getParentFile(), newName).getCanonicalFile();
		resolveRelative(root.toPath().relativize(target.toPath()).toString().replace(File.separatorChar, '/'));
		if (target.exists() || !file.renameTo(target)) throw new IOException("Cannot rename file");
		file = target;
	}

	public void setFileConnection(String fileName) throws IOException
	{
		checkOpen();
		if (fileName == null || fileName.length() == 0) throw new IOException("Empty file name");
		if (fileName.indexOf('\\') >= 0) throw new IOException("Invalid file name");
		String relative;
		if (fileName.startsWith("/"))
		{
			relative = fileName.substring(1);
		}
		else
		{
			File base = file.isDirectory() ? file : file.getParentFile();
			if (base == null) base = root;
			File target = new File(base, fileName);
			File canonical = target.getCanonicalFile();
			String rootPath = root.getCanonicalPath();
			if (!canonical.equals(root) && !canonical.getPath().startsWith(rootPath + File.separator))
				throw new IOException("File path escapes the private root");
			file = canonical;
			return;
		}
		file = resolveUrl("file:///" + ROOT_NAME + "/" + relative);
	}

	public void setReadable(boolean readable) throws IOException
	{
		checkOpen();
		if (!file.exists() || !file.setReadable(readable, false))
			throw new IOException("Cannot change file readability");
	}

	public void setWritable(boolean writable) throws IOException
	{
		checkOpen();
		if (!file.exists() || !file.setWritable(writable, false))
			throw new IOException("Cannot change file writability");
	}

	public void setHidden(boolean hidden) throws IOException
	{
		checkWrite();
		if (!file.exists() || isRoot()) throw new IOException("Cannot change hidden state");
		String name = file.getName();
		boolean currentlyHidden = file.isHidden();
		if (currentlyHidden == hidden) return;
		String next = hidden ? "." + name : name.substring(1);
		File target = new File(file.getParentFile(), next);
		if (target.exists() || !file.renameTo(target)) throw new IOException("Cannot change hidden state");
		file = target.getCanonicalFile();
	}

	public long directorySize(boolean includeSubDirs) throws IOException
	{
		File directory = requireExisting();
		if (!directory.isDirectory()) throw new IOException("Not a directory: " + directory);
		return sizeOf(directory, includeSubDirs);
	}

	private static long sizeOf(File directory, boolean includeSubDirs)
	{
		File[] entries = directory.listFiles();
		if (entries == null) return 0;
		long total = 0;
		for (File entry : entries)
		{
			if (entry.isFile()) total = saturatedAdd(total, entry.length());
			else if (includeSubDirs && entry.isDirectory()) total = saturatedAdd(total, sizeOf(entry, true));
		}
		return total;
	}

	private static long saturatedAdd(long left, long right)
	{
		return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
	}

	public long totalSize() { return root.getTotalSpace(); }
	public long availableSize() { return root.getUsableSpace(); }
	public long usedSize() { return Math.max(0, totalSize() - availableSize()); }
}
