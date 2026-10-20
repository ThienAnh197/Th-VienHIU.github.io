package vn.hiu.staffflow.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.dto.Views.DashboardView;
import vn.hiu.staffflow.service.DashboardService;
import vn.hiu.staffflow.service.WeekUtil;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final AppClock clock;

    public DashboardController(DashboardService dashboard, AppClock clock) {
        this.dashboard = dashboard;
        this.clock = clock;
    }

    /** KPI và tải việc dùng chung cho mọi vai trò (minh bạch, không dùng để chấm điểm cá nhân). */
    @GetMapping
    public DashboardView get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        LocalDate monday = WeekUtil.mondayOf(week != null ? week : clock.now().toLocalDate());
        return dashboard.get(monday);
    }
}
