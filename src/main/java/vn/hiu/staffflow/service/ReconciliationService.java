package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.ReconciliationCheck;
import vn.hiu.staffflow.domain.Domain.ReconciliationItem;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.ReconciliationCreateRequest;
import vn.hiu.staffflow.dto.Requests.ReconciliationItemRequest;
import vn.hiu.staffflow.dto.Views.ReconciliationItemView;
import vn.hiu.staffflow.dto.Views.ReconciliationView;
import vn.hiu.staffflow.dto.Views.TaskView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ReconciliationRepository;
import vn.hiu.staffflow.repository.ShiftRepository;
import vn.hiu.staffflow.repository.TaskRepository;

/**
 * Đối soát hằng tuần (mục 6.6): chọn ngẫu nhiên 10% số ca trong tuần (làm tròn lên, tối thiểu 1 ca nếu có ca)
 * để đối chiếu thực tế tại quầy, kèm danh sách nhiệm vụ đã đóng ("Xong") mà không có minh chứng trong tuần đó.
 * Việc chọn mẫu dùng seed cố định theo tuần + cơ sở để kết quả lặp lại được khi kiểm thử (T6), không phải vì
 * đặc tả yêu cầu — ghi rõ đây là giả định bổ sung do lập trình.
 */
@Service
public class ReconciliationService {
    private final ReconciliationRepository repo;
    private final ShiftRepository shifts;
    private final TaskRepository tasks;
    private final OrgRepository org;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final TaskService taskService;
    private final AppClock clock;

    public ReconciliationService(ReconciliationRepository repo, ShiftRepository shifts, TaskRepository tasks,
                                 OrgRepository org, AccessPolicy policy, AuditService audit,
                                 TaskService taskService, AppClock clock) {
        this.repo = repo;
        this.shifts = shifts;
        this.tasks = tasks;
        this.org = org;
        this.policy = policy;
        this.audit = audit;
        this.taskService = taskService;
        this.clock = clock;
    }

    public ReconciliationView create(AppUser actor, ReconciliationCreateRequest in) {
        policy.requireManageBranch(actor, in.branchId(), "tạo phiếu đối soát");
        LocalDate monday = WeekUtil.mondayOf(in.week());
        LocalDate sunday = monday.plusDays(6);

        List<Shift> weekShifts = shifts.findBetween(monday, sunday).stream()
                .filter(s -> in.branchId() == null || s.branchId() == in.branchId()).toList();
        List<Long> sampleIds = sample(weekShifts, monday, in.branchId());

        List<Task> flagged = tasks.findAll().stream()
                .filter(t -> t.closedWithoutEvidence())
                .filter(t -> !t.dueDate().isBefore(monday) && !t.dueDate().isAfter(sunday))
                .filter(t -> in.branchId() == null || (t.branchId() != null && t.branchId().longValue() == in.branchId()))
                .toList();

        List<ReconciliationItem> items = sampleIds.stream().map(id -> new ReconciliationItem(id, null, null)).toList();
        ReconciliationCheck saved = repo.save(new ReconciliationCheck(0, monday, in.branchId(), actor.id(),
                clock.now(), items, flagged.stream().map(Task::id).toList()));
        audit.log(actor, "RECONCILIATION_CREATE", "reconciliation", saved.id(),
                "Tuần " + monday + ", " + sampleIds.size() + " ca được chọn, " + flagged.size() + " nhiệm vụ gắn cờ");
        return view(saved, actor);
    }

    public ReconciliationView recordItem(AppUser actor, long checkId, long shiftId, ReconciliationItemRequest in) {
        ReconciliationCheck check = repo.findById(checkId).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu đối soát"));
        policy.requireManageBranch(actor, check.branchId(), "ghi nhận kết quả đối soát");
        boolean found = false;
        List<ReconciliationItem> newItems = new ArrayList<>();
        for (ReconciliationItem it : check.items()) {
            if (it.shiftId() == shiftId) {
                newItems.add(new ReconciliationItem(shiftId, in.matched(), clean(in.note())));
                found = true;
            } else {
                newItems.add(it);
            }
        }
        if (!found) {
            throw ApiException.notFound("Ca này không nằm trong mẫu của phiếu đối soát");
        }
        ReconciliationCheck saved = repo.save(check.withItems(newItems));
        audit.log(actor, "RECONCILIATION_ITEM", "reconciliation", checkId,
                "Ca #" + shiftId + ": " + (Boolean.TRUE.equals(in.matched()) ? "khớp" : "lệch"));
        return view(saved, actor);
    }

    public List<ReconciliationView> list(LocalDate week, Long branchId, AppUser actor) {
        LocalDate monday = week == null ? null : WeekUtil.mondayOf(week);
        return repo.findAll().stream()
                .filter(c -> monday == null || c.weekStart().equals(monday))
                .filter(c -> branchId == null || (c.branchId() != null && c.branchId().longValue() == branchId))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .map(c -> view(c, actor))
                .toList();
    }

    private List<Long> sample(List<Shift> weekShifts, LocalDate monday, Long branchId) {
        if (weekShifts.isEmpty()) {
            return List.of();
        }
        int count = (int) Math.ceil(weekShifts.size() * 0.10);
        if (count < 1) {
            count = 1;
        }
        count = Math.min(count, weekShifts.size());
        List<Shift> pool = new ArrayList<>(weekShifts);
        long seed = monday.toEpochDay() * 31 + (branchId == null ? 0 : branchId);
        Random rnd = new Random(seed);
        List<Long> picked = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = rnd.nextInt(pool.size());
            picked.add(pool.remove(idx).id());
        }
        return picked;
    }

    private ReconciliationView view(ReconciliationCheck c, AppUser actor) {
        String branchName = c.branchId() == null ? "Toàn hệ thống" : org.branch(c.branchId()).map(Branch::name).orElse("?");
        AppUser creator = org.user(c.createdBy()).orElse(null);
        List<ReconciliationItemView> items = c.items().stream().map(it -> {
            Shift s = shifts.findById(it.shiftId()).orElse(null);
            String summary = s == null ? "(ca đã bị xóa #" + it.shiftId() + ")"
                    : org.branch(s.branchId()).map(Branch::name).orElse("?") + " " + Fmt.d(s.date()) + " "
                            + Fmt.t(s.start()) + "–" + Fmt.t(s.end())
                            + (s.userId() == null ? " (trống)" : " / " + org.user(s.userId()).map(AppUser::code).orElse("?"));
            return new ReconciliationItemView(it.shiftId(), summary, it.matched(), it.note());
        }).toList();
        var allTaskViews = taskService.list(actor, null, null, null, null);
        List<TaskView> flagged = c.flaggedTaskIds().stream()
                .map(id -> allTaskViews.stream().filter(v -> v.id() == id).findFirst().orElse(null))
                .filter(v -> v != null)
                .toList();
        return new ReconciliationView(c.id(), Fmt.d(c.weekStart()), c.branchId(), branchName,
                creator == null ? "?" : creator.code(), Fmt.dt(c.createdAt()), items, flagged);
    }

    private static String clean(String s) {
        return s == null ? null : s.trim();
    }
}
