package vn.hiu.staffflow.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.MonthlyReport;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.MonthlyReportRequest;
import vn.hiu.staffflow.dto.Views.MonthlyReportView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.MonthlyReportRepository;
import vn.hiu.staffflow.repository.OrgRepository;

/** K8 - báo cáo tháng nộp đúng hạn (mục 7, Bảng 22). Chỉ ADMIN (NV-01) ghi nhận, vì đây là số liệu toàn hệ thống. */
@Service
public class MonthlyReportService {
    private static final Pattern YM = Pattern.compile("\\d{4}-\\d{2}");

    private final MonthlyReportRepository repo;
    private final OrgRepository org;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final AppClock clock;

    public MonthlyReportService(MonthlyReportRepository repo, OrgRepository org, AccessPolicy policy,
                                AuditService audit, AppClock clock) {
        this.repo = repo;
        this.org = org;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    public MonthlyReportView record(AppUser actor, MonthlyReportRequest in) {
        policy.requireManageBranch(actor, null, "ghi nhận báo cáo tháng");
        if (!YM.matcher(in.yearMonth()).matches()) {
            throw ApiException.unprocessable("Tháng không hợp lệ, dùng định dạng yyyy-MM", List.of("yearMonth: " + in.yearMonth()));
        }
        if (in.submittedDate() != null && in.submittedDate().isBefore(in.dueDate().minusMonths(2))) {
            throw ApiException.unprocessable("Ngày nộp không hợp lý so với hạn nộp", List.of());
        }
        MonthlyReport existing = repo.findAll().stream()
                .filter(r -> r.yearMonth().equals(in.yearMonth())).findFirst().orElse(null);
        MonthlyReport toSave = new MonthlyReport(existing == null ? 0 : existing.id(), in.yearMonth(), in.dueDate(),
                in.submittedDate(), clean(in.note()), actor.id(), clock.now());
        MonthlyReport saved = repo.save(toSave);
        audit.log(actor, existing == null ? "MONTHLY_REPORT_RECORD" : "MONTHLY_REPORT_UPDATE", "monthly_report",
                saved.id(), in.yearMonth() + (saved.onTime() ? " đúng hạn" : " trễ/chưa nộp"));
        return view(saved);
    }

    public List<MonthlyReportView> list() {
        return repo.findAll().stream()
                .sorted((a, b) -> b.yearMonth().compareTo(a.yearMonth()))
                .map(this::view).toList();
    }

    /** % báo cáo đúng hạn trên tổng số báo cáo đã ghi nhận; null nếu chưa có bản ghi nào (dùng cho K8). */
    public Double onTimePercent() {
        List<MonthlyReport> all = repo.findAll();
        if (all.isEmpty()) {
            return null;
        }
        long onTime = all.stream().filter(MonthlyReport::onTime).count();
        return onTime * 100.0 / all.size();
    }

    public int total() {
        return repo.findAll().size();
    }

    private MonthlyReportView view(MonthlyReport r) {
        AppUser u = org.user(r.recordedBy()).orElse(null);
        boolean submitted = r.submittedDate() != null;
        return new MonthlyReportView(r.id(), r.yearMonth(), Fmt.d(r.dueDate()), Fmt.d(r.submittedDate()),
                submitted, r.onTime(), r.note(), u == null ? "?" : u.code(), Fmt.dt(r.recordedAt()));
    }

    private static String clean(String s) {
        return s == null ? null : s.trim();
    }
}
