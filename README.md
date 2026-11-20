# LibMan — Quản lý thư viện đại học

Ứng dụng Java 17 sử dụng Jakarta Servlet, JSP, JDBC và MySQL 8. Người dùng đăng nhập theo ba vai trò. Hai luồng được thiết kế chi tiết là tra cứu theo tên tài liệu và nhập tài liệu từ nhà cung cấp.

## Chạy thử trên máy cá nhân

Cần JDK 17 và Maven 3.9. Mở terminal ở thư mục chứa `pom.xml` và chạy:

```sh
mvn test-compile exec:java
```

Mở **http://127.0.0.1:8080/**. Cổng có thể đổi bằng thuộc tính Java `libman.port`. Bản chạy thử dùng Tomcat nhúng và cơ sở dữ liệu H2 ở chế độ tương thích MySQL. Dữ liệu được lưu trong `.runtime/` cho lần mở sau. Nhấn Ctrl+C để dừng. Khi dùng cổng khác trong PowerShell, đặt cả đối số `"-Dlibman.port=8090"` trong dấu ngoặc kép.

| Tài khoản thử | Vai trò | Mật khẩu thử |
| --- | --- | --- |
| bandoc | Bạn đọc | ThuVien@2026 |
| thuthu | Nhân viên thư viện | ThuVien@2026 |
| quanly | Quản lý | ThuVien@2026 |

Các tên người, nhà cung cấp, tài liệu và số lượng có sẵn ban đầu là dữ liệu minh họa. Tài khoản bạn đọc ban đầu chưa có thẻ.

## Nghiệp vụ

- **Bạn đọc** tìm theo tên chứa từ khóa, xem chi tiết và số bản còn có thể mượn, đăng ký thẻ trực tuyến và xem kết quả duyệt.
- **Nhân viên thư viện** tra cứu, thêm/sửa/xóa tài liệu, nhà cung cấp và bạn đọc. Bản ghi đã có chứng từ được ngừng sử dụng. Nhân viên cấp, gia hạn và khóa thẻ, lập phiếu mượn tại quầy, nhận trả toàn bộ hoặc từng phần theo dòng mượn gốc.
- **Nhập tài liệu** tìm nhà cung cấp theo tên, thêm nhà cung cấp khi cần, quét/nhập mã tài liệu, thêm đầu tài liệu còn thiếu, nhập số lượng và đơn giá. Bản nháp được giữ trong phiên của tab. Khi hoàn tất, hệ thống lưu cả phiếu và tăng kho trong một giao dịch. Gửi lại cùng mã yêu cầu trả về đúng phiếu đã lưu. Có thể mở lại hóa đơn, in nhiều lần và xác nhận thanh toán riêng.
- **Quản lý** thống kê trong khoảng ngày theo số lượt mượn của từng đầu tài liệu, số phiếu mượn của từng bạn đọc và tổng số bản nhập của từng nhà cung cấp. Báo cáo tài liệu cho phép xem chi tiết các dòng mượn.
- Máy chủ kiểm tra quyền, phiên đăng nhập và CSRF. JDBC dùng tham số truy vấn, tiền lưu bằng DECIMAL, mật khẩu băm PBKDF2 với salt riêng. Các nghiệp vụ ghi có commit/rollback, khóa dòng và kiểm tra số lượng tại máy chủ.

## Kiểm tra nhanh

1. Đăng nhập `bandoc`, tìm **Cơ sở dữ liệu**, mở TL001. Gửi đăng ký ở **Thẻ bạn đọc**.
2. Đăng nhập `thuthu`, mở **Bạn đọc** và cấp thẻ với ngày hết hạn. Tại **Mượn & trả**, lập phiếu và thử trả từng phần.
3. Tại **Nhập tài liệu**, chọn NCC01, nhập 10 bản TL001 với đơn giá 50.000 đồng. Khi hoàn tất, tổng kho tăng từ 5 lên 15 nếu dùng dữ liệu ban đầu. Hóa đơn có tổng 500.000 đồng. In hóa đơn, sau đó xác nhận đã thanh toán.
4. Đăng nhập `quanly` và chọn khoảng ngày chứa giao dịch để xem các thống kê.

## Triển khai với MySQL và Tomcat

1. Tạo database trống, ví dụ `libman_db`, dùng utf8mb4 và InnoDB. Tạo tài khoản MySQL được phép tạo bảng và ghi dữ liệu trong riêng database này.
2. Cấu hình biến môi trường cho tiến trình Tomcat (hoặc terminal chạy Maven):

| Biến | Ý nghĩa |
| --- | --- |
| LIBMAN_DB_URL | JDBC URL, ví dụ `jdbc:mysql://127.0.0.1:3306/libman_db?connectionTimeZone=Asia/Ho_Chi_Minh` |
| LIBMAN_DB_USER | Tên người dùng MySQL |
| LIBMAN_DB_PASSWORD | Mật khẩu MySQL |
| LIBMAN_BOOTSTRAP | Đặt `true` trong lần khởi động đầu để tạo 12 bảng và ba tài khoản khởi đầu. Sau đó bỏ biến này. |
| LIBMAN_MEMBER_PASSWORD, LIBMAN_LIBRARIAN_PASSWORD, LIBMAN_MANAGER_PASSWORD | Ba mật khẩu khác nhau, mỗi mật khẩu ít nhất 10 ký tự, lần lượt cho bạn đọc, nhân viên và quản lý của database MySQL mới. Chỉ cần trong lần khởi tạo. |

3. Chạy `mvn clean package`. Chép `target/libman.war` vào thư mục `webapps` của Tomcat 11 (Java 17+). Mở `http://localhost:8080/libman/`. Để dùng máy chủ nhúng với MySQL, đặt cùng các biến môi trường rồi chạy lệnh ở phần chạy thử. Khi có LIBMAN_DB_URL, ứng dụng sử dụng database đó. Danh mục minh họa và gợi ý điền mật khẩu trên giao diện chỉ bật trong chế độ H2 cục bộ.
4. Khi đưa vào sử dụng qua mạng, cấu hình HTTPS cho Tomcat hoặc reverse proxy, bật Secure cho cookie phiên ở lớp triển khai và cấp quyền MySQL theo môi trường. Giữ bí mật triển khai ngoài Git. Tài khoản MySQL mới khởi tạo cùng tên vai trò như bảng trên, dùng mật khẩu người cài đặt cung cấp.

## Mã nguồn và kiểm thử

- `src/main/java/vn/libman/model/` — người dùng, kiểm tra dữ liệu, xử lý mật khẩu.
- `src/main/java/vn/libman/dao/` — truy cập tài khoản, danh mục, kho, mượn/trả và báo cáo.
- `src/main/java/vn/libman/web/Api.java` — servlet điều phối, phân quyền và ranh giới giao dịch.
- `src/main/webapp/` — giao diện JSP và các màn hình JavaScript dùng chung theo vai trò. Ảnh phòng đọc được tạo riêng cho ứng dụng.
- `src/main/resources/schema.sql` — lược đồ dữ liệu.
- `src/test/java/vn/libman/` — máy chủ chạy thử và kiểm thử tích hợp HTTP.

Chạy `mvn test` để kiểm tra phân quyền, tra cứu, vòng đời thẻ, gửi lặp đơn nhập, rollback khi lỗi giữa chừng, xác nhận thanh toán lặp, tranh chấp cho mượn, trả từng phần và ba báo cáo. Mặc định bộ kiểm thử sử dụng database H2 trong bộ nhớ. Có thể chỉ định database **MySQL trống dành riêng cho kiểm thử** bằng `LIBMAN_TEST_DB_URL`, `LIBMAN_DB_USER` và `LIBMAN_DB_PASSWORD`. Bộ kiểm thử sẽ tạo dữ liệu thử trong database đó.
