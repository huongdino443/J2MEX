# FreeJ2ME Lite — kiểm tra LCDUI và bản thử nghiệm v10

## 1. Kết quả kiểm tra trước khi sửa

Các lớp đã có trong FreeJ2ME, nhưng không có nghĩa các API đã hoạt động đầy đủ.

| Thành phần | Vấn đề tìm thấy |
|---|---|
| `Display` | Gọi `showNotify()` trước khi gán màn hình hiện tại; không gọi `hideNotify()` màn hình cũ; `setCurrentItem()` chỉ ghi log; trả màn hình sau Alert chưa đúng. |
| `Displayable` | `isShown()` luôn trả `true`; command trùng không bị loại; điều hướng menu command thiếu nhất quán; chưa có bố cục Form hợp lý. |
| `Form` | `getWidth()/getHeight()` cố định 64×128; chỉ vẽ sơ sài label/text; không có nhập TextField; Form rỗng không xử lý softkey. |
| `TextBox` | Chỉ lưu chuỗi, chưa tạo surface, vẽ nội dung hay nhận input; title/ticker riêng che trường của lớp cha; caret luôn bằng 0. |
| `List` | `getString()/getImage()` ép kiểu không đúng với loại item; selected flags là stub; insert vị trí 0 sai; select command bị dùng chung giữa mọi List. |
| `Alert` | Chưa có surface/render; listener mặc định quay lại chính Alert thay vì màn hình đích; timeout chưa chạy. |
| `Command` | Chưa dùng priority khi bố trí; softkey/Android Back chưa được nối vào hành vi LCDUI đúng; label null gây lỗi. |
| Phụ thuộc Form | Danh sách command trong `Item` chưa khởi tạo; `TextField.delete()` không public; `ChoiceGroup.append()` và các hàm lựa chọn là stub; Gauge bỏ qua giá trị khởi tạo. |

## 2. Phạm vi đã bổ sung trong v10

- `Display`: chuyển màn hình, `showNotify/hideNotify`, `isShown`, `setCurrentItem`
  và hàng đợi `callSerially`.
- `Form`: text/image, TextField, ChoiceGroup và Gauge cơ bản; focus bằng D-pad,
  kích hoạt bằng OK, chạm chọn item và callback `ItemStateListener`.
- `TextBox`: framebuffer LCDUI, getter/setter chuỗi, chèn/xóa, caret, giới hạn
  maxSize, cuộn văn bản và editor Android.
- `List`: IMPLICIT/EXCLUSIVE/MULTIPLE, selected flags, insert/delete, ảnh tùy chọn,
  select command theo từng instance, điều hướng và chọn bằng phím/chạm.
- `Alert`: hiển thị title/text/image/indicator, dismiss về màn hình đích, Command
  tùy chỉnh và timeout.
- `Command`: không thêm trùng cùng đối tượng, sắp theo priority, softkey/menu
  Options và route BACK/CANCEL.

### Bàn phím Android

TextBox mở editor Android khi được hiển thị; TextField trong Form mở editor khi
được chọn rồi nhấn OK hoặc chạm. Editor dùng IME Android, hỗ trợ văn bản Unicode,
maxSize, bàn phím số/thập phân/điện thoại/email/URL, password và UNEDITABLE.

- **Xong** hoặc IME Done: nhận văn bản về MIDlet.
- **Hủy** hoặc Back trong editor: không áp dụng phần đang nhập.
- Xong **không tự gọi Command của MIDlet**. Sau khi đóng editor, dùng softkey của
  game để Lưu/OK/Quay lại theo chính logic của game.
- Kết quả nhập được gắn với một yêu cầu và màn hình/field cụ thể. Kết quả cũ sau
  khi đổi màn hình bị bỏ qua; không ghi nội dung người dùng nhập vào log.

### Điều hướng và ranh giới Canvas

- Android Back trên màn hình LCDUI gọi Command BACK/CANCEL nếu có; Alert mặc định
  trở về màn hình đích. Nếu một Screen không có lệnh quay lại thì Back được tiêu
  thụ, không tự chọn một lệnh khác có thể gây mất dữ liệu.
- Android Back khi đang ở Canvas giữ hành vi cũ là yêu cầu thoát MIDlet.
- Không sửa `Canvas.java`, `GameCanvas`, `FrameView` hoặc vòng gửi framebuffer.
  LCDUI chỉ render khi nội dung/sự kiện thay đổi; không thêm bộ vẽ UI vào mỗi
  frame game. Chưa có đo FPS trên điện thoại để khẳng định hiệu năng thực tế.

## 3. Kiểm thử

- Smoke test Java kiểm tra model, Command, Form/TextBox/List/Alert, token nhập,
  Unicode, chuyển màn hình và hình/pím Canvas.
- Test giao thức chạy **HeadlessMain thật** qua loopback: yêu cầu keyboard,
  nhận Unicode, bỏ token cũ, nhận TextField, timeout Alert, trở lại Canvas và Back.
- Build APK và kiểm tra Android lint riêng.
- Ảnh framebuffer test được lưu trong `build/lcdui-test/` để xem bố cục.
- Chưa có thiết bị Android nối vào môi trường; việc IME bật/ẩn trên máy thật và
  hồi quy game trên v10 vẫn cần xác nhận.

## 4. Cách thử trên điện thoại

1. Theo quy trình cài sạch: sao lưu dữ liệu cần giữ trước khi gỡ bản cũ. Gỡ app
   xóa JAR nhập vào và RMS/lưu game trong dữ liệu riêng của app.
2. Mở app, chờ chuẩn bị runtime lần đầu.
3. Chọn **Kiểm thử LCDUI / bàn phím**.
4. Thử Form/TextField, TextBox tiếng Việt, hai loại List, Alert đóng bằng Command
   và Alert tự đóng.
5. Vào Canvas test; xem hình chuyển động, thử phím và dùng Phải mềm về menu.
6. Cuối cùng thử lại game từng chạy, gồm lưu RMS/thoát/mở lại. Nếu lỗi, xuất log.

## 5. Giới hạn còn lại — chưa được coi là MIDP LCDUI đầy đủ

- `CustomItem` vẫn có các stub repaint/invalidate/traverse và chưa có đường vẽ/
  input riêng trong Form.
- `DateField.setDate()` chưa hoạt động; chưa có editor ngày/giờ Android.
- Layout flags phức tạp, phân trang item dài, fit policy/wrap/font theo mọi loại
  List và lựa chọn POPUP chưa tái hiện đầy đủ giao diện MIDP.
- Constraint được ánh xạ vào IME; chưa có bộ kiểm định toàn bộ chuỗi theo mọi
  quy tắc MIDP (nhất là nhập bằng code/paste).
- Ticker hiện là dòng tĩnh, chưa chạy marquee; `AlertType.playSound`, vibrate và
  backlight chưa có tích hợp thiết bị thật.
- `Image.getRGB()` hiện còn stub. Không thay trong đợt này vì đây là đường API
  ảnh chung, cần kiểm thử game riêng trước khi mở rộng.

Việc một số API đã hoạt động qua smoke test không đồng nghĩa mọi game dùng LCDUI
đều tương thích. Những mục còn lại phải được ghi nhận riêng, không che bằng kết
quả giả.
