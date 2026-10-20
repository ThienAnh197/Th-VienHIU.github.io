package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Views.DashboardView;
import vn.hiu.staffflow.dto.Views.GroupWorkloadView;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.dto.Views.MilestoneSuggestion;
import vn.hiu.staffflow.dto.Views.WorkloadView;
import vn.hiu.staffflow.kpi.K1CoverageKpi;
import vn.hiu.staffflow.kpi.K2EmptyShiftKpi;
import vn.hiu.staffflow.kpi.K3TaskOnTimeKpi;
import vn.hiu.staffflow.kpi.K4SwapProcessingKpi;
import vn.hiu.staffflow.kpi.K5BalanceKpi;
import vn.hiu.staffflow.kpi.K6UpdateRateKpi;
import vn.hiu.staffflow.kpi.K7SatisfactionKpi;
import vn.hiu.staffflow.kpi.K8MonthlyReportKpi;

/**
 * Tổng hợp KPI K1-K8, tải việc, và gợi ý quyết định ở mốc tuần 4/8 (mục 7.1).
 * Các gợi ý CHỈ MANG TÍNH THAM KHẢO cho Giám đốc, không tự động thực thi bất kỳ hành động nào.
 */
@Service
public class DashboardService {
    private final K1CoverageKpi k1;
    private final K2EmptyShiftKpi k2;
    private final K3TaskOnTimeKpi k3;
    private final K4SwapProcessingKpi k4;
    private final K5BalanceKpi k5;
    private final K6UpdateRateKpi k6;
    private final K7SatisfactionKpi k7;
    private final K8MonthlyReportKpi k8;
    private final WorkloadService workload;
    private final LocalDate mainWeek;

    public DashboardService(K1CoverageKpi k1, K2EmptyShiftKpi k2, K3TaskOnTimeKpi k3, K4SwapProcessingKpi k4,
                            K5BalanceKpi k5, K6UpdateRateKpi k6, K7SatisfactionKpi k7, K8MonthlyReportKpi k8,
                            WorkloadService workload,
                            @Value("${staffflow.demo-week-start:2026-10-19}") String mainWeek) {
        this.k1 = k1;
        this.k2 = k2;
        this.k3 = k3;
        this.k4 = k4;
        this.k5 = k5;
        this.k6 = k6;
        this.k7 = k7;
        this.k8 = k8;
        this.workload = workload;
        this.mainWeek = LocalDate.parse(mainWeek);
    }

    public DashboardView get(LocalDate monday) {
        List<KpiResult> kpis = List.of(k1.compute(monday), k2.compute(monday), k3.compute(monday),
                k4.compute(monday), k5.compute(monday), k6.compute(monday), k7.compute(monday), k8.compute(monday));
        List<WorkloadView> byUser = workload.byUser(monday);
        List<GroupWorkloadView> byGroup = workload.byGroup(monday);

        long weeksSinceStart = java.time.temporal.ChronoUnit.WEEKS.between(mainWeek, monday);
        Integer milestone = null;
        if (!monday.isBefore(mainWeek)) {
            int weekNo = (int) weeksSinceStart + 1;
            if (weekNo == 4 || weekNo == 8) {
                milestone = weekNo;
            }
        }
        List<MilestoneSuggestion> suggestions = milestone == null ? List.of() : suggestionsFor(monday, kpis, milestone);

        return new DashboardView(Fmt.d(monday), Fmt.d(monday.plusDays(6)), kpis, byUser, byGroup, milestone, suggestions);
    }

    private List<MilestoneSuggestion> suggestionsFor(LocalDate monday, List<KpiResult> kpis, int milestone) {
        List<MilestoneSuggestion> out = new ArrayList<>();
        KpiResult k1r = find(kpis, "K1");
        KpiResult k3r = find(kpis, "K3");
        KpiResult k5r = find(kpis, "K5");
        KpiResult k6r = find(kpis, "K6");
        KpiResult k7r = find(kpis, "K7");

        if (milestone == 4) {
            Double k1DBP = k1.computeBranch(monday, 1L).rawValue();
            Double k1DS = k1.computeBranch(monday, 2L).rawValue();
            if (k1DBP != null && k1DS != null && k1DBP < 80.0 && k1DS < 80.0) {
                out.add(new MilestoneSuggestion(
                        "Xem xét lập đề xuất P4 (bổ sung biên chế/thuê sinh viên hỗ trợ)",
                        "K1 < 80% ở cả hai cơ sở thí điểm sau 4 tuần (ĐBP " + round1(k1DBP) + "%, Đầm Sen " + round1(k1DS)
                                + "%). Nếu xác nhận thiếu hụt là thật, đính kèm số liệu K1, K2 và lịch ca khi trình đề xuất."));
            }
        }

        boolean k6Low = k6r.status() != KpiStatus.NA && k6r.rawValue() < 70.0;
        boolean k7Low = k7r.status() != KpiStatus.NA && k7r.rawValue() < 3.0;
        if (k6Low || k7Low) {
            String reason = (k6Low ? "K6 = " + k6r.valueLabel() + " (< 70%)" : "")
                    + (k6Low && k7Low ? "; " : "")
                    + (k7Low ? "K7 = " + k7r.valueLabel() + " (< 3,0)" : "");
            out.add(new MilestoneSuggestion(
                    "Cân nhắc dừng mở rộng, rút gọn biểu mẫu, đào tạo lại",
                    reason + ". Nếu đến tuần 8 vẫn không đạt, cân nhắc quay về phương án P2 (bảng tính chung)."));
        }

        if (k1r.status() != KpiStatus.NA && k1r.rawValue() >= 95.0 && k5r.status() != KpiStatus.NA && k5r.rawValue() <= 1.5) {
            out.add(new MilestoneSuggestion("Cân nhắc mở rộng StaffFlow sang cơ sở D2",
                    "K1 = " + k1r.valueLabel() + " (≥ 95%) và K5 = " + k5r.valueLabel() + " (≤ 1,5 lần)."));
        }

        if (k3r.status() != KpiStatus.NA && k3r.rawValue() < 85.0) {
            out.add(new MilestoneSuggestion("Rà soát lại định mức thời gian của nhiệm vụ",
                    "K3 = " + k3r.valueLabel() + " (< 85%). Chỉ rà soát định mức, không quy trách nhiệm cá nhân khi chưa có dữ liệu."));
        }

        if (out.isEmpty()) {
            out.add(new MilestoneSuggestion("Chưa có gợi ý nào ở mốc tuần " + milestone,
                    "Các chỉ số hiện tại chưa chạm ngưỡng nào trong mục 7.1 của báo cáo, hoặc đang thiếu dữ liệu (N/A)."));
        }
        return out;
    }

    private static KpiResult find(List<KpiResult> list, String code) {
        return list.stream().filter(k -> k.code().equals(code)).findFirst().orElseThrow();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
