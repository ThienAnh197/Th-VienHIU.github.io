# StaffFlow – Phase 1 + 2 + 3 + 4 + 5 (HTML/CSS/JS + Java + SQL Server)

Ứng dụng điều phối nhân sự và ca trực đa cơ sở cho dự án môn THU061.2 (Nhóm 3).
Mặc định (`spring.profiles.active=memory`) dữ liệu nằm trong bộ nhớ và nạp lại mỗi lần khởi động — không cần
SQL Server để chạy thử. Phase 5 thêm hồ sơ `sqlserver` dùng SQL Server thật qua `database/schema.sql` +
`database/seed.sql` (xem mục "Phase 5 — chạy với SQL Server" bên dưới); **chuyển hồ sơ KHÔNG đổi một dòng
code service/controller/giao diện nào** vì mọi truy cập dữ liệu đều đi qua interface trong `repository/`.
Lộ trình các phase xem `docs/PHASES.md`.

> DỮ LIỆU GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH – không phải dữ liệu thật của thư viện.
> StaffFlow dùng để điều phối và làm rõ khối lượng việc, không dùng để chấm điểm cá nhân.

## Cần cài

- JDK 21
- Maven 3.9+ (hoặc dùng Maven có sẵn trong VS Code / IntelliJ)
- Không cần Laragon. Hồ sơ `memory` (mặc định) không cần SQL Server. Hồ sơ `sqlserver` (Phase 5) cần
  SQL Server 2019+ và SSMS 22 — xem mục "Phase 5" bên dưới.

## Chạy

```bash
mvn spring-boot:run
```

Mở http://localhost:8080 và đăng nhập. Tài khoản demo (chỉ dùng cho môi trường học tập), mật khẩu chung `Demo@2026`:

| Mã | Vai trò | Quyền tạo/sửa ca | Quyền duyệt đổi ca |
|---|---|---|---|
| NV-01 | Giám đốc (ADMIN) | Cả 3 cơ sở | Toàn hệ thống |
| NV-02 | Phó giám đốc (BRANCH_MANAGER) | **Chỉ Đầm Sen** | **Chỉ ca thuộc Đầm Sen** |
| NV-05, NV-06 | CV hệ thống (TECH) | Chỉ xem | Không được duyệt |
| NV-03, NV-04, NV-07, NV-08 | Chuyên viên (STAFF) | Chỉ xem | Không được duyệt |

Mọi tài khoản có thể báo vắng cho chính mình và gửi yêu cầu đổi ca cho ca mình phụ trách.

## Phase 1 có gì

- Đăng nhập bằng session HTTP (cookie HttpOnly, SameSite), mật khẩu BCrypt, chống CSRF bằng header `X-CSRF-Token`.
  401 nếu chưa đăng nhập, 403 nếu không đủ quyền, 422 nếu dữ liệu sai.
- Danh mục 3 cơ sở và 8 vị trí nhân sự (chỉ mã NV-01…NV-08).
- **Lịch trực theo tuần** cho 3 cơ sở, màu theo trạng thái (Đủ người / Trống / Bị ảnh hưởng do vắng).
- **Thanh bao phủ giờ-cơ sở**: giờ cần phủ (69 giờ/cơ sở, 207 giờ tổng) so với giờ đã có người (test T1).
- Tạo/sửa ca có kiểm tra ở backend: không xếp vào Chủ nhật/ngày lễ, giờ bắt đầu < kết thúc,
  nằm trong giờ mở cửa, không trùng ca của cùng một người (kể cả khác cơ sở).
- Dữ liệu seed:
  - **Tuần 19–25/10/2026**: 96/207 giờ-cơ sở = **46,4%** (khớp khoảng 36–48% của báo cáo, Bảng 14). Có 3 ca đã lập nhưng trống.
  - **Tuần 26/10–1/11/2026**: phủ đủ 207 giờ, đặt nhãn "Dữ liệu kiểm thử", chỉ để kiểm tra K1 = 100%.
- Thời điểm hệ thống được cố định ở 18/10/2026 15:00 (`staffflow.demo-now`) để kết quả lặp lại được. Để trống thuộc tính này nếu muốn dùng giờ thật.

## Phase 2 có gì

- **Báo vắng**: ghi nhận khoảng ngày vắng; các ca Đủ người của người đó trong khoảng ngày tự chuyển sang
  "Bị ảnh hưởng do vắng" (không còn tính vào K1); giữ nguyên người gốc, không tự động gán người thay.
  Báo vắng cho chính mình luôn được phép; báo vắng cho người khác cần quyền quản lý cơ sở của người đó (403 nếu không đủ quyền).
- **Gợi ý người thay** cho một ca cụ thể, đánh giá theo đúng thứ tự mục 6.3 của đặc tả: chức danh phù hợp hoặc có
  đào tạo chéo *đã hoàn thành* (chỉ 2 cặp: Xử lý tài liệu ↔ Phục vụ bạn đọc, Hệ thống ↔ Xử lý tài liệu) → không trùng ca →
  không vắng → không vượt 40 giờ/tuần sau khi nhận ca → có ghi chú nếu phải di chuyển liên cơ sở.
  Hiển thị lý do chọn/loại từng ứng viên và tải việc sau khi thay; nếu không ai đủ điều kiện, báo "Không tìm được người thay thế".
- **Đổi ca**: nhân viên gửi yêu cầu từ một ca mình phụ trách → trạng thái Chờ duyệt. Yêu cầu phải gửi trước giờ bắt đầu
  ca ít nhất 24 giờ, trễ hơn bị từ chối 422 — **trừ khi quản lý tạo thay cho người gửi muộn** (ghi `createdByManager`
  và ghi rõ trong nhật ký). Duyệt theo đúng phạm vi: NV-01 toàn hệ thống, NV-02 chỉ ca thuộc Đầm Sen, người khác → 403.
  Khi duyệt, kiểm tra lại xung đột lịch và tải việc; không duyệt một yêu cầu đã xử lý hai lần (409); ghi người duyệt,
  thời điểm duyệt và thời gian xử lý (K4).
- Mọi ca trực, báo vắng, đổi ca đều ghi `audit_logs` (hành động, người thực hiện, thời điểm, chi tiết).
- Giao diện: mở từ một ca trong màn hình Lịch trực ("Gửi yêu cầu đổi ca" / "Báo vắng"), và tab **Đổi ca & vắng**
  liệt kê yêu cầu đổi ca cùng nút Duyệt/Từ chối (chỉ hiện cho người có quyền) và lịch sử báo vắng.

## Phase 3 có gì

- **Nhiệm vụ dạng Kanban** (4 trạng thái: Mới / Đang làm / Chờ duyệt / Xong), mỗi nhiệm vụ gắn cơ sở, người phụ trách,
  hạn, độ ưu tiên, thời lượng dự kiến; có thể tạo từ **mẫu nhiệm vụ** (`task_templates`) để điền sẵn thời lượng.
  Chỉ ADMIN/BRANCH_MANAGER (trong phạm vi cơ sở mình quản lý) được tạo/sửa nhiệm vụ (403 nếu không đủ quyền);
  người phụ trách hoặc người quản lý được tự đổi trạng thái nhiệm vụ của mình.
  Chuyển sang "Xong" mà không ghi minh chứng vẫn được phép nhưng tự động gắn cờ **"Đóng không có minh chứng"**
  (`closedWithoutEvidence`) để phục vụ đối soát hằng tuần ở Phase 4. Mỗi lần đổi trạng thái được ghi vào nhật ký
  (`task_events`, xem được qua "Xem lịch sử" trên thẻ nhiệm vụ).
- **Tải việc (workload)**: `tải việc (phút) = tổng phút ca trực trong tuần + tổng phút nhiệm vụ có hạn trong tuần`;
  `hệ số tải (%) = tải việc / 2400 phút (40 giờ) × 100%`. Cảnh báo "Vượt 100% tải việc" chỉ hiện khi **thực sự** vượt,
  không có cảnh báo giả. Có bảng theo từng người và bảng tổng hợp theo nhóm chuyên môn (thấp nhất/cao nhất/tỉ lệ).
- **6 KPI (K1–K6)**, mỗi KPI có class tính riêng theo đúng yêu cầu đặc tả, hiển thị công thức khi bấm vào thẻ KPI:
  - K1 Tỉ lệ bao phủ ca trực, K2 Số ca trống trong 24 giờ tới, K3 Tỉ lệ nhiệm vụ đúng hạn (trả N/A nếu tuần đó
    chưa có nhiệm vụ nào đến hạn), K4 Thời gian xử lý yêu cầu đổi ca (trả N/A nếu tuần đó không có yêu cầu nào),
    K5 Mất cân bằng tải việc giữa các nhóm chuyên môn, K6 Tỉ lệ cập nhật trạng thái đúng hạn (giả định do đặc tả
    chưa định nghĩa rõ, ghi chú ngay trong Javadoc của `K6UpdateRateKpi`).
- **Gợi ý theo mốc tuần 4 và tuần 8** kể từ tuần triển khai (`DashboardService`), chỉ hiện ở đúng hai mốc đó.
- **Kịch bản T3** (seed sẵn): NV-03 trong tuần chính vừa có ca trực 8 giờ vừa được giao "Hội sách" (20 giờ) và
  "Kiểm kê" (16 giờ) → tổng 44 giờ > 40 giờ/tuần, hiển thị cảnh báo vượt tải trên cả bảng tải việc và thẻ KPI K5.
- Giao diện: tab **Nhiệm vụ** (bảng Kanban, lọc theo tuần/cơ sở, modal tạo/sửa/đổi trạng thái) và tab
  **KPI & tải việc** (6 thẻ KPI bấm xem công thức, bảng tải việc theo người và theo nhóm, khối gợi ý mốc tuần).

## Phase 4 có gì

- **Xuất báo cáo CSV** (UTF-8 có BOM, mở đúng tiếng Việt bằng Excel), số liệu lấy trực tiếp từ dữ liệu hệ thống
  tại thời điểm xuất, không viết cứng ở backend hay frontend (T6):
  - `GET /api/reports/week.csv?week=yyyy-MM-dd` — bao phủ giờ-cơ sở, danh sách nhiệm vụ trong tuần, tải việc
    theo người/nhóm, và toàn bộ KPI K1–K8 của tuần đó.
  - `GET /api/reports/month.csv` — toàn bộ báo cáo tháng (K8) đã ghi nhận.
  - Nút "Xuất CSV" ở tab **Báo cáo** mở trực tiếp các endpoint này (tệp tải xuống qua trình duyệt).
- **Nhập CSV ca trực và nhiệm vụ** (`POST /api/import/shifts`, `POST /api/import/tasks`, multipart/form-data):
  mỗi dòng được kiểm tra và báo lỗi RIÊNG (một dòng sai không làm hỏng các dòng còn lại), dùng lại đúng
  `ScheduleService`/`TaskService` nên áp dụng mọi quy tắc nghiệp vụ và phân quyền y hệt khi thao tác qua giao diện.
  Cột CSV ca trực: `branchCode,date,start,end,userCode,note`; nhiệm vụ: `title,templateOrd,branchCode,assigneeCode,estimatedMinutes,dueDate,priority`.
- **K7 – khảo sát hài lòng nhân sự**: 3 câu thang điểm 1–5, nộp ở đúng tuần 4 và tuần 8 kể từ tuần triển khai
  (`staffflow.demo-week-start`); mỗi người chỉ nộp được cho chính mình, nộp lại cùng kỳ sẽ ghi đè chứ không nhân bản.
  Mục tiêu ≥ 3,5 điểm; ngoài hai tuần mốc hoặc chưa có phản hồi thì trả `N/A`.
- **K8 – báo cáo tháng nộp đúng hạn**: ghi nhận tháng, hạn nộp, ngày nộp (`POST /api/monthly-reports`, chỉ
  ADMIN/NV-01 vì là số liệu toàn hệ thống); K8 = tỉ lệ số báo cáo đã ghi nhận nộp đúng hạn, mục tiêu 100%,
  tính trên TOÀN BỘ báo cáo đã ghi nhận chứ không lọc theo tuần đang xem (giả định bổ sung, ghi rõ trong Javadoc
  `K8MonthlyReportKpi`).
- **Phiếu đối soát hằng tuần** (mục 6.6, `POST /api/reconciliation`): chọn ngẫu nhiên 10% số ca trong tuần
  (làm tròn lên, tối thiểu 1 ca), kèm danh sách nhiệm vụ đã đóng "Xong" mà không có minh chứng trong cùng tuần.
  Quản lý/đầu mối cơ sở ghi nhận kết quả khớp/lệch cho từng ca (`PUT /api/reconciliation/{id}/items/{shiftId}`).
  Việc chọn mẫu dùng seed cố định theo tuần + cơ sở để kết quả lặp lại được khi kiểm thử — giả định bổ sung do
  lập trình, không phải yêu cầu của đặc tả.
- Giao diện: tab **Báo cáo** gồm 5 khối — xuất CSV, nhập CSV (kết quả từng dòng), form khảo sát K7 + bảng phản hồi,
  form báo cáo tháng K8 + bảng lịch sử, và phiếu đối soát (tạo phiếu, xem mẫu ca, ghi nhận khớp/lệch, danh sách
  nhiệm vụ gắn cờ).
- CSV tự viết (`CsvUtil`) theo RFC 4180 tối giản thay vì phụ thuộc Apache Commons CSV, để tránh thêm một thư viện
  chưa kiểm chứng được trong môi trường lập trình hiện tại — **giả định bổ sung**, có thể đổi sang Apache Commons
  CSV khi triển khai thật nếu nhóm muốn.

## Phase 5 — chạy với SQL Server

> **CHƯA CHẠY THỬ TRÊN SQL SERVER THẬT TRONG MÔI TRƯỜNG LÀM BÀI** (xem "Trạng thái kiểm chứng" bên dưới) —
> toàn bộ nội dung mục này là hướng dẫn dựa trên cú pháp T-SQL/JDBC chuẩn, nhóm **phải tự chạy thử** trên máy
> có SQL Server trước khi nộp, và báo lại lỗi gặp phải (nếu có) để sửa tiếp.

### 1. Cài đặt SQL Server + SSMS 22

1. Cài SQL Server 2019 trở lên (Developer/Express edition đều được) và SQL Server Management Studio (SSMS) 22.
2. Mở **SQL Server Configuration Manager** → *SQL Server Network Configuration* → *Protocols for \<instance\>* →
   bật **TCP/IP** (nhấp đúp để bật, có thể cần khởi động lại dịch vụ SQL Server sau đó).
3. Nếu dùng instance có tên (ví dụ `SQLEXPRESS`), khởi động dịch vụ **SQL Server Browser** để client tìm được
   cổng động; nếu muốn cổng cố định, đặt tĩnh 1433 ở tab *TCP/IP Properties → IP Addresses → IPAll → TCP Port*.
4. Trong SSMS 22, kết nối instance bằng **Windows Authentication**, vào *Server Properties → Security* và bật
   **SQL Server and Windows Authentication mode** (cần khởi động lại dịch vụ SQL Server để có hiệu lực).
5. Tạo một **login riêng cho ứng dụng** (không dùng `sa`): *Security → Logins → New Login* → đặt tên ví dụ
   `staffflow_app`, chọn SQL Server Authentication, đặt mật khẩu mạnh, bỏ chọn "Enforce password expiration"
   nếu chỉ dùng cho môi trường học tập. Sau khi chạy `schema.sql` (tạo database `StaffFlow`), vào
   *Databases → StaffFlow → Security → Users → New User*, gán login này làm user của database `StaffFlow`
   và cấp vai trò `db_datareader` + `db_datawriter` (đủ cho toàn bộ ứng dụng, không cần `db_owner`).

### 2. Tạo schema và dữ liệu mẫu

1. Trong SSMS 22, mở `database/schema.sql` (kết nối bằng tài khoản có quyền tạo database, ví dụ tài khoản
   Windows Authentication ban đầu), nhấn **Execute** (F5). An toàn chạy lại nhiều lần.
2. Mở `database/seed.sql` trong cùng kết nối (đổi context sang `USE StaffFlow;` nếu SSMS chưa tự chuyển),
   nhấn **Execute**. Script tự xóa dữ liệu cũ và reseed IDENTITY trước khi chèn lại, nên an toàn chạy lại.
3. Kiểm tra nhanh: `SELECT code, display_name FROM dbo.users;` phải ra đúng 8 dòng NV-01..NV-08.

### 3. Chuyển ứng dụng sang hồ sơ `sqlserver`

1. Sao chép `src/main/resources/application-example.properties` thành
   `src/main/resources/application-sqlserver.properties`, chỉnh `spring.datasource.url` cho đúng tên server/
   instance/cổng của máy bạn (ví dụ `jdbc:sqlserver://localhost:1433;databaseName=StaffFlow;encrypt=true;trustServerCertificate=true`
   — `trustServerCertificate=true` chỉ nên dùng khi phát triển/học tập cục bộ, không dùng khi triển khai thật).
2. Đặt mật khẩu qua biến môi trường thay vì ghi cứng vào file: `STAFFFLOW_DB_PASSWORD=<mật khẩu của staffflow_app>`.
3. Chạy `mvn spring-boot:run -Dspring-boot.run.profiles=sqlserver` (hoặc đặt
   `spring.profiles.active=sqlserver` trong `application.properties`). Spring Boot sẽ tự nạp các bean
   `@Repository @Profile("sqlserver")` ở gói `repository/sqlserver/` thay cho `repository/memory/` — không có
   thay đổi nào ở `service/`, `controller/`, hay frontend.

### 4. Khắc phục lỗi kết nối thường gặp

| Lỗi | Nguyên nhân thường gặp | Cách sửa |
|---|---|---|
| `The TCP/IP connection to the host ... has failed` | TCP/IP chưa bật, hoặc sai cổng | Bật TCP/IP ở bước 1.2, kiểm tra lại cổng (1433 hoặc cổng động qua SQL Server Browser) |
| `Login failed for user 'staffflow_app'` | Chưa bật SQL Server Authentication, hoặc login chưa được gán user trong database `StaffFlow` | Lặp lại bước 1.4–1.5, nhớ khởi động lại dịch vụ SQL Server sau khi đổi chế độ xác thực |
| `The driver could not establish a secure connection ... PKIX path building failed` | Chứng chỉ SSL tự ký của SQL Server không được tin cậy | Thêm `trustServerCertificate=true` vào connection string (chỉ dùng khi phát triển/học tập cục bộ) |
| `Cannot open database "StaffFlow"` | Chưa chạy `schema.sql`, hoặc chạy sai instance | Chạy lại bước 2.1, kiểm tra đang kết nối đúng instance SSMS hiển thị ở góc trên bên trái |
| Ứng dụng khởi động nhưng đăng nhập `Demo@2026` báo sai | `seed.sql` chưa chạy, hoặc chạy nhầm database khác | Chạy lại bước 2.2 trong đúng context `StaffFlow` |

### 5. Chống race condition bằng transaction thật (mục 2.1, 6.1, 6.4)

Ở hồ sơ `memory`, việc "kiểm tra rồi ghi" được bảo vệ bằng một khóa trong bộ nhớ (`TxLock`, xem Javadoc trong
`service/TxLock.java`) — chỉ đủ cho một instance ứng dụng chạy đơn lẻ. Ở hồ sơ `sqlserver`, bảo vệ thật sự đến
từ transaction cơ sở dữ liệu:

- **Trùng ca** (mục 2.1, 6.1): `ScheduleService.validate(...)` gọi `ShiftRepository.findBetweenForUpdate(...)`
  thay vì `findBetween(...)` ngay trước khi ghi ca mới/sửa ca — cài đặt ở `SqlServerShiftRepository` bằng
  `SELECT ... FROM dbo.shifts WITH (UPDLOCK, HOLDLOCK) WHERE date BETWEEN ? AND ?`, bọc `@Transactional`.
  `UPDLOCK` giữ khóa ghi ngay từ lúc đọc (không chỉ khóa đọc); `HOLDLOCK` (tương đương `SERIALIZABLE` cho câu
  lệnh này) giữ khóa đến hết transaction thay vì nhả ngay sau câu lệnh — hai request tạo/sửa ca cùng lúc không
  thể cùng đọc "chưa trùng" rồi cùng ghi đè.
- **Duyệt đổi ca hai lần** (mục 6.4): `SwapService.decide(...)` gọi `SwapRepository.findByIdForUpdate(...)`
  thay vì `findById(...)` ngay khi bắt đầu xử lý duyệt/từ chối — cài đặt ở `SqlServerSwapRepository` bằng
  cùng kỹ thuật `WITH (UPDLOCK, HOLDLOCK)` trên dòng yêu cầu đổi ca.
- Cả hai phương thức `...ForUpdate` đều là **default method** trong interface (`ShiftRepository`,
  `SwapRepository`): ở hồ sơ `memory`, mặc định gọi lại phương thức gốc (không đổi hành vi 60 test hiện có);
  ở hồ sơ `sqlserver`, bị cài đè bằng khóa thật. Đây là lý do `ScheduleService`/`SwapService` dùng chung một
  đoạn code cho cả hai hồ sơ mà không cần `if (profile == ...)`.

### 6. Những bảng/tính năng KHÔNG tạo ở Phase 5 (giả định bổ sung, xem thêm đầu `schema.sql`)

`skills`/`user_skills`, `shift_templates`, `task_evidence` (tách riêng khỏi `tasks.evidence_note`),
`weekly_kpi_snapshots` — đặc tả liệt kê là bảng "tối thiểu" (mục 5) nhưng không có luồng nghiệp vụ nào trong
mã Java hiện tại đọc/ghi các bảng này, nên không tạo bảng rỗng không ai dùng tới; mỗi bảng được ghi rõ lý do
và hướng mở rộng hợp lý cho phase sau trong comment đầu `schema.sql`.

## Kiểm thử T6 bằng curl

```bash
# xuất báo cáo tuần — tải về staffflow-bao-cao-tuan-2026-10-19.csv, mở bằng Excel kiểm tra tiếng Việt hiển thị đúng
curl -s -b jar.txt "http://localhost:8080/api/reports/week.csv?week=2026-10-19" -o bao-cao-tuan.csv
# xuất báo cáo tháng
curl -s -b jar.txt "http://localhost:8080/api/reports/month.csv" -o bao-cao-thang.csv
# nhập CSV ca trực (tạo file shifts.csv với nội dung mẫu ở trên trước)
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -F "file=@shifts.csv" http://localhost:8080/api/import/shifts
# tạo phiếu đối soát tuần chính, toàn hệ thống
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -H "Content-Type: application/json" \
  -d '{"week":"2026-10-19","branchId":null}' http://localhost:8080/api/reconciliation
```

## Kiểm thử T3 bằng curl

```bash
# xem tải việc tuần chính — NV-03 phải có totalMinutes = 2640 (44 giờ) và overLimit = true
curl -s -b jar.txt "http://localhost:8080/api/dashboard?week=2026-10-19"
# danh sách nhiệm vụ của NV-03 trong tuần chính (thay <id> bằng assigneeId của NV-03)
curl -s -b jar.txt "http://localhost:8080/api/tasks?week=2026-10-19&assigneeId=<id>"
# đổi trạng thái một nhiệm vụ sang "Xong" không ghi minh chứng -> closedWithoutEvidence = true
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"XONG","evidenceNote":""}' http://localhost:8080/api/tasks/<id>/status
```

## Kiểm thử T1 bằng curl

```bash
# đăng nhập, lưu cookie, lấy CSRF token
curl -s -c jar.txt -H "Content-Type: application/json" \
  -d '{"code":"NV-01","password":"Demo@2026"}' http://localhost:8080/api/auth/login
# xem lịch tuần 19/10 (kiểm tra requiredMinutes = 12420 = 207 giờ; coveredMinutes = 5760 = 96 giờ)
curl -s -b jar.txt "http://localhost:8080/api/schedule?week=2026-10-19"
# tuần kiểm thử phủ đủ: coveredMinutes = 12420
curl -s -b jar.txt "http://localhost:8080/api/schedule?week=2026-10-26"
# ca vào Chủ nhật phải bị từ chối 422 (thay TOKEN bằng csrfToken ở bước đăng nhập)
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -H "Content-Type: application/json" \
  -d '{"branchId":1,"date":"2026-10-25","start":"08:00","end":"12:00"}' http://localhost:8080/api/shifts
```

## Kiểm thử T2, T4, T5 bằng curl (tóm tắt)

```bash
# T2 – báo vắng NV-07 (id=7) từ 19/10 đến 21/10, xem ca bị ảnh hưởng + gợi ý thay
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -H "Content-Type: application/json" \
  -d '{"userId":7,"from":"2026-10-19","to":"2026-10-21","reason":"Nghỉ ốm"}' http://localhost:8080/api/absences
curl -s -b jar.txt "http://localhost:8080/api/absences/candidates/<shiftId>"

# T4 – NV-07 tạo yêu cầu đổi ca cho một ca tuần 26/10 (đăng nhập lại bằng NV-07), NV-01 duyệt
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -H "Content-Type: application/json" \
  -d '{"shiftId":<id>,"proposedUserId":4,"reason":"Bận việc"}' http://localhost:8080/api/swaps
curl -s -b jar.txt -H "X-CSRF-Token: TOKEN" -H "Content-Type: application/json" \
  -d '{"note":"Đồng ý"}' http://localhost:8080/api/swaps/<id>/approve

# T5 – đăng nhập NV-08, gọi thẳng API duyệt phải trả 403
curl -s -b jar08.txt -H "X-CSRF-Token: TOKEN08" -H "Content-Type: application/json" \
  -d '{"note":""}' http://localhost:8080/api/swaps/<id>/approve
```

Bảng ghi kết quả (nhóm tự điền sau khi chạy thật):

| Test | Kết quả thực tế | Điều chỉnh |
|---|---|---|
| T1 Tạo lịch tuần | | |
| T2 NV-07 nghỉ ốm 3 ngày | | |
| T4 Duyệt đổi ca | | |
| T5 NV-08 duyệt ca (phải 403) | | |

## Trạng thái kiểm chứng (trung thực)

- **Đã chạy**: 60 test (`ScheduleServiceTest` 19 test cho T1 và quy tắc ca trực; `SwapServiceTest` 9 test cho T2, T4, T5
  và các case 403 phụ; `TaskKpiTest` 13 test cho T3, Kanban nhiệm vụ và KPI K1–K6; `Phase4Test` 19 test cho T6,
  K7, K8, nhập/xuất CSV và phiếu đối soát) chạy ở mức service, cộng kiểm tra cú pháp JS bằng `node --check`
  (gồm `tasks.js`, `dashboard.js`, `reports.js`). Cả 60 test này dùng hồ sơ `memory` và **không bị Phase 5 ảnh
  hưởng** (các phương thức `findBetweenForUpdate`/`findByIdForUpdate` mới là default method, mặc định gọi lại
  hành vi cũ ở hồ sơ `memory` — xem mục "Phase 5" ở trên).
  Môi trường làm việc không tải được thư viện Maven nên các test này chạy trên bản giả lập tối thiểu của Spring/JUnit
  (có mở rộng thêm ở Phase 4 để hỗ trợ multipart, `ResponseEntity`, `@Min`/`@Max`; mở rộng thêm ở Phase 5 để
  hỗ trợ `JdbcTemplate`, `RowMapper`, `GeneratedKeyHolder`, `@Transactional`), không phải Spring Boot thật.
  Toàn bộ mã nguồn chính — bao gồm 12 lớp `repository/sqlserver/*` mới của Phase 5 — biên dịch sạch bằng
  `javac` với bộ giả lập này (không có lỗi/cảnh báo biên dịch), nhưng đây **chỉ là kiểm tra biên dịch**, không
  phải kiểm tra hành vi khi chạy thật.
- **Chưa kiểm chứng (Phase 1–4)**: `mvn spring-boot:run` thật, các controller/Spring Security-crypto/Jackson trên
  Spring Boot thật (đặc biệt là nhập/xuất CSV qua multipart và tệp tải xuống trình duyệt, chưa thử trên Tomcat
  embedded thật), và giao diện trên trình duyệt (chưa mở thật, kể cả tab Nhiệm vụ, KPI & tải việc, và Báo cáo).
- **Chưa kiểm chứng (Phase 5, SQL Server) — mức độ "chưa kiểm chứng" cao hơn hẳn các phase trước**: toàn bộ
  `database/schema.sql`, `database/seed.sql`, và 12 lớp `repository/sqlserver/*` **CHƯA TỪNG CHẠY VỚI SQL
  SERVER THẬT** trong môi trường làm bài này (sandbox không có SQL Server, không có driver `mssql-jdbc` thật,
  chỉ có bản giả lập `JdbcTemplate` ném `UnsupportedOperationException` nếu gọi). Cụ thể chưa kiểm chứng được:
  - `schema.sql` có chạy đúng trên một instance SQL Server thật không (cú pháp, ràng buộc `CHECK`/`UNIQUE`/
    `FOREIGN KEY`, collation `Vietnamese_CI_AS`).
  - `seed.sql` có chèn đúng dữ liệu, đúng khớp với `DemoDataSeeder.java` không, và băm BCrypt
    `$2b$10$dy7VR2R///o3TkWBz6Qj9OiVrTIVi/Oodi5c56XS2KHvR1ulNaUji` (sinh bằng thư viện `bcrypt` của Python,
    đã tự kiểm `bcrypt.checkpw` trả `True`) có thật sự được `BCryptPasswordEncoder` của Spring Security xác
    thực đúng mật khẩu `Demo@2026` hay không — tổ hợp "băm bằng Python, xác thực bằng Spring" này **chưa được
    thử trong môi trường này**.
  - Toàn bộ `RowMapper`/kiểu dữ liệu JDBC (`java.sql.Date`/`Time`/`Timestamp` ↔ `LocalDate`/`LocalTime`/
    `LocalDateTime`, cột `NULL` ↔ kiểu bọc `Long`/`Boolean`) có khớp đúng với driver `mssql-jdbc` thật không.
  - Câu `SELECT ... WITH (UPDLOCK, HOLDLOCK)` ở `SqlServerShiftRepository.findBetweenForUpdate` và
    `SqlServerSwapRepository.findByIdForUpdate` có thật sự chống được race condition khi hai request chạy
    đồng thời không — đây là phần **quan trọng nhất cần nhóm tự kiểm thử thủ công** (ví dụ: mở hai phiên SSMS,
    một phiên giữ transaction chưa commit sau khi `SELECT ... WITH (UPDLOCK, HOLDLOCK)`, phiên còn lại thử
    `SELECT`/`UPDATE` cùng dòng để quan sát việc bị chặn).
  - Toàn bộ hướng dẫn SSMS 22/cấu hình kết nối ở mục "Phase 5" ở trên dựa trên tài liệu/kiến thức chung về SQL
    Server, chưa được thao tác thực tế trên máy có SQL Server trong phiên làm việc này.

  **Nhóm bắt buộc phải tự chạy `mvn spring-boot:run -Dspring-boot.run.profiles=sqlserver` trên máy có SQL
  Server trước khi nộp**, đối chiếu lại các mục trên, và báo lại nguyên văn lỗi gặp phải (nếu có) để sửa tiếp —
  đừng chấm điểm hay nộp bài dựa trên giả định rằng mã Phase 5 đã chạy đúng chỉ vì nó biên dịch sạch.
  Nếu gặp lỗi khi chạy lần đầu (ở bất kỳ phase nào), gửi nguyên nội dung lỗi để sửa.

## Giả định bổ sung do lập trình (không phải dữ liệu của PDF)

- Spring Boot 3.5.6, Java 21, kho dữ liệu sau interface repository; Phase 5 thêm hồ sơ `sqlserver`
  (`JdbcTemplate`) bên cạnh hồ sơ `memory` có sẵn, chọn qua `spring.profiles.active`.
- Ca trực seed là giả định của nhóm; lịch "tuần chính" được chọn để K1 nằm trong 36–48%.
- Địa chỉ cơ sở D2 (36/70 Nguyễn Gia Trí) là giả định cần xác nhận, đã ghi chú trên giao diện.
- Dùng thống nhất thuật ngữ "nhóm chức danh" (PDF dùng "nhóm vị trí").
- Phase 5: không tạo 4 bảng "tối thiểu" theo mục 5 của đặc tả (`skills`/`user_skills`, `shift_templates`,
  `task_evidence`, `weekly_kpi_snapshots`) vì không có luồng nghiệp vụ nào trong mã Java đọc/ghi — xem lý do
  chi tiết trong comment đầu `database/schema.sql`. Tên bảng `branch_holidays` (đổi từ "holidays" trong mục 5)
  để khớp đúng tên cột đã dùng trong mã Java.
- Phase 5: băm mật khẩu demo trong `seed.sql` sinh bằng thư viện `bcrypt` của Python (không phải Spring/Java)
  vì môi trường làm việc không chạy được Spring Boot thật — đã tự kiểm `bcrypt.checkpw` đúng với `Demo@2026`,
  nhưng khả năng tương thích với `BCryptPasswordEncoder` của Spring Security khi chạy thật **chưa được xác
  nhận trong môi trường này** (xem "Trạng thái kiểm chứng").
- Logo `src/main/resources/static/img/logo-hiu.png`: do người dùng (nhóm) tải lên trực tiếp trong phiên làm
  việc, không phải tự động tải từ `thuvien.hiu.vn` (môi trường lập trình chặn truy cập ra ngoài tới các
  trang web ngoài registry gói). Đã cắt bỏ phần chú thích/ảnh thừa và làm nền trong suốt; nhóm nên tự đối
  chiếu lại với logo chính thức mới nhất của trường trước khi dùng cho mục đích chính thức.
