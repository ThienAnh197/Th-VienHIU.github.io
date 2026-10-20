package vn.hiu.staffflow.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Các kiểu dữ liệu nghiệp vụ. Mỗi record tương ứng một bảng sẽ tạo ở Phase 5 (SQL Server).
 * Phase 2-4 sẽ bổ sung: Absence, CrossTraining, Task, SwapRequest, ...
 */
public final class Domain {
    private Domain() {
    }

    public enum Role { ADMIN, BRANCH_MANAGER, TECH, STAFF }

    /** Mảng việc, dùng cho đào tạo chéo (Phase 2). */
    public enum Area {
        QUAY_PHUC_VU("Quầy phục vụ"), XU_LY_TAI_LIEU("Xử lý tài liệu"), HE_THONG("Hệ thống");

        private final String label;

        Area(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** "Nhóm chức danh" (PDF gọi là "nhóm vị trí"; giao diện thống nhất dùng "nhóm chức danh"). */
    public enum TitleGroup {
        GIAM_DOC("Giám đốc"),
        PHO_GIAM_DOC("Phó giám đốc"),
        XU_LY_TAI_LIEU("CV xử lý tài liệu"),
        HE_THONG("CV hệ thống"),
        PHUC_VU_BAN_DOC("CV phục vụ bạn đọc");

        private final String label;

        TitleGroup(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isLeader() {
            return this == GIAM_DOC || this == PHO_GIAM_DOC;
        }
    }

    public enum ShiftStatus {
        DU_NGUOI("Đủ người"), TRONG("Trống"), BI_ANH_HUONG("Bị ảnh hưởng do vắng");

        private final String label;

        ShiftStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Branch(long id, String code, String name, String address, String note) {
    }

    public record AppUser(long id, String code, String displayName, TitleGroup titleGroup, Role role,
                          long branchId, String passwordHash) {
    }

    /** Ngày đóng cửa ngoài Chủ nhật. branchId == null nghĩa là áp dụng cho cả 3 cơ sở. */
    public record Holiday(long id, Long branchId, LocalDate date, String note) {
    }

    public record Shift(long id, long branchId, LocalDate date, LocalTime start, LocalTime end,
                        Long userId, ShiftStatus status, String note, boolean testData) {

        public Shift withId(long newId) {
            return new Shift(newId, branchId, date, start, end, userId, status, note, testData);
        }

        public Shift withAssignment(Long newUserId, ShiftStatus newStatus) {
            return new Shift(id, branchId, date, start, end, newUserId, newStatus, note, testData);
        }

        public Shift withSlot(long newBranch, LocalDate newDate, LocalTime newStart, LocalTime newEnd,
                              Long newUserId, ShiftStatus newStatus, String newNote) {
            return new Shift(id, newBranch, newDate, newStart, newEnd, newUserId, newStatus, newNote, testData);
        }

        public int minutes() {
            return (int) Duration.between(start, end).toMinutes();
        }

        public LocalDateTime startAt() {
            return LocalDateTime.of(date, start);
        }
    }

    public record AuditLog(long id, long userId, String action, String entity, String entityId,
                           String detail, LocalDateTime at) {
        public AuditLog withId(long newId) {
            return new AuditLog(newId, userId, action, entity, entityId, detail, at);
        }
    }

    // ---------------------------------------------------------------- Phase 2

    public enum TrainingStatus {
        DA_HOAN_THANH("Đã hoàn thành"), DANG_DAO_TAO("Đang đào tạo");

        private final String label;

        TrainingStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Đào tạo chéo: userId được phép thay thế chức danh canSubstituteFor khi status = DA_HOAN_THANH (mục 4.3). */
    public record CrossTraining(long id, long userId, TitleGroup canSubstituteFor, TrainingStatus status) {
    }

    /** Báo vắng của một người trong khoảng ngày [from, to] (gồm cả hai đầu). */
    public record Absence(long id, long userId, LocalDate from, LocalDate to, String reason,
                          long reportedBy, LocalDateTime reportedAt) {
        public Absence withId(long newId) {
            return new Absence(newId, userId, from, to, reason, reportedBy, reportedAt);
        }
    }

    public enum SwapStatus {
        CHO_DUYET("Chờ duyệt"), DA_DUYET("Đã duyệt"), TU_CHOI("Từ chối");

        private final String label;

        SwapStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Yêu cầu đổi ca. proposedUserId là người đề xuất thay (có thể null nếu chưa chọn).
     * createdByManager = true khi quản lý tạo thay cho người gửi muộn hơn hạn 24 giờ (ghi rõ trong audit).
     */
    public record SwapRequest(long id, long shiftId, long requesterId, Long proposedUserId, String reason,
                              SwapStatus status, LocalDateTime submittedAt, boolean createdByManager,
                              Long approverId, LocalDateTime decidedAt, String decisionNote) {
        public SwapRequest withId(long newId) {
            return new SwapRequest(newId, shiftId, requesterId, proposedUserId, reason, status, submittedAt,
                    createdByManager, approverId, decidedAt, decisionNote);
        }

        public SwapRequest withDecision(SwapStatus newStatus, long approver, LocalDateTime at, String note) {
            return new SwapRequest(id, shiftId, requesterId, proposedUserId, reason, newStatus, submittedAt,
                    createdByManager, approver, at, note);
        }
    }

    // ---------------------------------------------------------------- Phase 3

    public enum TaskStatus {
        MOI("Mới"), DANG_LAM("Đang làm"), CHO_DUYET("Chờ duyệt"), XONG("Xong");

        private final String label;

        TaskStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Priority {
        CAO("Cao"), TRUNG_BINH("Trung bình"), THAP("Thấp");

        private final String label;

        Priority(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum TaskCategory {
        PHUC_VU("Nhóm hoạt động phục vụ"), NGHIEP_VU("Nhóm hoạt động nghiệp vụ");

        private final String label;

        TaskCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Danh mục 16 nhóm hoạt động theo Điều 7 Thông tư 14/2023/TT-BGDĐT (5 nhóm phục vụ, 11 nhóm nghiệp vụ).
     * TÊN LÀ BẢN RÚT GỌN THEO CÁCH HIỂU CỦA NHÓM — cần đối chiếu văn bản gốc trước khi dùng chính thức.
     * Định mức giờ (estimatedMinutes) là giả định của nhóm để demo tính tải việc, không phải số liệu đã kiểm định.
     */
    public record TaskTemplate(long id, int ord, TaskCategory category, String name, TitleGroup mainGroup,
                               TitleGroup backupGroup, int estimatedMinutes) {
    }

    /**
     * Nhiệm vụ. branchId == null nghĩa là nhiệm vụ ở phạm vi toàn hệ thống (ví dụ: phát triển tài nguyên,
     * hợp tác liên kết — thường thuộc Giám đốc). closedWithoutEvidence = true khi chuyển Xong mà chưa có minh chứng.
     */
    public record Task(long id, String title, Long templateId, Long branchId, long assigneeId,
                       int estimatedMinutes, LocalDate dueDate, Priority priority, TaskStatus status,
                       boolean closedWithoutEvidence, String evidenceNote, LocalDateTime createdAt,
                       LocalDateTime updatedAt) {
        public Task withId(long newId) {
            return new Task(newId, title, templateId, branchId, assigneeId, estimatedMinutes, dueDate, priority,
                    status, closedWithoutEvidence, evidenceNote, createdAt, updatedAt);
        }

        public Task withStatus(TaskStatus newStatus, boolean closedNoEvidence, String newEvidenceNote,
                               LocalDateTime now) {
            return new Task(id, title, templateId, branchId, assigneeId, estimatedMinutes, dueDate, priority,
                    newStatus, closedNoEvidence, newEvidenceNote, createdAt, now);
        }

        public Task withFields(String newTitle, Long newBranchId, long newAssigneeId, int newEstimatedMinutes,
                               LocalDate newDueDate, Priority newPriority, LocalDateTime now) {
            return new Task(id, newTitle, templateId, newBranchId, newAssigneeId, newEstimatedMinutes, newDueDate,
                    newPriority, status, closedWithoutEvidence, evidenceNote, createdAt, now);
        }
    }

    /** Nhật ký đổi trạng thái nhiệm vụ (dùng cho K3, K6 và đối soát). */
    public record TaskEvent(long id, long taskId, TaskStatus from, TaskStatus to, long byUserId, LocalDateTime at) {
        public TaskEvent withId(long newId) {
            return new TaskEvent(newId, taskId, from, to, byUserId, at);
        }
    }

    // ---------------------------------------------------------------- Phase 4

    /** Kỳ khảo sát K7 (báo cáo mục 7, Bảng 22): chỉ ở tuần 4 và tuần 8 kể từ tuần triển khai. */
    public enum SurveyPeriod {
        TUAN_4("Tuần 4"), TUAN_8("Tuần 8");

        private final String label;

        SurveyPeriod(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Khảo sát hài lòng K7: 3 câu thang điểm 1-5. Mỗi người chỉ có một bản ghi cho mỗi kỳ (nộp lại sẽ ghi đè). */
    public record SurveyResponse(long id, SurveyPeriod period, long userId, int q1, int q2, int q3, String note,
                                 LocalDateTime submittedAt) {
        public SurveyResponse withId(long newId) {
            return new SurveyResponse(newId, period, userId, q1, q2, q3, note, submittedAt);
        }

        public double avg() {
            return (q1 + q2 + q3) / 3.0;
        }
    }

    /** Báo cáo tháng K8. Một bản ghi cho mỗi tháng (yearMonth dạng "yyyy-MM"); nộp lại sẽ ghi đè bản ghi cũ. */
    public record MonthlyReport(long id, String yearMonth, LocalDate dueDate, LocalDate submittedDate, String note,
                                long recordedBy, LocalDateTime recordedAt) {
        public MonthlyReport withId(long newId) {
            return new MonthlyReport(newId, yearMonth, dueDate, submittedDate, note, recordedBy, recordedAt);
        }

        public boolean onTime() {
            return submittedDate != null && !submittedDate.isAfter(dueDate);
        }
    }

    /** Kết quả đối chiếu một ca được chọn ngẫu nhiên trong phiếu đối soát. matched == null nghĩa là chưa đối chiếu. */
    public record ReconciliationItem(long shiftId, Boolean matched, String note) {
    }

    /**
     * Phiếu đối soát hằng tuần (mục 6.6): chọn ngẫu nhiên 10% số ca trong tuần (làm tròn lên, tối thiểu 1 ca)
     * để đối chiếu thực tế tại quầy, kèm danh sách nhiệm vụ đã đóng mà không có minh chứng trong cùng tuần.
     */
    public record ReconciliationCheck(long id, LocalDate weekStart, Long branchId, long createdBy,
                                      LocalDateTime createdAt, List<ReconciliationItem> items,
                                      List<Long> flaggedTaskIds) {
        public ReconciliationCheck withId(long newId) {
            return new ReconciliationCheck(newId, weekStart, branchId, createdBy, createdAt, items, flaggedTaskIds);
        }

        public ReconciliationCheck withItems(List<ReconciliationItem> newItems) {
            return new ReconciliationCheck(id, weekStart, branchId, createdBy, createdAt, newItems, flaggedTaskIds);
        }
    }
}
