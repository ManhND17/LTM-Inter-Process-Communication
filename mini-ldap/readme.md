Cách chạy project 
# Bước 1: tạo thư mục output
mkdir out

# Bước 2: biên dịch toàn bộ file .java
javac -d out (Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName })

# Bước 3: chạy server
java -cp out com.minildap.net.DirectoryServer


Sau đó mở cửa sổ PowerShell thứ hai để chạy client:

cd D:\Code\Java\Network_Program\mini-ldap
java -cp out com.minildap.client.DirectoryClient

Cách test 
Test thủ công nhanh (smoke test)

Mở client, lần lượt gõ các lệnh sau và quan sát kết quả.

Đăng nhập admin mặc định:

> AUTH admin admin123


Kết quả mong đợi: status: OK, role: admin, groups:["admins"]

Thêm user mới:

> ADDUSER alice Passw0rd! user alice@corp.com "Alice Nguyen"


Kết quả: {"status":"OK","message":"User added"}

Tạo group và thêm user:

> CREATEGROUP developers
> ADDUSERTOGROUP alice developers
> LISTGROUP


Kết quả: developers có alice là thành viên.

Kiểm tra đọc user:

> READUSER alice


Kết quả: JSON chứa username, email, fullName, role.

Logout, đăng nhập user mới:

> LOGOUT
> AUTH alice Passw0rd!


Kết quả: role = user. Thử quyền hạn:

> LISTUSER


Nên được phép xem list (theo thiết lập hiện tại developer/user có thể list/read), nhưng nếu quyền không cho — sẽ báo Permission denied.

Thử xóa user bằng account không phải admin:

> DELETEUSER alice


Kết quả mong đợi: Permission denied.

Đăng nhập lại admin và xóa user:

> AUTH admin admin123
> DELETEUSER alice
> LISTUSER


Kết quả: alice đã biến mất.

B. Test chi tiết (kịch bản kiểm thử từng mục tiêu)
1) Authentication

Case A: đúng user/pass → OK.

Case B: user không tồn tại → ERROR: User not found.

Case C: sai mật khẩu → ERROR: Invalid password.

Case D: gửi lệnh AUTH thiếu tham số → Usage: AUTH <username> <password>.

2) Authorization / RBAC

Với admin: thử tất cả lệnh (ADDUSER, DELETEUSER, CREATEGROUP...) → thành công.

Với developer: thử ADDUSER/DELETEUSER → phải bị từ chối; UPDATEUSER/READUSER → được phép (theo policy).

Với user: chỉ được đọc thông tin bản thân READUSER <self>; thử READUSER <other> → Permission denied.

3) User CRUD (edge cases)

Tạo user trùng tên → User exists (lỗi).

Tạo user với tên chứa khoảng trắng hoặc ký tự đặc biệt — server hiện dùng split theo whitespace nên KHÔNG HỖ TRỢ tên/role có khoảng trắng; test này để bạn thấy giới hạn.

Update email/fullName với nhiều từ → đảm bảo UPDATEUSER user email fullname with spaces hoạt động (client dùng joinFrom để gộp phần còn lại).

4) Group CRUD

Tạo group trùng tên → Group exists.

Add member với group không tồn tại → Group not found.

Remove member không tồn tại → hành vi: thành công (idempotent) hoặc trả lỗi — hiện code trả Group not found nếu group không có.

5) Persistence

Sau khi thêm user/group, dừng server (Ctrl+C) → khởi động lại server → kiểm tra LISTUSER/LISTGROUP từ client khác → dữ liệu phải còn (lưu trong data/users.db, data/groups.db).

6) Concurrency / nhiều client

Mở nhiều client (2–5 terminal). Thực hiện thao tác đồng thời:

Client1: ADDUSER bob p1 user

Client2: ADDUSER carol p2 user

Client3: LISTUSER
Kiểm tra không có lỗi race; file save() là synchronized, nên an toàn cơ bản.

7) Error handling / robustness

Gửi lệnh không hợp lệ: FOO BAR → Unknown command.

Gửi lệnh trống → Empty command.

Gửi EXIT từ client → server trả __CLOSE__ → client đóng kết nối.

8) Security (basic)

Kiểm tra password hash: mở data/users.db → mật khẩu phải là chuỗi {SSHA}... không phải plaintext.

Thử tấn công brute-force (thử đăng nhập nhiều lần) → server hiện chưa có throttle; có thể thêm giới hạn sau.