package vn.hiu.staffflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.dto.Requests.AbsenceRequest;
import vn.hiu.staffflow.dto.Requests.SwapRequestIn;
import vn.hiu.staffflow.dto.Views.AbsenceView;
import vn.hiu.staffflow.dto.Views.CandidateView;
import vn.hiu.staffflow.dto.Views.SwapView;
import vn.hiu.staffflow.dto.Views.WeekView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.ShiftRepository;
import vn.hiu.staffflow.repository.memory.DemoDataSeeder;
import vn.hiu.staffflow.repository.memory.InMemoryAbsenceRepository;
import vn.hiu.staffflow.repository.memory.InMemoryAuditRepository;
import vn.hiu.staffflow.repository.memory.InMemoryCrossTrainingRepository;
import vn.hiu.staffflow.repository.memory.InMemoryMonthlyReportRepository;
import vn.hiu.staffflow.repository.memory.InMemoryOrgRepository;
import vn.hiu.staffflow.repository.memory.InMemoryShiftRepository;
import vn.hiu.staffflow.repository.memory.InMemorySurveyRepository;
import vn.hiu.staffflow.repository.memory.InMemorySwapRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskEventRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskTemplateRepository;
import vn.hiu.staffflow.service.AbsenceService;
import vn.hiu.staffflow.service.AccessPolicy;
import vn.hiu.staffflow.service.AuditService;
import vn.hiu.staffflow.service.CalendarService;
import vn.hiu.staffflow.service.CandidateService;
import vn.hiu.staffflow.service.ScheduleService;
import vn.hiu.staffflow.service.SwapService;
import vn.hiu.staffflow.service.TxLock;

/** Kiểm thử Phase 2: T2 (báo vắng + gợi ý thay), T4 (duyệt đổi ca), T5 (403 ngoài phạm vi). */
class SwapServiceTest {
    static final LocalDate MAIN = LocalDate.parse("2026-10-19");  // thứ Hai, tuần thiếu người
    static final LocalDate TEST_WEEK = LocalDate.parse("2026-10-26"); // tuần phủ đủ 207 giờ

    InMemoryOrgRepository org;
    ShiftRepository shifts;
    ScheduleService schedule;
    AbsenceService absenceService;
    SwapService swapService;
    AppUser admin, deputy, nv03, nv04, nv07, nv08;

    @BeforeEach
    void setUp() {
        org = new InMemoryOrgRepository();
        InMemoryShiftRepository shiftRepo = new InMemoryShiftRepository();
        shifts = shiftRepo;
        InMemoryCrossTrainingRepository crossTraining = new InMemoryCrossTrainingRepository();
        new DemoDataSeeder(org, shiftRepo, crossTraining, new InMemoryTaskTemplateRepository(),
                new InMemoryTaskRepository(), new InMemoryTaskEventRepository(), new InMemorySurveyRepository(),
                new InMemoryMonthlyReportRepository(), "2026-10-19").seed();

        CalendarService calendar = new CalendarService(org);
        AppClock clock = new AppClock("2026-10-20T10:00"); // thứ Ba 10:00, trước tuần TEST_WEEK hơn 24 giờ
        AuditService audit = new AuditService(new InMemoryAuditRepository(), clock);
        AccessPolicy policy = new AccessPolicy();
        TxLock tx = new TxLock();

        schedule = new ScheduleService(shiftRepo, org, calendar, policy, audit, tx);
        InMemoryAbsenceRepository absenceRepo = new InMemoryAbsenceRepository();
        CandidateService candidateService = new CandidateService(org, shiftRepo, absenceRepo, crossTraining);
        absenceService = new AbsenceService(absenceRepo, shiftRepo, org, policy, audit, candidateService, clock, tx);
        swapService = new SwapService(new InMemorySwapRepository(), shiftRepo, org, policy, audit, clock,
                candidateService, tx);

        admin = org.userByCode("NV-01").orElseThrow();
        deputy = org.userByCode("NV-02").orElseThrow();
        nv03 = org.userByCode("NV-03").orElseThrow();
        nv04 = org.userByCode("NV-04").orElseThrow();
        nv07 = org.userByCode("NV-07").orElseThrow();
        nv08 = org.userByCode("NV-08").orElseThrow();
    }

    // ================================================================= T2

    @Test
    void t2_baoVangDanhDauCaBiAnhHuongVaTinhLaiK1() {
        WeekView before = schedule.week(MAIN, 1L, admin); // ĐBP
        int coveredBefore = before.branches().get(0).coveredMinutes();

        // NV-07 nghỉ ốm thứ Hai - thứ Tư (3 ngày làm việc), mỗi ngày có ca 08:00-14:00 (6 giờ) tại ĐBP
        AbsenceView view = absenceService.report(admin,
                new AbsenceRequest(nv07.id(), MAIN, MAIN.plusDays(2), "Nghỉ ốm"));

        assertEquals(3, view.affectedShiftIds().size(), "phải có đúng 3 ca bị ảnh hưởng");

        WeekView after = schedule.week(MAIN, 1L, admin);
        int coveredAfter = after.branches().get(0).coveredMinutes();
        assertEquals(coveredBefore - 3 * 6 * 60, coveredAfter, "K1 phải tính lại theo giờ thực sự có người");

        for (Long id : view.affectedShiftIds()) {
            Shift s = shifts.findById(id).orElseThrow();
            assertEquals("BI_ANH_HUONG", s.status().name());
            assertEquals(nv07.id(), s.userId(), "vẫn giữ người gốc, chỉ đổi trạng thái, không tự gán người thay");
        }
    }

    @Test
    void t2_goiYNguoiThay_uuTienCungChucDanhRoiDenDaoTaoCheo() {
        AbsenceView view = absenceService.report(admin,
                new AbsenceRequest(nv07.id(), MAIN, MAIN.plusDays(2), "Nghỉ ốm"));
        long mondayShiftId = view.affectedShiftIds().get(0); // ĐBP, thứ Hai 08:00-14:00

        List<CandidateView> candidates = swapService.candidatesForShift(mondayShiftId);
        assertFalse(candidates.isEmpty());

        // NV-08 cùng chức danh nhưng đang có ca trùng giờ ở Đầm Sen cùng thời điểm -> không đủ điều kiện
        CandidateView c08 = candidates.stream().filter(c -> c.userCode().equals("NV-08")).findFirst().orElseThrow();
        assertFalse(c08.eligible());
        assertTrue(c08.reasons().stream().anyMatch(r -> r.contains("trùng")));

        // NV-03 có đào tạo chéo đã hoàn thành (xử lý tài liệu -> phục vụ bạn đọc), không trùng ca -> đủ điều kiện
        CandidateView c03 = candidates.stream().filter(c -> c.userCode().equals("NV-03")).findFirst().orElseThrow();
        assertTrue(c03.eligible(), String.join("; ", c03.reasons()));
        assertTrue(c03.reasons().stream().anyMatch(r -> r.contains("đào tạo chéo")));
        assertNotNull(c03.hoursAfterMinutes());
    }

    @Test
    void khongBaoVangDuocChoNguoiKhacNeuKhongCoQuyen() {
        ApiException ex = assertThrows(ApiException.class,
                () -> absenceService.report(nv08, new AbsenceRequest(nv07.id(), MAIN, MAIN, "")));
        assertEquals(403, ex.status());
    }

    @Test
    void tuBaoVangChoChinhMinhLuonDuocPhep() {
        AbsenceView v = absenceService.report(nv07, new AbsenceRequest(nv07.id(), MAIN, MAIN, "Việc gia đình"));
        assertEquals(1, v.affectedShiftIds().size());
    }

    // ================================================================= T4

    @Test
    void t4_duyetDoiCa_capNhatLichVaKhongDuyetHaiLan() {
        long shiftId = shiftOf(nv07.id(), TEST_WEEK, "08:00");
        SwapView created = swapService.create(nv07,
                new SwapRequestIn(shiftId, nv04.id(), "Bận việc cá nhân"));
        assertEquals("CHO_DUYET", created.status());
        assertFalse(created.createdByManager());
        assertNotNull(created.submittedAt());

        SwapView approved = swapService.approve(admin, created.id(), "Đồng ý, đã kiểm tra lịch");
        assertEquals("DA_DUYET", approved.status());
        assertEquals("NV-01", approved.approverCode());
        assertNotNull(approved.decidedAt());
        assertNotNull(approved.processingMinutes());

        Shift s = shifts.findById(shiftId).orElseThrow();
        assertEquals(nv04.id(), s.userId());
        assertEquals("DU_NGUOI", s.status().name());

        // duyệt lần hai phải bị từ chối
        ApiException ex = assertThrows(ApiException.class, () -> swapService.approve(admin, created.id(), ""));
        assertEquals(409, ex.status());
    }

    @Test
    void t4_guiMuonHon24GioBiTuChoi422_truKhiQuanLyTao() {
        // "now" = 2026-10-20T10:00; ca thứ Bảy 26/10 17:00 tại ĐBP còn hơn 24 giờ nên hợp lệ với người gửi thường;
        // đổi sang ca gần: tạo một ca mới cách "now" chưa đến 24 giờ để kiểm tra hạn gửi.
        Shift lateShift = shifts.save(new Shift(0, 1, LocalDate.parse("2026-10-20"),
                java.time.LocalTime.of(18, 0), java.time.LocalTime.of(20, 0), nv07.id(),
                vn.hiu.staffflow.domain.Domain.ShiftStatus.DU_NGUOI, "", false));

        ApiException ex = assertThrows(ApiException.class,
                () -> swapService.create(nv07, new SwapRequestIn(lateShift.id(), nv03.id(), "Gửi muộn")));
        assertEquals(422, ex.status());

        // quản lý (NV-01) tạo thay vẫn được phép, có ghi nhận trong audit (createdByManager = true)
        SwapView byManager = swapService.create(admin, new SwapRequestIn(lateShift.id(), nv03.id(), "Quản lý xử lý khẩn"));
        assertTrue(byManager.createdByManager());
    }

    // ================================================================= T5

    @Test
    void t5_nv08KhongDuyetDuocYeuCauDoiCa() {
        long shiftId = shiftOf(nv07.id(), TEST_WEEK, "08:00");
        SwapView created = swapService.create(nv07, new SwapRequestIn(shiftId, nv04.id(), "Bận"));

        ApiException ex = assertThrows(ApiException.class, () -> swapService.approve(nv08, created.id(), ""));
        assertEquals(403, ex.status());

        // dữ liệu không đổi
        SwapView unchanged = swapService.list(admin).stream()
                .filter(r -> r.id() == created.id()).findFirst().orElseThrow();
        assertEquals("CHO_DUYET", unchanged.status());
        Shift s = shifts.findById(shiftId).orElseThrow();
        assertEquals(nv07.id(), s.userId());
    }

    @Test
    void t5Phu_nv02KhongDuyetDuocYeuCauThuocCoSoKhac() {
        long shiftId = shiftOf(nv07.id(), TEST_WEEK, "08:00"); // ĐBP, không phải Đầm Sen
        SwapView created = swapService.create(nv07, new SwapRequestIn(shiftId, nv04.id(), "Bận"));

        ApiException ex = assertThrows(ApiException.class, () -> swapService.approve(deputy, created.id(), ""));
        assertEquals(403, ex.status());
    }

    @Test
    void nv02DuyetDuocYeuCauThuocDamSen() {
        long shiftId = shiftOf(nv08.id(), TEST_WEEK, "08:00"); // Đầm Sen
        SwapView created = swapService.create(nv08, new SwapRequestIn(shiftId, nv04.id(), "Bận"));
        // NV-04 đã có ca chiều cùng cơ sở -> không trùng với ca sáng; hợp lệ để duyệt
        SwapView approved = swapService.approve(deputy, created.id(), "OK");
        assertEquals("DA_DUYET", approved.status());
    }

    // ------------------------------------------------------------- tiện ích

    private long shiftOf(long userId, LocalDate monday, String startsWith) {
        return shifts.findBetween(monday, monday.plusDays(6)).stream()
                .filter(s -> s.userId() != null && s.userId() == userId && s.start().toString().startsWith(startsWith))
                .findFirst().orElseThrow(() -> new AssertionError("không tìm thấy ca")).id();
    }
}
