package org.recompile.freej2me.lite;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Translations for J2MEX-owned interface text; MIDlet-provided content is not translated. */
public final class AppLocalization {
    private static final Map<String, String> ENGLISH = new HashMap<>();

    static {
        add("Trang chủ", "Home");
        add("Bộ sưu tập", "Collections");
        add("Thêm", "Add");
        add("Tìm kiếm", "Search");
        add("Khác", "More");
        add("Tùy chọn", "Options");
        add("Thêm game", "Add games");
        add("Tìm ứng dụng...", "Search games...");
        add("Xóa nội dung tìm kiếm", "Clear search");
        add("Tạo bộ sưu tập", "Create collection");
        add("Sắp xếp game", "Sort games");
        add("Chạy game", "Launch game");
        add("Thiết lập", "Settings");
        add("Gỡ cài đặt game", "Uninstall game");
        add("Gỡ khỏi bộ sưu tập", "Remove from collection");
        add("Chỉnh sửa ", "Edit ");
        add("Thu gọn bộ sưu tập", "Collapse collection");
        add("Mở bộ sưu tập", "Open collection");
        add("Chưa có game trong bộ sưu tập này.", "This collection has no games yet.");
        add("Tạo bộ sưu tập để sắp xếp các game của bạn.",
                "Create collections to organize your games.");
        add("Tạo bộ sưu tập mới", "Create a new collection");
        add("Đã tạo bộ sưu tập.", "Collection created.");
        add("Đã tạo bộ sưu tập và thêm game.", "Collection created and game added.");
        add("Đổi tên bộ sưu tập", "Rename collection");
        add("Tên bộ sưu tập", "Collection name");
        add("Nhập tên bộ sưu tập", "Enter a collection name");
        add("Tên bộ sưu tập đã tồn tại", "A collection with this name already exists");
        add("Xác nhận", "Confirm");
        add("Hủy", "Cancel");
        add("Xóa", "Delete");
        add("Xóa bộ sưu tập?", "Delete collection?");
        add("” sẽ bị xóa. Các game trong thư viện vẫn được giữ nguyên.",
                "” will be deleted. The games will remain in your library.");
        add("Thêm game", "Add games");
        add("Chưa có game trong thư viện.", "There are no games in the library yet.");
        add("Mới thêm trước", "Recently added");
        add("Tên A–Z", "Name A–Z");
        add("Tên Z–A", "Name Z–A");
        add("Tìm ứng dụng", "Search games");
        add("Không tìm thấy ứng dụng", "No games found");
        add("Thư viện đang trống", "Your library is empty");
        add("Không có game phù hợp", "No matching games");
        add("Nhập tên game để tìm kiếm.", "Enter a game name to search.");
        add("Thử một tên khác hoặc xoá nội dung tìm kiếm.",
                "Try another name or clear your search.");
        add("Thêm game từ nhiều tệp cùng lúc.", "Add games from multiple files at once.");
        add("Chưa có game nào trong danh sách này.", "There are no games in this list yet.");
        add("Đã thêm vào bộ sưu tập", "Added to collection");
        add("Game đã có trong bộ sưu tập này.", "This game is already in the collection.");
        add("Chọn tệp", "Choose files");
        add("Chọn thư mục", "Choose folder");
        add("Mở thư mục", "Open folder");
        add("Đường dẫn hiện tại, chạm để lên thư mục cha",
                "Current path. Tap to go to the parent folder.");
        add("Chọn tất cả", "Select all");
        add("Mới", "Newest");
        add("Sắp xếp mới nhất trước. Chạm để sắp xếp A–Z.",
                "Newest first. Tap to sort by name A–Z.");
        add("Sắp xếp A–Z. Chạm để sắp xếp mới nhất trước.",
                "Sorted by name A–Z. Tap to sort newest first.");
        add("Tìm tệp", "Search files");
        add("Đóng tìm kiếm", "Close search");
        add("Tìm tệp...", "Search files...");
        add("Trở về", "Back");
        add("Đang đọc thư mục...", "Loading folder...");
        add("Không thể đọc thư mục này. Hãy cấp quyền truy cập bộ nhớ trong Cài đặt.",
                "Can't read this folder. Allow storage access in Settings.");
        add("Không thể liệt kê nội dung thư mục này.", "Can't list this folder's contents.");
        add("Không thể đọc thư mục này. Hãy kiểm tra quyền truy cập bộ nhớ.",
                "Can't read this folder. Check storage permissions.");
        add("Thư mục này chưa có thư mục con.", "This folder has no subfolders.");
        add("Thư mục này chưa có thư mục con hoặc tệp JAR, ZIP.",
                "This folder has no subfolders or JAR/ZIP files.");
        add("Không tìm thấy tệp khớp với nội dung tìm kiếm.",
                "No files match your search.");
        add("Chọn thư mục để quét", "Select a folder to scan");
        add("Chọn thư mục này", "Select this folder");
        add("Chọn thư mục lưu game", "Choose game storage folder");
        add("Nơi lưu game", "Game storage location");
        add("Dùng thư mục này", "Use this folder");
        add("Thư mục làm việc", "Working folder");
        add("Đã đổi thư mục lưu game.", "Game storage folder changed.");
        add("Đã gỡ game khỏi thư viện; dữ liệu lưu vẫn được giữ lại.",
                "Game removed from the library; saved data was kept.");
        add("Đặt lại dữ liệu", "Reset data");
        add("Đặt lại dữ liệu game?", "Reset game data?");
        add("Đã đặt lại dữ liệu game.", "Game data reset.");
        add("Đã gỡ game và xóa toàn bộ thư mục dữ liệu.",
                "Game removed and its entire data folder deleted.");
        add("Không xóa được JAR đã nhập.", "Could not delete the imported JAR.");
        add("Không thể xóa toàn bộ thư mục game.", "Could not delete the entire game folder.");
        add("Không thể xác minh game cần gỡ.", "Could not verify the game to uninstall.");
        add("Không tìm thấy game để gỡ cài đặt.", "Could not find the game to uninstall.");
        add("Không thể xóa bản bộ sưu tập cũ khỏi thư mục làm việc.",
                "Could not remove the old collection file from the working folder.");
        add("Không thể đặt lại dữ liệu game.", "Could not reset game data.");
        add("Không tìm thấy game để đặt lại dữ liệu.",
                "Could not find the game to reset.");
        add("Thư mục game không hợp lệ.", "Invalid game folder.");
        add("Thư mục dữ liệu game không hợp lệ.", "Invalid game data folder.");
        add("Không thể xóa dữ liệu game.", "Could not delete game data.");
        add("Không thể đọc dữ liệu game.", "Could not read game data.");
        add("Không thể kiểm tra dữ liệu game.", "Could not inspect game data.");
        add("Thư mục hiện tại còn game hoặc dữ liệu lưu. Không thể đổi vị trí mà không di chuyển dữ liệu.",
                "The current folder contains games or saves. Its location cannot be changed without moving that data.");
        add("Thư mục đã được dùng bởi một định dạng J2MEX khác.",
                "This folder uses a different J2MEX storage format.");
        add("Hãy chọn một thư mục con trong bộ nhớ dùng chung.",
                "Choose a folder inside shared storage.");
        add("Không thể dùng thư mục Android làm nơi lưu game.",
                "Android system folders cannot be used for game storage.");
        add("Thư mục đã chọn không tồn tại.", "The selected folder does not exist.");
        add("Không thể ghi vào thư mục đã chọn.", "The selected folder is not writable.");
        add("Thư mục lưu game không đọc/ghi được.",
                "The game storage folder is not readable or writable.");
        add("Không tìm thấy thư mục lưu game đã chọn.",
                "The selected game storage folder could not be found.");
        add("Không thể tạo thư mục lưu game.", "Could not create the game storage folder.");
        add("Không thể đổi thư mục lưu game.", "Could not change the game storage folder.");
        add("Không thể đọc thư mục thư viện game.",
                "Could not read the game library folder.");
        add("Không thể lưu trạng thái cài đặt game.",
                "Could not save the game's setup state.");
        add("Quay lại trang trước", "Go back");
        add("Trở về thư mục cha", "Go to the parent folder");
        add("Chưa chọn tệp nào", "No files selected");
        add("Hủy chọn và quay lại", "Clear selection and go back");
        add("Bỏ chọn tất cả tệp đang hiển thị", "Deselect all visible files");
        add("Chọn tất cả tệp đang hiển thị", "Select all visible files");
        add("Thiết lập game", "Game settings");
        add("Bắt đầu", "Start");
        add("Lưu", "Save");
        add("Hiển thị", "Display");
        add("Chừa chỗ cho gamepad", "Reserve space for gamepad");
        add("Canvas neo phía trên và không chồng lên gamepad",
                "Keep the canvas above the gamepad.");
        add("Đường bao", "Canvas border");
        add("Vị trí Canvas", "Canvas position");
        add("Tỷ lệ Canvas", "Canvas scale");
        add("Font bitmap tiếng Việt", "Vietnamese bitmap font");
        add("Bộ lọc CRT Grid", "CRT Grid filter");
        add("Điều khiển", "Controls");
        add("Chế độ cảm ứng", "Touch mode");
        add("Bật để game nhận và nhận diện sự kiện chạm; không điều khiển việc hiện gamepad ảo.",
                "Lets the game detect touch input; it does not control the virtual gamepad.");
        add("Hiển thị gamepad ảo", "Show virtual gamepad");
        add("Chỉ điều khiển việc hiện gamepad ảo, độc lập với chế độ cảm ứng của game.",
                "Controls the virtual gamepad independently of the game's touch mode.");
        add("Bố cục", "Layout");
        add("Tạo mẫu", "Create layout");
        add("Sửa", "Edit");
        add("Đổi chiều rộng và chiều cao", "Swap width and height");
        add("Chọn preset canvas", "Choose a canvas preset");
        add("Nhập chiều rộng và chiều cao Canvas hợp lệ.",
                "Enter valid canvas width and height values.");
        add("Kích thước Canvas phải từ 64 đến 2048.",
                "Canvas dimensions must be between 64 and 2048.");
        add("Công cụ chỉnh bố cục", "Layout editor tools");
        add("Vuốt sang phải để mở menu nhanh", "Swipe right to open the quick menu");
        add("Bố cục gamepad", "Gamepad layout");
        add("Ánh xạ phím", "Key mapping");
        add("Bàn phím hoặc tay cầm thật", "Physical keyboard or controller");
        add("Mặc định", "Default");
        add("Tùy chỉnh", "Custom");
        add("Sửa", "Edit");
        add("Chọn một hành động, rồi nhấn phím bàn phím hoặc nút tay cầm cần gán.",
                "Select an action, then press the keyboard key or controller button to assign.");
        add("Chưa chọn hành động.", "No action selected.");
        add("Đang chờ phím cho: ", "Press a key for: ");
        add("Nhấn phím…", "Press a key…");
        add("Đã gán: ", "Assigned: ");
        add("Đã khôi phục ánh xạ mặc định.", "Default key mapping restored.");
        add("Chưa gán", "Not assigned");
        add("Điều hướng", "Navigation");
        add("Phím mềm", "Soft keys");
        add("Bàn phím số", "Keypad");
        add("Lên", "Up");
        add("Xuống", "Down");
        add("Trái", "Left");
        add("Phải", "Right");
        add("Chọn", "Select");
        add("Phím mềm trái", "Left soft key");
        add("Phím mềm phải", "Right soft key");
        add("Gọi", "Send");
        add("Kết thúc", "End");
        add("Đặt lại", "Reset");
        add("Lưu", "Save");
        add("Hủy", "Cancel");
        add("Chưa có mẫu tùy chỉnh để xóa.", "There are no custom layouts to delete.");
        add("Chưa có mẫu tùy chỉnh để sửa.", "There are no custom layouts to edit.");
        add("Chọn mẫu cần xóa", "Select a layout to delete");
        add("Chọn mẫu cần sửa", "Select a layout to edit");
        add("Xóa mẫu gamepad?", "Delete gamepad layout?");
        add("” sẽ bị xóa. Game đang dùng mẫu này sẽ chuyển về Mẫu 1.",
                "” will be deleted. Games using it will switch to Layout 1.");
        add("Xong", "Done");
        add("Cần vùng trống 3×3 để hiện joystick.",
                "A free 3×3 area is required to show the joystick.");
        add("Chạm vào một nút trước.", "Select a button first.");
        add("Không đủ chỗ cho cỡ tiếp theo.", "There isn't enough room for the next size.");
        add("Vị trí Canvas", "Canvas position");
        add("Nhập chiều rộng và chiều cao Canvas hợp lệ.",
                "Enter valid canvas width and height values.");
        add("Kích thước Canvas phải từ 64 đến 2048.",
                "Canvas dimensions must be between 64 and 2048.");
        add("Không thể lưu cài đặt game.", "Couldn't save game settings.");
        add("Chọn tất cả", "Select all");
        add("Lưới", "Grid");
        add("Đổi kích thước", "Resize");
        add("Ẩn nút", "Hide buttons");
        add("Đã lưu", "Saved");
        add("Công cụ chỉnh bố cục", "Layout editor tools");
        add("Đóng công cụ", "Close tools");
        add("Mở công cụ", "Open tools");
        add("Các nút", "Buttons");
        add("Không thể sửa mẫu dựng sẵn.", "Built-in layouts can't be edited.");
        add("Đã lưu mẫu gamepad.", "Gamepad layout saved.");
        add("Không thể lưu mẫu gamepad.", "Couldn't save gamepad layout.");
        add("Không thể xóa mẫu gamepad.", "Couldn't delete gamepad layout.");
        add("Không thể xóa mẫu gamepad.", "Couldn't delete gamepad layout.");
        add("Đã lưu log.", "Log saved.");
        add("Hiển thị FPS", "Show FPS");
        add("Hiển thị tốc độ khung hình khi đang chơi game.",
                "Show the frame rate while playing.");
        add("Ghi log để gỡ lỗi", "Diagnostic logging");
        add("Cài đặt", "Settings");
        add("Menu nhanh", "Quick menu");
        add("Chụp màn hình", "Screenshot");
        add("Đã lưu ảnh vào thư mục Pictures/J2MEX.",
                "Screenshot saved to Pictures/J2MEX.");
        add("Chưa có canvas game để chụp.", "The game canvas is not ready for capture.");
        add("Không thể chụp ảnh màn hình.", "Couldn't capture the screenshot.");
        add("Không thể lưu ảnh chụp.", "Couldn't save the screenshot.");
        add("Chụp ảnh kèm bộ lọc CRT cần Android 8.0 trở lên.",
                "Capturing the CRT-filtered canvas requires Android 8.0 or newer.");
        add("Chỉ áp dụng đầy đủ sau khi thoát rồi mở lại game.",
                "This setting takes full effect after restarting the game.");
        add("Theo thiết bị", "Follow device");
        add("Thanh trạng thái", "Status bar");
        add("Hiển thị thanh trạng thái trong lúc chơi game.",
                "Show the status bar while playing.");
        add("Giữ hướng xoay hiện tại", "Lock current orientation");
        add("Khóa hướng màn hình theo chiều đang dùng khi mở game.",
                "Lock the screen to its current orientation when a game starts.");
        add("Ngôn ngữ", "Language");
        add("Chọn tiếng Việt, tiếng Anh hoặc theo thiết bị.",
                "Choose Vietnamese, English, or follow the device.");
        add("Dữ liệu game", "Game data");
        add("Thông tin", "Details");
        add("Phiên bản, nền tảng và thông tin dự án.", "Version, platform, and project details.");
        add("Cập nhật", "Updates");
        add("Kiểm tra phiên bản mới và tải bản phát hành từ GitHub.",
                "Check for a newer version and download the GitHub release.");
        add("Kiểm tra", "Check");
        add("Hiện chưa có bản cập nhật", "There are no updates right now");
        add("Đã có bản cập nhật mới", "A new update is available");
        add("Chưa kiểm tra phiên bản mới", "Update status has not been checked");
        add("Tải về", "Download");
        add("Trang phát hành", "Release page");
        add("Kiểm tra lại", "Check again");
        add("Đang kiểm tra cập nhật…", "Checking for updates…");
        add("Đang tải APK…", "Downloading APK…");
        add("Không kiểm tra được cập nhật", "Couldn't check for updates");
        add("Không tải được bản cập nhật.", "Couldn't download the update.");
        add("Đã tải APK. Hãy xác nhận trong trình cài đặt Android.",
                "APK downloaded. Confirm installation in Android's installer.");
        add("Không mở được trang phát hành.", "Couldn't open the release page.");
        add("Không mở được quyền cài ứng dụng không rõ nguồn gốc.",
                "Couldn't open the install unknown apps permission.");
        add("Chưa được cấp quyền cài ứng dụng không rõ nguồn gốc.",
                "Permission to install apps from unknown sources was not granted.");
        add("Không tìm thấy APK đã tải.", "The downloaded APK could not be found.");
        add("Không mở được trình cài đặt Android: ", "Couldn't open Android's installer: ");
        add("Giới thiệu", "About");
        add("Tổng quan ứng dụng và giấy phép thành phần.",
                "App overview and component licenses.");
        add("Xem", "View");
        add("Đóng", "Close");
        add("Không thể thực hiện", "Couldn't complete the action");
        add("Hoàn tất thêm game", "Game import complete");
        add("Không thể mở game", "Could not open game");
        add("Chọn game để mở", "Choose a game to open");
        add("Cần quyền truy cập bộ nhớ", "Storage access required");
        add("J2MEX cần quyền truy cập bộ nhớ để duyệt thư mục và tìm game. Hãy cấp quyền để tiếp tục, hoặc thoát ứng dụng.",
                "J2MEX needs storage access to browse folders and find games. Allow access to continue, or exit the app.");
        add("Cấp quyền", "Allow access");
        add("Thoát", "Exit");
        add("Thoát game", "Exit game");
        add("Thoát game?", "Exit game?");
        add("Bạn có chắc chắn muốn thoát khỏi game hiện tại không?",
                "Are you sure you want to exit the current game?");
        add("Lưu log", "Save log");
         add("Xuất log gần nhất", "Export latest log");
         add("Cần bật ghi log trước để lưu nhật ký",
                 "Enable logging to save a diagnostic log.");
         add("Xuất", "Export");
         add("Chưa có log lần chạy gần nhất. Hãy bật ghi log rồi mở game.",
                 "No recent run log is available. Enable logging before launching a game.");
        add("Bàn phím", "Keyboard");
        add("Vuốt sang phải để mở menu nhanh", "Swipe right to open the quick menu");
        add("Lưu log (bật Ghi log để gỡ lỗi trong mục Khác)",
                "Save log (enable Diagnostic logging in More)");
        add("Không tìm thấy MIDlet JAR", "MIDlet JAR not found");
        add("Java đã dừng", "Java stopped");
        add("Không tìm thấy font", "Font not found");
        add("Không thể thực hiện", "Couldn't complete the action");
        add("Preset canvas", "Canvas preset");
        add("System", "System default");
        add("Theo thiết bị", "System default");
        add("Theo cài đặt hệ thống", "System default");
        add("Mẫu 1", "Layout 1");
        add("Mẫu 2", "Layout 2");
        add("Mẫu 3 (Joystick)", "Layout 3 (Joystick)");
        add("Mẫu 4", "Layout 4");
        add("Đổi tên", "Rename");
        add("Xóa bộ sưu tập", "Delete collection");
        add("Gỡ cài đặt game?", "Uninstall game?");
        add("Gỡ cài đặt", "Uninstall");
        add("Thêm vào bộ sưu tập", "Add to collection");
        add("Chưa có bộ sưu tập", "No collections yet");
        add("Chọn thư mục", "Choose folder");
        add("Chọn tệp", "Choose files");
        add("Chọn thư mục để quét", "Select a folder to scan");
        add("Chưa khả dụng", "Not available yet");
        add("Bật", "On");
        add("Tắt", "Off");
        add("Trên", "Top");
        add("Giữa", "Center");
        add("Dưới", "Bottom");
        add("Không có game nào trong danh sách này.", "There are no games in this list yet.");
        add("Hãy bật ghi log trong mục Khác trước khi mở game.",
                "Enable diagnostic logging in More before launching a game.");
        add("Nhập văn bản…", "Enter text…");
        add("Không thể xóa mẫu gamepad.", "Couldn't delete the gamepad layout.");
        add("Không thể lưu cài đặt game.", "Couldn't save game settings.");
        add("Tốc độ giả lập", "Emulation speed");
        add("Thay đổi tốc độ đồng hồ MIDlet và các độ trễ được hỗ trợ. Âm thanh và giao diện Android vẫn ở tốc độ bình thường.",
                "Changes the MIDlet clock and supported delays. Audio and Android UI remain at normal speed.");
        add("Thiết lập lại", "Reset");
        add("Trình sửa bộ nhớ", "Memory editor");
        add("Tiếp tục", "Continue");
        add("Chỉ dùng với game offline. Trình sửa chỉ tìm và thay đổi giá trị số trong bộ nhớ Java của game đang chạy. Sau khi mở, kết nối mạng sẽ bị chặn đến khi khởi động lại game; nếu game đã dùng mạng, tính năng không khả dụng.",
                "For offline games only. This editor searches and changes numeric values in the running game's Java heap. Network access stays blocked until the game restarts; it is unavailable if the game has already used a network connection.");
        add("Tìm giá trị số trong heap Java của MIDlet đang chạy. Kết quả bị giới hạn để tránh làm chậm game.",
                "Search numeric values in the running MIDlet's Java heap. Results are bounded to limit impact on gameplay.");
        add("Giá trị số", "Numeric value");
        add("Tham chiếu tĩnh", "Static reference");
        add("Tham chiếu đối tượng", "Object reference");
        add("Chưa truy được nguồn", "Source unavailable");
        add("Đối tượng không còn khả dụng", "Object no longer available");
        add("Nhập giá trị rồi tìm kiếm.", "Enter a value and search.");
        add("Đang tìm kiếm…", "Searching…");
        add("Không có kết quả", "No results");
        add("Hiển thị 100 kết quả đầu tiên", "Showing the first 100 results");
        add("Sửa giá trị", "Edit value");
        add("Lưu", "Save");
        add("Nhập giá trị số", "Enter a numeric value");
        add("Nhập giá trị số cần tìm.", "Enter a numeric value to search for.");
        add("Giá trị nhập không hợp lệ.", "Enter a valid numeric value.");
        add("Game chưa khởi chạy xong.", "The game has not started yet.");
        add("Không tìm thấy giá trị phù hợp.", "No matching values.");
        add("Giá trị này không còn khả dụng.", "This result is no longer available.");
        add("Không thể thay đổi giá trị này.", "This value could not be changed.");
        add("Trình sửa bộ nhớ phải được mở trước.", "Open the memory editor first.");
        add("Không thể mở trình sửa bộ nhớ sau khi game đã dùng kết nối mạng.",
                "Memory editing is unavailable after the game uses a network connection.");
        add("Không xác định thao tác trình sửa bộ nhớ.", "Unknown memory-editor action.");
        add("Hãy đóng trình sửa bộ nhớ trước khi dùng kết nối mạng.",
                "Close the memory editor before using network connections.");
        add("Không thể dùng mạng trong phiên này vì trình sửa bộ nhớ đã được mở.",
                "Network access is disabled for this session because the memory editor was opened.");
    }

    private AppLocalization() { }

    private static void add(String vietnamese, String english) {
        ENGLISH.put(vietnamese, english);
    }

    public static boolean isEnglish(Context context) {
        return !"Tiếng Việt".equals(AppPreferences.automaticLanguage(context));
    }

    static String languageTag(Context context) {
        return isEnglish(context) ? "en" : "vi";
    }

    public static Locale locale(Context context) {
        return isEnglish(context) ? Locale.ENGLISH : new Locale("vi");
    }

    static Context localizedContext(Context base) {
        Locale locale = locale(base);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= 24) configuration.setLocale(locale);
        else configuration.locale = locale;
        return base.createConfigurationContext(configuration);
    }

    public static String text(Context context, String value) {
        if (value == null || !isEnglish(context)) return value;
        String translated = ENGLISH.get(value);
        if (translated != null) return translated;
        if (value.startsWith("Đã thêm vào “") && value.endsWith("”.")) {
            return "Added to “" + value.substring("Đã thêm vào “".length(),
                    value.length() - 2) + "”." ;
        }
        if (value.startsWith("Chỉnh sửa ")) return "Edit " + value.substring("Chỉnh sửa ".length());
        if (value.startsWith("“") && value.endsWith("” sẽ bị xóa. Các game trong thư viện vẫn được giữ nguyên.")) {
            return "“" + value.substring(1, value.length()
                    - "” sẽ bị xóa. Các game trong thư viện vẫn được giữ nguyên.".length())
                    + "” will be deleted. The games will remain in your library.";
        }
        if (value.startsWith("“") && value.endsWith("” sẽ bị gỡ khỏi thiết bị. Tệp game trong J2MEX cũng sẽ bị xóa.")) {
            return "“" + value.substring(1, value.length()
                    - "” sẽ bị gỡ khỏi thiết bị. Tệp game trong J2MEX cũng sẽ bị xóa.".length())
                    + "” will be uninstalled. Its J2MEX game file will also be deleted.";
        }
        if (value.startsWith("Đã gỡ “") && value.endsWith("” khỏi J2MEX.")) {
            return "Removed “" + value.substring("Đã gỡ “".length(),
                    value.length() - "” khỏi J2MEX.".length()) + "” from J2MEX.";
        }
        if (value.startsWith("“") && value.endsWith("” sẽ bị xóa khỏi J2MEX và các bộ sưu tập. Tệp gốc đã nhập vẫn được giữ nguyên.")) {
            return "“" + value.substring(1, value.length()
                    - "” sẽ bị xóa khỏi J2MEX và các bộ sưu tập. Tệp gốc đã nhập vẫn được giữ nguyên.".length())
                    + "” will be removed from J2MEX and collections. The original imported file will remain.";
        }
        if (value.startsWith("Đã chọn ") && value.endsWith(" tệp")) {
            return "Selected " + value.substring("Đã chọn ".length(), value.length() - 4)
                    + " files";
        }
        if (value.startsWith("Đã lưu ")) return "Saved " + value.substring("Đã lưu ".length());
        if (value.startsWith("Mở thư mục ")) return "Open folder " + value.substring("Mở thư mục ".length());
        if (value.startsWith("Chọn tệp ")) return "Select file " + value.substring("Chọn tệp ".length());
        if (value.startsWith("Thêm (") && value.endsWith(")"))
            return "Add (" + value.substring(6);
        if (value.startsWith("Đã thêm ") && value.endsWith(" game")) {
            String count = value.substring("Đã thêm ".length(), value.length() - " game".length());
            return "Added " + count + ("1".equals(count) ? " game" : " games");
        }
        if (value.startsWith("Đã bỏ qua ") && value.contains(" mục bị lỗi:")) {
            return value.replace("Đã bỏ qua ", "Skipped ")
                    .replace(" mục bị lỗi:", " item(s) with errors:")
                    .replace("\n• Và ", "\n• And ")
                    .replace(" lỗi khác.", " more errors.");
        }
        value = value.replace(": chỉ hỗ trợ JAR, JAD hoặc ZIP.", ": only JAR, JAD, or ZIP files are supported.")
                .replace(": không thể đọc tệp.", ": couldn't read the file.")
                .replace("Không có tệp JAR, JAD hoặc ZIP để nhập.",
                        "No JAR, JAD, or ZIP files to import.")
                .replace("Không đọc được descriptor.", "Couldn't read the descriptor.")
                .replace("JAD chỉ là descriptor; hãy chọn tệp JAR đi kèm cùng lúc.",
                        "A JAD is only a descriptor; select its JAR file at the same time.")
                .replace("Không thể mở thư mục:", "Couldn't open folder:")
                .replace(": không thể đọc thư mục.", ": couldn't read the folder.")
                .replace(": không thể đọc nội dung thư mục.", ": couldn't read the folder contents.")
                .replace("JAD vượt 64 KiB.", "JAD exceeds 64 KiB.")
                .replace("ZIP không chứa tệp JAR.", "ZIP archive contains no JAR files.")
                .replace("Không đọc được tệp ZIP và không có JAR hợp lệ bên trong.",
                        "Couldn't read the ZIP archive; it contains no valid JAR.")
                .replace("không có MIDlet-1 hợp lệ trong manifest.",
                        "manifest has no valid MIDlet-1 entry.")
                .replace("không tìm thấy lớp MIDlet trong JAR.", "MIDlet class not found in the JAR.")
                .replace("Tệp vượt giới hạn 512 MiB.", "File exceeds the 512 MiB limit.");
        if (value.startsWith("Đã chọn ")) return "Selected " + value.substring("Đã chọn ".length());
        return value;
    }

    static String[] texts(Context context, String[] values) {
        String[] translated = new String[values.length];
        for (int i = 0; i < values.length; i++) translated[i] = text(context, values[i]);
        return translated;
    }
}
