package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Priority;
import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.domain.Domain.TaskEvent;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.domain.Domain.TaskTemplate;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.TaskRequest;
import vn.hiu.staffflow.dto.Requests.TaskStatusRequest;
import vn.hiu.staffflow.dto.Views.TaskTemplateView;
import vn.hiu.staffflow.dto.Views.TaskView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.TaskEventRepository;
import vn.hiu.staffflow.repository.TaskRepository;
import vn.hiu.staffflow.repository.TaskTemplateRepository;

/**
 * Quản lý nhiệm vụ (mục 6.5): Kanban 4 trạng thái Mới/Đang làm/Chờ duyệt/Xong.
 * Chuyển sang Xong mà không có minh chứng (evidenceNote rỗng) vẫn cho phép nhưng gắn cờ
 * "đóng không có minh chứng" (closedWithoutEvidence) để phục vụ đối soát hằng tuần (mục 6.6).
 */
@Service
public class TaskService {
    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final TaskTemplateRepository templates;
    private final OrgRepository org;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final AppClock clock;
    private final TxLock tx;

    public TaskService(TaskRepository tasks, TaskEventRepository events, TaskTemplateRepository templates,
                       OrgRepository org, AccessPolicy policy, AuditService audit, AppClock clock, TxLock tx) {
        this.tasks = tasks;
        this.events = events;
        this.templates = templates;
        this.org = org;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
        this.tx = tx;
    }

    public List<TaskTemplateView> listTemplates() {
        return templates.findAll().stream().map(this::templateView).toList();
    }

    private TaskTemplateView templateView(TaskTemplate t) {
        return new TaskTemplateView(t.id(), t.category().name(), t.category().label(), t.name(),
                t.mainGroup().label(), t.backupGroup() == null ? null : t.backupGroup().label(), t.estimatedMinutes());
    }

    /** Lọc theo tuần (hạn nằm trong tuần), cơ sở, người phụ trách, trạng thái — mọi tham số có thể null. */
    public List<TaskView> list(AppUser actor, LocalDate weekMonday, Long branchId, Long assigneeId, TaskStatus status) {
        LocalDate weekEnd = weekMonday == null ? null : weekMonday.plusDays(6);
        return tasks.findAll().stream()
                .filter(t -> weekMonday == null || (!t.dueDate().isBefore(weekMonday) && !t.dueDate().isAfter(weekEnd)))
                .filter(t -> branchId == null || (t.branchId() != null && t.branchId().longValue() == branchId))
                .filter(t -> assigneeId == null || t.assigneeId() == assigneeId)
                .filter(t -> status == null || t.status() == status)
                .map(t -> view(t, actor))
                .toList();
    }

    public TaskView create(AppUser actor, TaskRequest in) {
        policy.requireManageTask(actor, in.branchId(), "tạo");
        Priority priority = parsePriority(in.priority());
        if (in.estimatedMinutes() <= 0) {
            throw ApiException.unprocessable("Thời lượng dự kiến phải lớn hơn 0", List.of());
        }
        org.user(in.assigneeId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy người phụ trách"));
        return tx.run(() -> {
            var now = clock.now();
            Task saved = tasks.save(new Task(0, in.title().trim(), in.templateId(), in.branchId(), in.assigneeId(),
                    in.estimatedMinutes(), in.dueDate(), priority, TaskStatus.MOI, false, null, now, now));
            events.add(new TaskEvent(0, saved.id(), null, TaskStatus.MOI, actor.id(), now));
            audit.log(actor, "TASK_CREATE", "task", saved.id(), saved.title());
            return view(saved, actor);
        });
    }

    public TaskView update(AppUser actor, long id, TaskRequest in) {
        Task current = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhiệm vụ"));
        policy.requireManageTask(actor, current.branchId(), "sửa");
        policy.requireManageTask(actor, in.branchId(), "chuyển nhiệm vụ sang phạm vi này");
        Priority priority = parsePriority(in.priority());
        if (in.estimatedMinutes() <= 0) {
            throw ApiException.unprocessable("Thời lượng dự kiến phải lớn hơn 0", List.of());
        }
        org.user(in.assigneeId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy người phụ trách"));
        return tx.run(() -> {
            Task saved = tasks.save(current.withFields(in.title().trim(), in.branchId(), in.assigneeId(),
                    in.estimatedMinutes(), in.dueDate(), priority, clock.now()));
            audit.log(actor, "TASK_UPDATE", "task", id, saved.title());
            return view(saved, actor);
        });
    }

    public TaskView changeStatus(AppUser actor, long id, TaskStatusRequest in) {
        Task current = tasks.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhiệm vụ"));
        policy.requireUpdateTaskStatus(actor, current.assigneeId(), current.branchId());
        TaskStatus newStatus = parseStatus(in.status());
        if (newStatus == current.status()) {
            throw ApiException.conflict("Nhiệm vụ đã ở trạng thái này");
        }
        boolean hasEvidence = in.evidenceNote() != null && !in.evidenceNote().isBlank();
        boolean closedNoEvidence = newStatus == TaskStatus.XONG && !hasEvidence;
        return tx.run(() -> {
            var now = clock.now();
            Task saved = tasks.save(current.withStatus(newStatus, closedNoEvidence,
                    hasEvidence ? in.evidenceNote().trim() : null, now));
            events.add(new TaskEvent(0, id, current.status(), newStatus, actor.id(), now));
            audit.log(actor, "TASK_STATUS", "task", id,
                    current.status().label() + " → " + newStatus.label()
                            + (closedNoEvidence ? " [đóng không có minh chứng]" : ""));
            return view(saved, actor);
        });
    }

    public List<vn.hiu.staffflow.dto.Views.TaskEventView> history(long taskId) {
        return events.findByTask(taskId).stream().map(e -> {
            AppUser u = org.user(e.byUserId()).orElse(null);
            return new vn.hiu.staffflow.dto.Views.TaskEventView(
                    e.from() == null ? "(mới tạo)" : e.from().label(), e.to().label(),
                    u == null ? "?" : u.code(), Fmt.dt(e.at()));
        }).toList();
    }

    private TaskView view(Task t, AppUser actor) {
        AppUser assignee = org.user(t.assigneeId()).orElse(null);
        String branchName = t.branchId() == null ? "Toàn hệ thống" : org.branch(t.branchId()).map(Branch::name).orElse("?");
        boolean overdue = t.status() != TaskStatus.XONG && t.dueDate().isBefore(clock.now().toLocalDate());
        return new TaskView(t.id(), t.title(), t.templateId(), t.branchId(), branchName, t.assigneeId(),
                assignee == null ? "?" : assignee.code(), assignee == null ? "?" : assignee.displayName(),
                t.estimatedMinutes(), Fmt.d(t.dueDate()), overdue, t.priority().name(), t.priority().label(),
                t.status().name(), t.status().label(), t.closedWithoutEvidence(), t.evidenceNote(),
                Fmt.dt(t.updatedAt()), policy.canManageTask(actor, t.branchId()),
                policy.canUpdateTaskStatus(actor, t.assigneeId(), t.branchId()));
    }

    private static Priority parsePriority(String s) {
        try {
            return Priority.valueOf(s);
        } catch (Exception e) {
            throw ApiException.unprocessable("Độ ưu tiên không hợp lệ", List.of("priority: " + s));
        }
    }

    private static TaskStatus parseStatus(String s) {
        try {
            return TaskStatus.valueOf(s);
        } catch (Exception e) {
            throw ApiException.unprocessable("Trạng thái không hợp lệ", List.of("status: " + s));
        }
    }
}
