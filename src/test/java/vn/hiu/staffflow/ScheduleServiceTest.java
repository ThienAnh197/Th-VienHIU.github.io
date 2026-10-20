package vn.hiu.staffflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Holiday;
import vn.hiu.staffflow.dto.Requests.ShiftRequest;
import vn.hiu.staffflow.dto.Views.BranchSchedule;
import vn.hiu.staffflow.dto.Views.ShiftView;
import vn.hiu.staffflow.dto.Views.WeekView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.memory.DemoDataSeeder;
import vn.hiu.staffflow.repository.memory.InMemoryAuditRepository;
import vn.hiu.staffflow.repository.memory.InMemoryCrossTrainingRepository;
import vn.hiu.staffflow.repository.memory.InMemoryMonthlyReportRepository;
import vn.hiu.staffflow.repository.memory.InMemoryOrgRepository;
import vn.hiu.staffflow.repository.memory.InMemoryShiftRepository;
import vn.hiu.staffflow.repository.memory.InMemorySurveyRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskEventRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskTemplateRepository;
import vn.hiu.staffflow.service.AccessPolicy;
import vn.hiu.staffflow.service.AuditService;
import vn.hiu.staffflow.service.CalendarService;
import vn.hiu.staffflow.service.ScheduleService;
import vn.hiu.staffflow.service.TxLock;

/** Kiểm thử Phase 1 (test case T1 và quy tắc ca trực), chạy ở mức service, không cần Spring/SQL Server. */
class ScheduleServiceTest {
    static final LocalDate MAIN = LocalDate.parse("2026-10-19");   // thứ Hai
    static final LocalDate TEST = LocalDate.parse("2026-10-26");

    InMemoryOrgRepository org;
    ScheduleService schedule;
    AppUser admin;      // NV-01
    AppUser deputy;     // NV-02, Đầm Sen
    AppUser staff;      // NV-08

    @BeforeEach
    void setUp() {
        org = new InMemoryOrgRepository();
        InMemoryShiftRepository shifts = new InMemoryShiftRepository();
        new DemoDataSeeder(org, shifts, new InMemoryCrossTrainingRepository(), new InMemoryTaskTemplateRepository(),
                new InMemoryTaskRepository(), new InMemoryTaskEventRepository(), new InMemorySurveyRepository(),
                new InMemoryMonthlyReportRepository(), "2026-10-19").seed();
        CalendarService calendar = new CalendarService(org);
        AuditService audit = new AuditService(new InMemoryAuditRepository(), new AppClock("2026-10-18T15:00"));
        schedule = new ScheduleService(shifts, org, calendar, new AccessPolicy(), audit, new TxLock());
        admin = org.userByCode("NV-01").orElseThrow();
        deputy = org.userByCode("NV-02").orElseThrow();
        staff = org.userByCode("NV-08").orElseThrow();
    }

    // ---------------------------------------------------------------- T1: tạo lịch tuần

    @Test
    void t1_moiCoSoMo69GioTong207() {
        WeekView w = schedule.week(MAIN, null, admin);
        assertEquals(3, w.branches().size());
        for (BranchSchedule b : w.branches()) {
            assertEquals(69 * 60, b.requiredMinutes());
        }
        assertEquals(207 * 60, w.requiredMinutes());
    }

    @Test
    void t1_tuanChinhThieuNguoi_K1TrongKhoang36Den48() {
        WeekView w = schedule.week(MAIN, null, admin);
        assertEquals(96 * 60, w.coveredMinutes());
        assertTrue(w.coveragePct() > 36.0 && w.coveragePct() < 48.0, "K1 baseline = " + w.coveragePct());
        assertTrue(w.notes().stream().anyMatch(n -> n.startsWith("Chưa phủ đủ")), "phải báo thiếu");
        assertFalse(w.hasTestData());
    }

    @Test
    void t1_caTrongKhongDuocTinhVaoBaoPhu() {
        WeekView w = schedule.week(MAIN, 3L, admin);   // D2: 12 giờ có người, có 1 ca trống thứ Hai chiều
        BranchSchedule d2 = w.branches().get(0);
        assertEquals(12 * 60, d2.coveredMinutes());
        assertEquals(1, d2.emptyShifts());
    }

    @Test
    void t1_tuanKiemThuPhuDu207Gio() {
        WeekView w = schedule.week(TEST, null, admin);
        assertEquals(207 * 60, w.coveredMinutes());
        assertEquals(100.0, w.coveragePct(), 0.0001);
        assertTrue(w.hasTestData());
        assertFalse(w.notes().stream().anyMatch(n -> n.startsWith("Chưa phủ đủ")));
    }

    @Test
    void t1_nhanBietLaTuanBatKyTrongTuan() {
        WeekView w = schedule.week(LocalDate.parse("2026-10-23"), null, admin); // thứ Sáu
        assertEquals("2026-10-19", w.weekStart());
        assertEquals("2026-10-25", w.weekEnd());
    }

    @Test
    void khongBaoPhuHaiLanKhiHaiCaChongNhauCungCoSo() {
        schedule.create(admin, req(1, "2026-10-21", "09:00", "11:00", 5L)); // NV-05 chồng lên ca NV-07 08-14 (ĐBP, thứ Tư)
        WeekView w = schedule.week(MAIN, 1L, admin);
        // ĐBP trước đó: 30 (NV-07) + 8 (NV-03) + 8 (NV-05) = 46 giờ; ca mới nằm gọn trong 08-14 nên không cộng thêm
        assertEquals(46 * 60, w.branches().get(0).coveredMinutes());
    }

    @Test
    void ngayLeLamCoSoDongCua() {
        org.load(org.branches(), org.users(), List.of(new Holiday(1, null, LocalDate.parse("2026-10-21"), "Nghỉ lễ thử")));
        WeekView w = schedule.week(MAIN, null, admin);
        assertEquals((69 - 12) * 60, w.branches().get(0).requiredMinutes());
        assertEquals(3 * 57 * 60, w.requiredMinutes());
    }

    // ---------------------------------------------------------------- Quy tắc ca trực

    @Test
    void khongChoCaVaoChuNhat() {
        ApiException ex = assertThrows(ApiException.class,
                () -> schedule.create(admin, req(1, "2026-10-25", "08:00", "12:00", null)));
        assertEquals(422, ex.status());
        assertTrue(ex.details().stream().anyMatch(s -> s.contains("đóng cửa")));
    }

    @Test
    void khongChoCaVaoNgayLe() {
        org.load(org.branches(), org.users(), List.of(new Holiday(1, 1L, LocalDate.parse("2026-10-21"), "Lễ")));
        ApiException ex = assertThrows(ApiException.class,
                () -> schedule.create(admin, req(1, "2026-10-21", "08:00", "12:00", null)));
        assertEquals(422, ex.status());
        // cơ sở khác vẫn mở cửa
        schedule.create(admin, req(2, "2026-10-21", "15:00", "17:00", null));
    }

    @Test
    void gioBatDauPhaiNhoHonGioKetThuc() {
        List<String> errors = schedule.validate(req(1, "2026-10-20", "12:00", "10:00", null), null);
        assertTrue(errors.stream().anyMatch(s -> s.contains("nhỏ hơn")));
        errors = schedule.validate(req(1, "2026-10-20", "12:00", "12:00", null), null);
        assertFalse(errors.isEmpty());
    }

    @Test
    void caPhaiNamTrongGioMoCua() {
        assertFalse(schedule.validate(req(1, "2026-10-20", "07:00", "09:00", null), null).isEmpty()); // sớm
        assertFalse(schedule.validate(req(1, "2026-10-20", "19:00", "21:00", null), null).isEmpty()); // muộn
        assertFalse(schedule.validate(req(1, "2026-10-24", "16:00", "18:00", null), null).isEmpty()); // thứ Bảy đóng 17:00
        assertTrue(schedule.validate(req(1, "2026-10-24", "15:00", "17:00", null), null).isEmpty());
        assertTrue(schedule.validate(req(1, "2026-10-20", "08:00", "20:00", null), null).isEmpty());
    }

    @Test
    void khongChoMotNguoiHaiCaChongLanKeCaKhacCoSo() {
        // NV-07 đã có ca thứ Hai 08:00-14:00 tại ĐBP; xếp thêm tại Đầm Sen 10:00-12:00 phải bị chặn
        ApiException ex = assertThrows(ApiException.class,
                () -> schedule.create(admin, req(2, "2026-10-19", "10:00", "12:00", 7L)));
        assertEquals(422, ex.status());
        assertTrue(ex.details().stream().anyMatch(s -> s.contains("trùng")));
    }

    @Test
    void caLienKeKhongBiXemLaChong() {
        // NV-07 kết thúc 14:00 -> ca bắt đầu 14:00 là hợp lệ
        ShiftView v = schedule.create(admin, req(2, "2026-10-19", "14:00", "16:00", 7L));
        assertEquals("DU_NGUOI", v.status());
    }

    @Test
    void suaCaKhongTuChongVoiChinhNo() {
        long id = schedule.week(MAIN, 1L, admin).branches().get(0).shifts().stream()
                .filter(s -> "NV-07".equals(s.userCode()) && s.date().equals("2026-10-19")).findFirst().orElseThrow().id();
        ShiftView v = schedule.update(admin, id, req(1, "2026-10-19", "08:00", "13:00", 7L));
        assertEquals("13:00", v.end());
    }

    @Test
    void caChuaCoNguoiCoTrangThaiTrong() {
        ShiftView v = schedule.create(admin, req(1, "2026-10-20", "16:00", "18:00", null));
        assertEquals("TRONG", v.status());
        assertEquals(null, v.userCode());
    }

    // ---------------------------------------------------------------- Phân quyền (kiểm tra ở backend)

    @Test
    void phoGiamDocChiTaoCaTaiDamSen() {
        assertEquals("DU_NGUOI", schedule.create(deputy, req(2, "2026-10-20", "16:00", "18:00", 8L)).status());
        ApiException ex = assertThrows(ApiException.class,
                () -> schedule.create(deputy, req(1, "2026-10-20", "16:00", "18:00", null)));
        assertEquals(403, ex.status());
    }

    @Test
    void chuyenVienKhongTaoDuocCa() {
        ApiException ex = assertThrows(ApiException.class,
                () -> schedule.create(staff, req(2, "2026-10-20", "16:00", "18:00", null)));
        assertEquals(403, ex.status());
        assertEquals(96 * 60, schedule.week(MAIN, null, admin).coveredMinutes()); // dữ liệu không đổi
    }

    @Test
    void phoGiamDocKhongSuaDuocCaCuaCoSoKhac() {
        long id = schedule.week(MAIN, 1L, admin).branches().get(0).shifts().get(0).id();
        ApiException ex = assertThrows(ApiException.class,
                () -> schedule.update(deputy, id, req(1, "2026-10-19", "08:00", "10:00", null)));
        assertEquals(403, ex.status());
    }

    @Test
    void canManageTrongViewPhanAnhQuyen() {
        List<ShiftView> viaDeputy = new ArrayList<>();
        schedule.week(MAIN, null, deputy).branches().forEach(b -> viaDeputy.addAll(b.shifts()));
        assertTrue(viaDeputy.stream().filter(s -> s.branchId() == 2).allMatch(ShiftView::canManage));
        assertTrue(viaDeputy.stream().filter(s -> s.branchId() != 2).noneMatch(ShiftView::canManage));
    }

    static ShiftRequest req(long branch, String date, String start, String end, Long userId) {
        return new ShiftRequest(branch, LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end), userId, "");
    }
}
