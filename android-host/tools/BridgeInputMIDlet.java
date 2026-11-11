import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/** Deterministic fixture for the Android/headless input wire protocol. */
public final class BridgeInputMIDlet extends MIDlet {
    protected void startApp() {
        Display display = Display.getDisplay(this);
        List menu = new List("Native menu", Choice.IMPLICIT, new String[]{"Unused", "Open editor"}, null);
        menu.setCommandListener((command, screen) -> {
            if (menu.getSelectedIndex() != 1) throw new AssertionError("Native list selection mismatch");
            showEditor(display);
        });
        display.setCurrent(menu);
    }
    private void showEditor(Display display) {
        TextBox box = new TextBox("Bridge input", "", 128, TextField.ANY);
        Command save = new Command("Save", Command.OK, 0);
        Command cancel = new Command("Cancel", Command.CANCEL, 1);
        box.addCommand(save);
        box.addCommand(cancel);
        box.setCommandListener((command, screen) -> {
            if (command != save) throw new AssertionError("The default input action must be Save");
            if (!box.getString().equals("Tiếng Việt ✓")) throw new AssertionError("TextBox input mismatch");
            System.out.println("PASS_BRIDGE_TEXTBOX_SAVED_BEFORE_COMMAND");
            TextField field = new TextField("Field", "", 128, TextField.ANY);
            StringItem actionItem = new StringItem("", "Choose language", Item.BUTTON);
            actionItem.setDefaultCommand(new Command("Choose", Command.ITEM, 0));
            actionItem.setItemCommandListener((itemCommand, item) ->
                    System.out.println("PASS_STRING_ITEM_BUTTON_ACTION"));
            Form form = new Form("Form", new Item[]{field, actionItem});
            form.setItemStateListener(item -> {
                if (!field.getString().equals("Form Unicode ✓")) throw new AssertionError("Form input mismatch");
                System.out.println("PASS_BRIDGE_FORM");
                Canvas canvas = new Canvas() {
                    protected void paint(Graphics g) {
                        g.setColor(0x336699); g.fillRect(0, 0, getWidth(), getHeight());
                    }
                    public void pointerPressed(int x, int y) {
                        if (x == 11 && y == 19) throw new NullPointerException("synthetic pointer failure");
                    }
                    public void keyPressed(int keyCode) {
                        if (keyCode == 77) System.out.println("PASS_POINTER_CALLBACK_SURVIVED");
                    }
                };
                Alert alert = new Alert("Timed", "Returning to Canvas", null, AlertType.INFO);
                alert.setTimeout(100);
                display.setCurrent(alert, canvas);
            });
            display.setCurrent(form);
        });
        display.setCurrent(box);
    }
    protected void pauseApp() { }
    protected void destroyApp(boolean unconditional) { }
}
