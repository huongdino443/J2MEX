# FreeJ2ME Lite v11 — List/Form native

## Phạm vi

Bản này port phần trình bày List/Form theo mã nguồn J2ME Loader. MIDlet và API
MIDP vẫn chạy trong FreeJ2ME/Java 8; không đưa game vào ART, không chuyển DEX.

### Đã triển khai

- List dùng Android ListView với hàng chữ/icon native; IMPLICIT, EXCLUSIVE và
  MULTIPLE, native Command/menu.
- Form dùng ScrollView/LinearLayout với StringItem, ImageItem, TextField,
  ChoiceGroup và Gauge cơ bản. TextField là EditText trực tiếp trên Form, không
  phải popup Xong/Hủy của v10.
- ChoiceGroup có radio, checkbox và spinner POPUP. Font màn hình native dùng
  font hệ thống Android; icon được giới hạn kích thước, không chồng hàng.
- Khi vào List/Form, ẩn FrameView và toàn bộ keypad game; khi về Canvas, khôi
  phục vùng game cùng keypad cũ.
- Programmatic updates gửi snapshot; native Views được cập nhật tại chỗ để giữ
  focus và ô đang nhập.
- State/Command có thứ tự: nội dung đang nhập được gửi kèm lựa chọn/Gauge trước
  khi gọi listener. Epoch, ID đối tượng và sequence/ack tách sự kiện cũ, lặp và
  phản hồi đang trễ khỏi màn hình hiện tại.

Nguồn port, giấy phép và thay đổi: `NATIVE_UI_PROVENANCE.md`. Không kéo AndroidX
hoặc bộ chuyển DEX của J2ME Loader vào app. License Apache 2.0 đã kèm trong APK.

## Canvas và runtime

Không sửa `Canvas.java`, `GameCanvas` hay `FrameView`, không thêm snapshot native
vào vòng mỗi frame Canvas. Việc đổi chế độ chỉ diễn ra khi chuyển Displayable.

Runtime vẫn chuẩn bị lần mở app đầu; marker runtime không thay, nên thay engine/
host không tự yêu cầu giải nén runtime lại trên cùng bộ dữ liệu đã hợp lệ.
APK vẫn ARM64, minSdk 21. Không coi đó là chứng nhận đã thử máy Android 5.

## Kiểm tra tự động

- Kết quả build: **34 kiểm tra LCDUI cũ + 30 kiểm tra native model/codec đạt**;
  test bridge thật List → TextBox → Form → Alert → Canvas đạt; APK build thành công.
- Bộ smoke LCDUI cũ: các API Form/TextBox/List/Alert/Command và Canvas.
- Native model/codec: Unicode, PNG icon, trạng thái nguyên khối trước listener,
  selection, cập nhật từ MIDlet, event cũ/lặp, ID item bị xóa, packet lỗi và
  native/Canvas isolation.
- Bridge thật: một JVM chạy HeadlessMain và MIDlet fixture, client gửi các packet
  Android mô phỏng: List → TextBox → Form → Alert → Canvas, nhập Unicode, token
  màn hình cũ và Back.
- Build Android, lint và kiểm tra chữ ký APK.

Các test model/giao thức **không phải kiểm thử Android View/IME trên thiết bị**.
Chưa có file MIDPlay JAR trong yêu cầu này để tái hiện trực tiếp ứng dụng đó.

## Thử trên điện thoại

1. Gỡ/cài sạch như quy trình trước. **Gỡ app xóa JAR và RMS/lưu game trong app.**
2. Mở app, chờ chuẩn bị runtime lần đầu.
3. Chọn **Kiểm thử LCDUI / bàn phím**. Menu có icon số và chữ tiếng Việt.
4. Vào Form: chạm ô nhập, chọn radio/checkbox/Gauge, bấm Kết quả ngay cả khi đang
   nhập. Nội dung trả về phải đúng lần sửa cuối.
5. Thử List EXCLUSIVE/MULTIPLE, Command/Back và Canvas test rồi về menu.
6. Mở MIDPlay, kiểm tra menu/cài đặt không còn khung 240×320 và keypad.
7. Thử lại một game Canvas đã chạy và lưu RMS. Nếu lỗi, xuất log từ toolbar.

## Giới hạn của PoC

- Chỉ **List/Form** được chuyển native trong bản này. TextBox/Alert vẫn dùng
  đường v10, nên đi vào chúng có thể thấy lại framebuffer/keypad/dialog cũ.
- DateField, CustomItem và item menu nhiều command chưa được port đầy đủ. Item
  không hỗ trợ có nhãn rõ, không giả làm widget đã hoạt động.
- Font riêng, MIDP layout flags, ticker động, lifecycle/phụ kiện phím và mọi edge
  case của MIDP chưa được chứng nhận tương đương J2ME Loader.
- Chưa có xác nhận hình thức/IME, FPS hay ma trận game trên điện thoại cho v11.
- Không hứa giảm dung lượng hay bỏ bước chuẩn bị runtime qua thay đổi giao diện.
