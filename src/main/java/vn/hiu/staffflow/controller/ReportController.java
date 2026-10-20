package vn.hiu.staffflow.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Views.DashboardView;
import vn.hiu.staffflow.dto.Views.WeekView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.DashboardService;
import vn.hiu.staffflow.service.MonthlyReportService;
import vn.hiu.staffflow.service.ReportExportService;
import vn.hiu.staffflow.service.ScheduleService;
import vn.hiu.staffflow.service.TaskService;
import vn.hiu.staffflow.service.WeekUtil;

/** Xuất báo cáo tuần và tháng dạng CSV UTF-8 có BOM (mục 2, mục 10, T6). */
@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportExportService export;
    private final ScheduleService schedule;
    private final TaskService tasks;
    private final DashboardService dashboard;
    private final MonthlyReportService monthlyReports;
    private final AppClock clock;

    public ReportController(ReportExportService export, ScheduleService schedule, TaskService tasks,
                            DashboardService dashboard, MonthlyReportService monthlyReports, AppClock clock) {
        this.export = export;
        this.schedule = schedule;
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.monthlyReports = monthlyReports;
        this.clock = clock;
    }

    @GetMapping("/week.csv")
    public ResponseEntity<byte[]> week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                       @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        LocalDate monday = WeekUtil.mondayOf(week != null ? week : clock.now().toLocalDate());
        WeekView w = schedule.week(monday, null, actor);
        var taskList = tasks.list(actor, monday, null, null, null);
        DashboardView d = dashboard.get(monday);
        byte[] csv = export.weekCsv(w, taskList, d);
        return csvResponse(csv, "staffflow-bao-cao-tuan-" + monday + ".csv");
    }

    @GetMapping("/month.csv")
    public ResponseEntity<byte[]> month() {
        byte[] csv = export.monthCsv(monthlyReports.list());
        return csvResponse(csv, "staffflow-bao-cao-thang.csv");
    }

    private static ResponseEntity<byte[]> csvResponse(byte[] csv, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }
}
