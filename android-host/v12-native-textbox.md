# FreeJ2ME Lite v12 — Nhập TextBox toàn màn hình

## Hành vi

- Khi MIDlet yêu cầu bàn phím Android, thay cửa sổ nổi nhỏ bằng màn hình nhập
  riêng, chiếm vùng nội dung ứng dụng. Toolbar, trạng thái frame và keypad game
  tạm ẩn; bấm Xong, lệnh hoặc Back thì quay về game.
- Với MIDP `TextBox`, Android hiển thị các Command có sẵn. Nút chính và IME Done
  gọi lệnh OK, nếu có; nếu không, lệnh SCREEN là lựa chọn mặc định. Các lệnh còn
  lại vẫn bấm được trong thanh lệnh.
- Hoàn tất gửi một gói gồm nội dung mới và command index. Engine đặt nội dung vào
  TextBox trước khi gọi đúng listener của Command. MIDlet chuyển màn hình từ
  listener nên trở lại game ngay; không cần bấm phím mềm game để kích hoạt Save.
- Với TextField trong Form (như ô tài khoản/mật khẩu trong video), Xong gửi giá
  trị vào đúng ô và gọi ItemStateListener. **Không tự gọi Command của Form**:
  nhập xong tài khoản không được tự đăng nhập trước khi nhập mật khẩu.
- Back Android bỏ thay đổi chưa gửi. Màn hình Canvas, keypad và List/Form v11
  không bị thay đổi.

## Kiểm thử

- `build-local.sh` chạy test engine và MIDlet bridge qua socket thật.
- Test gửi Unicode vào TextBox có `Save` (OK) và `Cancel`, xác nhận bridge cung
  cấp đúng nút mặc định, nội dung đã cập nhật trước CommandListener và màn hình
  tiếp theo mở được mà không gửi key soft-left.
- Android compile, APK assemble và lint được chạy ở lượt build.
- Không có thiết bị Android nối với runner; widget, IME và hình thức toàn màn
  hình chưa được xác minh trực tiếp trên điện thoại. APK này cần được thử với
  MIDPlay và một TextBox có Command Save trên thiết bị.

## Cài thử

1. Cài APK v13 lên Android ARM64/API 21+.
2. Mở một game dùng ô nhập hoặc MIDlet **Kiểm thử LCDUI / bàn phím**.
3. Với TextBox có Lưu/OK: nhập chữ rồi bấm nút chính hoặc phím Done trên bàn phím.
   Game phải nhận giá trị rồi xử lý lệnh ngay; không chạm thêm phím mềm trái.
4. Với Form nhiều trường: nhập trường thứ nhất rồi Done. Trường được cập nhật và
   Form ở lại để nhập tiếp; lệnh đăng nhập chỉ chạy khi người dùng chủ động chọn.
5. Back Android thoát màn hình nhập mà không gửi nội dung đang sửa. Command
   Cancel do MIDlet cung cấp sẽ chạy listener của game.

## Điều chỉnh v13

Bỏ nút Hủy Android riêng vì nó quay về game mà không cập nhật nội dung, khiến
con trỏ game còn nhấp nháy. Nút xác nhận chiếm toàn chiều ngang. Các Command
Cancel thật do MIDlet cung cấp vẫn được giữ.

Lưu ý: TextBox engine gửi nội dung trước khi chạy CommandListener. Việc ghi dữ
liệu lâu dài (RMS / trạng thái riêng của game) vẫn thuộc về logic của MIDlet.
