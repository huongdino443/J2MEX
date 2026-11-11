import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.*;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** Exercises high-level LCDUI without Android or a third-party game JAR. */
public final class LcdUiSmoke {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static final class Keyboard implements MobilePlatform.TextInputHandler {
        int id, requests, dismissals, constraints;
        String value;
        public void request(int requestId, String title, String text, int max, int flags) {
            id = requestId; value = text; constraints = flags; requests++;
        }
        public void dismiss() { dismissals++; }
    }
    private static final class TestCanvas extends Canvas {
        int paints, presses;
        protected void paint(Graphics g) {
            paints++;
            g.setColor(0x125634); g.fillRect(0, 0, getWidth(), getHeight());
        }
        public void keyPressed(int key) { presses++; }
    }
    public static void main(String[] args) throws Exception {
        MobilePlatform platform = new MobilePlatform(240, 320);
        Mobile.setPlatform(platform);
        Display display = new Display();
        Keyboard keyboard = new Keyboard();
        platform.setTextInputHandler(keyboard);
        AtomicInteger states = new AtomicInteger();
        TextField field = new TextField("Name", null, 32, TextField.ANY);
        ChoiceGroup choices = new ChoiceGroup("Modes", Choice.MULTIPLE,
                new String[]{"One", "Two"}, null);
        Form form = new Form("LCDUI", new Item[]{
                new StringItem("Status", "A wrapped string that must render without a desktop window."),
                field, choices, new Gauge("Progress", true, 10, 4)});
        form.setItemStateListener(item -> states.incrementAndGet());
        check(form.getWidth() == 240 && form.getHeight() == 320, "Form dimensions");
        display.setCurrent(form);
        check(form.isShown(), "Form isShown");
        check(form.platformImage != null, "Form surface");
        field.setString("programmatic");
        check(states.get() == 0, "Programmatic field update is not a user event");
        display.setCurrentItem(field);
        platform.keyPressed(Mobile.NOKIA_SOFT3);
        check(keyboard.requests == 1 && keyboard.value.equals("programmatic"), "Form opens keyboard");
        int formRequest = keyboard.id;
        platform.updateTextInput(formRequest, "Tiếng Việt ✓");
        check(field.getString().equals("Tiếng Việt ✓") && states.get() == 1, "Form accepts Unicode and notifies");
        field.delete(0, 1);
        check(field.getCaretPosition() == 0, "TextField.delete is public and moves caret");
        boolean[] flags = new boolean[3];
        check(choices.getSelectedFlags(flags) == 0, "MULTIPLE starts unselected");
        choices.setSelectedFlags(new boolean[]{true, true});
        check(choices.getSelectedFlags(flags) == 2 && !flags[2], "ChoiceGroup flags");
        check(choices.getSelectedIndex() == -1, "MULTIPLE selected index");
        check(((Gauge)form.get(3)).getValue() == 4, "Gauge initial value");
        AtomicInteger commandCalls = new AtomicInteger();
        Command back = new Command("Back", Command.BACK, 1);
        Command save = new Command("Save", Command.OK, 0);
        form.addCommand(back); form.addCommand(save); form.addCommand(save);
        check(form.getCommands().size() == 2, "No duplicate commands");
        form.setCommandListener((command, owner) -> {
            if (command == save) commandCalls.addAndGet(1);
            if (command == back) commandCalls.addAndGet(10);
        });
        form.keyPressed(Mobile.NOKIA_SOFT1);
        check(commandCalls.get() == 1, "Priority-aware left command");
        platform.handleBackPressed();
        check(commandCalls.get() == 11, "Android Back invokes BACK command");
        form.addCommand(new Command("Help", Command.HELP, 2));
        form.keyPressed(Mobile.NOKIA_SOFT1);
        form.keyPressed(Mobile.NOKIA_SOFT3);
        check(commandCalls.get() == 12, "Options menu dispatch");
        new File("build/lcdui-test").mkdirs();
        ImageIO.write(platform.getLCD(), "png", new File("build/lcdui-test/form.png"));

        TextBox box = new TextBox("Editor", null, 12, TextField.ANY);
        display.setCurrent(box);
        int boxRequest = keyboard.id;
        check(!form.isShown() && box.isShown(), "Display transition updates isShown");
        platform.updateTextInput(boxRequest, "Xin chào");
        check(box.getString().equals("Xin chào"), "TextBox receives keyboard text");
        box.insert("!", box.size()); box.delete(0, 1);
        check(box.getString().equals("in chào!"), "TextBox insert/delete");
        boolean rejected = false;
        try { box.setString("abcdefghijklmnop"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "TextBox rejects text larger than max size");
        box.setString("abcdefghijkl");
        char[] text = new char[12];
        check(box.getChars(text) == 12, "TextBox chars");
        box.addCommand(save); box.addCommand(back);
        box.addCommand(new Command("Help", Command.HELP, 2));
        box.setCommandListener((command, owner) -> commandCalls.incrementAndGet());
        box.keyPressed(Mobile.NOKIA_SOFT1);
        box.keyPressed(Mobile.NOKIA_SOFT3);
        check(commandCalls.get() == 13, "TextBox command menu remains usable");
        ImageIO.write(platform.getLCD(), "png", new File("build/lcdui-test/textbox.png"));
        display.setCurrent(form);
        platform.updateTextInput(boxRequest, "stale");
        check(!box.getString().equals("stale"), "Stale keyboard results ignored");

        List list = new List("Choices", Choice.IMPLICIT, new String[]{"Alpha", "Beta"}, null);
        list.insert(0, "First", null);
        check(list.getString(0).equals("First") && list.getImage(0) == null, "List insert and null image");
        list.addCommand(back);
        list.setCommandListener((command, owner) -> {
            if (command == List.SELECT_COMMAND) commandCalls.incrementAndGet();
        });
        display.setCurrent(list);
        list.setSelectedIndex(0, true);
        list.keyPressed(Mobile.NOKIA_DOWN);
        check(list.getSelectedIndex() == 1, "Implicit focus tracks selected index");
        list.keyPressed(Mobile.NOKIA_SOFT1);
        check(commandCalls.get() == 14, "Implicit Select with BACK command");
        List multiple = new List("Multiple", Choice.MULTIPLE, new String[]{"One", "Two"}, null);
        multiple.setSelectedFlags(new boolean[]{true, true});
        check(multiple.getSelectedFlags(flags) == 2 && multiple.getSelectedIndex() == -1, "List multiple flags");
        List other = new List("Other", Choice.IMPLICIT);
        other.setSelectCommand(new Command("Other select", Command.OK, 0));
        check(list.getSoftkeyLabel(true).equals("Select"), "Select command is per instance");
        ImageIO.write(platform.getLCD(), "png", new File("build/lcdui-test/list.png"));

        Alert alert = new Alert("Notice", "This alert must return to the previous screen.", null, AlertType.INFO);
        display.setCurrent(alert, form);
        ImageIO.write(platform.getLCD(), "png", new File("build/lcdui-test/alert.png"));
        alert.keyPressed(Mobile.NOKIA_SOFT1);
        check(display.getCurrent() == form, "Alert default dismiss restores next screen");
        Alert timed = new Alert("Timed");
        timed.setTimeout(30);
        display.setCurrent(timed, form);
        long deadline = System.currentTimeMillis() + 1000;
        while (display.getCurrent() != form && System.currentTimeMillis() < deadline) Thread.sleep(10);
        check(display.getCurrent() == form, "Alert timeout restores next screen");
        TextBox readonly = new TextBox("Read only", "Fixed", 20, TextField.UNEDITABLE);
        int requests = keyboard.requests;
        display.setCurrent(readonly);
        check(keyboard.requests == requests, "UNEDITABLE does not open keyboard");

        TextBox reentrant = new TextBox("Follow-up editor", "", 32, TextField.ANY);
        form.setItemStateListener(item -> display.setCurrent(reentrant));
        display.setCurrent(form);
        display.setCurrentItem(field);
        form.keyPressed(Mobile.NOKIA_SOFT3);
        platform.updateTextInput(keyboard.id, "new field");
        int followUpRequest = keyboard.id;
        platform.updateTextInput(followUpRequest, "next editor");
        check(reentrant.getString().equals("next editor"), "Listener can open a new keyboard request safely");

        TestCanvas canvas = new TestCanvas();
        display.setCurrent(canvas);
        check(canvas.paints > 0 && platform.getLCD().getRGB(10, 10) == 0xFF125634, "Canvas renderer unchanged");
        AtomicInteger softkeys = new AtomicInteger();
        Command run = new Command("Run", Command.OK, 0);
        Command backCommand = new Command("Back", Command.BACK, 1);
        canvas.addCommand(run); canvas.addCommand(backCommand);
        canvas.setCommandListener((command, owner) -> softkeys.addAndGet(command == run ? 1 : 10));
        platform.keyPressed(Mobile.NOKIA_SOFT1);
        platform.keyPressed(Mobile.NOKIA_SOFT2);
        check(softkeys.get() == 11, "left and right softkeys dispatch Canvas LCDUI Commands");
        platform.keyPressed(Mobile.NOKIA_SOFT3);
        check(canvas.presses == 1 && !platform.handleBackPressed(), "Canvas key and Back path unchanged");
        CountDownLatch serial = new CountDownLatch(1);
        display.callSerially(serial::countDown);
        check(serial.await(1, TimeUnit.SECONDS), "callSerially works");

        int canvasPixel = platform.getLCD().getRGB(10, 10);
        int priorRequests = keyboard.requests;
        NativeUi.setPresenter(screen -> { });
        TextBox nativeBox = new TextBox("Native input", "", 32, TextField.ANY);
        display.setCurrent(nativeBox);
        check(keyboard.requests == priorRequests + 1, "native TextBox still opens the Android editor");
        check(platform.getLCD().getRGB(10, 10) == canvasPixel,
                "editable native TextBox does not replace Canvas with a software LCDUI frame");
        nativeBox.setString("updated");
        check(platform.getLCD().getRGB(10, 10) == canvasPixel,
                "native TextBox edits do not repaint the framebuffer");

        NativeUi.setPresenter(null);
        TextBox fallbackBox = new TextBox("Software fallback", "", 32, TextField.ANY);
        display.setCurrent(fallbackBox);
        check(platform.getLCD().getRGB(10, 10) != canvasPixel,
                "software TextBox fallback remains available without a native presenter");
        System.out.println("PASS: LCDUI " + checks + " checks; Form/TextBox/List/Alert/Command, keyboard tokens, Canvas");
    }
}
