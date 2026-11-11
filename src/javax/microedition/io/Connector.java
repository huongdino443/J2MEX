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
package javax.microedition.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.recompile.mobile.Mobile;
import org.recompile.mobile.MemoryEditorGuard;
import org.recompile.mobile.SmsConnectionImpl;
import javax.microedition.io.file.FileConnectionImpl;
import javax.bluetooth.BluetoothConnector;

public class Connector
{

	public static final int READ = 1;
	public static final int READ_WRITE = 3;
	public static final int WRITE = 2;

	
public static InputStream openInputStream(String name) throws IOException
	{
if(name == null) { throw new IllegalArgumentException("Connection URL must not be null"); }
		//System.out.println("Connector: " + name);
		if(name.startsWith("resource:")) // older Siemens phones?
		{
			return Mobile.getPlatform().loader.getMIDletResourceAsSiemensStream(name.substring(9));
		}
		else
		{
			//return Mobile.getPlatform().loader.getMIDletResourceAsStream(name); // possible
final Connection connection = open(name, READ);
try {
final InputStream input = ((InputConnection)connection).openInputStream();
return new java.io.FilterInputStream(input) {
    public void close() throws IOException {
        try { super.close(); } finally { connection.close(); }
    }
};
} catch (IOException | RuntimeException error) {
try { connection.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
throw error;
}
		}
	}


public static DataInputStream openDataInputStream(String name) throws IOException
	{
return new DataInputStream(openInputStream(name));
}

public static OutputStream openOutputStream(String name) throws IOException
{
final Connection connection = open(name, WRITE);
try {
final OutputStream output = ((OutputConnection)connection).openOutputStream();
return new java.io.FilterOutputStream(output) {
    private boolean closed;
    public void write(byte[] bytes, int offset, int length) throws IOException {
        out.write(bytes, offset, length);
    }
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { super.close(); } finally { connection.close(); }
    }
};
} catch (IOException | RuntimeException error) {
try { connection.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
throw error;
}
}

public static DataOutputStream openDataOutputStream(String name) throws IOException
{
return new DataOutputStream(openOutputStream(name));
	}

public static Connection open(String name) throws IOException
{
return open(name, READ_WRITE, true);
}

public static Connection open(String name, int mode) throws IOException
{
return open(name, mode, true);
}

public static Connection open(String name, int mode, boolean timeouts) throws IOException
{
if(name == null) { throw new IllegalArgumentException("Connection URL must not be null"); }
if(mode != READ && mode != WRITE && mode != READ_WRITE)
{
throw new IllegalArgumentException("Invalid connection mode: " + mode);
}
MemoryEditorGuard.checkConnector(name);

if(name.regionMatches(true, 0, "btspp://", 0, 8)
        || name.regionMatches(true, 0, "btl2cap://", 0, 10))
{ return BluetoothConnector.open(name, mode); }

if(name.regionMatches(true, 0, "sms:", 0, 4))
{
if(!name.regionMatches(true, 4, "//", 0, 2))
    throw new ConnectionNotFoundException("Invalid SMS URL: " + name);
return new SmsConnectionImpl(name);
}

if(name.regionMatches(true, 0, "socket:", 0, 7))
{ return new SocketConnectionImpl(name, mode, timeouts); }

if(name.regionMatches(true, 0, "ssl:", 0, 4))
{ return new SecureConnectionImpl(name, mode, timeouts); }

if(name.regionMatches(true, 0, "file:", 0, 5))
{ return new FileConnectionImpl(name, mode); }

URL url;
try { url = new URL(name); }
catch(MalformedURLException e) { throw new ConnectionNotFoundException("Invalid connection URL: " + name); }

String protocol = url.getProtocol().toLowerCase();
if(protocol.equals("http")) { return new HttpConnectionImpl(url, mode, timeouts); }
if(protocol.equals("https")) { return new HttpsConnectionImpl(url, mode, timeouts); }
throw new ConnectionNotFoundException("Unsupported connection protocol: " + protocol);
}


}
