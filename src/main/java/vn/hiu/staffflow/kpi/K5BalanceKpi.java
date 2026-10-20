package vn.hiu.staffflow.kpi;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.dto.Views.GroupWorkloadView;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.service.WorkloadService;

/**
 * K5 = tải cao nhất ÷ tải thấp nhất trong cùng nhóm chức danh. Mục tiêu ≤ 1,5 lần.
 * N/A kèm giải thích nếu nhóm chỉ có 1 người hoặc tải thấp nhất = 0 (xem WorkloadService.byGroup).
 * Khi có nhiều nhóm so sánh được, K5 lấy tỉ lệ CAO NHẤT (trường hợp mất cân bằng nhất) để cảnh báo sớm.
 */
@Component
public class K5BalanceKpi implements KpiCalculator {
    private final WorkloadService workload;

    public K5BalanceKpi(WorkloadService workload) {
        this.workload = workload;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        List<GroupWorkloadView> groups = workload.byGroup(monday);
        List<GroupWorkloadView> comparable = groups.stream().filter(g -> g.ratio() != null).toList();
        if (comparable.isEmpty()) {
            String why = groups.stream().filter(g -> g.note() != null)
                    .map(g -> g.titleGroupLabel() + ": " + g.note())
                    .reduce((a, b) -> a + "; " + b).orElse("không đủ dữ liệu so sánh trong nhóm nào");
            return new KpiResult("K5", "Cân bằng tải việc trong nhóm chức danh", "N/A", null, "≤ 1,5 lần",
                    KpiStatus.NA, why, formula());
        }
        GroupWorkloadView worst = comparable.stream().max(Comparator.comparingDouble(GroupWorkloadView::ratio)).get();
        KpiStatus status = worst.ratio() <= 1.5 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        String detail = "Lệch nhất: " + worst.titleGroupLabel() + " (" + worst.minHours() + "–" + worst.maxHours()
                + " giờ/người, " + worst.memberCount() + " người)";
        return new KpiResult("K5", "Cân bằng tải việc trong nhóm chức danh", worst.ratio() + " lần", worst.ratio(),
                "≤ 1,5 lần", status, detail, formula());
    }

    private static String formula() {
        return "K5 = tải việc (giờ) cao nhất ÷ tải việc thấp nhất, so sánh giữa các thành viên cùng nhóm chức danh trong tuần";
    }
}
