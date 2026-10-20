package vn.hiu.staffflow.kpi;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.repository.ShiftRepository;

/**
 * K2 = số ca trống (Trống hoặc Bị ảnh hưởng do vắng) còn CHƯA XỬ LÝ trong vòng 24 giờ trước giờ bắt đầu ca,
 * tính tại thời điểm hiện tại của hệ thống; đếm mỗi ca một lần. Mục tiêu ≤ 1 ca/tuần.
 */
@Component
public class K2EmptyShiftKpi implements KpiCalculator {
    private final ShiftRepository shifts;
    private final AppClock clock;

    public K2EmptyShiftKpi(ShiftRepository shifts, AppClock clock) {
        this.shifts = shifts;
        this.clock = clock;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        var now = clock.now();
        var deadline = now.plusHours(24);
        List<Shift> weekShifts = shifts.findBetween(monday, monday.plusDays(6));
        long count = weekShifts.stream()
                .filter(s -> s.status() != ShiftStatus.DU_NGUOI)
                .filter(s -> !s.startAt().isAfter(deadline))
                .count();
        KpiStatus status = count <= 1 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        String detail = count == 0 ? "Không có ca nào trống trong vòng 24 giờ trước giờ trực"
                : count + " ca trống/bị ảnh hưởng còn trong vòng 24 giờ trước giờ trực (tính đến "
                        + vn.hiu.staffflow.dto.Fmt.dt(now) + ")";
        return new KpiResult("K2", "Ca trống chưa xử lý trước giờ trực 24 giờ", count + " ca", (double) count,
                "≤ 1 ca/tuần", status, detail,
                "K2 = số ca (Trống hoặc Bị ảnh hưởng do vắng) có giờ bắt đầu còn dưới 24 giờ tính từ bây giờ, đếm mỗi ca một lần");
    }
}
