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
package javax.microedition.rms;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.Vector;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.recompile.mobile.Mobile;

public class RecordStore
{

	public static final int AUTHMODE_ANY = 1;
	public static final int AUTHMODE_PRIVATE = 0;


	private String name;

	private String appname;

	private static String rmsPath;
private static final AtomicBoolean cityBloxxReadTraceStarted = new AtomicBoolean();

	private String rmsFile;

	private File file;

	private int version = 0;

	private int nextid = 0;

	private Vector<byte[]> records;

	private Vector<RecordListener> listeners;
// Legacy files used length zero for deleted records. A trailer now records
// which zero-length slots are live, without changing the legacy record layout.
private static final byte[] EMPTY_RECORD_MARKER = new byte[]{'R', 'M', 'S', 'E'};
private static final byte[] LARGE_RECORD_MARKER =
		new byte[]{'F', 'J', '2', 'M', 'R', 'M', 'X', '1'};
private static final int LARGE_RECORD_REFERENCE_SIZE = 8 + 4 + 32;

	private int lastModified = 0;

	private RecordStore(String recordStoreName, boolean createIfNecessary) throws RecordStoreException, RecordStoreNotFoundException
	{
		//System.out.println("> RecordStore "+recordStoreName);

		records = new Vector<byte[]>();
		listeners = new Vector<RecordListener>();

		records.add(new byte[]{}); // dummy record (record ids start at 1)

		int count;
		int offset;
		int reclen;

		name = recordStoreName;

		appname = Mobile.getPlatform().loader.suitename;

		rmsPath = Mobile.getPlatform().dataPath + "./rms/"+appname;
		rmsFile = Mobile.getPlatform().dataPath + "./rms/"+appname+"/"+recordStoreName;

		try
		{
			Files.createDirectories(Paths.get(rmsPath));
		}
		catch (Exception e)
		{
			//System.out.println("> Problem Creating Record Store Path: "+rmsPath);
			System.out.println(e.getMessage());
			throw(new RecordStoreException("Problem Creating Record Store Path "+rmsPath));
		}
		file = new File(rmsFile);
		logCityBloxxCityStore("open create=" + createIfNecessary
			+ " exists=" + file.isFile()
			+ " fileBytes=" + (file.isFile() ? file.length() : -1));
		if(!file.exists())
		{
			if(!createIfNecessary)
			{
				if(!org.recompile.mobile.CityBloxxFirstRun.seedMissingModeStore(
					Mobile.getPlatform().loader, appname, recordStoreName, file))
				{
					throw (new RecordStoreNotFoundException("Record Store Doesn't Exist: " + rmsFile));
				}
			}
			else
			{
				try // Check Record Store File
				{
					//System.out.println("> Creating New Record Store "+appname+"/"+recordStoreName);
					file.createNewFile();
					version = 1;
					nextid = 1;
					count = 0;
					save();
					nextid = 1;
				}
				catch (Exception e)
				{
					//System.out.println("> Problem Opening Record Store (createIfNecessary "+createIfNecessary+"): "+rmsFile);
					System.out.println(e.getMessage());
					throw(new RecordStoreException("Problem Opening Record Store (createIfNecessary "+createIfNecessary+"): "+rmsFile));
				}
			}
		}

		try // Read Records
		{
			Path path = Paths.get(file.getAbsolutePath());
			byte[] data = Files.readAllBytes(path);

if(data.length>=6)
			{
				offset = 0;
				version = getUInt16(data, offset); offset+=2;
				nextid = getUInt16(data, offset); offset+=2;
				count = getUInt16(data, offset); offset+=2;
				for(int i=0; i<count; i++)
				{
if (offset + 2 > data.length) throw new IOException("Truncated RMS record length");
					reclen = getUInt16(data, offset);
					offset+=2;
if (reclen > data.length - offset) throw new IOException("Truncated RMS record");
					loadRecord(data, offset, reclen, i + 1);
					offset+=reclen;
				}
if (offset < data.length) {
int bitmapLength = (count + 7) / 8;
if (data.length - offset != EMPTY_RECORD_MARKER.length + bitmapLength)
throw new IOException("Invalid RMS empty-record metadata");
for (byte marker : EMPTY_RECORD_MARKER)
if (data[offset++] != marker) throw new IOException("Unknown RMS metadata");
for (int i = 1; i <= count; i++)
if ((data[offset + (i - 1) / 8] & (1 << ((i - 1) % 8))) != 0) {
if (records.get(i) != null) throw new IOException("Invalid RMS empty-record flag");
records.set(i, new byte[0]);
}
}
			}
		}
		catch (Exception e)
		{
			System.out.println("Problem Reading Record Store: "+rmsFile);
			System.out.println(e.getMessage());
			throw(new RecordStoreException("Problem Reading Record Store: "+rmsFile));
		}

		if(isCityBloxxCityStore())
		{
			String firstRecord = records.size() <= 1 ? "absent"
				: records.get(1) == null ? "deleted"
				: "bytes=" + records.get(1).length;
			logCityBloxxCityStore("loaded fileBytes=" + file.length()
				+ " slots=" + (records.size() - 1)
				+ " live=" + getNumRecords()
				+ " record1=" + firstRecord);
		}
	}

	private boolean isCityBloxxCityStore()
	{
		return "City Bloxx".equals(appname) && "cityModeRS".equals(name);
	}

	private void logCityBloxxCityStore(String message)
	{
		if(isCityBloxxCityStore())
		{
			System.out.println("[CityBloxx-RMS] " + message);
		}
	}

private void save() throws RecordStoreException
	{
		byte[] temp = new byte[2];
Path pending = null;
		try
		{
if (records.size() - 1 > 65535 || nextid > 65535)
throw new IOException("RMS legacy record ID limit exceeded");
pending = Files.createTempFile(file.getParentFile().toPath(), ".rms-", ".tmp");
try (BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(pending.toFile()))) {

			// version //
			setUInt16(temp, 0, version);
			fout.write(temp);
			// next record id //
			setUInt16(temp, 0, nextid);
			fout.write(temp);
			// record count //
			setUInt16(temp, 0, records.size()-1);
			fout.write(temp);

			// records //
byte[] liveEmpty = new byte[(records.size() - 1 + 7) / 8];
			for(int i=1; i<records.size(); i++)
			{
byte[] record = records.get(i);
byte[] storedRecord = record;
if (record != null && record.length > 65535)
	storedRecord = writeLargeRecordBlob(i, record);
int length = storedRecord == null ? 0 : storedRecord.length;
if (length > 65535) throw new IOException("RMS legacy record size limit exceeded");
if (record != null && length == 0) liveEmpty[(i - 1) / 8] |= 1 << ((i - 1) % 8);
setUInt16(temp, 0, length);
				fout.write(temp);
if (storedRecord != null) fout.write(storedRecord);
			}
fout.write(EMPTY_RECORD_MARKER);
fout.write(liveEmpty);
}
try {
Files.move(pending, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
} catch (AtomicMoveNotSupportedException unsupported) {
Files.move(pending, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
}
		}
		catch (Exception e)
		{
throw new RecordStoreException("Problem Saving RecordStore " + name + ": " + e);
}
finally {
if (pending != null) try { Files.deleteIfExists(pending); }
catch (IOException cleanup) { System.out.println("Cannot remove temporary RMS file: " + cleanup); }
		}
	}

	private void loadRecord(byte[] data, int offset, int numBytes, int recordId) throws IOException
	{
		byte[] rec = Arrays.copyOfRange(data, offset, offset+numBytes);
if(numBytes == 0) rec = null; // Legacy zero-length slots were tombstones.
		else rec = restoreLargeRecordBlob(recordId, rec);
		records.addElement(rec);
	}

	private byte[] writeLargeRecordBlob(int recordId, byte[] record) throws IOException
	{
		byte[] digest = largeRecordDigest(record);
		File blobDirectory = new File(file.getParentFile(), file.getName() + ".rmsx");
		Files.createDirectories(blobDirectory.toPath());
		File blob = new File(blobDirectory, recordId + "-" + largeRecordHex(digest));
		if (!largeRecordBlobMatches(blob, record.length, digest))
		{
			Path pending = Files.createTempFile(blobDirectory.toPath(), ".rms-", ".tmp");
			try
			{
				Files.write(pending, record);
				try
				{
					Files.move(pending, blob.toPath(), StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
				}
				catch (AtomicMoveNotSupportedException unsupported)
				{
					Files.move(pending, blob.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
			finally
			{
				Files.deleteIfExists(pending);
			}
		}

		byte[] reference = new byte[LARGE_RECORD_REFERENCE_SIZE];
		System.arraycopy(LARGE_RECORD_MARKER, 0, reference, 0, LARGE_RECORD_MARKER.length);
		int length = record.length;
		reference[8] = (byte)(length >>> 24);
		reference[9] = (byte)(length >>> 16);
		reference[10] = (byte)(length >>> 8);
		reference[11] = (byte)length;
		System.arraycopy(digest, 0, reference, 12, digest.length);
		return reference;
	}

	private byte[] restoreLargeRecordBlob(int recordId, byte[] record) throws IOException
	{
		if (record.length != LARGE_RECORD_REFERENCE_SIZE) return record;
		for (int i = 0; i < LARGE_RECORD_MARKER.length; i++)
			if (record[i] != LARGE_RECORD_MARKER[i]) return record;

		int length = ((record[8] & 255) << 24) | ((record[9] & 255) << 16)
				| ((record[10] & 255) << 8) | (record[11] & 255);
		if (length <= 65535) throw new IOException("Invalid extended RMS record length");
		byte[] digest = Arrays.copyOfRange(record, 12, LARGE_RECORD_REFERENCE_SIZE);
		File blobDirectory = new File(file.getParentFile(), file.getName() + ".rmsx");
		File blob = new File(blobDirectory, recordId + "-" + largeRecordHex(digest));
		if (!largeRecordBlobMatches(blob, length, digest))
			throw new IOException("Missing or invalid extended RMS record data");
		return Files.readAllBytes(blob.toPath());
	}

	private boolean largeRecordBlobMatches(File blob, int expectedLength, byte[] expectedDigest)
			throws IOException
	{
		if (!blob.isFile() || blob.length() != expectedLength) return false;
		byte[] contents = Files.readAllBytes(blob.toPath());
		return contents.length == expectedLength
				&& MessageDigest.isEqual(largeRecordDigest(contents), expectedDigest);
	}

	private static byte[] largeRecordDigest(byte[] data) throws IOException
	{
		try { return MessageDigest.getInstance("SHA-256").digest(data); }
		catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 unavailable", impossible); }
	}

	private static String largeRecordHex(byte[] bytes)
	{
		char[] digits = "0123456789abcdef".toCharArray();
		char[] result = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++)
		{
			int value = bytes[i] & 255;
			result[i * 2] = digits[value >>> 4];
			result[i * 2 + 1] = digits[value & 15];
		}
		return new String(result);
	}

	private int getUInt16(byte[] data, int offset)
	{
		int out = 0;

		out |= (((int)data[offset])   & 0xFF) << 8;
		out |= (((int)data[offset+1]) & 0xFF);

		return out | 0x00000000;
	}
	private void setUInt16(byte[] data, int offset, int val)
	{
		data[offset]   = (byte)((val>>8) & 0xFF);
		data[offset+1] = (byte)((val) & 0xFF);
	}

private byte[] recordData(byte[] data, int offset, int numBytes) {
if (offset < 0 || numBytes < 0 || (data == null ? numBytes != 0 : offset > data.length - numBytes))
throw new ArrayIndexOutOfBoundsException("Invalid RMS data range");
return numBytes == 0 ? new byte[0] : Arrays.copyOfRange(data, offset, offset + numBytes);
}

private byte[] liveRecord(int id) throws InvalidRecordIDException {
if (id <= 0 || id >= records.size() || records.get(id) == null)
throw new InvalidRecordIDException("Invalid Record ID: " + id);
return records.get(id);
}

public synchronized int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreException
	{
		//System.out.println("> Add Record "+nextid+ " to "+name);
		try
		{
byte[] rec = recordData(data, offset, numBytes);
			records.addElement(rec);

			lastModified = nextid;
			nextid++;
			version++;

try { save(); }
catch (RecordStoreException failure) {
records.remove(records.size() - 1); nextid--; version--;
throw failure;
}

			for(int i=0; i<listeners.size(); i++)
			{
				listeners.get(i).recordAdded(this, lastModified);
			}
			return lastModified;
		}
		catch (Exception e)
		{
			//System.out.println("> Add Record Failed");
			throw(new RecordStoreException("Can't Add RMS Record"));
		}
	}

	public void addRecordListener(RecordListener listener)
	{
		listeners.add(listener);
	}

	public void closeRecordStore() { }

public synchronized void deleteRecord(int recordId) throws RecordStoreException, InvalidRecordIDException
	{
byte[] previous = liveRecord(recordId);
		version++;
		//System.out.println("> Delete Record");
records.set(recordId, null);
try { save(); }
catch (RecordStoreException failure) { records.set(recordId, previous); version--; throw failure; }
		for(int i=0; i<listeners.size(); i++)
		{
			listeners.get(i).recordDeleted(this, recordId);
		}
	}

	public static void deleteRecordStore(String recordStoreName)
	{
		try
		{
			File fstore = new File(Mobile.getPlatform().dataPath + "./rms/"+Mobile.getPlatform().loader.suitename+"/"+recordStoreName);
			fstore.delete();
			deleteLargeRecordBlobDirectory(fstore);
		}
		catch (Exception e)
		{
			System.out.println("Problem deleting RecordStore "+recordStoreName);
			e.printStackTrace();
		}
		System.gc();
	}

	private static void deleteLargeRecordBlobDirectory(File storeFile)
	{
		File blobDirectory = new File(storeFile.getParentFile(), storeFile.getName() + ".rmsx");
		if (!blobDirectory.exists()) return;
		try (java.util.stream.Stream<Path> paths = Files.walk(blobDirectory.toPath()))
		{
			Path[] entries = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
			for (Path entry : entries)
			{
				try { Files.deleteIfExists(entry); }
				catch (IOException failure) { System.out.println("Problem deleting extended RMS data: " + failure); }
			}
		}
		catch (IOException failure)
		{
			System.out.println("Problem deleting extended RMS data: " + failure);
		}
	}

	public RecordEnumeration enumerateRecords(RecordFilter filter, RecordComparator comparator, boolean keepUpdated)
	{
		System.out.println("RecordStore.enumerateRecords");
		return new enumeration(filter, comparator, keepUpdated);
	}

	public long getLastModified() { return lastModified; }

	public String getName()
	{
		return name;
	}

	public int getNextRecordID()
	{
		//System.out.println("> getNextRecordID");
		return nextid;
	}

	public int getNumRecords()
	{
		//System.out.println("> getNumRecords");
		int count = 0;
for(int i=1; i<records.size(); i++) // count live records, including empty ones
		{
if(records.get(i) != null) { count++; }
		}
		return count;
	}

	public byte[] getRecord(int recordId) throws InvalidRecordIDException, RecordStoreException
	{
		//System.out.println("> getRecord("+recordId+")");
		boolean traceCityBloxxRecord = isCityBloxxCityStore() && recordId == 1;
		if(isCityBloxxCityStore())
		{
			String state = recordId <= 0 || recordId >= records.size() ? "invalid"
				: records.get(recordId) == null ? "deleted"
				: "bytes=" + records.get(recordId).length;
			logCityBloxxCityStore("getRecord id=" + recordId + " " + state);
		}
byte[] record = liveRecord(recordId);
		try
		{
byte[] t = record;
			if(t.length==0)
			{
				if(traceCityBloxxRecord) logCityBloxxCityStore("getRecord returned empty record");
				if(traceCityBloxxRecord) scheduleCityBloxxReadTrace();
				return null;
			}
			byte[] copy = Arrays.copyOf(t, t.length);
			if(traceCityBloxxRecord)
			{
				logCityBloxxCityStore("getRecord returned bytes=" + copy.length
					+ " on thread=" + Thread.currentThread().getName()
					+ " state=" + Thread.currentThread().getState());
				scheduleCityBloxxReadTrace();
			}
			return copy;
		}
		catch (Exception e)
		{
			System.out.println("(getRecord) Record Store Exception: "+recordId);
			e.printStackTrace();
			throw(new RecordStoreException());
		}
	}

private void scheduleCityBloxxReadTrace()
{
	if(!cityBloxxReadTraceStarted.compareAndSet(false, true)) return;
	Thread probe = new Thread(() -> {
		long[] delays = {10L, 100L, 500L, 1500L};
		long previousDelay = 0;
		for(long delay : delays)
		{
			try { Thread.sleep(delay - previousDelay); }
			catch(InterruptedException interrupted) { return; }
			previousDelay = delay;
			int count = 0;
			for(Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet())
			{
				Thread thread = entry.getKey();
				if(thread == Thread.currentThread()) continue;
				count++;
				StackTraceElement[] stack = entry.getValue();
				logCityBloxxCityStore("resume-trace +" + delay + "ms thread="
					+ thread.getName() + " state=" + thread.getState()
					+ " daemon=" + thread.isDaemon() + " stackFrames=" + stack.length);
				for(int i=0; i<Math.min(stack.length, 8); i++)
					logCityBloxxCityStore("  at " + stack[i]);
			}
			logCityBloxxCityStore("resume-trace +" + delay + "ms liveThreads=" + count);
		}
	}, "CityBloxx-resume-trace");
	probe.setDaemon(true);
	probe.start();
}

	public int getRecord(int recordId, byte[] buffer, int offset) throws InvalidRecordIDException, RecordStoreException
	{
		//System.out.println("> getRecord(id, buffer, offset)");
		byte[] temp = getRecord(recordId);

		if(temp == null)
		{
			return 0;
		}

		int len = temp.length;

		while (offset+len > buffer.length) { len--; }

		for(int i=0; i<len; i++)
		{
			buffer[offset+i] = temp[i];
		}
		return len;
	}

	public int getRecordSize(int recordId) throws InvalidRecordIDException, RecordStoreException
	{
		//System.out.println("> Get Record Size");
return liveRecord(recordId).length;
	}

	public int getSize() { return 32767; }

	public int getSizeAvailable() { return 65536; }

	public int getVersion() { return version; }

	public static String[] listRecordStores()
	{
		//System.out.println("List Record Stores");
		if(rmsPath==null)
		{
			rmsPath = Mobile.getPlatform().dataPath + "./rms/"+Mobile.getPlatform().loader.name;
			try
			{
				Files.createDirectories(Paths.get(rmsPath));
			}
			catch (Exception e) { }
		}
		try
		{
			File folder = new File(rmsPath);
			File[] files = folder.listFiles();

			String[] out = new String[files.length];

			for(int i=0; i<files.length; i++)
			{
				//System.out.println((files[i].toString()).substring(rmsPath.length()+1));
				out[i] = (files[i].toString()).substring(rmsPath.length()+1);
			}

			return out;
		}
		catch (Exception e) { }
		return null;
	}

	public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary) throws RecordStoreException, RecordStoreNotFoundException
	{
		//System.out.println("Open Record Store A "+createIfNecessary);
		return new RecordStore(recordStoreName, createIfNecessary);
	}

	public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary, int authmode, boolean writable) throws RecordStoreException, RecordStoreNotFoundException
	{
		//System.out.println("Open Record Store B "+createIfNecessary);
		return new RecordStore(recordStoreName, createIfNecessary);
	}

	public static RecordStore openRecordStore(String recordStoreName, String vendorName, String suiteName) throws RecordStoreException, RecordStoreNotFoundException
	{
		System.out.println("Open Record Store C");
		return new RecordStore(recordStoreName, false);
	}

	public void removeRecordListener(RecordListener listener)
	{
		listeners.remove(listener);
	}

	public void setMode(int authmode, boolean writable) {  }

public synchronized void setRecord(int recordId, byte[] newData, int offset, int numBytes) throws RecordStoreException, InvalidRecordIDException
	{
		//System.out.println("> Set Record "+recordId+" in "+name);
byte[] previous = liveRecord(recordId);
byte[] rec = recordData(newData, offset, numBytes);
records.set(recordId, rec);
version++;
		lastModified = recordId;
try { save(); }
catch (RecordStoreException failure) { records.set(recordId, previous); version--; throw failure; }
		for(int i=0; i<listeners.size(); i++)
		{
			listeners.get(i).recordChanged(this, recordId);
		}
	}


	/* ************************************************************
				RecordEnumeration implementation
	    *********************************************************** */

	private class enumeration implements RecordEnumeration
	{
		private int index;
		private int[] elements;
		private int count;
		private boolean keepupdated;
		RecordFilter filter;
		RecordComparator comparator;

		public enumeration(RecordFilter filter, RecordComparator comparator, boolean keepUpdated)
		{
			keepupdated = keepUpdated;
			index = 0;
			count = 0;

			this.filter = filter;
			this.comparator = comparator;

			filter = filter;

			build();
		}

		private void build()
		{
			elements = new int[records.size()+1];
			for(int i=0; i<records.size()+1; i++) { elements[i] = 1; }
			count = 0;
			if(filter==null)
			{
				//System.out.println("Not Filtered");
				for(int i=1; i<records.size(); i++)
				{
if(records.get(i) != null) // not deleted; zero bytes is a valid record
					{
						elements[count] = i;
						count++;
					}
				}
			}
			else
			{
				//System.out.println("Filtered");
				for(int i=1; i<records.size(); i++)
				{
if(records.get(i) != null && filter.matches(records.get(i)))
					{
if(records.get(i) != null) // not deleted
						{
							elements[count] = i;
							count++;
						}
					}
				}
			}

			int result = 0;
			int temp;
			if(comparator!=null)
			{
				//System.out.println("Comparator");
				for(int i=0; i<count-1; i++)
				{
					for(int j=0; j<count-(1+i); j++)
					{
						result = comparator.compare(records.get(elements[j]), records.get(elements[j+1]));
						if(result==RecordComparator.FOLLOWS)
						{
							temp = elements[j];
							elements[j] = elements[j+1];
							elements[j+1] = temp;
						}

					}
				}
			}
		}

		public void destroy() { }

		public boolean hasNextElement()
		{
			if(keepupdated) { rebuild(); }
			if (index<count)
			{
				return true;
			}
			return false;
		}

		public boolean hasPreviousElement()
		{
			if(keepupdated) { rebuild(); }
			if(index>0)
			{
				return true;
			}
			return false;
		}

		public boolean isKeptUpdated() { return keepupdated; }

		public void keepUpdated(boolean keepUpdated) { keepupdated = keepUpdated; }

		public byte[] nextRecord() throws InvalidRecordIDException
		{
			//System.out.println("> Next Record");
			if(keepupdated) { rebuild(); }
			if(index>=count)
			{
				throw(new InvalidRecordIDException());
			}
			index++;
return enumerationRecord(elements[index-1]);
		}

		public int nextRecordId() throws InvalidRecordIDException
		{
			//System.out.println("> Next Record ID (idx:"+index+" cnt:"+count+")");
			if(keepupdated) { rebuild(); }
			if(index>=count) { throw(new InvalidRecordIDException()); }
return elements[index++];
		}

		public int numRecords()
		{
			//System.out.println("> numRecords()");
			if(keepupdated) { rebuild(); }
			return count;
		}

		public byte[] previousRecord() throws InvalidRecordIDException
		{
			//System.out.println("> Previous Record");
			if(keepupdated) { rebuild(); }
if(index<=0) { throw(new InvalidRecordIDException()); }
return enumerationRecord(elements[--index]);
		}

		public int previousRecordId() throws InvalidRecordIDException
		{
			//System.out.println("> Previous Record ID");
			if(keepupdated) { rebuild(); }
			if(index==0) { throw(new InvalidRecordIDException()); }
return elements[--index];
		}

private byte[] enumerationRecord(int id) throws InvalidRecordIDException {
byte[] record = liveRecord(id);
return record.length == 0 ? null : Arrays.copyOf(record, record.length);
}

		public void rebuild()
		{
			build();
if(index > count) { index = count; }
			if(index < 0) { index = 0; }
		}

		public void reset()
		{
			if(keepupdated) { rebuild(); }
			index = 0;
		}
	}
}
