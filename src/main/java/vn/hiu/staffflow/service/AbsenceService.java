package vn.hiu.staffflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Absence;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.AbsenceRequest;
import vn.hiu.staffflow.dto.Views.AbsenceView;
import vn.hiu.staffflow.dto.Views.CandidateView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.AbsenceRepository;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ShiftRepository;

/**
 * Báo vắng: ghi nhận khoảng thời gian, xác định ca bị ảnh hưởng, đánh dấu ca chưa có người thay
 * là "Bị ảnh hưởng do vắng" (không tính vào mức bao phủ K1), và gợi ý ứng viên thay thế.
 */
@Service
public class AbsenceService {
    private final AbsenceRepository absences;
    private final ShiftRepository shifts;
    private final OrgRepository org;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final CandidateService candidateService;
    private final AppClock clock;
    private final TxLock tx;

    public AbsenceService(AbsenceRepository absences, ShiftRepository shifts, OrgRepository org,
                          AccessPolicy policy, AuditService audit, CandidateService candidateService,
                          AppClock clock, TxLock tx) {
        this.absences = absences;
        this.shifts = shifts;
        this.org = org;
        this.policy = policy;
        this.audit = audit;
        this.candidateService = candidateService;
        this.clock = clock;
        this.tx = tx;
    }

    public AbsenceView report(AppUser actor, AbsenceRequest in) {
        AppUser target = org.user(in.userId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân sự"));
        policy.requireReportAbsenceFor(actor, target);
        if (in.from().isAfter(in.to())) {
            throw ApiException.unprocessable("Khoảng thời gian không hợp lệ",
                    List.of("Ngày bắt đầu phải không muộn hơn ngày kết thúc"));
        }
        return tx.run(() -> {
            Absence saved = absences.save(new Absence(0, target.id(), in.from(), in.to(),
                    clean(in.reason()), actor.id(), clock.now()));
            List<Long> affected = markAffectedShifts(target.id(), saved);
            audit.log(actor, "ABSENCE_REPORT", "absence", saved.id(),
                    target.code() + " " + in.from() + ".." + in.to() + " · " + affected.size() + " ca bị ảnh hưởng");
            return view(saved, affected);
        });
    }

    /** Đánh dấu các ca Đủ người của người vắng trong khoảng ngày thành Bị ảnh hưởng; trả về id các ca đó. */
    private List<Long> markAffectedShifts(long userId, Absence absence) {
        List<Long> ids = new ArrayList<>();
        for (Shift s : shifts.findBetween(absence.from(), absence.to())) {
            if (s.userId() != null && s.userId() == userId && s.status() == ShiftStatus.DU_NGUOI) {
                shifts.save(s.withAssignment(s.userId(), ShiftStatus.BI_ANH_HUONG));
                ids.add(s.id());
            }
        }
        return ids;
    }

    /** Danh sách ứng viên thay thế cho một ca cụ thể (dùng ở màn hình báo vắng và đổi ca). */
    public List<CandidateView> candidatesFor(long shiftId) {
        Shift s = shifts.findById(shiftId).orElseThrow(() -> ApiException.notFound("Không tìm thấy ca trực"));
        return candidateService.suggest(s);
    }

    public List<AbsenceView> list() {
        return absences.findAll().stream().map(a -> view(a, affectedIdsOf(a))).toList();
    }

    private List<Long> affectedIdsOf(Absence a) {
        return shifts.findBetween(a.from(), a.to()).stream()
                .filter(s -> s.userId() != null && s.userId() == a.userId()
                        && (s.status() == ShiftStatus.BI_ANH_HUONG || s.status() == ShiftStatus.DU_NGUOI))
                .map(Shift::id).toList();
    }

    private AbsenceView view(Absence a, List<Long> affected) {
        AppUser u = org.user(a.userId()).orElse(null);
        return new AbsenceView(a.id(), a.userId(), u == null ? null : u.code(), u == null ? null : u.displayName(),
                Fmt.d(a.from()), Fmt.d(a.to()), a.reason(), affected);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
