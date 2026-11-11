import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.List;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;
import org.recompile.freej2me.protocol.NativeScreen;

public final class NativeUiModelSmoke {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static NativeScreen.Event event(NativeScreen screen, int action, int target, long sequence) {
        NativeScreen.Event e = new NativeScreen.Event();
        e.epoch = screen.epoch; e.action = action; e.target = target; e.sequence = sequence; return e;
    }
    private static NativeScreen.State state(int id, String text, int value, boolean... flags) {
        NativeScreen.State s = new NativeScreen.State();
        s.id = id; s.text = text; s.caret = text.length(); s.value = value; s.flags = flags; return s;
    }
    private static int command(NativeScreen screen, String label) {
        for (NativeScreen.Action c : screen.commands) if (c.label.equals(label)) return c.id;
        throw new AssertionError("Missing command " + label);
    }
    public static void main(String[] args) throws Exception {
        MobilePlatform platform = new MobilePlatform(240, 320); Mobile.setPlatform(platform);
        Display display = new Display();
        AtomicInteger paints = new AtomicInteger(), presentations = new AtomicInteger(), callbacks = new AtomicInteger();
        AtomicReference<NativeScreen> shown = new AtomicReference<>();
        platform.setPainter(paints::incrementAndGet);
        NativeUi.setPresenter(s -> { shown.set(s); presentations.incrementAndGet(); });
        Image icon = Image.createImage(48, 48);
        icon.getGraphics().setColor(0x2255AA); icon.getGraphics().fillRect(0, 0, 48, 48);
        TextField field = new TextField("Tên", "Cũ", 128, TextField.ANY);
        ChoiceGroup group = new ChoiceGroup("Chọn", Choice.MULTIPLE, new String[]{"Một", "Hai"}, null);
        Gauge gauge = new Gauge("Mức", true, 10, 3);
        StringItem status = new StringItem("Trạng thái", "Ban đầu");
        Form form = new Form("Cài đặt tiếng Việt", new Item[]{field, group, gauge, status, new ImageItem("Icon", icon, 0, "icon")});
        Command save = new Command("Lưu", Command.OK, 0);
        form.addCommand(save);
        form.setItemStateListener(item -> {
            check(field.getString().equals("Tiếng Việt ✓") && group.isSelected(1) && gauge.getValue() == 7,
                    "All fields committed before ItemStateListener");
            status.setText("Đã cập nhật");
        });
        form.setCommandListener((c, owner) -> {
            check(c == save && field.getString().equals("Tiếng Việt ✓") && gauge.getValue() == 7,
                    "Command sees latest entire Form");
            callbacks.incrementAndGet();
        });
        check(presentations.get() == 0, "Offscreen construction sends no native view");
        display.setCurrent(form);
        NativeScreen first = shown.get();
        check(first.kind == NativeScreen.FORM && first.title.equals("Cài đặt tiếng Việt"), "Native Form snapshot");
        check(first.items.size() == 5 && first.items.get(4).image.length > 0, "Form image exported");
        check(paints.get() == 0, "Native Form does not repaint framebuffer");
        NativeScreen.Event submit = event(first, NativeScreen.Event.COMMAND, command(first, "Lưu"), 1);
        submit.states.add(state(first.items.get(0).id, "Tiếng Việt ✓", 0));
        submit.states.add(state(first.items.get(1).id, "", 0, false, true));
        submit.states.add(state(first.items.get(2).id, "", 7));
        NativeUi.apply(submit);
        check(callbacks.get() == 1 && shown.get().acknowledgedSequence == 1, "Command dispatched once with acknowledged sequence");
        check(shown.get().items.get(3).text.equals("Đã cập nhật"), "Listener's programmatic update returned");
        NativeUi.apply(submit);
        check(callbacks.get() == 1, "Repeated event sequence ignored");
        check(field.getCaretPosition() == "Tiếng Việt ✓".length(), "Native caret mirrored");

        NativeScreen encoded = shown.get();
        encoded.items.get(0).longPressEnabled = true;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        encoded.write(new DataOutputStream(bytes));
        // Socket.available() may be zero before the payload arrives.
        InputStream fragmented = new FilterInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override public int available() { return 0; }
        };
        NativeScreen decoded = NativeScreen.read(new DataInputStream(fragmented));
        check(decoded.title.equals(encoded.title) && decoded.items.get(4).image.length > 0, "Snapshot codec Unicode / images / fragmented stream");
        check(decoded.acknowledgedSequence == 1 && decoded.commands.size() == 1, "Snapshot metadata round trip");
        check(decoded.items.get(0).longPressEnabled, "Per-row long-press capability round trip");
        bytes.reset(); NativeScreen.writeEvent(new DataOutputStream(bytes), submit);
        NativeScreen.Event roundTrip = NativeScreen.readEvent(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        check(roundTrip.sequence == 1 && roundTrip.states.get(1).flags[1], "Event codec round trip");
        boolean rejected = false;
        try { NativeScreen.read(new DataInputStream(new ByteArrayInputStream(new byte[]{127, 127, 127, 127}))); }
        catch (IOException expected) { rejected = true; }
        check(rejected, "Oversized native packet rejected");
        byte[] truncated = Arrays.copyOf(bytes.toByteArray(), bytes.size() - 1);
        rejected = false;
        try { NativeScreen.readEvent(new DataInputStream(new ByteArrayInputStream(truncated))); }
        catch (IOException expected) { rejected = true; }
        check(rejected, "Truncated native event rejected");

        form.setItemStateListener(null);
        int deletedId = first.items.get(0).id;
        form.delete(0);
        TextField replacement = new TextField("Thay thế", "Không đổi", 128, TextField.ANY);
        form.insert(0, replacement);
        NativeScreen.Event staleItem = event(shown.get(), NativeScreen.Event.CHANGE, 0, 2);
        staleItem.states.add(state(deletedId, "Wrong item", 0));
        NativeUi.apply(staleItem);
        check(replacement.getString().equals("Không đổi"), "Removed item ID cannot edit replacement");
        group.set(0, "Nhãn mới", null);
        check(shown.get().items.get(1).children.get(0).text.equals("Nhãn mới"), "Programmatic ChoiceGroup update invalidates native screen");

        check(display.getBestImageWidth(Display.LIST_ELEMENT) == 32
                && display.getBestImageHeight(Display.LIST_ELEMENT) == 32,
                "List recommends a square icon, not framebuffer width");
        check(display.getBestImageWidth(Display.CHOICE_GROUP_ELEMENT)
                == display.getBestImageHeight(Display.CHOICE_GROUP_ELEMENT),
                "ChoiceGroup recommends square icons");
        Image recommended = Image.createImage(display.getBestImageWidth(Display.LIST_ELEMENT),
                display.getBestImageHeight(Display.LIST_ELEMENT));
        List launcher = new List("Launcher", Choice.IMPLICIT,
                new String[]{"1", "2"}, new Image[]{recommended, null});
        Command run = new Command("Chạy", Command.SCREEN, 0);
        launcher.addCommand(run);
        AtomicInteger defaultSelections = new AtomicInteger();
        launcher.setCommandListener((c, owner) -> {
            check(c == List.SELECT_COMMAND, "row activation retains the default Select callback");
            defaultSelections.incrementAndGet();
        });
        display.setCurrent(launcher);
        NativeScreen launcherScreen = shown.get();
        check(launcherScreen.commands.size() == 1 && command(launcherScreen, "Chạy") > 0,
                "Run command is retained without a duplicate synthetic Select button");
        check(!launcherScreen.items.get(0).longPressEnabled,
                "Ordinary implicit Lists do not enable long-press actions");
        java.awt.image.BufferedImage exportedIcon = javax.imageio.ImageIO.read(
                new ByteArrayInputStream(launcherScreen.items.get(0).image));
        check(exportedIcon.getWidth() == 32 && exportedIcon.getHeight() == 32,
                "recommended square icon keeps both dimensions across the native bridge");
        check(launcherScreen.focusId == launcherScreen.items.get(0).id,
                "initial row focus is exported");
        NativeScreen.Event launchSecond = event(launcherScreen, NativeScreen.Event.SELECT,
                launcherScreen.items.get(1).id, 1);
        NativeUi.apply(launchSecond);
        check(defaultSelections.get() == 1 && launcher.getSelectedIndex() == 1
                && shown.get().focusId == shown.get().items.get(1).id,
                "row activation updates selection and focus without a Select button");
        launcher.addCommand(List.SELECT_COMMAND);
        check(shown.get().commands.size() == 2,
                "an explicitly added MIDlet Select command is never removed");

        List list = new List("Menu", Choice.IMPLICIT, new String[]{"Một", "Hai"}, new Image[]{icon, null});
        Command select = new Command("Mở", Command.OK, 0);
        list.addCommand(new Command("Chi tiết", Command.ITEM, 1));
        list.setSelectCommand(select);
        list.setCommandListener((c, owner) -> {
            check(c == select && list.getSelectedIndex() == 1, "Native implicit selection reaches correct listener");
            callbacks.incrementAndGet();
        });
        display.setCurrent(list);
        NativeScreen menu = shown.get();
        check(command(menu, "Mở") > 0, "custom setSelectCommand remains visible");
        check(menu.kind == NativeScreen.LIST && menu.items.get(0).image.length > 0, "Native List icon and type");
        check(menu.items.get(0).longPressEnabled && menu.items.get(1).longPressEnabled,
                "IMPLICIT List rows with ITEM commands enable long-press actions");
        NativeUi.apply(submit);
        check(display.getCurrent() == list && callbacks.get() == 1, "Previous screen event ignored");
        NativeScreen.Event open = event(menu, NativeScreen.Event.SELECT, menu.items.get(1).id, 1);
        open.states.add(state(menu.items.get(0).id, "", 0));
        open.states.add(state(menu.items.get(1).id, "", 1));
        NativeUi.apply(open);
        check(callbacks.get() == 2 && list.getSelectedIndex() == 1, "Implicit native click invokes Select");
        NativeScreen.Event longPress = event(menu, NativeScreen.Event.CHANGE, menu.items.get(0).id, 2);
        longPress.states.add(state(menu.items.get(0).id, "", 1));
        longPress.states.add(state(menu.items.get(1).id, "", 0));
        NativeUi.apply(longPress);
        check(callbacks.get() == 2 && list.getSelectedIndex() == 0
                && shown.get().focusId == menu.items.get(0).id,
                "Long-press focuses a List row without invoking Select");
        List multiple = new List("Nhiều", Choice.MULTIPLE, new String[]{"A", "B"}, null);
        multiple.addCommand(new Command("Chọn thao tác", Command.ITEM, 1));
        multiple.setCommandListener((c, owner) -> { });
        display.setCurrent(multiple);
        NativeScreen m = shown.get();
        check(!m.items.get(0).longPressEnabled && !m.items.get(1).longPressEnabled,
                "MULTIPLE rows do not inherit IMPLICIT long-press actions");
        NativeScreen.Event flags = event(m, NativeScreen.Event.CHANGE, 0, 1);
        for (NativeScreen.Entry row : m.items) flags.states.add(state(row.id, "", 1));
        NativeUi.apply(flags);
        check(multiple.isSelected(0) && multiple.isSelected(1), "Native MULTIPLE selection preserved");
        List exclusive = new List("Một", Choice.EXCLUSIVE, new String[]{"A", "B"}, null);
        display.setCurrent(exclusive);
        NativeScreen x = shown.get();
        NativeScreen.Event exclusiveEvent = event(x, NativeScreen.Event.CHANGE, 0, 1);
        exclusiveEvent.states.add(state(x.items.get(0).id, "", 0));
        exclusiveEvent.states.add(state(x.items.get(1).id, "", 1));
        NativeUi.apply(exclusiveEvent);
        check(exclusive.getSelectedIndex() == 1 && !exclusive.isSelected(0), "Native EXCLUSIVE selection");
        Command back = new Command("Quay lại", Command.BACK, 1);
        exclusive.addCommand(back);
        exclusive.setCommandListener((c, screen) -> { if (c == back) display.setCurrent(list); });
        NativeUi.apply(event(shown.get(), NativeScreen.Event.BACK, 0, 2));
        check(display.getCurrent() == list && shown.get().epoch != menu.epoch, "Back returns with a fresh screen epoch");
        NativeUi.apply(open);
        check(callbacks.get() == 2, "Old event rejected even when same List is shown again");
        Canvas canvas = new Canvas() {
            protected void paint(Graphics g) { g.setColor(0x123456); g.fillRect(0, 0, getWidth(), getHeight()); }
        };
        display.setCurrent(canvas);
        check(shown.get().kind == NativeScreen.FRAMEBUFFER, "Canvas switches host out of native mode");
        check(platform.getLCD().getRGB(10, 10) == 0xFF123456, "Canvas pixels unchanged");
        int before = presentations.get();
        canvas.repaint(); canvas.repaint();
        check(presentations.get() == before, "No native snapshot work per Canvas frame");
        Command play = new Command("Play", Command.SCREEN, 1);
        Command stop = new Command("Stop", Command.SCREEN, 2);
        Command exit = new Command("Back", Command.BACK, 3);
        canvas.addCommand(play); canvas.addCommand(stop); canvas.addCommand(exit);
        AtomicInteger canvasCommands = new AtomicInteger();
        canvas.setCommandListener((c, s) -> {
            check(s == canvas && c == stop, "native menu invokes original Canvas command/listener");
            canvasCommands.incrementAndGet();
        });
        platform.keyPressed(Mobile.NOKIA_SOFT1);
        NativeScreen popup = shown.get();
        check(popup.kind == NativeScreen.COMMAND_MENU && popup.commands.size() == 2,
                "Canvas overflow is a native command packet; Back stays on right softkey");
        check(platform.getLCD().getRGB(10, 10) == 0xFF123456, "opening command popup never overwrites Canvas");
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        popup.write(new DataOutputStream(wire));
        check(NativeScreen.read(new DataInputStream(new ByteArrayInputStream(wire.toByteArray()))).kind
                == NativeScreen.COMMAND_MENU, "command popup survives host wire codec");
        NativeUi.apply(event(popup, NativeScreen.Event.COMMAND, command(popup, "Stop"), 1));
        check(canvasCommands.get() == 1 && shown.get().kind == NativeScreen.FRAMEBUFFER,
                "native command closes popup and retains Canvas");
        check(platform.getLCD().getRGB(10, 10) == 0xFF123456, "closing popup preserves Canvas pixels");
        NativeUi.apply(event(popup, NativeScreen.Event.COMMAND, command(popup, "Stop"), 2));
        check(canvasCommands.get() == 1, "closed menu rejects stale command");
        platform.keyPressed(Mobile.NOKIA_SOFT1);
        NativeUi.apply(event(shown.get(), NativeScreen.Event.BACK, 0, 3));
        check(shown.get().kind == NativeScreen.FRAMEBUFFER, "native Back closes popup without clearing Canvas");

        Alert loading = new Alert("Loading", "Preparing media", null, null);
        Gauge progress = new Gauge(null, false, 100, 10);
        loading.setIndicator(progress);
        display.setCurrent(loading, canvas);
        check(shown.get().kind == NativeScreen.ALERT, "Alert/loading uses Android presentation, not framebuffer");
        check(shown.get().items.get(0).text.equals("Preparing media"), "native loading message exported");
        progress.setValue(75);
        check(shown.get().items.get(1).value == 75, "loading progress invalidates native Alert");
        loading.setString("Ready");
        check(shown.get().items.get(0).text.equals("Ready"), "native loading message updates");
        wire.reset(); shown.get().write(new DataOutputStream(wire));
        check(NativeScreen.read(new DataInputStream(new ByteArrayInputStream(wire.toByteArray()))).kind
                == NativeScreen.ALERT, "Alert survives host wire codec");
        NativeUi.apply(event(shown.get(), NativeScreen.Event.COMMAND, shown.get().commands.get(0).id, 1));
        check(display.getCurrent() == canvas && shown.get().kind == NativeScreen.FRAMEBUFFER,
                "native Alert dismissal follows MIDP return screen");
        NativeUi.setPresenter(null);
        System.out.println("PASS: native LCDUI " + checks + " checks; atomic state, stale events, codec, List/Form, Canvas isolation");
    }
}
