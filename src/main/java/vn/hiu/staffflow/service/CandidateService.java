package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.CrossTraining;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.TitleGroup;
import vn.hiu.staffflow.domain.Domain.TrainingStatus;
import vn.hiu.staffflow.dto.Views.CandidateView;
import vn.hiu.staffflow.repository.AbsenceRepository;
import vn.hiu.staffflow.repository.CrossTrainingRepository;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.ShiftRepository;

/**
 * Gợi ý người thay cho một ca trực, theo thứ tự đánh giá của mục 6.3:
 * 1) chức danh phù hợp hoặc có đào tạo chéo "đã hoàn thành";
 * 2) không trùng ca;
 * 3) không vắng trong khoảng đó;
 * 4) không vượt 40 giờ/tuần sau khi nhận ca (tính trên giờ trực; nhiệm vụ cộng thêm từ Phase 3);
 * 5) phù hợp yêu cầu công việc của cơ sở (ghi rõ nếu phải di chuyển liên cơ sở).
 * NV-01, NV-02 (lãnh đạo) không thay ca quầy mặc định nên không xuất hiện trong danh sách.
 */
@Component
public class CandidateService {
    private static final int WEEKLY_LIMIT_MINUTES = 40 * 60;

    private final OrgRepository org;
    private final ShiftRepository shifts;
    private final AbsenceRepository absences;
    private final CrossTrainingRepository crossTraining;

    public CandidateService(OrgRepository org, ShiftRepository shifts, AbsenceRepository absences,
                            CrossTrainingRepository crossTraining) {
        this.org = org;
        this.shifts = shifts;
        this.absences = absences;
        this.crossTraining = crossTraining;
    }

    /** Nhóm chức danh "cần có" tại ca này: của người đang được gán (kể cả đang vắng), null nếu ca đang trống. */
    public TitleGroup neededGroup(Shift shift) {
        return shift.userId() == null ? null : org.user(shift.userId()).map(AppUser::titleGroup).orElse(null);
    }

    public List<CandidateView> suggest(Shift shift) {
        TitleGroup needed = neededGroup(shift);
        Long exclude = shift.userId();
        List<CandidateView> out = new ArrayList<>();
        for (AppUser u : org.users()) {
            if (u.titleGroup().isLeader()) {
                continue;
            }
            if (exclude != null && u.id() == exclude) {
                continue;
            }
            out.add(evaluate(shift, u, needed));
        }
        out.sort(Comparator.comparing(CandidateView::eligible).reversed()
                .thenComparing(CandidateView::crossBranch)
                .thenComparing(c -> c.hoursAfterMinutes() == null ? Double.MAX_VALUE : c.hoursAfterMinutes()));
        return out;
    }

    public CandidateView evaluate(Shift shift, AppUser candidate, TitleGroup needed) {
        List<String> reasons = new ArrayList<>();
        boolean eligible = true;

        boolean sameTitle = needed == null || candidate.titleGroup() == needed;
        boolean trained = needed != null && crossTraining.findByUser(candidate.id()).stream()
                .anyMatch(c -> c.canSubstituteFor() == needed && c.status() == TrainingStatus.DA_HOAN_THANH);
        if (sameTitle) {
            reasons.add(needed == null ? "Ca chưa gán chức danh cụ thể" : "Cùng chức danh " + needed.label());
        } else if (trained) {
            reasons.add("Có đào tạo chéo đã hoàn thành: được phép thay " + needed.label());
        } else {
            eligible = false;
            boolean inTraining = crossTraining.findByUser(candidate.id()).stream()
                    .anyMatch(c -> c.canSubstituteFor() == needed && c.status() == TrainingStatus.DANG_DAO_TAO);
            reasons.add(inTraining
                    ? "Đang đào tạo chéo cho " + needed.label() + " nhưng chưa hoàn thành nên chưa đủ điều kiện"
                    : "Không cùng chức danh và chưa có đào tạo chéo cho " + needed.label());
        }

        boolean overlap = shifts.findBetween(shift.date(), shift.date()).stream()
                .anyMatch(s -> s.id() != shift.id() && s.userId() != null && s.userId() == candidate.id()
                        && shift.start().isBefore(s.end()) && s.start().isBefore(shift.end()));
        if (overlap) {
            eligible = false;
            reasons.add("Đã có ca khác trùng thời gian trong ngày " + shift.date());
        } else {
            reasons.add("Không trùng ca khác");
        }

        boolean onLeave = !absences.findOverlapping(candidate.id(), shift.date(), shift.date()).isEmpty();
        if (onLeave) {
            eligible = false;
            reasons.add("Đang trong thời gian báo vắng");
        } else {
            reasons.add("Không trong thời gian báo vắng");
        }

        LocalDate monday = WeekUtil.mondayOf(shift.date());
        int currentMinutes = weeklyShiftMinutes(candidate.id(), monday, shift.id());
        int afterMinutes = currentMinutes + shift.minutes();
        double afterHours = afterMinutes / 60.0;
        if (afterMinutes > WEEKLY_LIMIT_MINUTES) {
            eligible = false;
            reasons.add("Vượt 40 giờ/tuần sau khi nhận ca (dự kiến " + round1(afterHours) + " giờ)");
        } else {
            reasons.add("Tải việc sau khi thay: " + round1(afterHours) + "/40 giờ");
        }

        boolean crossBranch = candidate.branchId() != shift.branchId();
        if (crossBranch) {
            String from = org.branch(candidate.branchId()).map(Branch::name).orElse("?");
            String to = org.branch(shift.branchId()).map(Branch::name).orElse("?");
            reasons.add("Cần di chuyển liên cơ sở: " + from + " → " + to);
        }

        return new CandidateView(candidate.id(), candidate.code(), candidate.displayName(),
                candidate.titleGroup().label(), org.branch(candidate.branchId()).map(Branch::name).orElse("?"),
                eligible, reasons, crossBranch, afterHours);
    }

    /** Tổng phút các ca trực đã gán cho người này trong tuần, không tính ca đang xét (excludeShiftId). */
    private int weeklyShiftMinutes(long userId, LocalDate monday, long excludeShiftId) {
        return shifts.findBetween(monday, monday.plusDays(6)).stream()
                .filter(s -> s.id() != excludeShiftId && s.userId() != null && s.userId() == userId)
                .mapToInt(Shift::minutes).sum();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
