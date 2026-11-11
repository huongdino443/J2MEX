package javax.microedition.lcdui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;
import org.recompile.mobile.Mobile;
import org.recompile.freej2me.protocol.NativeScreen;

/** Optional native presentation only. The MIDlet/model stays inside FreeJ2ME. */
public final class NativeUi {
    public interface Presenter { void show(NativeScreen screen); }
    private static volatile Presenter presenter;
    private static volatile Displayable active;
    private static volatile int epoch;
    private static int nextId;
    private static long revision, acknowledgedSequence;
    private static final Map<Object, Integer> ids = Collections.synchronizedMap(new IdentityHashMap<Object, Integer>());
    private static final ThreadLocal<Boolean> applying = new ThreadLocal<>();

    private NativeUi() { }
    public static void setPresenter(Presenter value) { presenter = value; }
    public static boolean usesNativeTextBox(Displayable screen) {
        return presenter != null && screen instanceof TextBox
                && ((((TextBox)screen).getConstraints() & TextField.UNEDITABLE) == 0);
    }
    public static boolean supports(Displayable screen) {
        return presenter != null && (screen instanceof List || screen instanceof Form || screen instanceof Alert);
    }
    public static synchronized void transition(Displayable next) {
        if (presenter == null) return;
        active = next; epoch++; ids.clear(); acknowledgedSequence = 0;
        if (!supports(next)) {
            NativeScreen screen = new NativeScreen();
            screen.epoch = epoch; screen.revision = ++revision;
            presenter.show(screen);
        }
    }
    public static synchronized boolean present(Displayable current) {
        if (!supports(current)) return false;
        if (!current.isShown() || Boolean.TRUE.equals(applying.get())) return true;
        if (active != current) transition(current);
        NativeScreen screen = new NativeScreen();
        screen.epoch = epoch; screen.revision = ++revision;
        screen.acknowledgedSequence = acknowledgedSequence;
        screen.kind = current instanceof List ? NativeScreen.LIST
                : current instanceof Alert ? NativeScreen.ALERT : NativeScreen.FORM;
        screen.title = current.getTitle();
        screen.ticker = current.getTicker() == null ? "" : current.getTicker().getString();
        if (current.currentItem < 0 && !current.items.isEmpty()) current.currentItem = 0;
        for (Command c : new ArrayList<Command>(current.getCommands())) addCommand(screen, c);
        if (current instanceof List) {
            List list = (List)current;
            screen.choiceType = list.nativeType();
            boolean longPressActions = screen.choiceType == NativeScreen.IMPLICIT
                    && current.commandlistener != null;
            if (longPressActions) {
                longPressActions = false;
                for (Command command : list.getCommands()) {
                    if (command.getCommandType() == Command.ITEM) {
                        longPressActions = true;
                        break;
                    }
                }
            }
            // Native row tap / DPAD-center already invokes the default Select.
            // Keep explicit MIDlet commands and custom select commands, not an
            // extra synthetic button alongside an application's own Run action.
            if (screen.choiceType == Choice.IMPLICIT && list.nativeSelectCommand() != null
                    && list.nativeSelectCommand() != List.SELECT_COMMAND)
                addCommand(screen, list.nativeSelectCommand());
            for (int i = 0; i < list.size(); i++) {
                NativeScreen.Entry row = new NativeScreen.Entry();
                row.id = id(list.items.get(i)); row.kind = NativeScreen.ROW;
                row.text = list.getString(i); row.image = image(list.getImage(i));
                row.selected = list.isSelected(i); row.longPressEnabled = longPressActions;
                screen.items.add(row);
                if (i == current.currentItem) screen.focusId = row.id;
            }
        } else if (current instanceof Alert) {
            Alert alert = (Alert)current;
            NativeScreen.Entry message = new NativeScreen.Entry();
            message.kind = NativeScreen.STRING; message.text = alert.getString();
            message.image = image(alert.getImage()); screen.items.add(message);
            if (alert.getIndicator() != null) screen.items.add(entry(alert.getIndicator()));
        } else {
            Form form = (Form)current;
            for (int i = 0; i < form.size(); i++) {
                Item item = form.get(i);
                NativeScreen.Entry e = entry(item);
                screen.items.add(e);
                if (i == current.currentItem) screen.focusId = e.id;
            }
        }
        presenter.show(screen);
        return true;
    }
    public static synchronized boolean presentCommands(Displayable target) {
        if (presenter == null || !target.isShown()) return false;
        if (active != target) transition(target);
        NativeScreen screen = new NativeScreen();
        screen.epoch = epoch; screen.revision = ++revision;
        screen.acknowledgedSequence = acknowledgedSequence;
        screen.kind = NativeScreen.COMMAND_MENU; screen.title = target.getTitle();
        for (Command command : new ArrayList<Command>(target.getCommands()))
            if (command.getCommandType() != Command.BACK && command.getCommandType() != Command.CANCEL)
                addCommand(screen, command);
        presenter.show(screen);
        return true;
    }
    public static synchronized void closeCommands(Displayable target) {
        if (presenter == null || active != target) return;
        if (supports(target)) { present(target); return; }
        NativeScreen screen = new NativeScreen();
        screen.epoch = epoch; screen.revision = ++revision;
        presenter.show(screen);
    }
    private static int id(Object object) {
        Integer value = ids.get(object);
        if (value == null) { value = ++nextId; ids.put(object, value); }
        return value;
    }
    private static void addCommand(NativeScreen screen, Command c) {
        for (NativeScreen.Action action : screen.commands) if (action.id == id(c)) return;
        NativeScreen.Action action = new NativeScreen.Action();
        action.id = id(c); action.type = c.getCommandType(); action.priority = c.getPriority(); action.label = c.getLabel();
        screen.commands.add(action);
    }
    private static NativeScreen.Entry entry(Item item) {
        NativeScreen.Entry e = new NativeScreen.Entry();
        e.id = id(item); e.label = item.getLabel();
        if (item.getDefaultCommand() != null) e.actionId = id(item.getDefaultCommand());
        if (item instanceof StringItem) {
            StringItem string = (StringItem)item;
            e.kind = NativeScreen.STRING; e.text = string.getText();
            e.value = string.getAppearanceMode();
            e.interactive = e.value == NativeScreen.APPEARANCE_BUTTON;
        } else if (item instanceof ImageItem) {
            ImageItem imageItem = (ImageItem)item;
            e.kind = NativeScreen.IMAGE; e.image = image(imageItem.getImage());
            e.text = imageItem.getAltText(); e.value = imageItem.getAppearanceMode();
            e.constraints = imageItem.getLayout();
        } else if (item instanceof TextField) {
            TextField field = (TextField)item;
            e.kind = NativeScreen.TEXT; e.text = field.getString(); e.constraints = field.getConstraints();
            e.maxSize = field.getMaxSize(); e.caret = field.getCaretPosition();
        } else if (item instanceof DateField) {
            DateField field = (DateField)item;
            e.kind = NativeScreen.DATE_FIELD; e.constraints = field.getInputMode();
            java.util.Date date = field.getDate();
            e.text = date == null ? "" : Long.toString(date.getTime());
        } else if (item instanceof ChoiceGroup) {
            ChoiceGroup group = (ChoiceGroup)item;
            e.kind = NativeScreen.CHOICE; e.choiceType = group.getType();
            for (int i = 0; i < group.size(); i++) {
                NativeScreen.Entry child = new NativeScreen.Entry();
                child.kind = NativeScreen.ROW; child.text = group.getString(i);
                child.image = image(group.getImage(i)); child.selected = group.isSelected(i);
                e.children.add(child);
            }
        } else if (item instanceof Gauge) {
            Gauge gauge = (Gauge)item;
            e.kind = NativeScreen.GAUGE; e.value = gauge.getValue(); e.maximum = gauge.getMaxValue();
            e.interactive = gauge.isInteractive();
        } else if (item instanceof Spacer) {
            e.kind = NativeScreen.SPACER; e.value = item.getMinimumHeight();
        } else if (item instanceof CustomItem) {
            CustomItem custom = (CustomItem)item;
            try {
                int width = Math.max(custom.getMinContentWidth(),
                        custom.getPrefContentWidth(160));
                width = Math.max(1, Math.min(216, width));
                int height = Math.max(custom.getMinContentHeight(),
                        custom.getPrefContentHeight(width));
                height = Math.max(1, Math.min(160, height));
                Image snapshot = Image.createImage(width, height);
                custom.paint(snapshot.getGraphics(), width, height);
                e.kind = NativeScreen.CUSTOM; e.value = width; e.maximum = height;
                e.constraints = Item.LAYOUT_CENTER;
                e.image = image(snapshot);
            } catch (RuntimeException error) {
                e.kind = NativeScreen.UNSUPPORTED;
                e.text = "CustomItem render lỗi: " + error.getClass().getSimpleName();
            }
        } else {
            e.kind = NativeScreen.UNSUPPORTED; e.text = item.getClass().getSimpleName() + " chưa hỗ trợ native";
        }
        return e;
    }
    private static byte[] image(Image image) {
        if (image == null) return new byte[0];
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image.platformImage.getCanvas(), "png", bytes)) throw new IOException("PNG writer missing");
            return bytes.toByteArray();
        } catch (IOException error) { throw new IllegalStateException("Cannot export LCDUI image", error); }
    }
    private static boolean hasId(Object object, int value) {
        Integer known = ids.get(object);
        return known != null && known == value;
    }

    public static void apply(NativeScreen.Event event) {
        Displayable target;
        synchronized (NativeUi.class) {
            target = active;
            if (event.epoch != epoch || target == null
                    || (!supports(target) && !target.listCommands)
                    || Mobile.getDisplay().getCurrent() != target) return;
            if (event.sequence <= acknowledgedSequence) return;
            acknowledgedSequence = event.sequence;
        }
        ArrayList<Item> changed = new ArrayList<>();
        applying.set(Boolean.TRUE);
        try {
            if (target instanceof List) {
                List list = (List)target;
                boolean[] selected = new boolean[list.size()];
                list.getSelectedFlags(selected);
                for (NativeScreen.State state : event.states)
                    for (int i = 0; i < list.size(); i++)
                        if (hasId(list.items.get(i), state.id)) selected[i] = state.value != 0;
                list.setSelectedFlags(selected);
            } else if (target instanceof Form) {
                Form form = (Form)target;
                for (NativeScreen.State state : event.states) {
                    for (int i = 0; i < form.size(); i++) {
                        Item item = form.get(i);
                        if (!hasId(item, state.id)) continue;
                        if (item instanceof TextField) {
                            TextField field = (TextField)item;
                            if ((field.getConstraints() & TextField.UNEDITABLE) != 0 || state.text.length() > field.getMaxSize()) continue;
                            boolean different = !field.getString().equals(state.text);
                            field.nativeText(state.text, state.caret);
                            if (different) changed.add(item);
                        } else if (item instanceof ChoiceGroup) {
                            ChoiceGroup group = (ChoiceGroup)item;
                            if (state.flags.length != group.size()) continue;
                            boolean[] before = new boolean[group.size()]; group.getSelectedFlags(before);
                            group.setSelectedFlags(state.flags);
                            if (!Arrays.equals(before, state.flags)) changed.add(item);
                        } else if (item instanceof Gauge && ((Gauge)item).isInteractive()) {
                            Gauge gauge = (Gauge)item;
                            int before = gauge.getValue(); gauge.setValue(state.value);
                            if (before != gauge.getValue()) changed.add(item);
                        } else if (item instanceof DateField) {
                            DateField field = (DateField)item;
                            java.util.Date next;
                            try {
                                next = state.text == null || state.text.length() == 0
                                        ? null : new java.util.Date(Long.parseLong(state.text));
                            } catch (NumberFormatException invalidDate) {
                                continue;
                            }
                            java.util.Date before = field.getDate();
                            if (before == null ? next != null
                                    : next == null || before.getTime() != next.getTime()) {
                                field.setDate(next);
                                changed.add(item);
                            }
                        }
                    }
                }
                // Commit every field first: callbacks and commands see the entire latest form.
                for (Item item : changed) {
                    if (Mobile.getDisplay().getCurrent() != target) break;
                    item.notifyStateChanged();
                }
            }
            if (Mobile.getDisplay().getCurrent() != target) return;
            if (event.action == NativeScreen.Event.BACK) target.handleBackPressed();
            else if (event.action == NativeScreen.Event.SELECT && target instanceof List) {
                List list = (List)target;
                for (int i = 0; i < list.size(); i++)
                    if (hasId(list.items.get(i), event.target)) { list.nativeSelect(i); break; }
            } else if (event.action == NativeScreen.Event.COMMAND) {
                Command selected = null;
                for (Command command : new ArrayList<Command>(target.getCommands()))
                    if (hasId(command, event.target)) selected = command;
                if (target instanceof List) {
                    Command select = ((List)target).nativeSelectCommand();
                    if (select != null && hasId(select, event.target)) selected = select;
                }
                if (target.listCommands) {
                    target.listCommands = false; target.currentCommand = 0;
                    closeCommands(target);
                }
                if (selected != null && target.commandlistener != null)
                    target.commandlistener.commandAction(selected, target);
            } else if (event.action == NativeScreen.Event.ITEM && target instanceof Form) {
                Form form = (Form)target;
                for (int i = 0; i < form.size(); i++) {
                    Item item = form.get(i);
                    if (hasId(item, event.target) && item.getDefaultCommand() != null && item.getItemCommandListener() != null) {
                        item.getItemCommandListener().commandAction(item.getDefaultCommand(), item);
                        break;
                    }
                }
            }
        } finally {
            applying.remove();
            Displayable now = Mobile.getDisplay().getCurrent();
            if (supports(now)) present(now);
        }
    }
}
