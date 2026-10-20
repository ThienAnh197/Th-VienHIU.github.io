package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Requests.ShiftRequest;
import vn.hiu.staffflow.dto.Views.BranchSchedule;
import vn.hiu.staffflow.dto.Views.DayInfo;
import vn.hiu.staffflow.dto.Views.ShiftView;
import vn.hiu.staffflow.dto.Views.WeekView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ShiftRepository;

/**
 * Lịch trực: xem theo tuần, tạo / sửa ca có kiểm tra quy tắc nghiệp vụ.
 * Phase 2 sẽ bổ sung: báo vắng, gán người thay, đổi ca.
 */
@Service
public class ScheduleService {
    private final ShiftRepository shifts;
    private final OrgRepository org;
    private final CalendarService calendar;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final TxLock tx;

    public ScheduleService(ShiftRepository shifts, OrgRepository org, CalendarService calendar,
                           AccessPolicy policy, AuditService audit, TxLock tx) {
        this.shifts = shifts;
        this.org = org;
        this.calendar = calendar;
        this.policy = policy;
        this.audit = audit;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ xem lịch

    public WeekView week(LocalDate anyDay, Long branchFilter, AppUser actor) {
        LocalDate monday = WeekUtil.mondayOf(anyDay);
        LocalDate sunday = monday.plusDays(6);
        List<Shift> all = shifts.findBetween(monday, sunday);

        List<BranchSchedule> result = new ArrayList<>();
        int totalRequired = 0;
        int totalCovered = 0;
        for (Branch b : org.branches()) {
            if (branchFilter != null && b.id() != branchFilter) {
                continue;
            }
            int required = calendar.requiredMinutes(b.id(), monday);
            int covered = CoverageCalculator.coveredMinutes(b.id(), all, calendar);
            List<ShiftView> views = all.stream().filter(s -> s.branchId() == b.id())
                    .map(s -> view(s, actor)).toList();
            int empty = (int) all.stream()
                    .filter(s -> s.branchId() == b.id() && s.status() != ShiftStatus.DU_NGUOI).count();
            result.add(new BranchSchedule(b.id(), b.code(), b.name(), b.address(), b.note(),
                    required, covered, pct(covered, required), empty, days(b.id(), monday), views));
            totalRequired += required;
            totalCovered += covered;
        }
        boolean hasTest = all.stream().anyMatch(Shift::testData);
        List<String> notes = new ArrayList<>();
        notes.add("Giờ cần phủ = giờ mở cửa của cơ sở trong tuần; giờ đã phân công chỉ tính ca có người, trạng thái Đủ người.");
        if (totalCovered < totalRequired) {
            notes.add("Chưa phủ đủ giờ mở cửa: thiếu " + fmtHours(totalRequired - totalCovered)
                    + " giờ-cơ sở. Hệ thống không tự đánh dấu hoàn thành.");
        }
        if (hasTest) {
            notes.add("Tuần này là DỮ LIỆU KIỂM THỬ (phủ đủ giờ) để kiểm tra trường hợp K1 = 100%.");
        }
        return new WeekView(Fmt.d(monday), Fmt.d(sunday), hasTest, totalRequired, totalCovered,
                pct(totalCovered, totalRequired), result, notes);
    }

    private List<DayInfo> days(long branchId, LocalDate monday) {
        List<DayInfo> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            LocalTime close = calendar.closeTime(branchId, d);
            list.add(new DayInfo(Fmt.d(d), d.getDayOfWeek().getValue(), close == null,
                    close == null ? "Đóng cửa" : Fmt.t(OpeningHours.OPEN) + "–" + Fmt.t(close)));
        }
        return list;
    }

    public ShiftView view(Shift s, AppUser actor) {
        String branchName = org.branch(s.branchId()).map(Branch::name).orElse("?");
        AppUser u = s.userId() == null ? null : org.user(s.userId()).orElse(null);
        return new ShiftView(s.id(), s.branchId(), branchName, Fmt.d(s.date()), s.date().getDayOfWeek().getValue(),
                Fmt.t(s.start()), Fmt.t(s.end()), s.minutes(), s.userId(),
                u == null ? null : u.code(), u == null ? null : u.displayName(),
                s.status().name(), s.status().label(), s.note(), s.testData(),
                policy.canManageBranch(actor, s.branchId()));
    }

    // ------------------------------------------------------------------ tạo / sửa ca

    public ShiftView create(AppUser actor, ShiftRequest in) {
        policy.requireManageBranch(actor, in.branchId(), "tạo ca trực");
        return tx.run(() -> {
            validateOrThrow(in, null);
            Shift saved = shifts.save(new Shift(0, in.branchId(), in.date(), in.start(), in.end(), in.userId(),
                    statusFor(in.userId()), clean(in.note()), false));
            audit.log(actor, "SHIFT_CREATE", "shift", saved.id(), describe(saved));
            return view(saved, actor);
        });
    }

    public ShiftView update(AppUser actor, long id, ShiftRequest in) {
        Shift current = shifts.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy ca trực"));
        policy.requireManageBranch(actor, current.branchId(), "sửa ca trực");
        policy.requireManageBranch(actor, in.branchId(), "chuyển ca sang cơ sở này");
        return tx.run(() -> {
            validateOrThrow(in, id);
            Shift saved = shifts.save(current.withSlot(in.branchId(), in.date(), in.start(), in.end(),
                    in.userId(), statusFor(in.userId()), clean(in.note())));
            audit.log(actor, "SHIFT_UPDATE", "shift", saved.id(), describe(saved));
            return view(saved, actor);
        });
    }

    /** Kiểm tra quy tắc ca trực; trả về danh sách vi phạm (rỗng = hợp lệ). */
    public List<String> validate(ShiftRequest in, Long excludeShiftId) {
        List<String> errors = new ArrayList<>();
        if (org.branch(in.branchId()).isEmpty()) {
            errors.add("Cơ sở không tồn tại");
            return errors;
        }
        if (!in.start().isBefore(in.end())) {
            errors.add("Giờ bắt đầu phải nhỏ hơn giờ kết thúc");
        }
        LocalTime close = calendar.closeTime(in.branchId(), in.date());
        if (close == null) {
            errors.add("Cơ sở đóng cửa vào ngày " + in.date() + " (Chủ nhật hoặc ngày lễ), không thể xếp ca");
        } else if (in.start().isBefore(OpeningHours.OPEN) || in.end().isAfter(close)) {
            errors.add("Ca phải nằm trong giờ mở cửa " + Fmt.t(OpeningHours.OPEN) + "–" + Fmt.t(close));
        }
        if (in.userId() != null) {
            if (org.user(in.userId()).isEmpty()) {
                errors.add("Nhân sự không tồn tại");
            } else if (in.start().isBefore(in.end())) {
                // findBetweenForUpdate: ở hồ sơ sqlserver, đây là điểm khóa UPDLOCK,HOLDLOCK chống trùng ca
                // khi hai yêu cầu xử lý cùng lúc (mục 2.1, 6.1). Luôn nằm trong tx.run(...) ở create()/update().
                for (Shift other : shifts.findBetweenForUpdate(in.date(), in.date())) {
                    boolean same = excludeShiftId != null && other.id() == excludeShiftId;
                    if (!same && other.userId() != null && other.userId().longValue() == in.userId()
                            && in.start().isBefore(other.end()) && other.start().isBefore(in.end())) {
                        String where = org.branch(other.branchId()).map(Branch::name).orElse("?");
                        errors.add("Nhân sự đã có ca " + Fmt.t(other.start()) + "–" + Fmt.t(other.end())
                                + " tại " + where + " trùng thời gian");
                        break;
                    }
                }
            }
        }
        return errors;
    }

    private void validateOrThrow(ShiftRequest in, Long excludeId) {
        List<String> errors = validate(in, excludeId);
        if (!errors.isEmpty()) {
            throw ApiException.unprocessable("Ca trực không hợp lệ", errors);
        }
    }

    private static ShiftStatus statusFor(Long userId) {
        // Phase 2: nếu nhân sự đang nghỉ vắng thì trạng thái là BI_ANH_HUONG
        return userId == null ? ShiftStatus.TRONG : ShiftStatus.DU_NGUOI;
    }

    private static String clean(String note) {
        return note == null ? "" : note.trim();
    }

    private String describe(Shift s) {
        String user = s.userId() == null ? "trống" : org.user(s.userId()).map(AppUser::code).orElse("?");
        return org.branch(s.branchId()).map(Branch::name).orElse("?") + " " + s.date() + " "
                + Fmt.t(s.start()) + "–" + Fmt.t(s.end()) + " / " + user;
    }

    private static Double pct(int part, int whole) {
        return whole == 0 ? null : part * 100.0 / whole;
    }

    private static String fmtHours(int minutes) {
        double h = minutes / 60.0;
        return h == Math.floor(h) ? String.valueOf((int) h) : String.format(java.util.Locale.US, "%.1f", h);
    }
}
