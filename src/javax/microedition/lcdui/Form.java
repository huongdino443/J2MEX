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


public class Form extends Screen
{

	public ItemStateListener listener;

	public Form(String title)
	{
		setTitle(title);
		platformImage = new PlatformImage(width, height);
		render();
	}

	public Form(String title, Item[] itemarray)
	{
		setTitle(title);
		if (itemarray != null) for (Item item : itemarray) append(item);
		render();
	}


	public int append(Image img) { return append(new ImageItem("", img, 0, "")); }

	public int append(Item item)
	{
		if (item == null) throw new NullPointerException("item");
		items.add(item);
		item.setOwner(this);
		render();
		return items.size() - 1;
	}

	public int append(String str) { return append(new StringItem("", str)); }

	public void delete(int itemNum)
	{
		Item removed = items.remove(itemNum);
		if (removed != null) removed.setOwner(null);
		if (items.isEmpty()) currentItem = -1;
		else if (currentItem >= items.size()) currentItem = items.size() - 1;
		else if (currentItem > itemNum) currentItem--;
		render();
	}

	public void deleteAll()
	{
		for (Item item : items) item.setOwner(null);
		items.clear();
		currentItem = -1;
		render();
	}

	public Item get(int itemNum) { return items.get(itemNum); }

	public void insert(int itemNum, Item item)
	{
		if (item == null) throw new NullPointerException("item");
		items.add(itemNum, item);
		item.setOwner(this);
		if (currentItem >= itemNum) currentItem++;
		render();
	}

	public void set(int itemNum, Item item)
	{
		if (item == null) throw new NullPointerException("item");
		Item old = items.set(itemNum, item);
		if (old != null) old.setOwner(null);
		item.setOwner(this);
		render();
	}

	public void setItemStateListener(ItemStateListener iListener) { listener = iListener; }

	public int size() { return items.size(); }

	public Item getFocusedItem()
	{
		return currentItem >= 0 && currentItem < items.size() ? items.get(currentItem) : null;
	}

	void focusItem(Item item)
	{
		int index = items.indexOf(item);
		if (index >= 0)
		{
			currentItem = index;
			render();
		}
	}

	void itemChanged(Item item)
	{
		if (listener != null) listener.itemStateChanged(item);
		render();
	}

	void itemInvalidated() { if (isShown()) render(); }

	/*
		Draw form, handle input
	*/

	public void keyPressed(int key)
	{
		if (handleCommandKey(key)) return;
		if (items.isEmpty()) return;
		Item focused = getFocusedItem();
		int delta = key == Mobile.NOKIA_UP || key == Mobile.KEY_NUM2 ? -1
				: key == Mobile.NOKIA_DOWN || key == Mobile.KEY_NUM8 ? 1 : 0;
		if (delta != 0 && focused instanceof ChoiceGroup && ((ChoiceGroup)focused).moveFocus(delta))
		{
			if (((ChoiceGroup)focused).getType() != Choice.MULTIPLE) focused.notifyStateChanged();
			render();
			return;
		}
		if (key == Mobile.NOKIA_LEFT || key == Mobile.KEY_NUM4
				|| key == Mobile.NOKIA_RIGHT || key == Mobile.KEY_NUM6)
		{
			int step = key == Mobile.NOKIA_LEFT || key == Mobile.KEY_NUM4 ? -1 : 1;
			if (focused instanceof ChoiceGroup)
			{
				if (((ChoiceGroup)focused).moveFocus(step)) focused.notifyStateChanged();
			}
			else if (focused instanceof Gauge && ((Gauge)focused).isInteractive())
			{
				Gauge gauge = (Gauge)focused;
				gauge.setValue(gauge.getValue() + step);
				gauge.notifyStateChanged();
			}
			render();
			return;
		}
		switch(key)
		{
			case Mobile.KEY_NUM2:
			case Mobile.NOKIA_UP:
				currentItem--;
				break;
			case Mobile.KEY_NUM8:
			case Mobile.NOKIA_DOWN:
				currentItem++;
				break;
			case Mobile.NOKIA_SOFT3:
			case Mobile.KEY_NUM5:
				activateFocusedItem();
				break;
			default:
				return;
		}
		if (currentItem >= items.size()) currentItem = 0;
		if (currentItem < 0) currentItem = items.size() - 1;
		render();
	}

	private void activateFocusedItem()
	{
		Item item = getFocusedItem();
		if (item == null) return;
		if (item instanceof TextField)
		{
			TextField field = (TextField)item;
			if ((field.getConstraints() & TextField.UNEDITABLE) != 0) return;
			Mobile.getPlatform().requestTextInput(
					field.getLabel() == null || field.getLabel().isEmpty() ? getTitle() : field.getLabel(),
					field.getString(), field.getMaxSize(), field.getConstraints());
		}
		else if (item instanceof ChoiceGroup)
		{
			ChoiceGroup group = (ChoiceGroup)item;
			group.activateFocused();
			itemChanged(group);
		}
		else if (item.getDefaultCommand() != null && item.getItemCommandListener() != null)
		{
			item.getItemCommandListener().commandAction(item.getDefaultCommand(), item);
		}
	}

	public void pointerPressed(int x, int y)
	{
		if (handlePointerCommand(x, y)) return;
		int index = renderedItemAt(y);
		if (index >= 0 && index < items.size())
		{
			currentItem = index;
			render();
			activateFocusedItem();
		}
	}

	public void updateTextInput(String text)
	{
		Item item = getFocusedItem();
		if (item instanceof TextField) applyTextInput((TextField)item, text);
	}

	public void applyTextInput(TextField field, String text)
	{
		if (!items.contains(field) || (field.getConstraints() & TextField.UNEDITABLE) != 0) return;
		field.setString(text);
		field.notifyStateChanged();
	}

	public void notifySetCurrent()
	{
		render();
	}

}
