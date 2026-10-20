/*
  StaffFlow - seed.sql (PHASE 5, SQL Server / SSMS 22)
  -----------------------------------------------------
  DỮ LIỆU GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH - không phải dữ liệu thật của thư viện.
  Chỉ dùng mã vị trí NV-01..NV-08, không có họ tên thật (Nghị định 13/2023/NĐ-CP).

  Chạy sau schema.sql, trong cùng phiên SSMS 22 (USE StaffFlow đã có hiệu lực). An toàn chạy lại nhiều lần:
  TRUNCATE/DELETE theo thứ tự con -> cha trước khi INSERT lại, rồi RESEED IDENTITY về 0 để ID khớp với các
  giá trị cố định dùng trong mã Java (ví dụ NV-01 luôn là id = 1) — xem SqlServerOrgRepository và các lớp
  repository/sqlserver/... khác đều giả định thứ tự ID này giống hệt DemoDataSeeder (Phase 1-4, hồ sơ "memory").

  Mật khẩu demo cho cả 8 tài khoản: Demo@2026 (băm BCrypt cố định bên dưới, do Spring Security BCryptPasswordEncoder
  sinh ra — có thể băm lại bằng bcrypt nếu muốn đổi mật khẩu, salt ngẫu nhiên mỗi lần băm nên chuỗi sẽ khác
  nhưng vẫn khớp "Demo@2026").

  Tuần chính (2026-10-19): phản ánh thực trạng thiếu nhân lực của báo cáo (Bảng 14): 96/207 giờ-cơ sở = 46,4%
  (nằm trong khoảng 36-48%). KHÔNG tự phân ca cho đủ 207 giờ.
  Tuần kiểm thử (2026-10-26): phủ đủ 207 giờ, chỉ để kiểm tra trường hợp K1 = 100%.
*/

USE StaffFlow;
GO

-- ===================================================================== Xóa dữ liệu cũ (con -> cha) + reseed IDENTITY

DELETE FROM dbo.reconciliation_flagged_tasks;
DELETE FROM dbo.reconciliation_items;
DELETE FROM dbo.reconciliation_checks;
DELETE FROM dbo.monthly_reports;
DELETE FROM dbo.survey_responses;
DELETE FROM dbo.task_events;
DELETE FROM dbo.tasks;
DELETE FROM dbo.task_templates;
DELETE FROM dbo.shift_swap_requests;
DELETE FROM dbo.absence_records;
DELETE FROM dbo.audit_logs;
DELETE FROM dbo.shifts;
DELETE FROM dbo.cross_training;
DELETE FROM dbo.branch_holidays;
DELETE FROM dbo.users;
DELETE FROM dbo.branches;

-- Đặt lại IDENTITY để NV-01 = id 1, cơ sở DBP = id 1... Lưu ý: với bảng CHƯA TỪNG có dòng (vừa tạo bằng
-- schema.sql), RESEED 0 làm dòng đầu tiên nhận id = 0; với bảng đã từng có dòng, RESEED 0 làm dòng đầu nhận id = 1.
-- Vì vậy chọn giá trị theo last_value: NULL (chưa từng dùng) -> RESEED 1; ngược lại -> RESEED 0.
DECLARE @reseed NVARCHAR(MAX);
SELECT @reseed = STRING_AGG(
    CAST(N'DBCC CHECKIDENT (''dbo.' + t.name + N''', RESEED, '
         + CASE WHEN ic.last_value IS NULL THEN N'1' ELSE N'0' END + N') WITH NO_INFOMSGS;' AS NVARCHAR(MAX)),
    CHAR(10))
FROM sys.tables AS t
JOIN sys.identity_columns AS ic ON ic.object_id = t.object_id
WHERE t.schema_id = SCHEMA_ID(N'dbo')
  AND t.name IN (N'branches', N'users', N'shifts', N'audit_logs', N'cross_training', N'absence_records',
                 N'shift_swap_requests', N'task_templates', N'tasks', N'task_events', N'survey_responses',
                 N'monthly_reports', N'reconciliation_checks');
EXEC sys.sp_executesql @reseed;
GO

-- ===================================================================== Cơ sở (mục 4.1) — id 1=DBP, 2=DS, 3=D2

INSERT INTO dbo.branches (code, name, address, note) VALUES
 (N'DBP', N'Điện Biên Phủ', N'215 Điện Biên Phủ, tầng 3 (trụ sở chính)', N''),
 (N'DS',  N'Đầm Sen',       N'120 Hòa Bình (khai trương 19/8/2026)', N''),
 (N'D2',  N'Cơ sở D2',      N'36/70 Nguyễn Gia Trí, tầng 1 tòa B',
          N'Địa chỉ là giả định của nhóm, cần xác nhận [GĐ][KC]');
GO

-- ===================================================================== Nhân sự (mục 4.2) — id 1..8 = NV-01..NV-08
-- Mật khẩu demo chung: Demo@2026 (BCrypt). KHÔNG dùng hash này cho môi trường thật — chỉ phục vụ học tập.

DECLARE @pwd NVARCHAR(100) = N'$2b$10$dy7VR2R///o3TkWBz6Qj9OiVrTIVi/Oodi5c56XS2KHvR1ulNaUji';

INSERT INTO dbo.users (code, display_name, title_group, role, branch_id, password_hash) VALUES
 (N'NV-01', N'NV-01 – Giám đốc',            N'GIAM_DOC',        N'ADMIN',           1, @pwd),
 (N'NV-02', N'NV-02 – Phó giám đốc',        N'PHO_GIAM_DOC',    N'BRANCH_MANAGER',  2, @pwd),
 (N'NV-03', N'NV-03 – CV xử lý tài liệu',   N'XU_LY_TAI_LIEU',  N'STAFF',           1, @pwd),
 (N'NV-04', N'NV-04 – CV xử lý tài liệu',   N'XU_LY_TAI_LIEU',  N'STAFF',           2, @pwd),
 (N'NV-05', N'NV-05 – CV hệ thống',         N'HE_THONG',        N'TECH',            1, @pwd),
 (N'NV-06', N'NV-06 – CV hệ thống',         N'HE_THONG',        N'TECH',            3, @pwd),
 (N'NV-07', N'NV-07 – CV phục vụ bạn đọc',  N'PHUC_VU_BAN_DOC', N'STAFF',           1, @pwd),
 (N'NV-08', N'NV-08 – CV phục vụ bạn đọc',  N'PHUC_VU_BAN_DOC', N'STAFF',           2, @pwd);
GO

-- ===================================================================== Đào tạo chéo (mục 4.3)
-- Xử lý tài liệu <-> Phục vụ bạn đọc (NV-03/04 <-> NV-07/08); Hệ thống <-> Xử lý tài liệu (NV-05/06 <-> NV-03/04).
-- NV-03 "Đang đào tạo" để thay cho hệ thống: minh họa quy tắc chỉ bản ghi "đã hoàn thành" mới tính.

INSERT INTO dbo.cross_training (user_id, can_substitute_for, status) VALUES
 (3, N'PHUC_VU_BAN_DOC', N'DA_HOAN_THANH'),
 (7, N'XU_LY_TAI_LIEU',  N'DA_HOAN_THANH'),
 (4, N'PHUC_VU_BAN_DOC', N'DA_HOAN_THANH'),
 (8, N'XU_LY_TAI_LIEU',  N'DA_HOAN_THANH'),
 (5, N'XU_LY_TAI_LIEU',  N'DA_HOAN_THANH'),
 (3, N'HE_THONG',        N'DANG_DAO_TAO'),
 (6, N'XU_LY_TAI_LIEU',  N'DA_HOAN_THANH'),
 (4, N'HE_THONG',        N'DA_HOAN_THANH');
GO

-- ===================================================================== Danh mục nhiệm vụ chuẩn (mục 4.4)
-- 16 nhóm hoạt động theo Điều 7 Thông tư 14/2023/TT-BGDĐT (5 nhóm phục vụ + 11 nhóm nghiệp vụ).
-- TÊN RÚT GỌN theo cách hiểu của nhóm — cần đối chiếu văn bản gốc. Định mức giờ là GIẢ ĐỊNH để demo tải việc.

INSERT INTO dbo.task_templates (ord, category, name, main_group, backup_group, estimated_minutes) VALUES
 (1,  N'PHUC_VU',   N'Phục vụ bạn đọc tại quầy, lưu thông tài liệu', N'PHUC_VU_BAN_DOC', N'XU_LY_TAI_LIEU', 180),
 (2,  N'PHUC_VU',   N'Hướng dẫn, hỗ trợ tra cứu, sử dụng thư viện', N'PHUC_VU_BAN_DOC', N'XU_LY_TAI_LIEU', 120),
 (3,  N'PHUC_VU',   N'Quản lý phòng đọc, phòng học nhóm', N'PHUC_VU_BAN_DOC', N'HE_THONG', 240),
 (4,  N'PHUC_VU',   N'Tổ chức không gian đọc, trưng bày tài liệu', N'PHUC_VU_BAN_DOC', N'PHO_GIAM_DOC', 180),
 (5,  N'PHUC_VU',   N'Hỗ trợ đào tạo kỹ năng thông tin cho bạn đọc', N'PHUC_VU_BAN_DOC', N'XU_LY_TAI_LIEU', 120),
 (6,  N'NGHIEP_VU', N'Xây dựng, phát triển nguồn tài nguyên thông tin', N'GIAM_DOC', N'XU_LY_TAI_LIEU', 360),
 (7,  N'NGHIEP_VU', N'Xử lý kỹ thuật tài liệu (phân loại DDC, biên mục MARC 21)', N'XU_LY_TAI_LIEU', N'HE_THONG', 300),
 (8,  N'NGHIEP_VU', N'Bảo quản, kiểm kê tài liệu', N'XU_LY_TAI_LIEU', N'PHUC_VU_BAN_DOC', 240),
 (9,  N'NGHIEP_VU', N'Xây dựng sản phẩm, dịch vụ thông tin – thư viện', N'XU_LY_TAI_LIEU', N'HE_THONG', 240),
 (10, N'NGHIEP_VU', N'Liên thông, chia sẻ nguồn lực giữa các thư viện', N'GIAM_DOC', N'XU_LY_TAI_LIEU', 180),
 (11, N'NGHIEP_VU', N'Tổ chức hoạt động văn hóa đọc, truyền thông tri thức', N'PHO_GIAM_DOC', N'PHUC_VU_BAN_DOC', 300),
 (12, N'NGHIEP_VU', N'Phát triển thư viện số, số hóa tài liệu', N'HE_THONG', N'XU_LY_TAI_LIEU', 360),
 (13, N'NGHIEP_VU', N'Hiện đại hóa hạ tầng, ứng dụng công nghệ (RFID, OPAC, hệ thống)', N'HE_THONG', N'GIAM_DOC', 300),
 (14, N'NGHIEP_VU', N'Truyền thông, quảng bá hoạt động thư viện', N'PHO_GIAM_DOC', N'PHUC_VU_BAN_DOC', 180),
 (15, N'NGHIEP_VU', N'Hợp tác, liên kết trong nước và quốc tế', N'GIAM_DOC', N'PHO_GIAM_DOC', 240),
 (16, N'NGHIEP_VU', N'Đánh giá, báo cáo hiệu quả hoạt động hằng năm', N'GIAM_DOC', N'PHO_GIAM_DOC', 480);
GO

-- ===================================================================== Lịch trực tuần chính (19-25/10/2026)
-- 96/207 giờ-cơ sở có người = 46,4% (khớp khoảng 36-48% của báo cáo, Bảng 14). KHÔNG tự phân ca cho đủ 207 giờ.

-- NV-07 (id 7, ĐBP) và NV-08 (id 8, Đầm Sen): quầy thứ Hai-Sáu 08:00-14:00 -> 30 + 30 giờ
INSERT INTO dbo.shifts (branch_id, date, start_time, end_time, user_id, status, note, test_data) VALUES
 (1, '2026-10-19', '08:00', '14:00', 7, N'DU_NGUOI', N'', 0),
 (1, '2026-10-20', '08:00', '14:00', 7, N'DU_NGUOI', N'', 0),
 (1, '2026-10-21', '08:00', '14:00', 7, N'DU_NGUOI', N'', 0),
 (1, '2026-10-22', '08:00', '14:00', 7, N'DU_NGUOI', N'', 0),
 (1, '2026-10-23', '08:00', '14:00', 7, N'DU_NGUOI', N'', 0),
 (2, '2026-10-19', '08:00', '14:00', 8, N'DU_NGUOI', N'', 0),
 (2, '2026-10-20', '08:00', '14:00', 8, N'DU_NGUOI', N'', 0),
 (2, '2026-10-21', '08:00', '14:00', 8, N'DU_NGUOI', N'', 0),
 (2, '2026-10-22', '08:00', '14:00', 8, N'DU_NGUOI', N'', 0),
 (2, '2026-10-23', '08:00', '14:00', 8, N'DU_NGUOI', N'', 0),
-- Hỗ trợ quầy buổi chiều: NV-03 (ĐBP, T2+T4), NV-05 (ĐBP, T3+T5), NV-04 (Đầm Sen, T3+T5), NV-06 (D2, T2/T4/T6)
 (1, '2026-10-19', '14:00', '18:00', 3, N'DU_NGUOI', N'', 0),
 (1, '2026-10-21', '14:00', '18:00', 3, N'DU_NGUOI', N'', 0),
 (1, '2026-10-20', '14:00', '18:00', 5, N'DU_NGUOI', N'', 0),
 (1, '2026-10-22', '14:00', '18:00', 5, N'DU_NGUOI', N'', 0),
 (2, '2026-10-20', '14:00', '18:00', 4, N'DU_NGUOI', N'', 0),
 (2, '2026-10-22', '14:00', '18:00', 4, N'DU_NGUOI', N'', 0),
 (3, '2026-10-19', '08:00', '12:00', 6, N'DU_NGUOI', N'', 0),
 (3, '2026-10-21', '08:00', '12:00', 6, N'DU_NGUOI', N'', 0),
 (3, '2026-10-23', '08:00', '12:00', 6, N'DU_NGUOI', N'', 0),
-- Ca đã lập nhưng chưa có người (cảnh báo đỏ)
 (3, '2026-10-19', '14:00', '18:00', NULL, N'TRONG', N'', 0),
 (2, '2026-10-24', '08:00', '12:00', NULL, N'TRONG', N'', 0),
 (1, '2026-10-24', '08:00', '12:00', NULL, N'TRONG', N'', 0);
GO

-- ===================================================================== Tuần kiểm thử (26/10-1/11/2026): K1 = 100%
-- {cơ sở, người ca sáng 08-14, người ca chiều 14-20 (17:00 vào thứ Bảy)}: {1,7,3} {2,8,4} {3,6,5}

INSERT INTO dbo.shifts (branch_id, date, start_time, end_time, user_id, status, note, test_data) VALUES
 (1, '2026-10-26', '08:00', '14:00', 7, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-26', '14:00', '20:00', 3, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-27', '08:00', '14:00', 7, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-27', '14:00', '20:00', 3, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-28', '08:00', '14:00', 7, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-28', '14:00', '20:00', 3, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-29', '08:00', '14:00', 7, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-29', '14:00', '20:00', 3, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-30', '08:00', '14:00', 7, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-30', '14:00', '20:00', 3, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-31', '08:00', '14:00', 7, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (1, '2026-10-31', '14:00', '17:00', 3, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-26', '08:00', '14:00', 8, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-26', '14:00', '20:00', 4, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-27', '08:00', '14:00', 8, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-27', '14:00', '20:00', 4, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-28', '08:00', '14:00', 8, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-28', '14:00', '20:00', 4, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-29', '08:00', '14:00', 8, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-29', '14:00', '20:00', 4, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-30', '08:00', '14:00', 8, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-30', '14:00', '20:00', 4, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-31', '08:00', '14:00', 8, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (2, '2026-10-31', '14:00', '17:00', 4, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-26', '08:00', '14:00', 6, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-26', '14:00', '20:00', 5, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-27', '08:00', '14:00', 6, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-27', '14:00', '20:00', 5, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-28', '08:00', '14:00', 6, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-28', '14:00', '20:00', 5, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-29', '08:00', '14:00', 6, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-29', '14:00', '20:00', 5, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-30', '08:00', '14:00', 6, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-30', '14:00', '20:00', 5, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-31', '08:00', '14:00', 6, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1),
 (3, '2026-10-31', '14:00', '17:00', 5, N'DU_NGUOI', N'Dữ liệu kiểm thử K1=100%', 1);
GO

-- ===================================================================== Nhiệm vụ
-- Tuần lịch sử (12/10/2026) để K3 có số liệu (đúng/trễ hạn); tuần chính có nhiệm vụ CHƯA đến hạn so với
-- "bây giờ" (18/10) để minh họa quy tắc không tính vào K3 nhưng vẫn tính vào tải việc. Kịch bản T3 (hội sách
-- trùng tuần kiểm kê): hai nhiệm vụ lớn cùng hạn, cùng người phụ trách NV-03, khiến tải việc vượt 40 giờ/tuần.
-- (mỗi task() trong DemoDataSeeder.java chỉ ghi lại MỘT task_event từ NULL -> trạng thái cuối cùng, không ghi
-- lại bước trung gian "Mới" — tái hiện đúng hành vi đó ở đây.)

INSERT INTO dbo.tasks (title, template_id, branch_id, assignee_id, estimated_minutes, due_date, priority, status,
                       closed_without_evidence, evidence_note, created_at, updated_at) VALUES
 (N'Kiểm kê tài liệu định kỳ quý III', 8, 1, 3, 240, '2026-10-14', N'TRUNG_BINH', N'XONG', 0,
  N'Biên bản kiểm kê đính kèm', '2026-10-12T08:00:00', '2026-10-14T10:00:00'),
 (N'Biên mục lô sách mới nhập', 7, 2, 4, 300, '2026-10-15', N'TRUNG_BINH', N'XONG', 0,
  N'Danh sách biên mục', '2026-10-12T08:00:00', '2026-10-16T09:00:00'),
 (N'Cập nhật dữ liệu OPAC sau bảo trì', 9, 1, 5, 180, '2026-10-13', N'CAO', N'DANG_LAM', 0,
  NULL, '2026-10-12T08:00:00', '2026-10-13T09:00:00'),
 (N'Hội sách mùa khai trương – chuẩn bị gian trưng bày', 6, 1, 3, 1200, '2026-10-22', N'CAO', N'DANG_LAM', 0,
  NULL, '2026-10-19T00:00:00', '2026-10-19T00:00:00'),
 (N'Kiểm kê tài liệu tuần khai trương Đầm Sen', 8, 1, 3, 960, '2026-10-22', N'CAO', N'MOI', 0,
  NULL, '2026-10-19T00:00:00', '2026-10-19T00:00:00'),
 (N'Sắp xếp lại giá sách khu đọc', 2, 1, 7, 120, '2026-10-21', N'THAP', N'XONG', 1,
  NULL, '2026-10-19T00:00:00', '2026-10-19T15:00:00'),
 (N'Hỗ trợ tra cứu cho lớp nhập môn', 2, 2, 8, 120, '2026-10-23', N'TRUNG_BINH', N'CHO_DUYET', 0,
  N'Ảnh buổi hướng dẫn', '2026-10-19T00:00:00', '2026-10-20T09:00:00'),
 (N'Xây dựng đề án hợp tác thư viện số 2027', NULL, NULL, 1, 480, '2026-11-02', N'TRUNG_BINH', N'MOI', 0,
  NULL, '2026-10-19T00:00:00', '2026-10-19T00:00:00');
GO

INSERT INTO dbo.task_events (task_id, from_status, to_status, by_user_id, at) VALUES
 (1, NULL, N'XONG',      3, '2026-10-14T10:00:00'),
 (2, NULL, N'XONG',      4, '2026-10-16T09:00:00'),
 (3, NULL, N'DANG_LAM',  5, '2026-10-13T09:00:00'),
 (4, NULL, N'DANG_LAM',  3, '2026-10-19T00:00:00'),
 (5, NULL, N'MOI',       3, '2026-10-19T00:00:00'),
 (6, NULL, N'XONG',      7, '2026-10-19T15:00:00'),
 (7, NULL, N'CHO_DUYET', 8, '2026-10-20T09:00:00'),
 (8, NULL, N'MOI',       1, '2026-10-19T00:00:00');
GO

-- ===================================================================== K7 (khảo sát) + K8 (báo cáo tháng)
-- 3 phản hồi tuần 4 (điểm trung bình > 3,5 -> minh họa trạng thái Đạt); CHƯA có phản hồi tuần 8 (minh họa N/A).
-- Tháng 09/2026 nộp đúng hạn; tháng 10/2026 CHƯA nộp (minh họa K8 chưa đạt 100%).

INSERT INTO dbo.survey_responses (period, user_id, q1, q2, q3, note, submitted_at) VALUES
 (N'TUAN_4', 3, 3, 4, 5, N'Khối lượng việc ổn, mong rõ lịch sớm hơn', '2026-11-16T10:00:00'),
 (N'TUAN_4', 7, 4, 4, 4, NULL, '2026-11-16T10:00:00'),
 (N'TUAN_4', 8, 3, 3, 4, N'Ca cuối tuần hơi dồn', '2026-11-16T10:00:00');
GO

INSERT INTO dbo.monthly_reports (year_month, due_date, submitted_date, note, recorded_by, recorded_at) VALUES
 ('2026-09', '2026-10-05', '2026-10-03', N'Nộp đúng hạn qua hệ thống', 1, '2026-10-05T00:00:00'),
 ('2026-10', '2026-11-05', NULL, NULL, 1, '2026-11-05T00:00:00');
GO