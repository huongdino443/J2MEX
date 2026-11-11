# Tham khảo và tái sử dụng LCDUI native của J2ME Loader

## Kết luận

Có thể tái sử dụng mã nguồn LCDUI của J2ME Loader để port lớp hiển thị Android.
Không thể thay nguyên thư mục `javax.microedition.lcdui` trong engine FreeJ2ME
bằng thư mục cùng tên của J2ME Loader mà không thay đổi kiến trúc.

Hướng đề xuất: giữ model/API MIDP trong JVM FreeJ2ME, port widget/adapter/bố cục
từ J2ME Loader vào Android host và bổ sung cầu nối trạng thái/sự kiện. Không lấy
pipeline JAR-to-DEX và không thay Canvas/GameCanvas.

Đây là báo cáo khảo sát ban đầu. Việc port List/Form sau khi người dùng duyệt
được ghi riêng trong `v11-native-ui.md`; báo cáo này giải thích cơ sở kiến trúc,
không phải bằng chứng đã kiểm thử giao diện trên điện thoại.

## Đối chiếu Canvas khi chuyển clone

J2ME Loader tại mốc nguồn bên dưới kiểm tra `visible` trong
`Canvas.PaintEvent.process()`; mỗi Canvas còn sở hữu buffer và Android Surface
riêng. `serviceRepaints()` đi qua event queue của Canvas, không chép ảnh Canvas
bất kỳ vào một framebuffer toàn cục.

FreeJ2ME Android host dùng một framebuffer vật lý chung. Trong JAR NLTB mẫu,
cả năm lớp `a.es` đến `e.es` gọi `repaint()` rồi `serviceRepaints()` mỗi vòng
lặp. Trước bản v15, `repaint()` kiểm tra current Canvas nhưng
`serviceRepaints()` lại luôn đẩy ảnh, khiến input đúng clone mà hình ảnh nhấp
nháy. `GameCanvas.flushGraphics()` cũng thiếu cùng kiểm tra.

Bản v15 bổ sung visibility guard tự viết ở hai điểm này, theo nguyên tắc chỉ
Canvas đang hiển thị được trình bày ảnh. Không sao chép pipeline Android Surface,
không đổi cách paint/copy ảnh Canvas đang hiển thị, không đưa JAR-to-DEX vào host.

Tham chiếu:
- `app/src/main/java/javax/microedition/lcdui/Canvas.java`
  (`PaintEvent.process`, `serviceRepaints`, `flushBuffer`, `repaintScreen`)
- `app/src/main/java/javax/microedition/lcdui/game/GameCanvas.java`
  (`flushGraphics`)

## Nguồn đã kiểm tra

Repository: https://github.com/nikita36078/J2ME-Loader

Mốc nguồn cố định:
`261c216c017e77391774ca10748c1229884cecd2`.

Các đường dẫn dưới đây tính từ `app/src/main/java/`:

| Thành phần | Mã nguồn upstream | Cách làm thực tế |
|---|---|---|
| Form | `javax/microedition/lcdui/Form.java` | ScrollView chứa LinearLayout; Item tạo Android View |
| List | `javax/microedition/lcdui/List.java` | ListView, CompoundListAdapter, click/selection listeners |
| TextBox/TextField | `javax/microedition/lcdui/TextBox.java`, `TextFieldImpl.java` | EditText/AppCompatEditText, InputFilter, InputType, TextWatcher |
| ChoiceGroup | `javax/microedition/lcdui/ChoiceGroup.java` | ChoiceGroupView/adapter và spinner |
| Alert | `javax/microedition/lcdui/Alert.java` | AppCompat AlertDialog và callback Command |
| Command | `javax/microedition/lcdui/commands/ScreenSoftBar.java` | Nút mềm, menu và fireCommandAction |
| Layout chung | `javax/microedition/lcdui/Screen.java`, `Displayable.java` | View container, ticker, thanh command |
| Chuyển màn hình | `javax/microedition/lcdui/Display.java` | ContextHolder/MicroActivity, UI handler và event queue |

Adapter liên quan:
`javax/microedition/lcdui/list/CompoundListAdapter.java`,
`CompoundAdapter.java`, `CompoundItem.java`.

Ví dụ liên kết nguồn:

- https://github.com/nikita36078/J2ME-Loader/blob/261c216c017e77391774ca10748c1229884cecd2/app/src/main/java/javax/microedition/lcdui/Form.java
- https://github.com/nikita36078/J2ME-Loader/blob/261c216c017e77391774ca10748c1229884cecd2/app/src/main/java/javax/microedition/lcdui/List.java
- https://github.com/nikita36078/J2ME-Loader/blob/261c216c017e77391774ca10748c1229884cecd2/LICENSE

## Menu Command trên Canvas: đối chiếu lại nguồn

Ở `commands/ScreenSoftBar.java`, J2ME Loader đặt các nút mềm Android trực tiếp
trên màn hình. Nút gắn với Command gọi `fireCommandAction`; nút Menu mở
`PopupWindow` có `ListView` chứa các Command còn lại. `AbstractSoftKeysBar.java`
phân loại Command OK/BACK/EXIT để đặt lệnh thường dùng vào nút giữa/phải, còn
danh sách tràn mở bằng PopupWindow. Cách này không biến menu thành ảnh do game vẽ.

FreeJ2ME Lite tách JVM engine và Android host thành hai tiến trình, nên không thể
chuyển trực tiếp `Command` object hoặc gọi `fireCommandAction` từ Android. Cần
port hành vi, không bê nguyên lớp: engine gửi screen/revision và danh sách
command ID/label/type qua bridge; Android hiển thị softbar và PopupWindow phủ
lên Canvas mà không ẩn FrameView; khi người dùng chọn, gửi ID về engine để gọi
đúng CommandListener trên hàng đợi sự kiện LCDUI. Phím mềm trái/phải và lên/xuống/
OK cần điều khiển cùng overlay đó. Dùng ID/revision để bỏ sự kiện cũ khi MIDlet
đổi màn hình hoặc cập nhật danh sách lệnh.

Điểm khác với Form/List native hiện tại là overlay Command không được chuyển
`Displayable` sang một màn hình khác hay tắt keypad/Canvas. Đây là cách khớp nhất
với thanh mềm và popup của J2ME Loader mà vẫn giữ nguyên framebuffer game. Chưa
sao chép mã upstream vào dự án; nếu port mã cụ thể, phải giữ header Apache-2.0.

## Vì sao không bê nguyên cả lớp API

1. FreeJ2ME chạy trong JVM Java SE riêng; Android UI chạy trong tiến trình ART
   của host. Các lời gọi `android.view.View`, ContextHolder/MicroActivity của
   J2ME Loader không tồn tại trong JVM này.
2. Image/Font của J2ME Loader dựa vào Bitmap/Drawable/kiểu font Android; model
   FreeJ2ME hiện dựa vào Java SE/AWT. Cần chuyển dữ liệu ảnh và thông tin font,
   không truyền nguyên đối tượng Java giữa hai tiến trình.
3. Callback của J2ME Loader gọi MIDlet trong cùng môi trường. Host hiện tại cần
   gửi sự kiện ngược về đúng screen/item/command trong engine.
4. Một số lớp tham khảo dùng AndroidX/AppCompat, generated binding và tài nguyên
   riêng của dự án. App hiện tại không có các phụ thuộc đó.
5. Thay toàn bộ Displayable/Display của upstream sẽ kéo theo Canvas và cơ chế
   event/Activity của họ, trái yêu cầu giữ nguyên đường vẽ game.

## Phạm vi tái sử dụng đề xuất

Ưu tiên port mã native đã có thay vì tự viết lại giao diện framebuffer:

- Bố cục Form và cách quản lý các View của Item.
- Adapter danh sách, logic chọn IMPLICIT/EXCLUSIVE/MULTIPLE, cách bố trí text/icon.
- Cấu hình editor, giới hạn độ dài, constraints, password và TextWatcher.
- Bố cục thanh command/menu và cách biểu diễn Alert.

Phải chỉnh các chỗ phụ thuộc ContextHolder, Image/Font, Activity, event queue và
tài nguyên. Các lớp port nên nằm trong namespace frontend riêng, không giả làm
lớp MIDP thực thi bên Android.

Để giữ app nhẹ, ưu tiên widget Android nền tảng ở các vị trí thay thế tương đương
AppCompat; không đưa toàn bộ ứng dụng J2ME Loader vào host. Nếu một thành phần cần
AndroidX thật sự, đánh giá riêng dung lượng và lợi ích trước khi thêm.

## Cầu nối cần bổ sung

### Engine sang Android

Snapshot/cập nhật chỉ dành cho Screen LCDUI:

- ID màn hình, revision, loại Screen, title/ticker và command ID.
- Item ID, loại Item, label/text, constraints/maxSize, selection, gauge.
- Ảnh được mã hóa/chuyển thành dữ liệu frontend đọc được.

Không serialize đối tượng MIDlet, Android View hoặc AWT qua cầu nối.

### Android sang engine

- Text/caret, lựa chọn item, thay đổi giá trị, focus, Command và Back.
- Gắn sự kiện với ID màn hình/item và revision; bỏ sự kiện cũ.
- Đồng bộ nội dung đang nhập trước khi gọi CommandListener. Không để người dùng
  bấm Lưu trong khi engine vẫn đọc giá trị trước lần sửa cuối.
- Kiểm thử riêng trường hợp ItemStateListener/CommandListener lập tức thay màn
  hình hoặc mở editor mới.

Khác với J2ME Loader cùng tiến trình, bridge bất đồng bộ không tự có semantics
getter/callback giống hệt. Thứ tự sự kiện và đồng bộ model phải được kiểm chứng.

### Chuyển chế độ hiển thị

- Form/List/TextBox: ẩn framebuffer và keypad game; dùng không gian cho native UI.
- Alert: dialog native theo đúng màn hình đích/command/timeout.
- Canvas/GameCanvas: trả lại FrameView/keypad và đường framebuffer hiện tại.
- Không đưa cập nhật native LCDUI vào vòng gửi mỗi frame Canvas.

## Lộ trình kiểm chứng

1. **List/Form native:** port adapter/bố cục từ upstream, làm snapshot và callback;
   thử màn hình menu và cài đặt MIDPlay mà người dùng đã gửi.
2. **Chuyển qua lại Canvas:** kiểm tra hide/show, phím, kích thước, không còn keypad
   game trên Screen native; không thay bộ vẽ game.
3. **Nhập liệu và Command:** TextBox/EditText trực tiếp trong Screen, TextField,
   ChoiceGroup, Alert; kiểm tra giá trị trước callback và sự kiện cũ.
4. **Hồi quy:** MIDPlay, MIDlet diagnostic, game từng chạy, RMS, Back, rotation/
   lifecycle và tốc độ khi ở Canvas.

Mỗi bước phải có kiểm thử model/giao thức, build và giới hạn rõ ràng. Chưa nâng
minSdk chỉ vì tham khảo upstream; widget nền tảng có thể nhắm API 21 nhưng bản
port vẫn phải build/lint và thử thiết bị trước khi xác nhận hỗ trợ.

## Giấy phép và nguồn gốc

LICENSE của repository là Apache License 2.0. Header những tệp LCDUI/adapter
được kiểm tra cũng khai báo Apache 2.0.

Khi thực sự sao chép/port, cần giữ copyright/header, kèm bản giấy phép, ghi rõ
thay đổi và giữ NOTICE có liên quan nếu thành phần sử dụng có NOTICE. FreeJ2ME
hiện khai báo GPLv3-or-later; Apache 2.0 nhìn chung tương thích với việc kết hợp
vào dự án GPLv3, nhưng không thay thế nghĩa vụ GPL của FreeJ2ME.

Kết quả này áp dụng cho các tệp đã kiểm tra, **không phải kết luận rằng mọi thư
viện, tài nguyên hoặc mã native trong toàn repository đều có cùng giấy phép**.
Không cần xin phép bổ sung riêng cho việc dùng đúng điều kiện của các tệp Apache
2.0 này. Không tạo fork/push GitHub trong bước khảo sát.

## Những điều hướng này không tự giải quyết

- Không bỏ JVM hay biến game JAR thành ứng dụng ART.
- Không tự giảm gói runtime hoặc bỏ bước chuẩn bị lần đầu.
- Font native chỉ thay đường chữ của Screen native; chữ do game vẽ trên Canvas
  vẫn là bài toán khác.
- Chưa có số đo hiệu năng, dung lượng hay kết quả thiết bị cho bản port.
