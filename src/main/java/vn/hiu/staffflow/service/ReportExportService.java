package vn.hiu.staffflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.dto.Views.BranchSchedule;
import vn.hiu.staffflow.dto.Views.DashboardView;
import vn.hiu.staffflow.dto.Views.GroupWorkloadView;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.MonthlyReportView;
import vn.hiu.staffflow.dto.Views.TaskView;
import vn.hiu.staffflow.dto.Views.WeekView;
import vn.hiu.staffflow.dto.Views.WorkloadView;

/**
 * Xuất báo cáo CSV UTF-8 có BOM (mục 2, mục 10, T6). Số liệu lấy TRỰC TIẾP từ ScheduleService/TaskService/
 * WorkloadService/DashboardService — không viết cứng số liệu ở đây hay ở frontend.
 */
@Service
public class ReportExportService {

    /** T6: báo cáo tuần — tổng giờ mở cửa, giờ có người, ca trống, nhiệm vụ, tải việc, K1-K8. */
    public byte[] weekCsv(WeekView week, List<TaskView> taskList, DashboardView dashboard) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "StaffFlow - Báo cáo tuần", week.weekStart() + " đến " + week.weekEnd() });
        rows.add(new String[] { "DỮ LIỆU GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH - không phải dữ liệu thật của thư viện" });
        rows.add(new String[] {});

        rows.add(new String[] { "BAO PHU GIO-CO SO" });
        rows.add(new String[] { "Cơ sở", "Giờ cần phủ", "Giờ có người", "Ca trống/bị ảnh hưởng", "Tỉ lệ bao phủ (%)" });
        for (BranchSchedule b : week.branches()) {
            rows.add(new String[] { b.name(), hours(b.requiredMinutes()), hours(b.coveredMinutes()),
                    String.valueOf(b.emptyShifts()), pct(b.coveragePct()) });
        }
        rows.add(new String[] { "Tổng", hours(week.requiredMinutes()), hours(week.coveredMinutes()), "", pct(week.coveragePct()) });
        rows.add(new String[] {});

        rows.add(new String[] { "NHIEM VU TRONG TUAN (" + taskList.size() + ")" });
        rows.add(new String[] { "Tiêu đề", "Cơ sở", "Người phụ trách", "Hạn", "Trạng thái", "Ưu tiên",
                "Thời lượng (giờ)", "Đóng không có minh chứng" });
        for (TaskView t : taskList) {
            rows.add(new String[] { t.title(), t.branchName(), t.assigneeCode(), t.dueDate(), t.statusLabel(),
                    t.priorityLabel(), hours(t.estimatedMinutes()), t.closedWithoutEvidence() ? "Có" : "Không" });
        }
        rows.add(new String[] {});

        rows.add(new String[] { "TAI VIEC THEO NGUOI" });
        rows.add(new String[] { "Nhân sự", "Nhóm chức danh", "Ca trực (giờ)", "Nhiệm vụ (giờ)", "Tổng (giờ)",
                "Hệ số tải (%)", "Vượt 100% tải việc" });
        for (WorkloadView w : dashboard.workloadByUser()) {
            rows.add(new String[] { w.userCode() + " - " + w.userName(), w.titleGroupLabel(), hours(w.shiftMinutes()),
                    hours(w.taskMinutes()), hours(w.totalMinutes()), String.valueOf(w.pct()), w.overLimit() ? "Có" : "Không" });
        }
        rows.add(new String[] {});

        rows.add(new String[] { "TAI VIEC THEO NHOM CHUC DANH" });
        rows.add(new String[] { "Nhóm", "Số người", "Thấp nhất (giờ)", "Cao nhất (giờ)", "Tỉ lệ cao/thấp", "Ghi chú" });
        for (GroupWorkloadView g : dashboard.workloadByGroup()) {
            rows.add(new String[] { g.titleGroupLabel(), String.valueOf(g.memberCount()),
                    g.minHours() == null ? "N/A" : String.valueOf(g.minHours()),
                    g.maxHours() == null ? "N/A" : String.valueOf(g.maxHours()),
                    g.ratio() == null ? "N/A" : String.valueOf(g.ratio()), g.note() == null ? "" : g.note() });
        }
        rows.add(new String[] {});

        rows.add(new String[] { "KPI K1-K8" });
        rows.add(new String[] { "Mã", "Tên", "Giá trị", "Trạng thái", "Mục tiêu", "Diễn giải" });
        for (KpiResult k : dashboard.kpis()) {
            rows.add(new String[] { k.code(), k.label(), k.valueLabel(), statusLabel(k.status().name()), k.target(), k.detail() });
        }

        return CsvUtil.write(rows);
    }

    /** Báo cáo tháng K8: toàn bộ bản ghi monthly_reports đã ghi nhận. */
    public byte[] monthCsv(List<MonthlyReportView> reports) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "StaffFlow - Báo cáo tháng (K8)" });
        rows.add(new String[] { "DỮ LIỆU GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH - không phải dữ liệu thật của thư viện" });
        rows.add(new String[] {});
        rows.add(new String[] { "Tháng", "Hạn nộp", "Ngày nộp", "Đúng hạn", "Người ghi nhận", "Thời điểm ghi nhận", "Ghi chú" });
        for (MonthlyReportView r : reports) {
            rows.add(new String[] { r.yearMonth(), r.dueDate(), r.submittedDate() == null ? "Chưa nộp" : r.submittedDate(),
                    r.submitted() ? (r.onTime() ? "Đúng hạn" : "Trễ hạn") : "", r.recordedByCode(), r.recordedAt(),
                    r.note() == null ? "" : r.note() });
        }
        long onTime = reports.stream().filter(MonthlyReportView::onTime).count();
        rows.add(new String[] {});
        rows.add(new String[] { "Tổng", String.valueOf(reports.size()), "Đúng hạn", String.valueOf(onTime),
                reports.isEmpty() ? "N/A" : pct(onTime * 100.0 / reports.size()) });
        return CsvUtil.write(rows);
    }

    private static String hours(int minutes) {
        double h = minutes / 60.0;
        return h == Math.floor(h) ? String.valueOf((int) h) : String.format(java.util.Locale.US, "%.2f", h);
    }

    private static String pct(Double v) {
        return v == null ? "N/A" : String.format(java.util.Locale.US, "%.1f%%", v);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "DAT" -> "Đạt";
            case "CHUA_DAT" -> "Chưa đạt";
            default -> "Chưa đủ dữ liệu";
        };
    }
}
