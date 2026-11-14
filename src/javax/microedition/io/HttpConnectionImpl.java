/*
This file is part of FreeJ2ME.

FreeJ2ME is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
*/
package javax.microedition.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

class HttpConnectionImpl implements HttpConnection
{
private static final int CONNECT_TIMEOUT_MS = 15000;
private static final int READ_TIMEOUT_MS = 30000;
private static final String MIDP_USER_AGENT = "Profile/MIDP-2.0 Configuration/CLDC-1.1";

protected final URL url;
protected final int mode;
protected final HttpURLConnection connection;
private final Map<String, String> requestProperties = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
private String requestMethod = GET;
private InputStream input;
private OutputStream output;
private boolean closed;

HttpConnectionImpl(URL url, int mode, boolean timeouts) throws IOException
{
this.url = url;
this.mode = mode;
connection = (HttpURLConnection)url.openConnection();
connection.setUseCaches(false);
connection.setInstanceFollowRedirects(true);
connection.setRequestProperty("User-Agent", MIDP_USER_AGENT);
requestProperties.put("User-Agent", MIDP_USER_AGENT);
if(timeouts)
{
connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
connection.setReadTimeout(READ_TIMEOUT_MS);
}
connection.setRequestMethod(requestMethod);
}

private void ensureOpen() throws IOException
{
if(closed) { throw new IOException("Connection is closed"); }
}

public void close() throws IOException
{
if(closed) { return; }
closed = true;
IOException failure = null;
try { if(input != null) { input.close(); } } catch(IOException error) { failure = error; }
try
{
if(output != null) { output.close(); }
}
catch(IOException error)
{
if(failure == null) { failure = error; } else { failure.addSuppressed(error); }
}
connection.disconnect();
if(failure != null) { throw failure; }
}

public InputStream openInputStream() throws IOException
{
ensureOpen();
if((mode & Connector.READ) == 0) { throw new IOException("Connection is not open for reading"); }
if(input == null) { input = connection.getInputStream(); }
return input;
}

public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }

public OutputStream openOutputStream() throws IOException
{
ensureOpen();
if((mode & Connector.WRITE) == 0) { throw new IOException("Connection is not open for writing"); }
if(output == null)
{
if(GET.equals(requestMethod)) { setRequestMethod(POST); }
connection.setDoOutput(true);
output = connection.getOutputStream();
}
return output;
}

public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }

public String getEncoding() { return headerValue("Content-Encoding"); }
public long getLength() { return connection.getContentLengthLong(); }
public String getType() { return connection.getContentType(); }
public long getDate() throws IOException { ensureOpen(); return connection.getDate(); }
public long getExpiration() throws IOException { ensureOpen(); return connection.getExpiration(); }
public String getFile() { return url.getFile(); }
public String getHeaderField(int n) throws IOException { ensureOpen(); return connection.getHeaderField(n); }
public String getHeaderField(String name) throws IOException { ensureOpen(); return headerValue(name); }
public long getHeaderFieldDate(String name, long def) throws IOException { ensureOpen(); return connection.getHeaderFieldDate(name, def); }
public int getHeaderFieldInt(String name, int def) throws IOException { ensureOpen(); return connection.getHeaderFieldInt(name, def); }
public String getHeaderFieldKey(int n) throws IOException { ensureOpen(); return connection.getHeaderFieldKey(n); }
public String getHost() { return url.getHost(); }
public long getLastModified() throws IOException { ensureOpen(); return connection.getLastModified(); }
public int getPort() { return url.getPort() < 0 ? defaultPort() : url.getPort(); }
public String getProtocol() { return url.getProtocol(); }
public String getQuery() { return url.getQuery(); }
public String getRef() { return url.getRef(); }
public String getRequestMethod() { return requestMethod; }
public String getRequestProperty(String key)
{
String value = requestProperties.get(key);
return value == null ? connection.getRequestProperty(key) : value;
}
public int getResponseCode() throws IOException { ensureOpen(); return connection.getResponseCode(); }
public String getResponseMessage() throws IOException { ensureOpen(); return connection.getResponseMessage(); }
public String getURL() { return url.toExternalForm(); }

public void setRequestMethod(String method) throws IOException
{
if(closed) { throw new IllegalStateException("Connection is closed"); }
try
{
connection.setRequestMethod(method);
requestMethod = method;
}
catch(ProtocolException error) { throw new IOException("Unsupported HTTP method: " + method, error); }
}

public void setRequestProperty(String key, String value) throws IOException
{
if(closed) { throw new IllegalStateException("Connection is closed"); }
try
{
connection.setRequestProperty(key, value);
requestProperties.put(key, value);
}
catch(IllegalStateException error) { throw error; }
}

protected int defaultPort() { return 80; }
protected String headerValue(String name) { return connection.getHeaderField(name); }
}
