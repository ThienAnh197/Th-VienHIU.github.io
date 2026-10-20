# Lộ trình các phase

Mỗi phase kết thúc bằng một bản chạy được. Giao diện và service không đổi khi chuyển dữ liệu sang SQL Server,
vì mọi truy cập dữ liệu đi qua interface trong `repository/`.

| Phase | Nội dung | Test case | Dữ liệu |
|---|---|---|---|
| **1** ✔ viết xong | Khung Spring Boot, đăng nhập/phân quyền, danh mục, lịch trực 3 cơ sở, kiểm tra ca, bao phủ giờ-cơ sở | T1 | Bộ nhớ |
| **2** ✔ viết xong | Báo vắng; gợi ý người thay (chức danh, đào tạo chéo, trùng ca, tải ≤ 40 giờ, liên cơ sở); yêu cầu đổi ca (hạn 24 giờ), duyệt theo phạm vi NV-01/NV-02, 403 cho người khác | T2, T4, T5 | Bộ nhớ |
| **3** ✔ viết xong | Kanban nhiệm vụ 4 trạng thái (+ mẫu nhiệm vụ), tải việc (`Vượt 100% tải việc`) theo người/nhóm, KPI K1–K6 mỗi KPI một class, dashboard, gợi ý quyết định tuần 4/8, giao diện Kanban + bảng KPI | T3 | Bộ nhớ |
| **4** ✔ viết xong | Xuất CSV UTF-8 có BOM (tuần + tháng), nhập CSV ca/nhiệm vụ (báo lỗi từng dòng), khảo sát K7, báo cáo tháng K8, phiếu đối soát 10% ca + nhiệm vụ không minh chứng, giao diện tab Báo cáo | T6 | Bộ nhớ |
| **5** ✔ viết xong — **CHƯA CHẠY THỬ TRÊN SQL SERVER THẬT** | SQL Server + SSMS 22: `database/schema.sql`, `seed.sql` (T-SQL, `NVARCHAR`, `Vietnamese_CI_AS`), 12 repository JdbcTemplate (`repository/sqlserver/*`), transaction `UPDLOCK, HOLDLOCK` (chống trùng ca + chống duyệt đổi ca hai lần), README mục "Phase 5" (cấu hình TCP/IP + login riêng + khắc phục lỗi kết nối) | T1–T6 (chạy lại được trên hồ sơ `sqlserver`, chưa tự kiểm chứng) | SQL Server |

## Điểm đã nối ở Phase 5

- `TxLock` (khóa trong bộ nhớ, vẫn giữ nguyên cho hồ sơ `memory`) — Javadoc đã cập nhật nói rõ vai trò phòng
  thủ theo chiều sâu ở hồ sơ `sqlserver`; bảo vệ thật đến từ transaction `UPDLOCK, HOLDLOCK` bên dưới.
- `repository/memory/*` (12 lớp) có đối ứng `repository/sqlserver/*` (12 lớp mới) với `@Profile("sqlserver")`,
  cùng cài đặt interface trong `repository/` — `service/`, `controller/`, frontend không đổi dòng nào.
- `ShiftRepository.findBetweenForUpdate(...)` và `SwapRepository.findByIdForUpdate(...)`: default method mới
  (mặc định gọi lại phương thức gốc ở hồ sơ `memory` — không đổi hành vi 60 test hiện có), cài đè bằng
  `SELECT ... WITH (UPDLOCK, HOLDLOCK)` ở hồ sơ `sqlserver`. `ScheduleService.validate(...)` và
  `SwapService.decide(...)` đã đổi sang gọi hai phương thức này tại đúng điểm ghi đè.
- `DemoDataSeeder` (hồ sơ `memory`) -> `database/seed.sql` (hồ sơ `sqlserver`): cùng dữ liệu seed, dùng băm
  BCrypt thật cho mật khẩu `Demo@2026`.
- Bỏ comment 2 dependency (`spring-boot-starter-jdbc`, `mssql-jdbc`) trong `pom.xml`; chọn hồ sơ qua
  `spring.profiles.active=sqlserver` hoặc `-Dspring-boot.run.profiles=sqlserver`.

## Còn lại cho nhóm tự làm trước khi nộp

- Tự chạy `mvn spring-boot:run -Dspring-boot.run.profiles=sqlserver` trên máy có SQL Server thật, chạy lại
  T1–T6 (các lệnh `curl` trong README vẫn dùng được, không đổi API) và báo lỗi nếu có.
- Tự kiểm thử race condition thật (hai phiên SSMS/hai request đồng thời) cho hai điểm `UPDLOCK, HOLDLOCK`.
