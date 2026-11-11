import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/** On-device tests use only this MIDlet; no third-party game is bundled. */
public final class LcdUiDiagnosticMIDlet extends MIDlet implements CommandListener {
    private final Display display = Display.getDisplay(this);
    private final Command back = new Command("Về menu", Command.BACK, 1);
    private final Command result = new Command("Kết quả", Command.OK, 0);
    private final Command exit = new Command("Thoát test", Command.EXIT, 9);
    private List menu;
    private Form form;
    private TextField name;
    private TextBox editor;

    public void startApp() {
        if (menu == null) {
            Image[] icons = new Image[7];
            for (int i = 0; i < icons.length; i++) {
                icons[i] = Image.createImage(48, 48);
                Graphics g = icons[i].getGraphics();
                g.setColor(0x205080 + i * 0x101008); g.fillRect(0, 0, 48, 48);
                g.setColor(0xFFFFFF); g.drawString(Integer.toString(i + 1), 20, 16, Graphics.LEFT | Graphics.TOP);
            }
            menu = new List("Kiểm thử LCDUI v11", Choice.IMPLICIT, new String[]{
                "Form / TextField / ChoiceGroup", "TextBox + bàn phím Android",
                "List EXCLUSIVE", "List MULTIPLE", "Alert đóng bằng Command",
                "Alert tự đóng sau 2 giây", "Canvas: kiểm tra hình / phím"
            }, icons);
            menu.addCommand(exit);
            menu.setCommandListener(this);
        }
        display.setCurrent(menu);
    }
    public void pauseApp() { }
    public void destroyApp(boolean unconditional) { }
    public void commandAction(Command command, Displayable screen) {
        if (command == exit) { notifyDestroyed(); return; }
        if (command == back) { display.setCurrent(menu); return; }
        if (screen == menu && command == List.SELECT_COMMAND) {
            switch (menu.getSelectedIndex()) {
                case 0:
                    name = new TextField("Tên / Unicode", "Xin chào", 128, TextField.ANY);
                    form = new Form("Form", new Item[]{
                        new StringItem("Hướng dẫn", "Form native: chạm trực tiếp vào ô nhập và lựa chọn. Kết quả nhận đủ nội dung đang nhập, không cần hộp thoại Xong."),
                        name, new TextField("Số", "123", 16, TextField.NUMERIC),
                        new ChoiceGroup("Lựa chọn", Choice.MULTIPLE, new String[]{"Một", "Hai", "Ba"}, null),
                        new Gauge("Thanh giá trị", true, 10, 4)
                    });
                    form.addCommand(result); form.addCommand(back);
                    form.setCommandListener(this); display.setCurrent(form); return;
                case 1:
                    editor = new TextBox("TextBox", "Nhập tiếng Việt ở đây", 512, TextField.ANY);
                    editor.addCommand(result); editor.addCommand(back);
                    editor.setCommandListener(this); display.setCurrent(editor); return;
                case 2: case 3:
                    List choices = new List("List " + (menu.getSelectedIndex() == 2 ? "EXCLUSIVE" : "MULTIPLE"),
                            menu.getSelectedIndex() == 2 ? Choice.EXCLUSIVE : Choice.MULTIPLE,
                            new String[]{"Mục một", "Mục hai", "Mục ba", "Mục bốn"}, null);
                    choices.addCommand(result); choices.addCommand(back);
                    choices.setCommandListener(this); display.setCurrent(choices); return;
                case 4:
                    display.setCurrent(new Alert("Alert", "OK hoặc Android Back phải quay lại menu.", null, AlertType.INFO), menu);
                    return;
                case 5:
                    Alert timed = new Alert("Alert timeout", "Tự trở về menu sau hai giây.", null, AlertType.INFO);
                    timed.setTimeout(2000); display.setCurrent(timed, menu); return;
                case 6: display.setCurrent(new CanvasTest()); return;
                default: return;
            }
        }
        if (command == result) {
            String text = screen == form ? "Tên: " + name.getString()
                    : screen == editor ? editor.getString() : listResult((List)screen);
            display.setCurrent(new Alert("Kết quả", text, null, AlertType.INFO), screen);
        }
    }
    private String listResult(List list) {
        boolean[] flags = new boolean[list.size()];
        list.getSelectedFlags(flags);
        StringBuilder selected = new StringBuilder();
        for (int i = 0; i < flags.length; i++) if (flags[i]) selected.append(list.getString(i)).append("\n");
        return selected.length() == 0 ? "Chưa chọn mục nào." : selected.toString();
    }
    private final class CanvasTest extends Canvas {
        private Timer timer;
        private int frames, key;
        protected void paint(Graphics g) {
            g.setColor(0x152B3C); g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0x66DD88); g.fillRect((frames * 3) % Math.max(1, getWidth() - 20), 70, 20, 20);
            g.setColor(0xFFFFFF);
            g.drawString("Canvas nguyên bản", 6, 10, Graphics.LEFT | Graphics.TOP);
            g.drawString("Frame " + frames + " / key " + key, 6, 34, Graphics.LEFT | Graphics.TOP);
            g.drawString("Phải mềm: về menu", 6, 110, Graphics.LEFT | Graphics.TOP);
        }
        public void showNotify() {
            timer = new Timer(true);
            timer.schedule(new TimerTask() {
                public void run() { frames++; repaint(); }
            }, 0, 33);
        }
        public void hideNotify() { if (timer != null) timer.cancel(); }
        public void keyPressed(int code) {
            key = code;
            if (code == -7) display.setCurrent(menu);
            else repaint();
        }
    }
}
