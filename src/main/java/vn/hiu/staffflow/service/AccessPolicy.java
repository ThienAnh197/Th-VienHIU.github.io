package vn.hiu.staffflow.service;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Role;
import vn.hiu.staffflow.exception.ApiException;

/**
 * Mọi quyết định phân quyền nằm ở backend. Không bao giờ tin role / user id gửi từ frontend.
 * - NV-01 (ADMIN): toàn hệ thống.
 * - NV-02 (BRANCH_MANAGER): chỉ cơ sở của mình (Đầm Sen).
 * - NV-05/06 (TECH) và chuyên viên (STAFF): không quản lý ca/duyệt.
 */
@Component
public class AccessPolicy {

    public boolean isManager(AppUser u) {
        return u.role() == Role.ADMIN || u.role() == Role.BRANCH_MANAGER;
    }

    public boolean canManageBranch(AppUser u, Long branchId) {
        if (u.role() == Role.ADMIN) {
            return true;
        }
        return u.role() == Role.BRANCH_MANAGER && branchId != null && branchId.longValue() == u.branchId();
    }

    public void requireManageBranch(AppUser u, Long branchId, String action) {
        if (!canManageBranch(u, branchId)) {
            throw ApiException.forbidden("Bạn không có quyền " + action + " tại cơ sở này");
        }
    }

    public boolean canViewAudit(AppUser u) {
        return u.role() == Role.ADMIN || u.role() == Role.TECH;
    }

    /** Báo vắng cho chính mình luôn được phép; báo vắng cho người khác cần quyền quản lý cơ sở của người đó. */
    public boolean canReportAbsenceFor(AppUser actor, AppUser target) {
        return actor.id() == target.id() || canManageBranch(actor, target.branchId());
    }

    public void requireReportAbsenceFor(AppUser actor, AppUser target) {
        if (!canReportAbsenceFor(actor, target)) {
            throw ApiException.forbidden("Bạn không có quyền báo vắng cho nhân sự này");
        }
    }

    /** Quản lý nhiệm vụ: ADMIN mọi phạm vi; BRANCH_MANAGER chỉ nhiệm vụ thuộc cơ sở mình; nhiệm vụ toàn hệ thống
     * (branchId null) chỉ ADMIN được tạo/sửa. */
    public boolean canManageTask(AppUser u, Long taskBranchId) {
        if (u.role() == Role.ADMIN) {
            return true;
        }
        return u.role() == Role.BRANCH_MANAGER && taskBranchId != null && taskBranchId.longValue() == u.branchId();
    }

    public void requireManageTask(AppUser u, Long taskBranchId, String action) {
        if (!canManageTask(u, taskBranchId)) {
            throw ApiException.forbidden("Bạn không có quyền " + action + " nhiệm vụ này");
        }
    }

    /** Người phụ trách được tự cập nhật trạng thái nhiệm vụ của mình; quản lý cập nhật được mọi nhiệm vụ trong phạm vi. */
    public boolean canUpdateTaskStatus(AppUser u, long assigneeId, Long taskBranchId) {
        return u.id() == assigneeId || canManageTask(u, taskBranchId);
    }

    public void requireUpdateTaskStatus(AppUser u, long assigneeId, Long taskBranchId) {
        if (!canUpdateTaskStatus(u, assigneeId, taskBranchId)) {
            throw ApiException.forbidden("Bạn không có quyền cập nhật trạng thái nhiệm vụ này");
        }
    }
}
