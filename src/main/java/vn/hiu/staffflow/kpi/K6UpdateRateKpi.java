package vn.hiu.staffflow.kpi;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.domain.Domain.TaskEvent;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.repository.TaskEventRepository;
import vn.hiu.staffflow.repository.TaskRepository;

/**
 * K6 = nhân sự có cập nhật trạng thái ÷ tổng nhân sự được yêu cầu cập nhật trong tuần × 100%. Mục tiêu ≥ 90%.
 * GIẢ ĐỊNH (đặc tả không nêu định nghĩa chính xác, cần nhóm xác nhận lại):
 * - "được yêu cầu cập nhật trong tuần" = có ít nhất 1 nhiệm vụ đang phụ trách, chưa ở trạng thái Xong,
 *   với hạn không sau ngày cuối tuần đang xem.
 * - "có cập nhật" = có ít nhất 1 lần đổi trạng thái (task_event) trên nhiệm vụ của mình trong chính tuần đó.
 */
@Component
public class K6UpdateRateKpi implements KpiCalculator {
    private final TaskRepository tasks;
    private final TaskEventRepository events;

    public K6UpdateRateKpi(TaskRepository tasks, TaskEventRepository events) {
        this.tasks = tasks;
        this.events = events;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        Set<Long> required = new HashSet<>();
        for (Task t : tasks.findAll()) {
            if (t.status() != TaskStatus.XONG && !t.dueDate().isAfter(sunday)) {
                required.add(t.assigneeId());
            }
        }
        if (required.isEmpty()) {
            return new KpiResult("K6", "Nhân sự cập nhật trạng thái nhiệm vụ", "N/A", null, "≥ 90%", KpiStatus.NA,
                    "Không có nhân sự nào được yêu cầu cập nhật trong tuần này (chưa có nhiệm vụ đang mở)", formula());
        }
        Map<Long, Long> ownerOfTask = new HashMap<>();
        for (Task t : tasks.findAll()) {
            ownerOfTask.put(t.id(), t.assigneeId());
        }
        Set<Long> updated = new HashSet<>();
        for (TaskEvent e : events.findAll()) {
            LocalDate d = e.at().toLocalDate();
            if (!d.isBefore(monday) && !d.isAfter(sunday)) {
                Long owner = ownerOfTask.get(e.taskId());
                if (owner != null && required.contains(owner)) {
                    updated.add(owner);
                }
            }
        }
        double pct = updated.size() * 100.0 / required.size();
        KpiStatus status = pct >= 90.0 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        String detail = updated.size() + "/" + required.size() + " nhân sự có cập nhật trạng thái nhiệm vụ trong tuần";
        return new KpiResult("K6", "Nhân sự cập nhật trạng thái nhiệm vụ", round1(pct) + "%", pct, "≥ 90%", status,
                detail, formula());
    }

    private static String formula() {
        return "K6 = (nhân sự có ≥1 lần đổi trạng thái nhiệm vụ trong tuần) ÷ (nhân sự có nhiệm vụ đang mở, hạn ≤ cuối tuần) × 100%";
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
