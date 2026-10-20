package vn.hiu.staffflow.kpi;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.service.MonthlyReportService;

/**
 * K8 = tỉ lệ báo cáo tháng nộp đúng hạn (monthly_reports), mục tiêu 100%.
 * GIẢ ĐỊNH: K8 là số liệu TOÀN HỆ THỐNG tính trên mọi bản ghi báo cáo tháng đã ghi nhận, không gắn với
 * tuần đang xem trên dashboard (đặc tả không nói K8 phải lọc theo tuần) — ghi rõ đây là giả định bổ sung.
 */
@Component
public class K8MonthlyReportKpi implements KpiCalculator {
    private final MonthlyReportService reports;

    public K8MonthlyReportKpi(MonthlyReportService reports) {
        this.reports = reports;
    }

    @Override
    public KpiResult compute(LocalDate monday) {
        Double pct = reports.onTimePercent();
        int n = reports.total();
        if (pct == null) {
            return new KpiResult("K8", "Báo cáo tháng đúng hạn", "N/A", null, "100%", KpiStatus.NA,
                    "Chưa ghi nhận báo cáo tháng nào", formula());
        }
        KpiStatus status = pct >= 100.0 ? KpiStatus.DAT : KpiStatus.CHUA_DAT;
        double rounded = Math.round(pct * 10) / 10.0;
        return new KpiResult("K8", "Báo cáo tháng đúng hạn", rounded + "%", rounded, "100%", status,
                n + " báo cáo tháng đã ghi nhận (toàn hệ thống, không lọc theo tuần)", formula());
    }

    private static String formula() {
        return "K8 = (số báo cáo tháng nộp đúng hạn) ÷ (tổng số báo cáo tháng đã ghi nhận) × 100%";
    }
}
