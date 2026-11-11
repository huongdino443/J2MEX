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
package javax.bluetooth;

import javax.microedition.io.Connection;


public class RemoteDevice
{

	private String address = "";
	private String friendlyName = "";


	protected RemoteDevice(String address) { this.address = address; }

	RemoteDevice(String address, String friendlyName)
	{
		this.address = address == null ? "" : address.replace(":", "").toUpperCase();
		this.friendlyName = friendlyName == null ? "" : friendlyName;
	}


	public boolean authenticate() { return true; }

	public boolean authorize(Connection conn) { return true; }
	
	public boolean encrypt(Connection conn, boolean on) { return true; }
	
	public boolean equals(Object obj) {
		return obj instanceof RemoteDevice
				&& address.equals(((RemoteDevice)obj).address);
	}
	
	public String getBluetoothAddress() { return address; }
	
	public String getFriendlyName(boolean alwaysAsk) { return friendlyName; }
	
	public static RemoteDevice getRemoteDevice(Connection conn) { return new RemoteDevice(""); }
	
	public int hashCode() { return address.hashCode(); }
	
	public boolean isAuthenticated() { return true; }
	
	public boolean isAuthorized(Connection conn) { return true; }
	
	public boolean isEncrypted() { return false; }
	
	public boolean isTrustedDevice() { return true; }

}