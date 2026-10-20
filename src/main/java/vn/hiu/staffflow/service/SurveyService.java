package vn.hiu.staffflow.service;

import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.SurveyPeriod;
import vn.hiu.staffflow.domain.Domain.SurveyResponse;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.SurveyRequest;
import vn.hiu.staffflow.dto.Views.SurveyView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.SurveyRepository;

/**
 * K7 - khảo sát hài lòng nhân sự (mục 7, Bảng 22): 3 câu thang điểm 1-5, nhập ở tuần 4 và tuần 8.
 * Mỗi người chỉ gửi được cho CHÍNH MÌNH (không cho sửa khảo sát hộ người khác); nộp lại cùng kỳ sẽ ghi đè.
 */
@Service
public class SurveyService {
    private final SurveyRepository repo;
    private final OrgRepository org;
    private final AuditService audit;
    private final AppClock clock;

    public SurveyService(SurveyRepository repo, OrgRepository org, AuditService audit, AppClock clock) {
        this.repo = repo;
        this.org = org;
        this.audit = audit;
        this.clock = clock;
    }

    public SurveyView submit(AppUser actor, SurveyRequest in) {
        SurveyPeriod period = parsePeriod(in.period());
        SurveyResponse existing = repo.findAll().stream()
                .filter(r -> r.userId() == actor.id() && r.period() == period).findFirst().orElse(null);
        var now = clock.now();
        SurveyResponse toSave = new SurveyResponse(existing == null ? 0 : existing.id(), period, actor.id(),
                in.q1(), in.q2(), in.q3(), clean(in.note()), now);
        SurveyResponse saved = repo.save(toSave);
        audit.log(actor, existing == null ? "SURVEY_SUBMIT" : "SURVEY_RESUBMIT", "survey", saved.id(),
                period.label() + " - " + actor.code());
        return view(saved);
    }

    public List<SurveyView> list(String period) {
        SurveyPeriod filter = (period == null || period.isBlank()) ? null : parsePeriod(period);
        return repo.findAll().stream()
                .filter(r -> filter == null || r.period() == filter)
                .map(this::view)
                .toList();
    }

    /** Điểm trung bình 3 câu của mọi phản hồi trong một kỳ; null nếu chưa có phản hồi nào (dùng cho K7). */
    public Double averageFor(SurveyPeriod period) {
        List<SurveyResponse> rs = repo.findAll().stream().filter(r -> r.period() == period).toList();
        if (rs.isEmpty()) {
            return null;
        }
        return rs.stream().mapToDouble(SurveyResponse::avg).average().orElse(0);
    }

    public int countFor(SurveyPeriod period) {
        return (int) repo.findAll().stream().filter(r -> r.period() == period).count();
    }

    private SurveyView view(SurveyResponse r) {
        AppUser u = org.user(r.userId()).orElse(null);
        return new SurveyView(r.id(), r.period().name(), r.period().label(), u == null ? "?" : u.code(),
                u == null ? "?" : u.displayName(), r.q1(), r.q2(), r.q3(), Math.round(r.avg() * 10) / 10.0,
                r.note(), Fmt.dt(r.submittedAt()));
    }

    private static SurveyPeriod parsePeriod(String s) {
        try {
            return SurveyPeriod.valueOf(s);
        } catch (Exception e) {
            throw ApiException.unprocessable("Kỳ khảo sát không hợp lệ (TUAN_4 hoặc TUAN_8)", List.of("period: " + s));
        }
    }

    private static String clean(String s) {
        return s == null ? null : s.trim();
    }
}
