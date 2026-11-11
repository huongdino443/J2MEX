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

import java.util.ArrayList;



public class ChoiceGroup extends Item implements Choice
{

private final int type;

	private ArrayList<String> strings = new ArrayList<String>();

	private ArrayList<Image> images = new ArrayList<Image>();

private ArrayList<Boolean> selected = new ArrayList<Boolean>();

private ArrayList<Font> fonts = new ArrayList<Font>();

	private int fitPolicy;

private int selectedIndex = -1;
private int focusedIndex;

	public ChoiceGroup(String choiceLabel, int choiceType)
	{
setLabel(choiceLabel);
		type = choiceType;
validateType(choiceType);
	}

	public ChoiceGroup(String choiceLabel, int choiceType, String[] stringElements, Image[] imageElements)
	{
setLabel(choiceLabel);
		type = choiceType;
validateType(choiceType);
		for(int i=0; i<stringElements.length; i++)
		{
append(stringElements[i], imageElements == null ? null : imageElements[i]);
		}
	}

	ChoiceGroup(String choiceLabel, int choiceType, boolean validateChoiceType)
	{
setLabel(choiceLabel);
		type = choiceType;
validateType(choiceType);
	}

	ChoiceGroup(String choiceLabel, int choiceType, String[] stringElements, Image[] imageElements, boolean validateChoiceType)
	{
setLabel(choiceLabel);
		type = choiceType;
validateType(choiceType);
		for(int i=0; i<stringElements.length; i++)
		{
append(stringElements[i], imageElements == null ? null : imageElements[i]);
		}
	}

private static void validateType(int value)
{
if (value != Choice.EXCLUSIVE && value != Choice.MULTIPLE && value != Choice.POPUP)
throw new IllegalArgumentException("Invalid ChoiceGroup type");
}

public int append(String stringPart, Image imagePart)
{
strings.add(stringPart == null ? "" : stringPart);
images.add(imagePart);
selected.add(Boolean.FALSE);
fonts.add(Font.getDefaultFont());
if (selectedIndex < 0) {
selectedIndex = 0;
if (type != Choice.MULTIPLE) selected.set(0, Boolean.TRUE);
}
invalidate();
return strings.size() - 1;
}

public void delete(int itemNum)
{
strings.remove(itemNum); images.remove(itemNum); selected.remove(itemNum); fonts.remove(itemNum);
if (strings.isEmpty()) selectedIndex = -1;
else if (selectedIndex >= strings.size()) selectedIndex = strings.size() - 1;
else if (selectedIndex > itemNum) selectedIndex--;
if (type != Choice.MULTIPLE && selectedIndex >= 0) {
for (int i = 0; i < selected.size(); i++) selected.set(i, i == selectedIndex);
}
invalidate();
}

public void deleteAll() { strings.clear(); images.clear(); selected.clear(); fonts.clear(); selectedIndex = -1; invalidate(); }

	public int getFitPolicy() { return fitPolicy; }

public Font getFont(int itemNum) { return fonts.get(itemNum); }

public Image getImage(int elementNum) { return images.get(elementNum); }

public int getSelectedFlags(boolean[] selectedArray)
{
if (selectedArray == null || selectedArray.length < strings.size()) throw new IllegalArgumentException("selectedArray too small");
int count = 0;
for (int i = 0; i < selected.size(); i++) {
selectedArray[i] = selected.get(i);
if (selectedArray[i]) count++;
}
for (int i = selected.size(); i < selectedArray.length; i++) selectedArray[i] = false;
return count;
}

public int getSelectedIndex() { return type == Choice.MULTIPLE ? -1 : selectedIndex; }

	public String getString(int elementNum) { return strings.get(elementNum); }

public void insert(int elementNum, String stringPart, Image imagePart)
	{
		strings.add(elementNum, stringPart);
		images.add(elementNum, imagePart);
selected.add(elementNum, Boolean.FALSE);
fonts.add(elementNum, Font.getDefaultFont());
if (selectedIndex >= elementNum) selectedIndex++;
if (selectedIndex < 0) setSelectedIndex(elementNum, true);
invalidate();
	}

public boolean isSelected(int elementNum) { return selected.get(elementNum); }

  	public void set(int elementNum, String stringPart, Image imagePart)
	{
		strings.set(elementNum, stringPart);
		images.set(elementNum, imagePart);
invalidate();
	}

public void setFitPolicy(int policy) { fitPolicy = policy; invalidate(); }

public void setFont(int itemNum, Font font) { fonts.set(itemNum, font == null ? Font.getDefaultFont() : font); invalidate(); }

public void setSelectedFlags(boolean[] selectedArray)
{
if (selectedArray == null || selectedArray.length < strings.size()) throw new IllegalArgumentException("selectedArray too small");
if (type == Choice.MULTIPLE) {
for (int i = 0; i < strings.size(); i++) selected.set(i, selectedArray[i]);
} else {
int chosen = -1;
for (int i = 0; i < strings.size(); i++) if (selectedArray[i]) { chosen = i; break; }
setSelectedIndex(chosen < 0 && !strings.isEmpty() ? 0 : chosen, true);
}
invalidate();
}

public void setSelectedIndex(int elementNum, boolean isSelected)
{
if (elementNum < 0 || elementNum >= strings.size()) {
if (!isSelected && elementNum >= 0 && elementNum < selected.size()) selected.set(elementNum, Boolean.FALSE);
return;
}
if (type == Choice.MULTIPLE) {
selected.set(elementNum, isSelected);
selectedIndex = elementNum;
} else if (isSelected) {
selectedIndex = elementNum;
for (int i = 0; i < selected.size(); i++) selected.set(i, i == elementNum);
}
focusedIndex = elementNum;
invalidate();
}

public int size() { return strings.size(); }

int getType() { return type; }

int getFocusedIndex() { return Math.max(0, Math.min(focusedIndex, strings.size() - 1)); }

boolean moveFocus(int delta)
{
int next = getFocusedIndex() + delta;
if (next < 0 || next >= strings.size()) return false;
focusedIndex = next;
if (type != Choice.MULTIPLE) setSelectedIndex(next, true);
return true;
}

void activateFocused()
{
if (strings.isEmpty()) return;
int index = getFocusedIndex();
setSelectedIndex(index, type != Choice.MULTIPLE || !isSelected(index));
}

}
