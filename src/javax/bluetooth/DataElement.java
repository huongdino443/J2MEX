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

import java.util.Vector;

public class DataElement
{
		   
	public static final int NULL		= 0;
	public static final int INT_1	= 0x10;
	public static final int INT_2	= 0x11;
	public static final int INT_4	= 0x12;
	public static final int INT_8	= 0x13;
	public static final int INT_16	= 0x14;
	public static final int U_INT_1	= 0x08;
	public static final int U_INT_2	= 0x09;
	public static final int U_INT_4	= 0x0A;
	public static final int U_INT_8	= 0x0B;
	public static final int U_INT_16 = 0x0C;
	public static final int UUID		= 0x18;
	public static final int STRING	= 0x20;
	public static final int BOOL    	= 0x28;
	public static final int DATSEQ	= 0x30;
	public static final int DATALT	= 0x38;
	public static final int URL		= 0x40;


	private boolean boolvalue = false;

	private int intvalue = 0;

	private long longvalue = 0L;

	private Object objvalue = null;
	private final Vector<DataElement> elements = new Vector<DataElement>();

	public DataElement(boolean bool) { boolvalue = bool; intvalue = BOOL; }

	public DataElement(int valueType) { intvalue = valueType; }
	
	public DataElement(int valueType, long value) { intvalue = valueType; longvalue = value; }
	
	public DataElement(int valueType, Object value) { intvalue = valueType; objvalue = value; }


	public void addElement(DataElement elem) {
		if (elem == null) throw new NullPointerException("element");
		if (intvalue != DATSEQ && intvalue != DATALT) throw new IllegalStateException("Not a sequence");
		elements.addElement(elem);
	}

	public boolean getBoolean() { return boolvalue; }
	
	public int getDataType() { return intvalue; }
	
	public long getLong() { return longvalue; }
	
	public int getSize() { return elements.size(); }
	
	public Object getValue() {
		return intvalue == DATSEQ || intvalue == DATALT ? elements.elements() : objvalue;
	}
	
	public void insertElementAt(DataElement elem, int index) {
		if (elem == null) throw new NullPointerException("element");
		if (intvalue != DATSEQ && intvalue != DATALT) throw new IllegalStateException("Not a sequence");
		elements.insertElementAt(elem, index);
	}
	
	public boolean removeElement(DataElement elem) {
		if (intvalue != DATSEQ && intvalue != DATALT) throw new IllegalStateException("Not a sequence");
		return elements.removeElement(elem);
	}
	
	public String toString() {
		if (intvalue == BOOL) return Boolean.toString(boolvalue);
		if (intvalue == DATSEQ || intvalue == DATALT) return elements.toString();
		if (objvalue != null) return objvalue.toString();
		return Long.toString(longvalue);
	}
}