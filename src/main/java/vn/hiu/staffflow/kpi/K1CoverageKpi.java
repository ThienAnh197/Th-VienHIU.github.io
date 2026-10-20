package vn.hiu.staffflow.kpi;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ShiftRepository;
import vn.hiu.staffflow.service.CalendarService;
import vn.hiu.staffflow.service.CoverageCalculator;

/** K1 = giờ-cơ sở có người phụ trách hợp lệ ÷ 207 × 100% (tuần đầy đủ). Mục tiêu ≥ 95%. */
@Component
public class K1CoverageKpi implements KpiCalculator {
    private final CalendarService calendar;
    private final ShiftRepository shifts;
    private final OrgRepository org;

    public K1CoverageKpi(CalendarService calendar, ShiftRepository shifts, OrgRepository org) {
        this.calendar = calendar;
        this.shifts = shifts;
        this.org = org;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        List<Shift> weekShifts = shifts.findBetween(monday, monday.plusDays(6));
        int required = 0;
        int covered = 0;
        for (Branch b : org.branches()) {
            required += calendar.requiredMinutes(b.id(), monday);
            covered += CoverageCalculator.coveredMinutes(b.id(), weekShifts, calendar);
        }
        return build(required, covered, "toàn hệ thống (3 cơ sở)");
    }

    /** Dùng riêng cho gợi ý quyết định mục 7.1 (cần K1 của từng cơ sở thí điểm). */
    public KpiResult computeBranch(LocalDate monday, long branchId) {
        List<Shift> weekShifts = shifts.findBetween(monday, monday.plusDays(6));
        int required = calendar.requiredMinutes(branchId, monday);
        int covered = CoverageCalculator.coveredMinutes(branchId, weekShifts, calendar);
        String name = org.branch(branchId).map(Branch::name).orElse("?");
        return build(required, covered, name);
    }

    private KpiResult build(int required, int covered, String scope) {
        Double pct = required == 0 ? null : covered * 100.0 / required;
        KpiStatus status = pct == null ? KpiStatus.NA : (pct >= 95.0 ? KpiStatus.DAT : KpiStatus.CHUA_DAT);
        String valueLabel = pct == null ? "N/A" : round1(pct) + "%";
        String detail = pct == null ? "Không có giờ mở cửa cần phủ trong tuần (" + scope + ")"
                : String.format("%s: %.1f/%.1f giờ-cơ sở có người phụ trách hợp lệ", scope, covered / 60.0, required / 60.0);
        return new KpiResult("K1", "Giờ-cơ sở có người phụ trách hợp lệ", valueLabel, pct, "≥ 95%", status, detail,
                "K1 = giờ-cơ sở có người phụ trách (ca Đủ người, nằm trong giờ mở cửa) ÷ tổng giờ mở cửa cần phủ × 100%");
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
