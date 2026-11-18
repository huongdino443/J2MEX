# JAR Tool J2ME — v13

Công cụ chạy trên trình duyệt để nhân bản MIDlet trong một file JAR, ghép nhiều game vào một JAR và tạo file cài đặt JAD.

## Bắt đầu nhanh

1. Mở [JAR Tool trên GitHub Pages](https://huongdino443.github.io/J2MEX/tools/jar-tool/) bằng
   trình duyệt hiện đại như Chrome, Firefox hoặc Edge. Bạn cũng có thể tải `index.html` và mở trực tiếp.
2. Chọn tab **Nhân bản / ghép JAR** hoặc **Tạo JAD**, rồi làm theo hướng dẫn trên màn hình.

Trang tải JSZip 3.10.1 từ CDN của Cloudflare nên cần kết nối Internet khi mở công cụ. Việc đọc và xử lý các file JAR diễn ra trong trình duyệt; nội dung game không được tải lên máy chủ của công cụ.

## Tính năng

- **Nhân bản một game:** tạo nhiều bản MIDlet từ một JAR.
- **Ghép nhiều game:** chọn số bản riêng cho từng game rồi đưa chúng vào cùng một JAR.
- **Đánh số bản:** dùng `a`–`z`, sau đó `aa`–`zz`; giới hạn lý thuyết là 702 MIDlet cho mỗi JAR kết quả.
- **Tạo JAD:** tạo file JAD từ manifest của JAR và điền kích thước JAR.
- **Thiết lập JAR kết quả:** tên mặc định khi ghép nhiều game là `Multi-JAR`, nhà cung cấp là `TrVH`, thời gian chuyển ứng dụng được đặt thành 0.

Tool đóng gói framework menu MIDlet kèm theo, chuyển namespace framework sang `trvh`, và tách dữ liệu lưu giữa các bản MIDlet. Nếu phát hiện một số thư viện hoặc tài nguyên trùng tên mà không thể xử lý an toàn, tool sẽ dừng thay vì xuất JAR có nguy cơ hỏng.

## Giới hạn và tương thích

- 702 MIDlet là giới hạn do tool đặt ra, không bảo đảm thiết bị hoặc giả lập có thể chạy đồng thời số lượng đó. Mức dùng RAM, dung lượng và khả năng chạy phụ thuộc vào game, thiết bị và giả lập.
- JAR có framework/thư viện riêng hoặc tài nguyên nạp động có thể không ghép được an toàn. Một JAR xuất thành công cũng chưa đảm bảo mọi game tương thích với mọi giả lập; hãy cài và kiểm tra từng kết quả.
- Các kiểm tra đã thực hiện tập trung vào cấu trúc JAR, manifest, namespace lớp khởi chạy và thao tác tạo JAR mẫu. Chưa kiểm thử runtime trên mọi thiết bị hoặc giả lập.

## Ghi công và quyền sử dụng

Framework menu gốc do **Quyetdaik** thực hiện. Bản tích hợp và đóng gói trong tool này do **Trần Văn Hưởng** thực hiện. JAR được tạo có `META-INF/CREDITS.TXT` ghi lại hai phần ghi công này; không nên xóa khi phân phối.

Gói này không đưa ra giấy phép mới cho framework hoặc tài nguyên game được nhúng. Trước khi công khai hoặc phân phối lại, hãy kiểm tra quyền sử dụng các thành phần đó với chủ sở hữu tương ứng.
