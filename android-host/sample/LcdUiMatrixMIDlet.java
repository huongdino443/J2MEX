import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/** Manual coverage matrix for the native LCDUI presentation. */
public final class LcdUiMatrixMIDlet extends MIDlet
        implements CommandListener, ItemCommandListener, ItemStateListener {
    private final Display display = Display.getDisplay(this);
    private final Command back = new Command("Về menu", Command.BACK, 1);
    private final Command result = new Command("Kết quả", Command.OK, 0);
    private final Command exit = new Command("Thoát", Command.EXIT, 9);
    private final Command help = new Command("Hướng dẫn", Command.HELP, 2);
    private final Command itemAction = new Command("Kích hoạt", Command.ITEM, 1);

    private List home;
    private Displayable alertReturn;
    private StringItem itemStatus;
    private TextField anyField, numberField, decimalField, passwordField;
    private TextField emailField, urlField, phoneField, sensitiveField, readOnlyField;
    private TextField capsWordField, capsSentenceField;
    private ChoiceGroup exclusive, multiple, popup;
    private TextBox activeTextBox;
    private int canvasKey, canvasX = 18, canvasY = 58;

    private static final String[] TESTS = {
        "Hướng dẫn / phạm vi",
        "Form: TextField / constraints",
        "Form: StringItem / button",
        "Form: ChoiceGroup",
        "Form: ImageItem",
        "Form: Gauge / Spacer",
        "Form: DateField",
        "Form: CustomItem",
        "Form: cuộn / nhiều dòng",
        "List: IMPLICIT",
        "List: EXCLUSIVE",
        "List: MULTIPLE",
        "TextBox: nhiều dòng",
        "TextBox: số",
        "TextBox: mật khẩu",
        "Alert: các kiểu",
        "Alert: tự đóng",
        "Alert: progress",
        "Command: loại / thứ tự",
        "Canvas: hình / phím / lệnh"
    };

    public void startApp() {
        if (home == null) {
            home = new List("J2MEX LCDUI Matrix", Choice.IMPLICIT, TESTS, null);
            home.addCommand(help);
            home.addCommand(exit);
            home.setCommandListener(this);
        }
        display.setCurrent(home);
    }

    public void pauseApp() { }
    public void destroyApp(boolean unconditional) { }

    public void commandAction(Command command, Displayable screen) {
        if (command == exit) {
            notifyDestroyed();
            return;
        }
        if (command == back) {
            if (screen instanceof Alert && alertReturn != null) {
                display.setCurrent(alertReturn);
                alertReturn = null;
            } else {
                display.setCurrent(home);
            }
            return;
        }
        if (command == help) {
            showAlert("Hướng dẫn", "Mở từng mục trong menu. Kiểm tra cuộn, focus, phím điều hướng, bàn phím Android, lệnh và trạng thái chọn. Các nút ITEM có thể chạm hoặc nhấn giữ tùy cách hiển thị.", AlertType.INFO, screen);
            return;
        }
        if (command == List.SELECT_COMMAND && screen == home) {
            openTest(home.getSelectedIndex());
            return;
        }
        if (command == List.SELECT_COMMAND && screen instanceof List) {
            List list = (List)screen;
            showAlert("List IMPLICIT", "Đã chọn: " + list.getString(list.getSelectedIndex()), AlertType.INFO, list);
            return;
        }
        if (command == result) {
            showAlert("Kết quả", describeState(screen), AlertType.INFO, screen);
            return;
        }
        if (screen instanceof Alert) {
            showAlert("Command", "Đã nhận: " + command.getLabel()
                    + "\nType: " + commandTypeName(command.getCommandType())
                    + "\nPriority: " + command.getPriority(), AlertType.INFO, alertReturn);
            return;
        }
        showAlert("Command", "Đã nhận: " + command.getLabel()
                + "\nType: " + commandTypeName(command.getCommandType())
                + "\nPriority: " + command.getPriority(), AlertType.INFO, screen);
    }

    public void commandAction(Command command, Item item) {
        if (itemStatus != null) {
            itemStatus.setText("Đã kích hoạt: " + safe(item.getLabel())
                    + " — " + command.getLabel());
        }
    }

    public void itemStateChanged(Item item) {
        if (itemStatus != null) {
            itemStatus.setText("Đã đổi: " + safe(item.getLabel()));
        }
    }

    private void openTest(int index) {
        switch (index) {
            case 0:
                showAlert("Phạm vi kiểm thử",
                        "Form: TextField, StringItem, ImageItem, ChoiceGroup, Gauge, Spacer, cuộn.\n"
                        + "Màn hình: List, TextBox, Alert, Canvas.\n"
                        + "Cũng có ảnh, Unicode, command overflow, ITEM action và trạng thái chọn.\n"
                        + "Ticker được bỏ qua để tương thích v190: constructor của nó chưa public.\n"
                        + "Không dùng mạng, SMS hay dữ liệu bên ngoài.",
                        AlertType.INFO, home);
                break;
            case 1: openTextFields(); break;
            case 2: openStringItems(); break;
            case 3: openChoiceGroups(); break;
            case 4: openImageItems(); break;
            case 5: openGaugeAndSpacer(); break;
            case 6: openDateFields(); break;
            case 7: openCustomItem(); break;
            case 8: openLongForm(); break;
            case 9: openImplicitList(); break;
            case 10: openChoiceList(Choice.EXCLUSIVE, "EXCLUSIVE"); break;
            case 11: openChoiceList(Choice.MULTIPLE, "MULTIPLE"); break;
            case 12: openTextBox(false, false); break;
            case 13: openTextBox(true, false); break;
            case 14: openTextBox(false, true); break;
            case 15: openAlertTypes(); break;
            case 16: openTimedAlert(); break;
            case 17: openProgressAlert(); break;
            case 18: openCommandMatrix(); break;
            case 19: display.setCurrent(new CanvasMatrix()); break;
            default: break;
        }
    }

    private Form newForm(String title) {
        anyField = numberField = decimalField = passwordField = null;
        emailField = urlField = phoneField = sensitiveField = readOnlyField = null;
        capsWordField = capsSentenceField = null;
        exclusive = multiple = popup = null;
        Form form = new Form(title);
        form.addCommand(result);
        form.addCommand(back);
        form.addCommand(help);
        form.setCommandListener(this);
        form.setItemStateListener(this);
        itemStatus = new StringItem("Tương tác", "Chạm vào item hoặc thay đổi giá trị.");
        return form;
    }

    private void openTextFields() {
        Form form = newForm("TextField: constraints");
        anyField = new TextField("ANY / Unicode", "Xin chào, tiếng Việt", 128, TextField.ANY);
        numberField = new TextField("NUMERIC", "12345", 12, TextField.NUMERIC);
        decimalField = new TextField("DECIMAL", "12.50", 12, TextField.DECIMAL);
        passwordField = new TextField("PASSWORD", "matkhau", 32, TextField.PASSWORD);
        emailField = new TextField("EMAILADDR", "test@example.com", 64, TextField.EMAILADDR);
        urlField = new TextField("URL", "https://example.com", 128, TextField.URL);
        phoneField = new TextField("PHONENUMBER", "+84901234567", 20, TextField.PHONENUMBER);
        sensitiveField = new TextField("SENSITIVE / NON_PREDICTIVE", "Không gợi ý",
                64, TextField.ANY | TextField.SENSITIVE | TextField.NON_PREDICTIVE);
        capsWordField = new TextField("INITIAL_CAPS_WORD", "hello world", 64,
                TextField.ANY | TextField.INITIAL_CAPS_WORD);
        capsSentenceField = new TextField("INITIAL_CAPS_SENTENCE", "xin chào", 64,
                TextField.ANY | TextField.INITIAL_CAPS_SENTENCE);
        readOnlyField = new TextField("UNEDITABLE", "Không sửa được", 64,
                TextField.ANY | TextField.UNEDITABLE);
        form.append(new StringItem("Kiểm tra", "Kiểu bàn phím, focus, label dài, Unicode và nội dung sau khi nhập."));
        form.append(anyField);
        form.append(numberField);
        form.append(decimalField);
        form.append(passwordField);
        form.append(emailField);
        form.append(urlField);
        form.append(phoneField);
        form.append(sensitiveField);
        form.append(capsWordField);
        form.append(capsSentenceField);
        form.append(readOnlyField);
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openStringItems() {
        Form form = newForm("StringItem: appearance");
        StringItem plain = new StringItem("PLAIN", "Văn bản thường, có thể xuống nhiều dòng. Kiểm tra khoảng cách và chiều cao hàng.");
        StringItem hyperlink = new StringItem("HYPERLINK", "Mục liên kết — kích hoạt bằng ITEM command.", Item.HYPERLINK);
        StringItem button = new StringItem("BUTTON", "Nút StringItem — chạm để kích hoạt.", Item.BUTTON);
        StringItem longButton = new StringItem("BUTTON dài", "Nội dung nút dài để kiểm tra căn lề, xuống dòng và ellipsis.", Item.BUTTON);
        bindItem(hyperlink);
        bindItem(button);
        bindItem(longButton);
        form.append(plain);
        form.append(hyperlink);
        form.append(button);
        form.append(longButton);
        form.append(new StringItem("Ghi chú", "Thử chạm, nhấn giữ và dùng phím điều hướng nếu có."));
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openChoiceGroups() {
        Form form = newForm("ChoiceGroup: modes");
        Image[] colors = new Image[] {
                swatch(0x8E79C6, "P"), swatch(0x3277C5, "B"),
                swatch(0x38A56A, "G"), swatch(0xD44A54, "R"),
                swatch(0xE28B34, "O"), swatch(0xD65B9D, "K")
        };
        String[] colorNames = {"Purple", "Blue", "Green", "Red", "Orange", "Pink"};
        exclusive = new ChoiceGroup("EXCLUSIVE", Choice.EXCLUSIVE, colorNames, colors);
        multiple = new ChoiceGroup("MULTIPLE", Choice.MULTIPLE,
                new String[] {"Âm thanh", "Rung", "Thông báo", "Tự phát"}, null);
        popup = new ChoiceGroup("POPUP dropdown", Choice.POPUP, colorNames, colors);
        form.append(exclusive);
        form.append(multiple);
        form.append(popup);
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openImageItems() {
        Form form = newForm("ImageItem: appearance");
        Image badge = swatch(0x547CA8, "J");
        ImageItem plain = new ImageItem("PLAIN / CENTER", badge,
                Item.LAYOUT_CENTER | Item.LAYOUT_NEWLINE_BEFORE, "Ảnh mẫu màu xanh", Item.PLAIN);
        ImageItem link = new ImageItem("HYPERLINK", swatch(0x43A47A, "L"),
                Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE, "Ảnh liên kết", Item.HYPERLINK);
        ImageItem button = new ImageItem("BUTTON", swatch(0xB783D4, "B"),
                Item.LAYOUT_CENTER | Item.LAYOUT_NEWLINE_BEFORE, "Ảnh nút", Item.BUTTON);
        bindItem(link);
        bindItem(button);
        form.append(plain);
        form.append(link);
        form.append(button);
        form.append(new StringItem("Alt text", "Dùng trường hợp ảnh và alt text cùng xuất hiện."));
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openGaugeAndSpacer() {
        Form form = newForm("Gauge / Spacer");
        form.append(new StringItem("Gauge", "Thử kéo/đổi giá trị và kiểm tra focus."));
        form.append(new Gauge("Interactive 0–10", true, 10, 4));
        form.append(new Gauge("Determinate 35%", false, 100, 35));
        form.append(new Gauge("Indefinite running", false,
                Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
        form.append(new Gauge("Indefinite idle", false,
                Gauge.INDEFINITE, Gauge.CONTINUOUS_IDLE));
        form.append(new StringItem("Spacer trước", "Khoảng cách dưới đây:"));
        form.append(new Spacer(1, 24));
        form.append(new StringItem("Spacer sau", "So sánh khoảng cách hai hàng."));
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openDateFields() {
        Form form = newForm("DateField: modes");
        form.append(new StringItem("Kiểm tra", "Chạm vào ô để mở bộ chọn ngày hoặc giờ."));
        DateField date = new DateField("DATE", DateField.DATE);
        DateField time = new DateField("TIME", DateField.TIME);
        DateField dateTime = new DateField("DATE_TIME", DateField.DATE_TIME);
        java.util.Date now = new java.util.Date();
        date.setDate(now);
        time.setDate(now);
        dateTime.setDate(now);
        form.append(date);
        form.append(time);
        form.append(dateTime);
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openCustomItem() {
        Form form = newForm("CustomItem");
        form.append(new StringItem("Kiểm tra", "Nếu CustomItem chưa được hỗ trợ, ghi lại nội dung fallback hoặc lỗi hiển thị."));
        form.append(new CustomItem("Mục vẽ tùy chỉnh") {
            protected int getMinContentHeight() { return 52; }
            protected int getMinContentWidth() { return 120; }
            protected int getPrefContentHeight(int width) { return 72; }
            protected int getPrefContentWidth(int height) { return 180; }
            protected void paint(Graphics g, int width, int height) {
                g.setColor(0x25364B);
                g.fillRect(0, 0, width, height);
                g.setColor(0xB7C1FF);
                g.drawRect(0, 0, Math.max(1, width - 1), Math.max(1, height - 1));
                g.setColor(0xFFFFFF);
                g.drawString("CustomItem", 8, 8, Graphics.LEFT | Graphics.TOP);
            }
        });
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openLongForm() {
        Form form = newForm("Form: cuộn / nội dung");
        form.append(new StringItem("Đoạn văn", "Đây là một đoạn văn dài để kiểm tra cách xuống dòng trong Form native. Nội dung có dấu tiếng Việt: ă â đ ê ô ơ ư; ký tự đặc biệt: & < > — …"));
        StringItem bold = new StringItem("Font đậm", "Bold sample");
        bold.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        StringItem italic = new StringItem("Font nghiêng", "Italic sample");
        italic.setFont(Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_ITALIC, Font.SIZE_MEDIUM));
        StringItem large = new StringItem("Font lớn", "Large sample");
        large.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE));
        form.append(bold);
        form.append(italic);
        form.append(large);
        form.append(new TextField("Tên", "Nguyễn Ánh", 64, TextField.ANY));
        form.append(new StringItem("Hàng 1", "Dòng ngắn"));
        form.append(new StringItem("Hàng 2", "Nội dung dài hơn để xem giới hạn chiều rộng và ellipsis."));
        form.append(new ChoiceGroup("Một lựa chọn", Choice.EXCLUSIVE,
                new String[] {"Lựa chọn thứ nhất", "Lựa chọn thứ hai"}, null));
        form.append(new StringItem("Hàng cuối", "Cuộn xuống cuối rồi cuộn lên."));
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void openImplicitList() {
        List list = new List("List: IMPLICIT", Choice.IMPLICIT,
                new String[] {"Một dòng", "Tên dài để kiểm tra ellipsis", "Unicode: Tiếng Việt",
                        "Có ảnh", "Chọn hàng để xem Alert"}, new Image[] {
                        null, null, null, swatch(0x397CB3, "I"), null
                });
        list.addCommand(back);
        list.addCommand(help);
        list.setCommandListener(this);
        display.setCurrent(list);
    }

    private void openChoiceList(int type, String name) {
        List list = new List("List: " + name, type,
                new String[] {"Mục một", "Mục hai", "Mục ba", "Tên mục dài để thử ellipsis",
                        "Unicode: ă â đ ê ô ơ ư"}, null);
        list.addCommand(result);
        list.addCommand(back);
        list.addCommand(help);
        list.setCommandListener(this);
        display.setCurrent(list);
    }

    private void openTextBox(boolean numeric, boolean password) {
        int constraints = numeric ? TextField.NUMERIC
                : password ? TextField.PASSWORD : TextField.ANY;
        String value = numeric ? "1234567890"
                : password ? "secret-123" : "Nhập tiếng Việt ở đây.\nDòng thứ hai để thử cuộn và resize khi bàn phím đóng.";
        activeTextBox = new TextBox(numeric ? "TextBox: số"
                : password ? "TextBox: mật khẩu" : "TextBox: nhiều dòng",
                value, numeric ? 24 : 512, constraints);
        activeTextBox.addCommand(result);
        activeTextBox.addCommand(back);
        activeTextBox.addCommand(help);
        activeTextBox.setCommandListener(this);
        display.setCurrent(activeTextBox);
    }

    private void openAlertTypes() {
        List list = new List("AlertType", Choice.IMPLICIT,
                new String[] {"INFO", "WARNING", "ERROR", "ALARM", "CONFIRMATION"}, null);
        list.addCommand(back);
        list.setCommandListener(new AlertTypeListener());
        display.setCurrent(list);
    }

    private final class AlertTypeListener implements CommandListener {
        public void commandAction(Command command, Displayable screen) {
            if (command == back) {
                display.setCurrent(home);
                return;
            }
            if (command != List.SELECT_COMMAND) return;
            int selected = ((List)screen).getSelectedIndex();
            AlertType[] types = {AlertType.INFO, AlertType.WARNING, AlertType.ERROR,
                    AlertType.ALARM, AlertType.CONFIRMATION};
            String[] names = {"INFO", "WARNING", "ERROR", "ALARM", "CONFIRMATION"};
            showAlert(names[selected], "Đây là AlertType " + names[selected]
                    + ". Chạm Về menu để đóng.", types[selected], screen);
        }
    }

    private void openTimedAlert() {
        Alert alert = new Alert("Alert tự đóng", "Alert này tự đóng sau 3 giây.", null, AlertType.INFO);
        alert.setTimeout(3000);
        display.setCurrent(alert, home);
    }

    private void openProgressAlert() {
        Alert alert = new Alert("Alert / progress", "Alert có Gauge indicator.", null, AlertType.INFO);
        alert.setIndicator(new Gauge(null, false, 100, 42));
        alert.setTimeout(Alert.FOREVER);
        alert.addCommand(back);
        alert.addCommand(help);
        alert.setCommandListener(this);
        alertReturn = home;
        display.setCurrent(alert);
    }

    private void openCommandMatrix() {
        Form form = newForm("Command: loại / thứ tự");
        form.append(new StringItem("Thử", "Quan sát hai nút chính và menu overflow. Chạm từng command để xem type/priority."));
        form.addCommand(new Command("SCREEN", Command.SCREEN, 3));
        form.addCommand(new Command("CANCEL", Command.CANCEL, 4));
        form.addCommand(new Command("STOP", Command.STOP, 5));
        form.addCommand(new Command("ITEM", Command.ITEM, 6));
        form.addCommand(new Command("Tùy chọn", Command.HELP, 7));
        form.append(itemStatus);
        display.setCurrent(form);
    }

    private void showAlert(String title, String message, AlertType type, Displayable returnTo) {
        Alert alert = new Alert(title, message, null, type);
        alert.setTimeout(Alert.FOREVER);
        alert.addCommand(back);
        alert.addCommand(help);
        alert.setCommandListener(this);
        alertReturn = returnTo;
        display.setCurrent(alert);
    }

    private String describeState(Displayable screen) {
        if (screen instanceof TextBox) return "TextBox:\n" + ((TextBox)screen).getString();
        if (screen instanceof List) return describeList((List)screen);
        if (screen instanceof Form) return describeForm();
        return "Màn hình: " + screen.getTitle();
    }

    private String describeList(List list) {
        boolean[] selected = new boolean[list.size()];
        list.getSelectedFlags(selected);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) out.append("• ").append(list.getString(i)).append('\n');
        }
        return out.length() == 0 ? "Chưa chọn mục nào." : out.toString();
    }

    private String describeForm() {
        StringBuilder out = new StringBuilder();
        if (anyField != null) out.append("ANY: ").append(anyField.getString()).append('\n');
        if (numberField != null) out.append("NUMERIC: ").append(numberField.getString()).append('\n');
        if (decimalField != null) out.append("DECIMAL: ").append(decimalField.getString()).append('\n');
        if (passwordField != null) out.append("PASSWORD: ").append(passwordField.getString()).append('\n');
        if (emailField != null) out.append("EMAIL: ").append(emailField.getString()).append('\n');
        if (urlField != null) out.append("URL: ").append(urlField.getString()).append('\n');
        if (phoneField != null) out.append("PHONE: ").append(phoneField.getString()).append('\n');
        if (sensitiveField != null) out.append("SENSITIVE: ").append(sensitiveField.getString()).append('\n');
        if (capsWordField != null) out.append("CAPS_WORD: ").append(capsWordField.getString()).append('\n');
        if (capsSentenceField != null) out.append("CAPS_SENTENCE: ").append(capsSentenceField.getString()).append('\n');
        if (readOnlyField != null) out.append("UNEDITABLE: ").append(readOnlyField.getString()).append('\n');
        appendChoice(out, "EXCLUSIVE", exclusive);
        appendChoice(out, "MULTIPLE", multiple);
        appendChoice(out, "POPUP", popup);
        return out.length() == 0 ? "Form đã gửi command Kết quả." : out.toString();
    }

    private void appendChoice(StringBuilder out, String label, ChoiceGroup choice) {
        if (choice == null) return;
        out.append(label).append(": ");
        boolean[] selected = new boolean[choice.size()];
        choice.getSelectedFlags(selected);
        boolean any = false;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                if (any) out.append(", ");
                out.append(choice.getString(i));
                any = true;
            }
        }
        if (!any) out.append("(none)");
        out.append('\n');
    }

    private void bindItem(Item item) {
        item.setDefaultCommand(itemAction);
        item.setItemCommandListener(this);
    }

    private Image swatch(int color, String text) {
        Image image = Image.createImage(40, 40);
        Graphics g = image.getGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 40, 40);
        g.setColor(0xFFFFFF);
        g.drawRect(1, 1, 37, 37);
        g.drawString(text, 20, 11, Graphics.HCENTER | Graphics.TOP);
        return image;
    }

    private String commandTypeName(int type) {
        switch (type) {
            case Command.SCREEN: return "SCREEN";
            case Command.BACK: return "BACK";
            case Command.CANCEL: return "CANCEL";
            case Command.OK: return "OK";
            case Command.HELP: return "HELP";
            case Command.STOP: return "STOP";
            case Command.EXIT: return "EXIT";
            case Command.ITEM: return "ITEM";
            default: return "UNKNOWN(" + type + ")";
        }
    }

    private String safe(String value) {
        return value == null || value.length() == 0 ? "(không nhãn)" : value;
    }

    private final class CanvasMatrix extends Canvas {
        CanvasMatrix() {
            addCommand(back);
            addCommand(help);
            addCommand(new Command("Kết quả", Command.OK, 0));
            setCommandListener(LcdUiMatrixMIDlet.this);
        }

        protected void paint(Graphics g) {
            g.setColor(0x172434);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xB7C1FF);
            g.drawRect(8, 8, Math.max(1, getWidth() - 17), Math.max(1, getHeight() - 17));
            g.setColor(0xFFFFFF);
            g.drawString("LCDUI Canvas", 16, 18, Graphics.LEFT | Graphics.TOP);
            g.drawString("Phím: " + canvasKey, 16, 42, Graphics.LEFT | Graphics.TOP);
            g.drawString("Chạm: " + canvasX + ", " + canvasY, 16, 62, Graphics.LEFT | Graphics.TOP);
            g.setColor(0x43A47A);
            g.fillArc(canvasX, canvasY, 32, 32, 0, 360);
            g.setColor(0xFFFFFF);
            g.drawString("Kiểm tra command bar và giữ nguyên Canvas.", 16, 108, Graphics.LEFT | Graphics.TOP);
        }

        public void keyPressed(int code) {
            canvasKey = code;
            repaint();
        }

        public void pointerPressed(int x, int y) {
            canvasX = x;
            canvasY = y;
            repaint();
        }
    }
}
