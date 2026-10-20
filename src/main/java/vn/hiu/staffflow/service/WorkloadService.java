package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.domain.Domain.TitleGroup;
import vn.hiu.staffflow.dto.Views.GroupWorkloadView;
import vn.hiu.staffflow.dto.Views.WorkloadView;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ShiftRepository;
import vn.hiu.staffflow.repository.TaskRepository;

/**
 * QUY TẮC TẢI VIỆC (dùng thời lượng DỰ KIẾN, nhất quán ở tải việc, KPI K5 và báo cáo — mục 6.2):
 *   tải việc (phút) = tổng phút ca trực trong tuần + tổng phút nhiệm vụ có HẠN trong tuần (tính một lần,
 *                      kể cả nhiệm vụ trải nhiều ngày, vì mỗi nhiệm vụ chỉ có một bản ghi với một hạn)
 *   hệ số tải (%)   = tải việc / 40 giờ × 100%  (dùng BigDecimal/NULLIF-tương đương để tránh chia 0)
 * Cảnh báo "Vượt 100% tải việc" chỉ hiện khi vượt thật sự; không hiện cảnh báo giả.
 */
@Service
public class WorkloadService {
    public static final int WEEKLY_LIMIT_MINUTES = 40 * 60;

    private final OrgRepository org;
    private final ShiftRepository shifts;
    private final TaskRepository tasks;

    public WorkloadService(OrgRepository org, ShiftRepository shifts, TaskRepository tasks) {
        this.org = org;
        this.shifts = shifts;
        this.tasks = tasks;
    }

    public List<WorkloadView> byUser(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        List<Shift> weekShifts = shifts.findBetween(monday, sunday);
        List<Task> weekTasks = tasks.findAll().stream()
                .filter(t -> !t.dueDate().isBefore(monday) && !t.dueDate().isAfter(sunday)).toList();

        List<WorkloadView> out = new ArrayList<>();
        for (AppUser u : org.users()) {
            int shiftMin = weekShifts.stream()
                    .filter(s -> s.userId() != null && s.userId() == u.id()).mapToInt(Shift::minutes).sum();
            int taskMin = weekTasks.stream()
                    .filter(t -> t.assigneeId() == u.id()).mapToInt(Task::estimatedMinutes).sum();
            int total = shiftMin + taskMin;
            double pct = total * 100.0 / WEEKLY_LIMIT_MINUTES;
            String branchName = org.branch(u.branchId()).map(Branch::name).orElse("?");
            out.add(new WorkloadView(u.id(), u.code(), u.displayName(), u.titleGroup().name(),
                    u.titleGroup().label(), u.branchId(), branchName, shiftMin, taskMin, total,
                    round1(pct), total > WEEKLY_LIMIT_MINUTES));
        }
        return out;
    }

    /** Tải cao nhất / thấp nhất trong cùng nhóm chức danh (dữ liệu nguồn cho K5). */
    public List<GroupWorkloadView> byGroup(LocalDate monday) {
        List<WorkloadView> users = byUser(monday);
        Map<TitleGroup, List<WorkloadView>> byGroup = new LinkedHashMap<>();
        for (TitleGroup g : TitleGroup.values()) {
            byGroup.put(g, new ArrayList<>());
        }
        for (WorkloadView w : users) {
            byGroup.get(TitleGroup.valueOf(w.titleGroup())).add(w);
        }
        List<GroupWorkloadView> out = new ArrayList<>();
        for (var entry : byGroup.entrySet()) {
            List<WorkloadView> members = entry.getValue();
            if (members.isEmpty()) {
                continue;
            }
            double minMin = members.stream().mapToInt(WorkloadView::totalMinutes).min().orElse(0);
            double maxMin = members.stream().mapToInt(WorkloadView::totalMinutes).max().orElse(0);
            Double minH = round1(minMin / 60.0);
            Double maxH = round1(maxMin / 60.0);
            Double ratio;
            String note;
            if (members.size() < 2) {
                ratio = null;
                note = "Chỉ có 1 người trong nhóm, không so sánh được";
            } else if (minMin == 0) {
                ratio = null;
                note = "Có người tải việc bằng 0 trong tuần này, tỉ lệ không xác định (N/A)";
            } else {
                ratio = round1(maxMin / minMin);
                note = null;
            }
            out.add(new GroupWorkloadView(entry.getKey().name(), entry.getKey().label(), members.size(),
                    minH, maxH, ratio, note));
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
