package vn.hiu.staffflow.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dữ liệu frontend gửi lên; được kiểm tra bằng Bean Validation (lỗi -> HTTP 422). */
public final class Requests {
    private Requests() {
    }

    public record LoginRequest(@NotBlank String code, @NotBlank String password) {
    }

    public record ShiftRequest(@NotNull Long branchId, @NotNull LocalDate date, @NotNull LocalTime start,
                               @NotNull LocalTime end, Long userId, @Size(max = 200) String note) {
    }

    public record AbsenceRequest(@NotNull Long userId, @NotNull LocalDate from, @NotNull LocalDate to,
                                 @Size(max = 300) String reason) {
    }

    public record SwapRequestIn(@NotNull Long shiftId, Long proposedUserId, @Size(max = 300) String reason) {
    }

    public record SwapDecisionRequest(@Size(max = 300) String note) {
    }

    public record TaskRequest(@NotBlank @Size(max = 200) String title, Long templateId, Long branchId,
                              @NotNull Long assigneeId, @NotNull Integer estimatedMinutes,
                              @NotNull LocalDate dueDate, @NotBlank String priority) {
    }

    public record TaskStatusRequest(@NotBlank String status, @Size(max = 300) String evidenceNote) {
    }

    // ---------------------------------------------------------------- Phase 4

    public record SurveyRequest(@NotBlank String period, @NotNull @Min(1) @Max(5) Integer q1,
                                @NotNull @Min(1) @Max(5) Integer q2, @NotNull @Min(1) @Max(5) Integer q3,
                                @Size(max = 300) String note) {
    }

    public record MonthlyReportRequest(@NotBlank String yearMonth, @NotNull LocalDate dueDate,
                                       LocalDate submittedDate, @Size(max = 300) String note) {
    }

    public record ReconciliationCreateRequest(@NotNull LocalDate week, Long branchId) {
    }

    public record ReconciliationItemRequest(@NotNull Boolean matched, @Size(max = 300) String note) {
    }
}
