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

public class Loader
{

public static Object3D[] load(byte[] data, int offset) throws java.io.IOException
{
if (data == null) throw new NullPointerException("data");
if (offset < 0 || offset >= data.length) throw new IndexOutOfBoundsException("offset");
return M3GLoader.decode(data, offset, "");
}

private static final ThreadLocal<java.util.Set<String>> loading =
new ThreadLocal<java.util.Set<String>>() {
protected java.util.Set<String> initialValue() { return new java.util.HashSet<String>(); }
};

public static Object3D[] load(java.lang.String name) throws java.io.IOException
{
if (name == null) throw new NullPointerException("name");
java.util.Set<String> stack = loading.get();
String key;
try { key = new java.net.URI(name).normalize().toString(); }
catch (java.net.URISyntaxException e) { throw new java.io.IOException("Invalid M3G resource URI", e); }
if (stack.size() >= 16 || !stack.add(key)) throw new java.io.IOException("Cyclic/deep M3G external reference: " + key);
try (java.io.InputStream input = open(key)) {
if (input == null) throw new java.io.IOException("M3G resource not found: " + key);
java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
byte[] buffer = new byte[8192];
int count;
while ((count = input.read(buffer)) != -1) {
if (output.size() > M3GInput.MAX_BYTES - count) throw new java.io.IOException("M3G resource too large");
output.write(buffer, 0, count);
}
return M3GLoader.decode(output.toByteArray(), 0, key);
} finally {
stack.remove(key);
if (stack.isEmpty()) loading.remove();
}
}

private static java.io.InputStream open(String name) throws java.io.IOException
{
if (name.startsWith("http://") || name.startsWith("https://")) return new java.net.URL(name).openStream();
if (name.indexOf(':') >= 0) throw new java.io.IOException("Unsupported M3G resource scheme");
org.recompile.mobile.MobilePlatform platform = org.recompile.mobile.Mobile.getPlatform();
if (platform == null || platform.loader == null) throw new java.io.IOException("No MIDlet resource loader");
return platform.loader.getMIDletResourceAsStream(name.startsWith("/") ? name : "/" + name);
}

}
