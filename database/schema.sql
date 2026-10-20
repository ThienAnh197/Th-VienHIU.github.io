/*
  StaffFlow - schema.sql (PHASE 5, SQL Server / SSMS 22)
  ------------------------------------------------------
  Chạy toàn bộ tệp này trong SSMS 22 (kết nối instance của bạn, mở tệp, nhấn Execute / F5).
  An toàn chạy lại nhiều lần: mỗi CREATE TABLE đều có IF OBJECT_ID(...) IS NOT NULL DROP TABLE ... trước đó.
  Thứ tự DROP đi từ bảng con -> bảng cha (vì có FOREIGN KEY); thứ tự CREATE ngược lại.

  QUY ƯỚC:
  - Chuỗi: NVARCHAR (hỗ trợ tiếng Việt có dấu). Database tạo với collation Vietnamese_CI_AS.
  - Ngày/giờ: DATE, TIME(0), DATETIME2(0).
  - Khóa chính: BIGINT IDENTITY(1,1).
  - Tên cột dùng snake_case để khớp quy ước T-SQL phổ biến; Java (JdbcTemplate) map thủ công sang
    các record trong domain/Domain.java bằng RowMapper, không dùng ORM.

  GIẢ ĐỊNH BỔ SUNG (không phải yêu cầu của đặc tả, ghi rõ để không lẫn với dữ liệu nghiệp vụ của PDF):
  - KHÔNG tạo bảng skills / user_skills: mô hình kỹ năng hiện dùng title_group (nhóm chức danh) trên bảng
    users + cross_training (đào tạo chéo) là đủ cho mọi quy tắc nghiệp vụ hiện có (mục 4.3, 6.3). Thêm
    skills/user_skills tổng quát hơn nhưng không có nghiệp vụ nào trong đặc tả cần đến, nên không tạo bảng
    rỗng không được code dùng tới.
  - KHÔNG tạo bảng shift_templates: ứng dụng hiện tạo ca trực từng ca một (giao diện) hoặc theo lô qua
    nhập CSV (Phase 4), chưa có tính năng "mẫu ca lặp lại". Đây là điểm mở rộng hợp lý cho phase sau.
  - KHÔNG tạo bảng task_evidence riêng: minh chứng nhiệm vụ hiện là một ô ghi chú văn bản
    (tasks.evidence_note) đi kèm cờ tasks.closed_without_evidence, chưa có tính năng đính kèm nhiều tệp/
    nhiều dòng minh chứng. Nếu cần, đây cũng là điểm mở rộng hợp lý cho phase sau.
  - KHÔNG tạo bảng weekly_kpi_snapshots: K1-K6 luôn tính trực tiếp từ dữ liệu ca/nhiệm vụ tại thời điểm xem
    dashboard (không lưu "chụp" KPI theo tuần). Nếu cần giữ lịch sử KPI qua thời gian để vẽ xu hướng, đây là
    điểm mở rộng hợp lý cho phase sau.
  - branch_holidays đổi tên từ "holidays" (tên bảng trong mục 5 của đặc tả) cho khớp đúng chữ "branch_holidays".
*/

USE master;
GO
IF DB_ID('StaffFlow') IS NULL
BEGIN
    CREATE DATABASE StaffFlow COLLATE Vietnamese_CI_AS;
END
GO
USE StaffFlow;
GO

-- ===================================================================== DROP (con -> cha)
IF OBJECT_ID('dbo.reconciliation_flagged_tasks', 'U') IS NOT NULL DROP TABLE dbo.reconciliation_flagged_tasks;
IF OBJECT_ID('dbo.reconciliation_items', 'U') IS NOT NULL DROP TABLE dbo.reconciliation_items;
IF OBJECT_ID('dbo.reconciliation_checks', 'U') IS NOT NULL DROP TABLE dbo.reconciliation_checks;
IF OBJECT_ID('dbo.monthly_reports', 'U') IS NOT NULL DROP TABLE dbo.monthly_reports;
IF OBJECT_ID('dbo.survey_responses', 'U') IS NOT NULL DROP TABLE dbo.survey_responses;
IF OBJECT_ID('dbo.task_events', 'U') IS NOT NULL DROP TABLE dbo.task_events;
IF OBJECT_ID('dbo.tasks', 'U') IS NOT NULL DROP TABLE dbo.tasks;
IF OBJECT_ID('dbo.task_templates', 'U') IS NOT NULL DROP TABLE dbo.task_templates;
IF OBJECT_ID('dbo.shift_swap_requests', 'U') IS NOT NULL DROP TABLE dbo.shift_swap_requests;
IF OBJECT_ID('dbo.absence_records', 'U') IS NOT NULL DROP TABLE dbo.absence_records;
IF OBJECT_ID('dbo.audit_logs', 'U') IS NOT NULL DROP TABLE dbo.audit_logs;
IF OBJECT_ID('dbo.shifts', 'U') IS NOT NULL DROP TABLE dbo.shifts;
IF OBJECT_ID('dbo.cross_training', 'U') IS NOT NULL DROP TABLE dbo.cross_training;
IF OBJECT_ID('dbo.branch_holidays', 'U') IS NOT NULL DROP TABLE dbo.branch_holidays;
IF OBJECT_ID('dbo.users', 'U') IS NOT NULL DROP TABLE dbo.users;
IF OBJECT_ID('dbo.branches', 'U') IS NOT NULL DROP TABLE dbo.branches;
GO

-- ===================================================================== Danh mục

CREATE TABLE dbo.branches (
    id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    code    NVARCHAR(10)  NOT NULL UNIQUE,
    name    NVARCHAR(200) NOT NULL,
    address NVARCHAR(300) NOT NULL DEFAULT N'',
    note    NVARCHAR(300) NOT NULL DEFAULT N''
);
GO

-- role: ADMIN | BRANCH_MANAGER | TECH | STAFF ; title_group: GIAM_DOC | PHO_GIAM_DOC | XU_LY_TAI_LIEU | HE_THONG | PHUC_VU_BAN_DOC
CREATE TABLE dbo.users (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    code          NVARCHAR(10)  NOT NULL UNIQUE,
    display_name  NVARCHAR(200) NOT NULL,
    title_group   NVARCHAR(30)  NOT NULL,
    role          NVARCHAR(20)  NOT NULL,
    branch_id     BIGINT        NOT NULL REFERENCES dbo.branches(id),
    password_hash NVARCHAR(100) NOT NULL,
    CONSTRAINT CK_users_role CHECK (role IN (N'ADMIN', N'BRANCH_MANAGER', N'TECH', N'STAFF')),
    CONSTRAINT CK_users_title_group CHECK (title_group IN
        (N'GIAM_DOC', N'PHO_GIAM_DOC', N'XU_LY_TAI_LIEU', N'HE_THONG', N'PHUC_VU_BAN_DOC'))
);
CREATE INDEX IX_users_branch ON dbo.users(branch_id);
GO

-- branch_id NULL = áp dụng cho cả 3 cơ sở (mục 4.1)
CREATE TABLE dbo.branch_holidays (
    id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id BIGINT NULL REFERENCES dbo.branches(id),
    date      DATE   NOT NULL,
    note      NVARCHAR(200) NOT NULL DEFAULT N''
);
CREATE INDEX IX_branch_holidays_date ON dbo.branch_holidays(date);
GO

-- ===================================================================== Lịch trực (Phase 1)

-- status: DU_NGUOI | TRONG | BI_ANH_HUONG
CREATE TABLE dbo.shifts (
    id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id  BIGINT NOT NULL REFERENCES dbo.branches(id),
    date       DATE   NOT NULL,
    start_time TIME(0) NOT NULL,
    end_time   TIME(0) NOT NULL,
    user_id    BIGINT NULL REFERENCES dbo.users(id),
    status     NVARCHAR(20) NOT NULL,
    note       NVARCHAR(200) NOT NULL DEFAULT N'',
    test_data  BIT NOT NULL DEFAULT 0,
    CONSTRAINT CK_shifts_status CHECK (status IN (N'DU_NGUOI', N'TRONG', N'BI_ANH_HUONG')),
    CONSTRAINT CK_shifts_time CHECK (start_time < end_time)
);
-- Index phục vụ lọc theo tuần/cơ sở (xem lịch) và kiểm tra trùng ca theo người/ngày (UPDLOCK,HOLDLOCK ở mục 6.1).
CREATE INDEX IX_shifts_branch_date ON dbo.shifts(branch_id, date);
CREATE INDEX IX_shifts_user_date   ON dbo.shifts(user_id, date) WHERE user_id IS NOT NULL;
GO

CREATE TABLE dbo.audit_logs (
    id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id   BIGINT NOT NULL REFERENCES dbo.users(id),
    action    NVARCHAR(50)  NOT NULL,
    entity    NVARCHAR(50)  NOT NULL,
    entity_id NVARCHAR(50)  NOT NULL,
    detail    NVARCHAR(400) NOT NULL DEFAULT N'',
    at        DATETIME2(0)  NOT NULL
);
CREATE INDEX IX_audit_logs_at ON dbo.audit_logs(at DESC);
GO

-- ===================================================================== Phase 2: vắng mặt, đào tạo chéo, đổi ca

-- can_substitute_for: một trong các title_group; status: DA_HOAN_THANH | DANG_DAO_TAO (mục 4.3)
CREATE TABLE dbo.cross_training (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES dbo.users(id),
    can_substitute_for  NVARCHAR(30) NOT NULL,
    status              NVARCHAR(20) NOT NULL,
    CONSTRAINT CK_cross_training_status CHECK (status IN (N'DA_HOAN_THANH', N'DANG_DAO_TAO'))
);
CREATE INDEX IX_cross_training_user ON dbo.cross_training(user_id);
GO

CREATE TABLE dbo.absence_records (
    id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES dbo.users(id),
    from_date    DATE   NOT NULL,
    to_date      DATE   NOT NULL,
    reason       NVARCHAR(300) NOT NULL DEFAULT N'',
    reported_by  BIGINT NOT NULL REFERENCES dbo.users(id),
    reported_at  DATETIME2(0) NOT NULL,
    CONSTRAINT CK_absence_dates CHECK (from_date <= to_date)
);
CREATE INDEX IX_absence_user_dates ON dbo.absence_records(user_id, from_date, to_date);
GO

-- status: CHO_DUYET | DA_DUYET | TU_CHOI (mục 6.4)
CREATE TABLE dbo.shift_swap_requests (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    shift_id           BIGINT NOT NULL REFERENCES dbo.shifts(id),
    requester_id       BIGINT NOT NULL REFERENCES dbo.users(id),
    proposed_user_id   BIGINT NULL REFERENCES dbo.users(id),
    reason             NVARCHAR(300) NOT NULL DEFAULT N'',
    status             NVARCHAR(20) NOT NULL,
    submitted_at       DATETIME2(0) NOT NULL,
    created_by_manager BIT NOT NULL DEFAULT 0,
    approver_id        BIGINT NULL REFERENCES dbo.users(id),
    decided_at         DATETIME2(0) NULL,
    decision_note      NVARCHAR(300) NULL,
    CONSTRAINT CK_swap_status CHECK (status IN (N'CHO_DUYET', N'DA_DUYET', N'TU_CHOI'))
);
CREATE INDEX IX_swap_shift  ON dbo.shift_swap_requests(shift_id);
CREATE INDEX IX_swap_status ON dbo.shift_swap_requests(status);
GO

-- ===================================================================== Phase 3: nhiệm vụ, KPI nguồn dữ liệu

-- category: PHUC_VU | NGHIEP_VU (mục 4.4, Điều 7 Thông tư 14/2023/TT-BGDĐT)
CREATE TABLE dbo.task_templates (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    ord                INT NOT NULL,
    category           NVARCHAR(20) NOT NULL,
    name               NVARCHAR(300) NOT NULL,
    main_group         NVARCHAR(30) NOT NULL,
    backup_group       NVARCHAR(30) NULL,
    estimated_minutes  INT NOT NULL,
    CONSTRAINT CK_task_templates_category CHECK (category IN (N'PHUC_VU', N'NGHIEP_VU'))
);
GO

-- priority: CAO | TRUNG_BINH | THAP ; status: MOI | DANG_LAM | CHO_DUYET | XONG (mục 6.5)
-- branch_id NULL = nhiệm vụ phạm vi toàn hệ thống.
CREATE TABLE dbo.tasks (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    title                   NVARCHAR(300) NOT NULL,
    template_id             BIGINT NULL REFERENCES dbo.task_templates(id),
    branch_id               BIGINT NULL REFERENCES dbo.branches(id),
    assignee_id             BIGINT NOT NULL REFERENCES dbo.users(id),
    estimated_minutes       INT NOT NULL CHECK (estimated_minutes > 0),
    due_date                DATE NOT NULL,
    priority                NVARCHAR(20) NOT NULL,
    status                  NVARCHAR(20) NOT NULL,
    closed_without_evidence BIT NOT NULL DEFAULT 0,
    evidence_note           NVARCHAR(300) NULL,
    created_at              DATETIME2(0) NOT NULL,
    updated_at              DATETIME2(0) NOT NULL,
    CONSTRAINT CK_tasks_priority CHECK (priority IN (N'CAO', N'TRUNG_BINH', N'THAP')),
    CONSTRAINT CK_tasks_status CHECK (status IN (N'MOI', N'DANG_LAM', N'CHO_DUYET', N'XONG'))
);
CREATE INDEX IX_tasks_due_branch     ON dbo.tasks(due_date, branch_id);
CREATE INDEX IX_tasks_assignee       ON dbo.tasks(assignee_id);
CREATE INDEX IX_tasks_status         ON dbo.tasks(status);
GO

CREATE TABLE dbo.task_events (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES dbo.tasks(id),
    from_status NVARCHAR(20) NULL,
    to_status   NVARCHAR(20) NOT NULL,
    by_user_id  BIGINT NOT NULL REFERENCES dbo.users(id),
    at          DATETIME2(0) NOT NULL
);
CREATE INDEX IX_task_events_task ON dbo.task_events(task_id);
CREATE INDEX IX_task_events_at   ON dbo.task_events(at);
GO

-- ===================================================================== Phase 4: khảo sát, báo cáo tháng, đối soát

-- period: TUAN_4 | TUAN_8 (mục 7, Bảng 22). Mỗi người một bản ghi cho mỗi kỳ (nộp lại ghi đè ở tầng ứng dụng).
CREATE TABLE dbo.survey_responses (
    id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    period       NVARCHAR(10) NOT NULL,
    user_id      BIGINT NOT NULL REFERENCES dbo.users(id),
    q1           INT NOT NULL CHECK (q1 BETWEEN 1 AND 5),
    q2           INT NOT NULL CHECK (q2 BETWEEN 1 AND 5),
    q3           INT NOT NULL CHECK (q3 BETWEEN 1 AND 5),
    note         NVARCHAR(300) NULL,
    submitted_at DATETIME2(0) NOT NULL,
    CONSTRAINT CK_survey_period CHECK (period IN (N'TUAN_4', N'TUAN_8')),
    CONSTRAINT UQ_survey_user_period UNIQUE (period, user_id)
);
GO

-- year_month dạng 'yyyy-MM', một bản ghi cho mỗi tháng (nộp lại ghi đè ở tầng ứng dụng).
CREATE TABLE dbo.monthly_reports (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    year_month      CHAR(7) NOT NULL UNIQUE,
    due_date        DATE NOT NULL,
    submitted_date  DATE NULL,
    note            NVARCHAR(300) NULL,
    recorded_by     BIGINT NOT NULL REFERENCES dbo.users(id),
    recorded_at     DATETIME2(0) NOT NULL
);
GO

-- branch_id NULL = phiếu đối soát toàn hệ thống (mục 6.6)
CREATE TABLE dbo.reconciliation_checks (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    week_start  DATE NOT NULL,
    branch_id   BIGINT NULL REFERENCES dbo.branches(id),
    created_by  BIGINT NOT NULL REFERENCES dbo.users(id),
    created_at  DATETIME2(0) NOT NULL
);
CREATE INDEX IX_reconciliation_week ON dbo.reconciliation_checks(week_start);
GO

-- matched: NULL = chưa đối chiếu, 1 = khớp, 0 = lệch
CREATE TABLE dbo.reconciliation_items (
    check_id BIGINT NOT NULL REFERENCES dbo.reconciliation_checks(id),
    shift_id BIGINT NOT NULL REFERENCES dbo.shifts(id),
    matched  BIT NULL,
    note     NVARCHAR(300) NULL,
    PRIMARY KEY (check_id, shift_id)
);
GO

CREATE TABLE dbo.reconciliation_flagged_tasks (
    check_id BIGINT NOT NULL REFERENCES dbo.reconciliation_checks(id),
    task_id  BIGINT NOT NULL REFERENCES dbo.tasks(id),
    PRIMARY KEY (check_id, task_id)
);
GO
