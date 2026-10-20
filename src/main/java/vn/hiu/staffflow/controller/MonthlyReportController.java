package vn.hiu.staffflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Requests.MonthlyReportRequest;
import vn.hiu.staffflow.dto.Views.MonthlyReportView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.MonthlyReportService;

/** K8 - báo cáo tháng nộp đúng hạn (mục 7, Bảng 22). Chỉ ADMIN được ghi nhận (service kiểm tra quyền). */
@RestController
@RequestMapping("/api/monthly-reports")
public class MonthlyReportController {
    private final MonthlyReportService service;

    public MonthlyReportController(MonthlyReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<MonthlyReportView> list() {
        return service.list();
    }

    @PostMapping
    public MonthlyReportView record(@Valid @RequestBody MonthlyReportRequest in,
                                    @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.record(actor, in);
    }
}
