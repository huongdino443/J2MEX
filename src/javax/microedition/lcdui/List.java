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

import org.recompile.mobile.Mobile;
import org.recompile.mobile.PlatformImage;
import org.recompile.mobile.PlatformGraphics;

import java.util.ArrayList;

public class List extends Screen implements Choice
{

	public static final Command SELECT_COMMAND = new Command("Select", Command.SCREEN, 0);

	private int fitPolicy = Choice.TEXT_WRAP_ON;

	private int type;

	private Command selectCommand = SELECT_COMMAND;

	private final ArrayList<Boolean> selected = new ArrayList<Boolean>();

	private final ArrayList<Font> fonts = new ArrayList<Font>();
	private int firstVisible;

	public List(String title, int listType)
	{
		setTitle(title);
		type = listType;
		validateType(listType);

		render();
	}

	public List(String title, int listType, String[] stringElements, Image[] imageElements)
	{
		setTitle(title);
		type = listType;
		validateType(listType);

		if (stringElements != null) for(int i=0; i<stringElements.length; i++)
		{
			append(stringElements[i], imageElements != null && i < imageElements.length ? imageElements[i] : null);
		}

		render();
	}

	private static void validateType(int value)
	{
		if (value != Choice.EXCLUSIVE && value != Choice.MULTIPLE && value != Choice.IMPLICIT)
			throw new IllegalArgumentException("Invalid List type");
	}

	public int append(String stringPart, Image imagePart)
	{ 
		String value = stringPart == null ? "" : stringPart;
		if (imagePart != null) items.add(new ImageItem(value, imagePart, 0, value));
		else items.add(new StringItem(value, value));
		selected.add(Boolean.FALSE);
		fonts.add(Font.getDefaultFont());
		if (currentItem < 0) currentItem = 0;
		if (type != Choice.MULTIPLE && getSelectedIndex() < 0) setSelectedIndex(0, true);
		render();
		return items.size() - 1;
	}

	public void delete(int elementNum)
	{
		items.remove(elementNum);
		selected.remove(elementNum);
		fonts.remove(elementNum);
		if (items.isEmpty()) currentItem = -1;
		else if (currentItem >= items.size()) currentItem = items.size() - 1;
		else if (currentItem > elementNum) currentItem--;
		if (type != Choice.MULTIPLE && getSelectedIndex() < 0 && !items.isEmpty())
			setSelectedIndex(Math.min(elementNum, items.size() - 1), true);
		render();
	}

	public void deleteAll() { items.clear(); selected.clear(); fonts.clear(); currentItem = -1; render(); }

	public int getFitPolicy() { return fitPolicy; }

	public Font getFont(int elementNum) { return fonts.get(elementNum); }

	public Image getImage(int elementNum)
	{
		Item item = items.get(elementNum);
		return item instanceof ImageItem ? ((ImageItem)item).getImage() : null;
	}
	
	public int getSelectedFlags(boolean[] selectedArray_return)
	{
		if (selectedArray_return == null || selectedArray_return.length < items.size())
			throw new IllegalArgumentException("selectedArray too small");
		int count = 0;
		for (int i = 0; i < items.size(); i++)
		{
			selectedArray_return[i] = selected.get(i);
			if (selectedArray_return[i]) count++;
		}
		for (int i = items.size(); i < selectedArray_return.length; i++) selectedArray_return[i] = false;
		return count;
	}

	public int getSelectedIndex()
	{
		if (type == Choice.MULTIPLE) return -1;
		for (int i = 0; i < selected.size(); i++) if (selected.get(i)) return i;
		return -1;
	}

	public String getString(int elementNum)
	{
		Item item = items.get(elementNum);
		return item instanceof StringItem ? ((StringItem)item).getText() : item.getLabel();
	}

	public void insert(int elementNum, String stringPart, Image imagePart)
	{
		if (elementNum < 0 || elementNum > items.size()) throw new IndexOutOfBoundsException();
		String value = stringPart == null ? "" : stringPart;
		if (imagePart != null) items.add(elementNum, new ImageItem(value, imagePart, 0, value));
		else items.add(elementNum, new StringItem(value, value));
		selected.add(elementNum, Boolean.FALSE);
		fonts.add(elementNum, Font.getDefaultFont());
		if (currentItem >= elementNum) currentItem++;
		if (type != Choice.MULTIPLE && getSelectedIndex() < 0) setSelectedIndex(elementNum, true);
		render();
	}

	public boolean isSelected(int elementNum) { return selected.get(elementNum); }

	// public void removeCommand(Command cmd) {  }

	public void set(int elementNum, String stringPart, Image imagePart)
	{
		String value = stringPart == null ? "" : stringPart;
		if (imagePart != null) items.set(elementNum, new ImageItem(value, imagePart, 0, value));
		else items.set(elementNum, new StringItem(value, value));
		render();
	}
	
	public void setFitPolicy(int fitpolicy) { fitPolicy = fitpolicy; }

	public void setFont(int elementNum, Font font)
	{
		fonts.set(elementNum, font == null ? Font.getDefaultFont() : font);
		render();
	}
		
	public void setSelectCommand(Command command) { selectCommand = command; render(); }
 
	public void setSelectedFlags(boolean[] selectedArray)
	{
		if (selectedArray == null || selectedArray.length < items.size())
			throw new IllegalArgumentException("selectedArray too small");
		if (type == Choice.MULTIPLE)
		{
			for (int i = 0; i < items.size(); i++) selected.set(i, selectedArray[i]);
		}
		else
		{
			int chosen = -1;
			for (int i = 0; i < items.size(); i++) if (selectedArray[i]) { chosen = i; break; }
			if (chosen < 0 && !items.isEmpty()) chosen = 0;
			if (chosen >= 0) setSelectedIndex(chosen, true);
		}
		render();
	}

	public void setSelectedIndex(int elementNum, boolean selected)
	{
		if (elementNum < 0 || elementNum >= items.size()) return;
		if(type == Choice.MULTIPLE)
		{
			this.selected.set(elementNum, selected);
			currentItem = elementNum;
		}
		else if(selected)
		{
			for (int i = 0; i < this.selected.size(); i++) this.selected.set(i, i == elementNum);
			currentItem = elementNum;
		}
		render();
	}

	//void setTicker(Ticker ticker)
	
	//void setTitle(String s)

	public int size() { return items.size(); }

	/*
		Draw list, handle input
	*/

	public void keyPressed(int key)
	{
		if (handleCommandKey(key)) return;
		if(items.isEmpty()) { return; }
		switch(key)
		{
			case Mobile.KEY_NUM2:
			case Mobile.NOKIA_UP: currentItem--; break;
			case Mobile.KEY_NUM8:
			case Mobile.NOKIA_DOWN: currentItem++; break;
			case Mobile.NOKIA_SOFT3:
			case Mobile.KEY_NUM5:
				selectCurrent();
				return;
			default: return;
		}
		if (currentItem >= items.size()) currentItem = 0;
		if (currentItem < 0) currentItem = items.size() - 1;
		if (type != Choice.MULTIPLE)
			for (int i = 0; i < selected.size(); i++) selected.set(i, i == currentItem);
		render();
	}

	private void selectCurrent()
	{
		if (currentItem < 0 || currentItem >= items.size()) return;
		if (type == Choice.MULTIPLE) selected.set(currentItem, !selected.get(currentItem));
		else setSelectedIndex(currentItem, true);
		render();
		if (type == Choice.IMPLICIT) doDefaultCommand();
	}

	protected void doDefaultCommand()
	{
		if (commandlistener != null && selectCommand != null)
		{
			commandlistener.commandAction(selectCommand, this);
		}
	}

int nativeType() { return type; }
Command nativeSelectCommand() { return type == Choice.IMPLICIT ? selectCommand : null; }
void nativeSelect(int index)
{
currentItem = index;
if (type == Choice.IMPLICIT) { setSelectedIndex(index, true); doDefaultCommand(); }
}
	public String getSoftkeyLabel(boolean left)
	{
		if (type == Choice.IMPLICIT && onlyBackCommand())
			return left ? (selectCommand == null ? "" : selectCommand.getLabel())
					: (commands.isEmpty() ? "" : commands.get(0).getLabel());
		return super.getSoftkeyLabel(left);
	}

	private boolean onlyBackCommand()
	{
		return commands.isEmpty() || (commands.size() == 1
				&& (commands.get(0).getCommandType() == Command.BACK
				|| commands.get(0).getCommandType() == Command.CANCEL));
	}

	protected void doLeftCommand()
	{
		if (type == Choice.IMPLICIT && onlyBackCommand()) selectCurrent();
		else super.doLeftCommand();
	}

	public void pointerPressed(int x, int y)
	{
		if (handlePointerCommand(x, y)) return;
		if (y < contentTop()) return;
		int rowHeight = Math.max(18, Font.getDefaultFont().getHeight() + 6);
		int index = firstVisible + (y - contentTop()) / rowHeight;
		if (index >= 0 && index < items.size())
		{
			currentItem = index;
			selectCurrent();
		}
	}

	public void renderItems()
	{
		ensurePlatformImage();
		PlatformGraphics gc = platformImage.getGraphics();
		gc.reset();
		gc.setFont(Font.getDefaultFont());
		drawHeader(gc);
		int top = contentTop();
		int rowHeight = Math.max(18, Font.getDefaultFont().getHeight() + 6);
		int visible = Math.max(1, (height - top - 20) / rowHeight);
		if (currentItem < 0 && !items.isEmpty()) currentItem = 0;
		int first = items.isEmpty() ? 0 : Math.max(0, currentItem / visible * visible);
		firstVisible = first;
		int last = Math.min(items.size(), first + visible);
		gc.setClip(0, top, width, Math.max(1, height - top - 20));
		for (int i = first; i < last; i++)
		{
			int y = top + (i - first) * rowHeight;
			if (i == currentItem)
			{
				gc.setColor(0x245B8F);
				gc.fillRect(0, y, width, rowHeight);
				gc.setColor(0xFFFFFF);
			}
			else gc.setColor(0x000000);
			String mark = type == Choice.MULTIPLE ? (selected.get(i) ? "[x] " : "[ ] ")
					: (selected.get(i) ? "(*) " : "( ) ");
			String value = getString(i);
			Image image = getImage(i);
			if (image != null) gc.drawImage(image, 4, y, Graphics.LEFT | Graphics.TOP);
			int textX = image == null ? 6 : Math.min(width / 3, image.getWidth() + 8);
			if (getFont(i) != null) gc.setFont(getFont(i));
			gc.drawString(mark + (value == null ? "" : value), textX, y + 2, Graphics.LEFT);
			gc.setFont(Font.getDefaultFont());
		}
		gc.setClip(0, 0, width, height);
		drawSoftkeys(gc);
		flushIfCurrent();
	}

	public void notifySetCurrent()
	{
		render();
	}
}
