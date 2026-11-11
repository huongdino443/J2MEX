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

import org.recompile.mobile.Mobile;
import org.recompile.mobile.PlatformImage;
import org.recompile.mobile.PlatformGraphics;

public abstract class Displayable
{

	public PlatformImage platformImage;

	public int width = 0;

	public int height = 0;

	public boolean fullScreen = false;
	
	protected String title = "";

	protected ArrayList<Command> commands = new ArrayList<Command>();

	protected ArrayList<Item> items = new ArrayList<Item>();

	protected CommandListener commandlistener;

	protected boolean listCommands = false;
	
	protected int currentCommand = 0;

	protected int currentItem = -1;

private int[] renderedTops = new int[0];
private int[] renderedBottoms = new int[0];

	public Ticker ticker;

	public Displayable()
	{
		width = Mobile.getPlatform().lcdWidth;
		height = Mobile.getPlatform().lcdHeight;
	}

protected void ensurePlatformImage()
{
if (platformImage == null) platformImage = new PlatformImage(width, height);
}

	public void addCommand(Command cmd)
{
if (cmd != null && !commands.contains(cmd))
{
int index = 0;
while (index < commands.size() && commands.get(index).getPriority() <= cmd.getPriority()) index++;
commands.add(index, cmd);
if (isShown()) render();
}
	}

public void removeCommand(Command cmd)
{
commands.remove(cmd);
if (isShown()) render();
}
	
	public int getWidth() { return width; }

	public int getHeight() { return height; }
	
	public String getTitle() { return title; }

	public void setTitle(String text) { title = text == null ? "" : text; if (isShown()) render(); }

	public boolean isShown() { return Mobile.getDisplay() != null && Mobile.getDisplay().getCurrent() == this; }

	public Ticker getTicker() { return ticker; }

	public void setTicker(Ticker tick) { ticker = tick; if (isShown()) render(); }
	
	public void setCommandListener(CommandListener listener) { commandlistener = listener; }

	protected void sizeChanged(int width, int height) { }

	public Display getDisplay() { return Mobile.getDisplay(); }

	public ArrayList<Command> getCommands() { return commands; }

public boolean dispatchTextInputCommand(Command command)
{
	if (!(this instanceof TextBox) || command == null || !commands.contains(command)
			|| commandlistener == null || Mobile.getDisplay() == null
			|| Mobile.getDisplay().getCurrent() != this) return false;
	doCommand(commands.indexOf(command));
	return true;
}

	public void keyPressed(int key) { }
	public void keyReleased(int key) { }
	public void keyRepeated(int key) { }
	public void pointerDragged(int x, int y) { }
	public void pointerPressed(int x, int y) { }
	public void pointerReleased(int x, int y) { }
	public void showNotify() { }
	public void hideNotify() { }

	public void notifySetCurrent() { }

	protected void render()
	{
if (NativeUi.present(this)) return;
if (NativeUi.usesNativeTextBox(this)) return;
if (this instanceof Canvas && !listCommands) {
NativeUi.closeCommands(this);
((Canvas)this).repaint();
return;
}
		ensurePlatformImage();
		if(listCommands==true)
		{
			renderCommands();
		}
		else
		{
			renderItems();
		}
	}

	public void renderItems()
	{
		ensurePlatformImage();
		PlatformGraphics gc = platformImage.getGraphics();
		gc.reset();
		gc.setColor(0xFFFFFF);
		gc.fillRect(0, 0, width, height);
		gc.setColor(0x000000);
		gc.setFont(Font.getDefaultFont());
		drawHeader(gc);

		if (!items.isEmpty())
		{
			if (currentItem < 0) currentItem = 0;
			if (currentItem >= items.size()) currentItem = items.size() - 1;

			int top = contentTop();
			int bottom = Math.max(top + 1, height - 20);
			int available = bottom - top;
			int selectedTop = 0;
			int selectedHeight = 0;
			int totalHeight = 0;
			for (int i = 0; i < items.size(); i++)
			{
				int itemHeight = itemHeight(items.get(i));
				if (i < currentItem) selectedTop += itemHeight;
				if (i == currentItem) selectedHeight = itemHeight;
				totalHeight += itemHeight;
			}
			int scroll = Math.max(0, Math.min(selectedTop, Math.max(0, totalHeight - available)));
			if (selectedHeight >= available) scroll = selectedTop;
			else if (selectedTop + selectedHeight - scroll > available)
				scroll = Math.min(totalHeight - available, selectedTop + selectedHeight - available);

			renderedTops = new int[items.size()];
			renderedBottoms = new int[items.size()];
			gc.setClip(0, top, width, available);
			int y = top - scroll;
			for (int i = 0; i < items.size(); i++)
			{
				Item item = items.get(i);
				int itemHeight = itemHeight(item);
				renderedTops[i] = y;
				renderedBottoms[i] = y + itemHeight;
				if (y + itemHeight >= top && y < bottom)
					drawItem(gc, item, i == currentItem, y, itemHeight);
				y += itemHeight;
			}
			gc.setClip(0, 0, width, height);
		}
		drawSoftkeys(gc);
		flushIfCurrent();
	}

	protected void renderCommands()
	{
if (NativeUi.presentCommands(this)) return;
		ensurePlatformImage();
		PlatformGraphics gc = platformImage.getGraphics();
		gc.reset();
		gc.setColor(0xFFFFFF);
		gc.fillRect(0, 0, width, height);
		gc.setColor(0x000000);
		gc.setFont(Font.getDefaultFont());
drawHeader(gc, Command.optionsLabel());
		int top = contentTop();
		int rowHeight = Math.max(18, Font.getDefaultFont().getHeight() + 4);
		int maxRows = Math.max(1, (height - top - 20) / rowHeight);
		int first = commands.isEmpty() ? 0 : (currentCommand / maxRows) * maxRows;
		int last = Math.min(commands.size(), first + maxRows);
		for (int i = first; i < last; i++)
		{
			int y = top + (i - first) * rowHeight;
			if (i == currentCommand)
			{
				gc.setColor(0x245B8F);
				gc.fillRect(0, y, width, rowHeight);
				gc.setColor(0xFFFFFF);
			}
			gc.drawString(commands.get(i).getLongLabel(), 8, y + 2, Graphics.LEFT);
			gc.setColor(0x000000);
		}
		gc.setColor(0x000000);
		gc.drawLine(0, height - 20, width, height - 20);
		gc.drawString("Select", 4, height - 17, Graphics.LEFT);
		gc.drawString("Back", width - 4, height - 17, Graphics.RIGHT);
		flushIfCurrent();
	}

	protected void keyPressedCommands(int key)
	{
		if (commands.isEmpty()) { listCommands = false; render(); return; }
		switch (key)
		{
			case Mobile.KEY_NUM2:
			case Mobile.NOKIA_UP: currentCommand--; break;
			case Mobile.KEY_NUM8:
			case Mobile.NOKIA_DOWN: currentCommand++; break;
			case Mobile.NOKIA_SOFT1:
			case Mobile.KEY_NUM5:
			case Mobile.NOKIA_SOFT3:
				int selected = Math.max(0, Math.min(currentCommand, commands.size() - 1));
				listCommands = false;
				currentCommand = 0;
				doCommand(selected);
				if (isShown()) render();
				return;
			case Mobile.NOKIA_SOFT2:
				listCommands = false;
				currentCommand = 0;
				render();
				return;
			default: return;
		}
		if (currentCommand >= commands.size()) currentCommand = 0;
		if (currentCommand < 0) currentCommand = commands.size() - 1;
		renderCommands();
	}

	protected void doCommand(int index)
	{
		if (index >= 0 && index < commands.size())
		{
			Command command = commands.get(index);
			if (commandlistener != null) commandlistener.commandAction(command, this);
		}
	}

	protected void doDefaultCommand()
	{
		doCommand(0);
	}

	protected void doLeftCommand()
	{
		int back = backCommandIndex();
		int actionCount = commands.size() - (back >= 0 ? 1 : 0);
		if (actionCount > 1 && (commands.size() > 2 || back >= 0))
		{
			listCommands = true;
			currentCommand = Math.max(0, Math.min(currentCommand, commands.size() - 1));
			renderCommands();
		}
		else if (!commands.isEmpty()) doCommand(back == 0 && commands.size() > 1 ? 1 : 0);
	}

	protected void doRightCommand()
	{
		if (listCommands)
		{
			keyPressedCommands(Mobile.NOKIA_SOFT2);
		}
		else
		{
			int back = backCommandIndex();
			if (back >= 0) doCommand(back);
			else if (commands.size() == 2) doCommand(1);
		}
	}

	protected boolean handleCommandKey(int key)
	{
		if (listCommands)
		{
			keyPressedCommands(key);
			return true;
		}
		if (key == Mobile.NOKIA_SOFT1) { doLeftCommand(); return true; }
		if (key == Mobile.NOKIA_SOFT2) { doRightCommand(); return true; }
		return false;
	}

	/** Route Android's two softkey buttons through LCDUI commands before Canvas game keys. */
	public boolean dispatchSoftkey(int key)
	{
if (listCommands) { keyPressedCommands(key); return true; }
		if ((key != Mobile.NOKIA_SOFT1 && key != Mobile.NOKIA_SOFT2) || commands.isEmpty())
			return false;
		return handleCommandKey(key);
	}

	public boolean handleBackPressed()
	{
		if (listCommands)
		{
			listCommands = false;
			currentCommand = 0;
			render();
			return true;
		}
		int back = backCommandIndex();
		if (back >= 0) { doCommand(back); return true; }
		return false;
	}

	private int backCommandIndex()
	{
		for (int i = 0; i < commands.size(); i++)
		{
			int type = commands.get(i).getCommandType();
			if (type == Command.BACK || type == Command.CANCEL) return i;
		}
		return -1;
	}

	public String getSoftkeyLabel(boolean left)
	{
		if (commands.isEmpty()) return "";
		int back = backCommandIndex();
		int actions = commands.size() - (back >= 0 ? 1 : 0);
		if (!left)
		{
			if (back >= 0 && commands.size() > 1) return commands.get(back).getLabel();
			return back < 0 && commands.size() == 2 ? commands.get(1).getLabel() : "";
		}
if (actions > 1 && (commands.size() > 2 || back >= 0)) return Command.optionsLabel();
		return commands.get(back == 0 && commands.size() > 1 ? 1 : 0).getLabel();
	}

	protected int renderedItemAt(int y)
	{
		if (y < contentTop() || y >= height - 20) return -1;
		for (int i = 0; i < renderedTops.length; i++)
			if (y >= renderedTops[i] && y < renderedBottoms[i]) return i;
		return -1;
	}

	protected boolean handlePointerCommand(int x, int y)
	{
		if (y >= height - 20)
		{
			if (x < width / 2) doLeftCommand(); else doRightCommand();
			return true;
		}
		if (listCommands)
		{
			int rowHeight = Math.max(18, Font.getDefaultFont().getHeight() + 4);
			int rows = Math.max(1, (height - contentTop() - 20) / rowHeight);
			int row = (y - contentTop()) / rowHeight;
			int index = (currentCommand / rows) * rows + row;
			if (y >= contentTop() && index >= 0 && index < commands.size())
			{
				currentCommand = index;
				keyPressedCommands(Mobile.NOKIA_SOFT3);
			}
			return true;
		}
		return false;
	}

	public void updateTextInput(String text) { }

	public void textInputAccepted() { }

	public void textInputCancelled() { }

	protected void drawHeader(PlatformGraphics gc)
	{
		drawHeader(gc, title);
	}

	protected void drawHeader(PlatformGraphics gc, String heading)
	{
		gc.setColor(0xFFFFFF);
		gc.fillRect(0, 0, width, height);
		gc.setColor(0x000000);
		if (heading != null && !heading.isEmpty())
			gc.drawString(heading, width / 2, 2, Graphics.HCENTER);
		int y = 19;
		if (ticker != null && ticker.getString() != null && !ticker.getString().isEmpty())
		{
			gc.setColor(0xE8E8E8);
			gc.fillRect(0, 18, width, 16);
			gc.setColor(0x000000);
			gc.drawString(ticker.getString(), 3, 19, Graphics.LEFT);
			y = 35;
		}
		gc.drawLine(0, y, width, y);
	}

	protected int contentTop()
	{
		return ticker == null ? 23 : 39;
	}

	private int itemHeight(Item item)
	{
		int available = Math.max(32, width - 16);
		if (item instanceof TextField) return 38;
		if (item instanceof ChoiceGroup)
			return Math.max(22, 18 * (((ChoiceGroup)item).size() + (empty(item.getLabel()) ? 0 : 1)));
		if (item instanceof ImageItem)
		{
			Image image = ((ImageItem)item).getImage();
			return Math.max(22, Math.min(72, image == null ? 0 : image.getHeight()) + (empty(item.getLabel()) ? 8 : 20));
		}
		if (item instanceof Gauge) return 34;
		if (item instanceof StringItem)
		{
			String text = ((StringItem)item).getText();
			return Math.max(20, wrappedLineCount(text, available) * 16 + (empty(item.getLabel()) ? 4 : 18));
		}
		return Math.max(20, item.getPreferredHeight());
	}

	protected int wrappedLineCount(String text, int available)
	{
		if (text == null || text.length() == 0) return 1;
		Font font = Font.getDefaultFont();
		int lines = 0;
		for (String paragraph : text.split("\\n", -1))
		{
			int lineWidth = 0;
			int lineChars = 0;
			for (int i = 0; i < paragraph.length(); i++)
			{
				int charWidth = font.charWidth(paragraph.charAt(i));
				if (lineChars > 0 && lineWidth + charWidth > available)
				{
					lines++;
					lineWidth = 0;
					lineChars = 0;
				}
				lineWidth += charWidth;
				lineChars++;
			}
			lines++;
		}
		return Math.max(1, lines);
	}

	private void drawItem(PlatformGraphics gc, Item item, boolean selected, int y, int itemHeight)
	{
		int foreground = selected ? 0xFFFFFF : 0x000000;
		if (selected)
		{
			gc.setColor(0x245B8F);
			gc.fillRect(0, y, width, itemHeight);
		}
		gc.setColor(foreground);
		int x = 8;
		int textY = y + 2;
		String label = item.getLabel();
		if (!empty(label))
		{
			gc.drawString(label, x, textY, Graphics.LEFT);
			textY += 16;
		}
		if (item instanceof StringItem)
		{
			drawWrappedText(gc, ((StringItem)item).getText(), x, textY, width - 16, foreground);
		}
		else if (item instanceof TextField)
		{
			TextField field = (TextField)item;
			gc.setColor(selected ? 0xFFFFFF : 0x000000);
			gc.drawRect(x, textY, width - 18, 17);
			String value = field.getString();
			if ((field.getConstraints() & TextField.PASSWORD) != 0)
				value = value.replaceAll("(?s).", "*");
			gc.drawString(value, x + 3, textY + 1, Graphics.LEFT);
		}
		else if (item instanceof ImageItem)
		{
			Image image = ((ImageItem)item).getImage();
			if (image != null) gc.drawImage(image, x, textY, Graphics.LEFT | Graphics.TOP);
			else drawWrappedText(gc, ((ImageItem)item).getAltText(), x, textY, width - 16, foreground);
		}
		else if (item instanceof ChoiceGroup)
		{
			ChoiceGroup group = (ChoiceGroup)item;
			for (int i = 0; i < group.size(); i++)
			{
				String mark = group.getType() == Choice.MULTIPLE
						? (group.isSelected(i) ? "[x] " : "[ ] ")
						: (group.isSelected(i) ? "(*) " : "( ) ");
				if (selected && i == group.getFocusedIndex()) mark = ">" + mark;
				gc.drawString(mark + group.getString(i), x, textY, Graphics.LEFT);
				textY += 18;
			}
		}
		else if (item instanceof Gauge)
		{
			Gauge gauge = (Gauge)item;
			int barX = x, barY = textY + 4, barWidth = Math.max(20, width - 24), barHeight = 10;
			gc.drawRect(barX, barY, barWidth, barHeight);
			if (gauge.getMaxValue() > 0)
			{
				int filled = Math.max(0, Math.min(barWidth - 2,
						(int)((barWidth - 2L) * gauge.getValue() / gauge.getMaxValue())));
				gc.fillRect(barX + 1, barY + 1, filled, barHeight - 1);
			}
		}
		else
		{
			drawWrappedText(gc, label, x, textY, width - 16, foreground);
		}
		gc.setColor(0x000000);
	}

	protected void drawWrappedText(PlatformGraphics gc, String text, int x, int y, int maxWidth, int color)
	{
		if (text == null) return;
		Font font = Font.getDefaultFont();
		gc.setColor(color);
		for (String paragraph : text.split("\\n", -1))
		{
			int start = 0;
			while (start < paragraph.length())
			{
				int end = start;
				int lastSpace = -1;
				int lineWidth = 0;
				while (end < paragraph.length())
				{
					char c = paragraph.charAt(end);
					int charWidth = font.charWidth(c);
					if (end > start && lineWidth + charWidth > maxWidth) break;
					lineWidth += charWidth;
					if (Character.isWhitespace(c)) lastSpace = end;
					end++;
				}
				if (end < paragraph.length() && lastSpace > start) end = lastSpace;
				if (end == start) end++;
				gc.drawString(paragraph.substring(start, end).trim(), x, y, Graphics.LEFT);
				y += 16;
				start = end;
				while (start < paragraph.length() && paragraph.charAt(start) == ' ') start++;
			}
			if (paragraph.length() == 0) y += 16;
		}
	}

	protected void drawSoftkeys(PlatformGraphics gc)
	{
		gc.setColor(0x000000);
		gc.drawLine(0, height - 20, width, height - 20);
		String left = getSoftkeyLabel(true);
		String right = getSoftkeyLabel(false);
		if (!left.isEmpty()) gc.drawString(left, 4, height - 17, Graphics.LEFT);
		if (!right.isEmpty()) gc.drawString(right, width - 4, height - 17, Graphics.RIGHT);
	}

	private boolean empty(String text) { return text == null || text.length() == 0; }

	protected void flushIfCurrent()
	{
		if (isShown() && platformImage != null)
			Mobile.getPlatform().repaint(platformImage, 0, 0, width, height);
	}
}
