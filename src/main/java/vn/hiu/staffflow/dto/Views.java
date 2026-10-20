package vn.hiu.staffflow.dto;

import java.util.List;

/** Dữ liệu trả về cho frontend (không bao giờ chứa mật khẩu băm). */
public final class Views {
    private Views() {
    }

    public record UserView(long id, String code, String displayName, String titleCode, String title,
                           String role, long branchId, String branchName) {
    }

    public record MeView(UserView user, String csrfToken) {
    }

    public record ShiftView(long id, long branchId, String branchName, String date, int dayOfWeek,
                            String start, String end, int minutes, Long userId, String userCode,
                            String userName, String status, String statusLabel, String note,
                            boolean testData, boolean canManage) {
    }

    public record DayInfo(String date, int dayOfWeek, boolean closed, String hours) {
    }

    public record BranchSchedule(long branchId, String code, String name, String address, String note,
                                 int requiredMinutes, int coveredMinutes, Double coveragePct,
                                 int emptyShifts, List<DayInfo> days, List<ShiftView> shifts) {
    }

    public record WeekView(String weekStart, String weekEnd, boolean hasTestData,
                           int requiredMinutes, int coveredMinutes, Double coveragePct,
                           List<BranchSchedule> branches, List<String> notes) {
    }

    // ---------------------------------------------------------------- Phase 2

    public record AbsenceView(long id, long userId, String userCode, String userName, String from, String to,
                              String reason, List<Long> affectedShiftIds) {
    }

    /** Lý do chọn hoặc loại một ứng viên thay thế, kèm tải việc sau khi thay (mục 6.3). */
    public record CandidateView(long userId, String userCode, String userName, String titleLabel,
                                String branchName, boolean eligible, List<String> reasons,
                                boolean crossBranch, Double hoursAfterMinutes) {
    }

    public record SwapView(long id, long shiftId, String shiftSummary, long requesterId, String requesterCode,
                           Long proposedUserId, String proposedUserCode, String reason, String status,
                           String statusLabel, String submittedAt, boolean createdByManager,
                           String approverCode, String decidedAt, String decisionNote,
                           Long processingMinutes, boolean canApprove) {
    }

    // ---------------------------------------------------------------- Phase 3

    public record TaskTemplateView(long id, String category, String categoryLabel, String name,
                                   String mainGroupLabel, String backupGroupLabel, int estimatedMinutes) {
    }

    public record TaskView(long id, String title, Long templateId, Long branchId, String branchName,
                           long assigneeId, String assigneeCode, String assigneeName, int estimatedMinutes,
                           String dueDate, boolean overdue, String priority, String priorityLabel,
                           String status, String statusLabel, boolean closedWithoutEvidence, String evidenceNote,
                           String updatedAt, boolean canManage, boolean canUpdateStatus) {
    }

    public record TaskEventView(String fromLabel, String toLabel, String byCode, String at) {
    }

    public record WorkloadView(long userId, String userCode, String userName, String titleGroup,
                               String titleGroupLabel, long branchId, String branchName, int shiftMinutes,
                               int taskMinutes, int totalMinutes, double pct, boolean overLimit) {
    }

    public record GroupWorkloadView(String titleGroup, String titleGroupLabel, int memberCount,
                                    Double minHours, Double maxHours, Double ratio, String note) {
    }

    public enum KpiStatus { DAT, CHUA_DAT, NA }

    public record KpiResult(String code, String label, String valueLabel, Double rawValue, String target,
                            KpiStatus status, String detail, String formula) {
    }

    public record MilestoneSuggestion(String title, String detail) {
    }

    public record DashboardView(String weekStart, String weekEnd, List<KpiResult> kpis,
                                List<WorkloadView> workloadByUser, List<GroupWorkloadView> workloadByGroup,
                                Integer milestoneWeek, List<MilestoneSuggestion> suggestions) {
    }

    // ---------------------------------------------------------------- Phase 4

    public record SurveyView(long id, String period, String periodLabel, String userCode, String userName,
                             int q1, int q2, int q3, double avg, String note, String submittedAt) {
    }

    public record MonthlyReportView(long id, String yearMonth, String dueDate, String submittedDate,
                                    boolean submitted, boolean onTime, String note, String recordedByCode,
                                    String recordedAt) {
    }

    public record ReconciliationItemView(long shiftId, String shiftSummary, Boolean matched, String note) {
    }

    public record ReconciliationView(long id, String weekStart, Long branchId, String branchName,
                                     String createdByCode, String createdAt, List<ReconciliationItemView> items,
                                     List<TaskView> flaggedTasks) {
    }

    public record ImportRowResult(int row, boolean ok, String message) {
    }

    public record ImportSummary(int totalRows, int okRows, int errorRows, List<ImportRowResult> rows) {
    }
}
