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
package javax.microedition.lcdui;



public class TextField extends Item
{
	public static final int ANY = 0;
	public static final int CONSTRAINT_MASK = 0xFFFF;
	public static final int DECIMAL = 5;
	public static final int EMAILADDR = 1;
	public static final int INITIAL_CAPS_SENTENCE = 0x200000;
	public static final int INITIAL_CAPS_WORD = 0x100000;
	public static final int NON_PREDICTIVE = 0x80000;
	public static final int NUMERIC = 2;
	public static final int PASSWORD = 0x10000;
	public static final int PHONENUMBER = 3;
	public static final int SENSITIVE = 0x40000;
	public static final int UNEDITABLE = 0x20000;
	public static final int URL = 4;


	private String text;
	private int max;
	private int constraints;
	private String mode;
private int caretPosition;

	public TextField(String label, String value, int maxSize, int Constraints)
	{
		setLabel(label);
if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
max = maxSize;
text = limit(value == null ? "" : value, max);
		constraints = Constraints;
caretPosition = text.length();
	}

private static String limit(String value, int maximum)
	{
if (value.length() > maximum) throw new IllegalArgumentException("Text exceeds maxSize");
return value;
	}

public void delete(int offset, int length)
{
if (offset < 0 || length < 0 || offset + length > text.length())
throw new StringIndexOutOfBoundsException();
text = text.substring(0, offset) + text.substring(offset + length);
caretPosition = offset;
invalidate();
}

public int getCaretPosition() { return caretPosition; }

	public int getChars(char[] data)
	{
if (data == null || data.length < text.length()) throw new ArrayIndexOutOfBoundsException();
text.getChars(0, text.length(), data, 0);
		return text.length();
	}

	public int getConstraints() { return constraints; }

	public int getMaxSize() { return max; }

	public String getString() { return text; }

public void insert(char[] data, int offset, int length, int position)
	{
if (data == null || offset < 0 || length < 0 || offset + length > data.length
        || position < 0 || position > text.length()) throw new StringIndexOutOfBoundsException();
insert(new String(data, offset, length), position);
	}

	public void insert(String src, int position)
	{
if (src == null || position < 0 || position > text.length()) throw new StringIndexOutOfBoundsException();
int available = Math.max(0, max - text.length());
String inserted = limit(src, available);
text = text.substring(0, position) + inserted + text.substring(position);
caretPosition = position + inserted.length();
invalidate();
	}

	public void setChars(char[] data, int offset, int length)
	{
if (data == null) { setString(""); return; }
if (data == null || offset < 0 || length < 0 || offset + length > data.length)
throw new StringIndexOutOfBoundsException();
setString(new String(data, offset, length));
	}

public void setConstraints(int Constraints) { constraints = Constraints; invalidate(); }

	public void setInitialInputMode(String characterSubset) { mode = characterSubset; }

public int setMaxSize(int maxSize)
{
if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
max = maxSize;
if (text.length() > max) text = text.substring(0, max);
caretPosition = Math.min(caretPosition, text.length());
invalidate();
return max;
}

public void setString(String value)
{
text = limit(value == null ? "" : value, max);
caretPosition = text.length();
invalidate();
}

public int size() { return text.length(); }

void nativeText(String value, int caret)
{
setString(value);
caretPosition = Math.max(0, Math.min(caret, text.length()));
}

}
