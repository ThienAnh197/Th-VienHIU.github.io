package vn.hiu.staffflow.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;
import vn.hiu.staffflow.domain.Domain.SwapRequest;
import vn.hiu.staffflow.domain.Domain.SwapStatus;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.SwapRequestIn;
import vn.hiu.staffflow.dto.Views.CandidateView;
import vn.hiu.staffflow.dto.Views.SwapView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ShiftRepository;
import vn.hiu.staffflow.repository.SwapRepository;

/**
 * Đổi ca (mục 6.4):
 * - Nhân viên tạo yêu cầu -> Chờ duyệt; hạn gửi ít nhất 24 giờ trước giờ bắt đầu ca, trừ khi quản lý
 *   tạo thay (ghi rõ "created_by_manager" trong audit).
 * - Phạm vi duyệt: NV-01 toàn hệ thống; NV-02 chỉ ca thuộc Đầm Sen. Người khác -> 403.
 * - Khi duyệt: kiểm tra lại xung đột lịch và tải việc trong transaction; không duyệt hai lần.
 */
@Service
public class SwapService {
    private final SwapRepository swaps;
    private final ShiftRepository shifts;
    private final OrgRepository org;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final AppClock clock;
    private final CandidateService candidateService;
    private final TxLock tx;

    public SwapService(SwapRepository swaps, ShiftRepository shifts, OrgRepository org, AccessPolicy policy,
                       AuditService audit, AppClock clock, CandidateService candidateService, TxLock tx) {
        this.swaps = swaps;
        this.shifts = shifts;
        this.org = org;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
        this.candidateService = candidateService;
        this.tx = tx;
    }

    public SwapView create(AppUser actor, SwapRequestIn in) {
        Shift shift = shifts.findById(in.shiftId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy ca trực"));
        boolean isOwner = shift.userId() != null && shift.userId() == actor.id();
        boolean isManager = policy.canManageBranch(actor, shift.branchId());
        if (!isOwner && !isManager) {
            throw ApiException.forbidden("Bạn không có quyền tạo yêu cầu đổi ca cho ca này");
        }
        return tx.run(() -> {
            LocalDateTime now = clock.now();
            boolean tooLate = shift.startAt().isBefore(now.plusHours(24));
            boolean managerOverride = isManager && !isOwner;
            if (tooLate && !managerOverride) {
                throw ApiException.unprocessable(
                        "Yêu cầu đổi ca phải gửi trước giờ bắt đầu ca ít nhất 24 giờ",
                        List.of("Ca bắt đầu lúc " + Fmt.dt(shift.startAt()) + ", hiện tại " + Fmt.dt(now)
                                + " — chỉ còn " + Duration.between(now, shift.startAt()).toHours() + " giờ"));
            }
            SwapRequest saved = swaps.save(new SwapRequest(0, shift.id(), actor.id(), in.proposedUserId(),
                    clean(in.reason()), SwapStatus.CHO_DUYET, now, managerOverride, null, null, null));
            audit.log(actor, "SWAP_REQUEST", "swap", saved.id(),
                    (managerOverride ? "[Quản lý tạo thay, gửi muộn hơn hạn 24 giờ] " : "") + describe(shift));
            return view(saved, actor);
        });
    }

    public SwapView approve(AppUser actor, long id, String note) {
        return decide(actor, id, true, note);
    }

    public SwapView reject(AppUser actor, long id, String note) {
        return decide(actor, id, false, note);
    }

    private SwapView decide(AppUser actor, long id, boolean approve, String note) {
        return tx.run(() -> {
            // findByIdForUpdate: ở hồ sơ sqlserver, đây là điểm khóa UPDLOCK,HOLDLOCK chống duyệt trùng hai lần
            // khi hai yêu cầu duyệt cùng một swap xử lý cùng lúc (mục 6.4).
            SwapRequest req = swaps.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu đổi ca"));
            Shift shift = shifts.findById(req.shiftId()).orElseThrow(() -> ApiException.notFound("Ca trực không còn tồn tại"));
            policy.requireManageBranch(actor, shift.branchId(), "duyệt yêu cầu đổi ca");
            if (req.status() != SwapStatus.CHO_DUYET) {
                throw ApiException.conflict("Yêu cầu này đã được xử lý trước đó (" + req.status().label() + ")");
            }
            if (approve) {
                Long candidateId = req.proposedUserId();
                if (candidateId == null) {
                    throw ApiException.unprocessable("Chưa chọn người thay cho yêu cầu này", List.of());
                }
                AppUser candidate = org.user(candidateId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân sự đề xuất thay"));
                CandidateView eval = candidateService.evaluate(shift, candidate, candidateService.neededGroup(shift));
                if (!eval.eligible()) {
                    throw ApiException.unprocessable("Không thể duyệt: ứng viên không còn đủ điều kiện", eval.reasons());
                }
                shifts.save(shift.withAssignment(candidateId, ShiftStatus.DU_NGUOI));
            }
            SwapRequest decided = req.withDecision(approve ? SwapStatus.DA_DUYET : SwapStatus.TU_CHOI,
                    actor.id(), clock.now(), clean(note));
            swaps.save(decided);
            audit.log(actor, approve ? "SWAP_APPROVE" : "SWAP_REJECT", "swap", id, clean(note));
            return view(decided, actor);
        });
    }

    public List<SwapView> list(AppUser actor) {
        return swaps.findAll().stream().map(r -> view(r, actor)).toList();
    }

    public List<CandidateView> candidatesForShift(long shiftId) {
        Shift s = shifts.findById(shiftId).orElseThrow(() -> ApiException.notFound("Không tìm thấy ca trực"));
        return candidateService.suggest(s);
    }

    private SwapView view(SwapRequest r, AppUser actor) {
        Shift shift = shifts.findById(r.shiftId()).orElse(null);
        AppUser requester = org.user(r.requesterId()).orElse(null);
        AppUser proposed = r.proposedUserId() == null ? null : org.user(r.proposedUserId()).orElse(null);
        AppUser approver = r.approverId() == null ? null : org.user(r.approverId()).orElse(null);
        Long processingMinutes = r.decidedAt() == null ? null
                : Duration.between(r.submittedAt(), r.decidedAt()).toMinutes();
        boolean canApprove = shift != null && r.status() == SwapStatus.CHO_DUYET
                && policy.canManageBranch(actor, shift.branchId());
        return new SwapView(r.id(), r.shiftId(), shift == null ? "?" : describe(shift), r.requesterId(),
                requester == null ? "?" : requester.code(), r.proposedUserId(),
                proposed == null ? null : proposed.code(), r.reason(), r.status().name(), r.status().label(),
                Fmt.dt(r.submittedAt()), r.createdByManager(), approver == null ? null : approver.code(),
                Fmt.dt(r.decidedAt()), r.decisionNote(), processingMinutes, canApprove);
    }

    private String describe(Shift s) {
        String branch = org.branch(s.branchId()).map(Branch::name).orElse("?");
        return branch + " " + s.date() + " " + Fmt.t(s.start()) + "–" + Fmt.t(s.end());
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
