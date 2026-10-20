package vn.hiu.staffflow.kpi;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.SwapRequest;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.repository.SwapRepository;

/** K4 = thời gian trung bình từ gửi đến duyệt yêu cầu đổi ca (các yêu cầu được xử lý trong tuần). Mục tiêu ≤ 24 giờ. */
@Component
public class K4SwapProcessingKpi implements KpiCalculator {
    private final SwapRepository swaps;

    public K4SwapProcessingKpi(SwapRepository swaps) {
        this.swaps = swaps;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        List<SwapRequest> decided = swaps.findAll().stream()
                .filter(r -> r.decidedAt() != null)
                .filter(r -> !r.decidedAt().toLocalDate().isBefore(monday) && !r.decidedAt().toLocalDate().isAfter(sunday))
                .toList();
        if (decided.isEmpty()) {
            return new KpiResult("K4", "Thời gian xử lý yêu cầu đổi ca", "N/A", null, "≤ 24 giờ", KpiStatus.NA,
                    "Chưa có yêu cầu đổi ca nào được duyệt/từ chối trong tuần này", formula());
        }
        double avgHours = decided.stream()
                .mapToLong(r -> Duration.between(r.submittedAt(), r.decidedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        KpiStatus status = avgHours <= 24.0 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        String detail = decided.size() + " yêu cầu đã xử lý, trung bình " + round1(avgHours) + " giờ/yêu cầu";
        return new KpiResult("K4", "Thời gian xử lý yêu cầu đổi ca", round1(avgHours) + " giờ", avgHours,
                "≤ 24 giờ", status, detail, formula());
    }

    private static String formula() {
        return "K4 = trung bình (thời điểm duyệt/từ chối − thời điểm gửi) của các yêu cầu đổi ca xử lý trong tuần";
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
