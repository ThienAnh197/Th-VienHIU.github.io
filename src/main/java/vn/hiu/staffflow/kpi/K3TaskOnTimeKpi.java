package vn.hiu.staffflow.kpi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.domain.Domain.TaskEvent;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.repository.TaskEventRepository;
import vn.hiu.staffflow.repository.TaskRepository;

/**
 * K3 = nhiệm vụ Xong đúng hạn ÷ tổng nhiệm vụ ĐÃ ĐẾN HẠN × 100%. Mục tiêu ≥ 85%.
 * Phạm vi: nhiệm vụ có hạn trong tuần đang xem VÀ hạn đã qua (dueDate ≤ hôm nay) — nhiệm vụ chưa đến hạn
 * không tính vào mẫu số, dù đã làm xong sớm hay chưa, theo đúng yêu cầu mục 6.5.
 */
@Component
public class K3TaskOnTimeKpi implements KpiCalculator {
    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final AppClock clock;

    public K3TaskOnTimeKpi(TaskRepository tasks, TaskEventRepository events, AppClock clock) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        LocalDate today = clock.now().toLocalDate();
        List<Task> due = tasks.findAll().stream()
                .filter(t -> !t.dueDate().isBefore(monday) && !t.dueDate().isAfter(sunday))
                .filter(t -> !t.dueDate().isAfter(today))
                .toList();
        if (due.isEmpty()) {
            return new KpiResult("K3", "Nhiệm vụ hoàn thành đúng hạn", "N/A", null, "≥ 85%", KpiStatus.NA,
                    "Chưa có nhiệm vụ nào đến hạn trong tuần này (loại trừ nhiệm vụ chưa đến hạn khỏi mẫu số)",
                    formula());
        }
        long onTime = due.stream().filter(this::completedOnTime).count();
        double pct = onTime * 100.0 / due.size();
        KpiStatus status = pct >= 85.0 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        String detail = onTime + "/" + due.size() + " nhiệm vụ đã đến hạn được hoàn thành đúng hạn";
        return new KpiResult("K3", "Nhiệm vụ hoàn thành đúng hạn", round1(pct) + "%", pct, "≥ 85%", status, detail,
                formula());
    }

    private boolean completedOnTime(Task t) {
        if (t.status() != TaskStatus.XONG) {
            return false;
        }
        LocalDateTime completedAt = events.findByTask(t.id()).stream()
                .filter(e -> e.to() == TaskStatus.XONG)
                .max(Comparator.comparing(TaskEvent::at))
                .map(TaskEvent::at)
                .orElse(null);
        return completedAt != null && !completedAt.toLocalDate().isAfter(t.dueDate());
    }

    private static String formula() {
        return "K3 = (nhiệm vụ Xong, với lần chuyển sang Xong gần nhất ≤ hạn) ÷ (nhiệm vụ có hạn trong tuần và hạn đã qua) × 100%";
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
