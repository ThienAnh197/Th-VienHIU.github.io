package vn.hiu.staffflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.dto.Requests.TaskRequest;
import vn.hiu.staffflow.dto.Requests.TaskStatusRequest;
import vn.hiu.staffflow.dto.Views.DashboardView;
import vn.hiu.staffflow.dto.Views.GroupWorkloadView;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.dto.Views.TaskView;
import vn.hiu.staffflow.dto.Views.WorkloadView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.kpi.K1CoverageKpi;
import vn.hiu.staffflow.kpi.K2EmptyShiftKpi;
import vn.hiu.staffflow.kpi.K3TaskOnTimeKpi;
import vn.hiu.staffflow.kpi.K4SwapProcessingKpi;
import vn.hiu.staffflow.kpi.K5BalanceKpi;
import vn.hiu.staffflow.kpi.K6UpdateRateKpi;
import vn.hiu.staffflow.kpi.K7SatisfactionKpi;
import vn.hiu.staffflow.kpi.K8MonthlyReportKpi;
import vn.hiu.staffflow.repository.memory.DemoDataSeeder;
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
import vn.hiu.staffflow.service.AccessPolicy;
import vn.hiu.staffflow.service.AuditService;
import vn.hiu.staffflow.service.CalendarService;
import vn.hiu.staffflow.service.DashboardService;
import vn.hiu.staffflow.service.MonthlyReportService;
import vn.hiu.staffflow.service.SurveyService;
import vn.hiu.staffflow.service.TaskService;
import vn.hiu.staffflow.service.TxLock;
import vn.hiu.staffflow.service.WorkloadService;

/** Kiểm thử Phase 3: T3 (hội sách trùng tuần kiểm kê -> tải việc vượt 40 giờ), Kanban nhiệm vụ, KPI K1-K6. */
class TaskKpiTest {
    static final LocalDate MAIN = LocalDate.parse("2026-10-19");
    static final LocalDate HIST = LocalDate.parse("2026-10-12");

    InMemoryOrgRepository org;
    InMemoryShiftRepository shifts;
    InMemoryTaskRepository taskRepo;
    InMemoryTaskEventRepository eventRepo;
    InMemoryTaskTemplateRepository templateRepo;
    InMemorySwapRepository swapRepo;
    InMemorySurveyRepository surveyRepo;
    InMemoryMonthlyReportRepository monthlyReportRepo;
    AppClock clock;
    TaskService taskService;
    WorkloadService workloadService;
    SurveyService surveyService;
    MonthlyReportService monthlyReportService;
    K1CoverageKpi k1;
    K2EmptyShiftKpi k2;
    K3TaskOnTimeKpi k3;
    K4SwapProcessingKpi k4;
    K5BalanceKpi k5;
    K6UpdateRateKpi k6;
    K7SatisfactionKpi k7;
    K8MonthlyReportKpi k8;
    DashboardService dashboard;
    AppUser admin, nv03, nv07;

    @BeforeEach
    void setUp() {
        org = new InMemoryOrgRepository();
        shifts = new InMemoryShiftRepository();
        InMemoryCrossTrainingRepository crossTraining = new InMemoryCrossTrainingRepository();
        templateRepo = new InMemoryTaskTemplateRepository();
        taskRepo = new InMemoryTaskRepository();
        eventRepo = new InMemoryTaskEventRepository();
        swapRepo = new InMemorySwapRepository();
        surveyRepo = new InMemorySurveyRepository();
        monthlyReportRepo = new InMemoryMonthlyReportRepository();
        new DemoDataSeeder(org, shifts, crossTraining, templateRepo, taskRepo, eventRepo, surveyRepo,
                monthlyReportRepo, "2026-10-19").seed();

        clock = new AppClock("2026-10-18T15:00"); // giống application.properties mặc định
        CalendarService calendar = new CalendarService(org);
        AccessPolicy policy = new AccessPolicy();
        AuditService audit = new AuditService(new InMemoryAuditRepository(), clock);
        TxLock tx = new TxLock();

        taskService = new TaskService(taskRepo, eventRepo, templateRepo, org, policy, audit, clock, tx);
        workloadService = new WorkloadService(org, shifts, taskRepo);
        surveyService = new SurveyService(surveyRepo, org, audit, clock);
        monthlyReportService = new MonthlyReportService(monthlyReportRepo, org, policy, audit, clock);
        k1 = new K1CoverageKpi(calendar, shifts, org);
        k2 = new K2EmptyShiftKpi(shifts, clock);
        k3 = new K3TaskOnTimeKpi(taskRepo, eventRepo, clock);
        k4 = new K4SwapProcessingKpi(swapRepo);
        k5 = new K5BalanceKpi(workloadService);
        k6 = new K6UpdateRateKpi(taskRepo, eventRepo);
        k7 = new K7SatisfactionKpi(surveyService, "2026-10-19");
        k8 = new K8MonthlyReportKpi(monthlyReportService);
        dashboard = new DashboardService(k1, k2, k3, k4, k5, k6, k7, k8, workloadService, "2026-10-19");

        admin = org.userByCode("NV-01").orElseThrow();
        nv03 = org.userByCode("NV-03").orElseThrow();
        nv07 = org.userByCode("NV-07").orElseThrow();
    }

    // ================================================================= T3

    @Test
    void t3_hoiSachTrungTuanKiemKe_NguoiVuotTaiHienCanhBao() {
        List<WorkloadView> loads = workloadService.byUser(MAIN);
        WorkloadView w03 = loads.stream().filter(w -> w.userCode().equals("NV-03")).findFirst().orElseThrow();

        // NV-03: 8 giờ ca trực + 20 giờ "Hội sách" + 16 giờ "Kiểm kê" = 44 giờ > 40 giờ
        assertEquals(8 * 60, w03.shiftMinutes());
        assertEquals((20 + 16) * 60, w03.taskMinutes());
        assertEquals(44 * 60, w03.totalMinutes());
        assertTrue(w03.overLimit(), "NV-03 phải hiện cảnh báo Vượt 100% tải việc");
        assertTrue(w03.pct() > 100.0);
    }

    @Test
    void t3_khongAiVuot_khongHienCanhBaoGia() {
        List<WorkloadView> loads = workloadService.byUser(MAIN);
        WorkloadView w08 = loads.stream().filter(w -> w.userCode().equals("NV-08")).findFirst().orElseThrow();
        assertFalse(w08.overLimit(), "NV-08 không vượt tải thì không được cảnh báo giả");
    }

    @Test
    void t3_khongTuGiamGioNhiemVu() {
        TaskView t = taskService.list(admin, MAIN, null, nv03.id(), null).stream()
                .filter(x -> x.title().contains("Hội sách")).findFirst().orElseThrow();
        assertEquals(20 * 60, t.estimatedMinutes(), "không được tự giảm giờ nhiệm vụ để KPI đạt");
    }

    // ================================================================= Kanban

    @Test
    void kanban_tuNguoiPhuTrachCoTheTuCapNhatTrangThai() {
        TaskView created = taskService.create(admin,
                new TaskRequest("Rà soát mục lục", null, 1L, nv03.id(), 60, MAIN.plusDays(10), "TRUNG_BINH"));
        assertEquals("MOI", created.status());

        TaskView updated = taskService.changeStatus(nv03, created.id(), new TaskStatusRequest("DANG_LAM", null));
        assertEquals("DANG_LAM", updated.status());
        assertFalse(updated.closedWithoutEvidence());
    }

    @Test
    void kanban_xongKhongCoMinhChung_vanChoPhepNhungGanCo() {
        TaskView created = taskService.create(admin,
                new TaskRequest("Dán nhãn sách mới", null, 1L, nv03.id(), 30, MAIN.plusDays(1), "THAP"));
        TaskView done = taskService.changeStatus(nv03, created.id(), new TaskStatusRequest("XONG", "  "));
        assertEquals("XONG", done.status());
        assertTrue(done.closedWithoutEvidence());

        TaskView created2 = taskService.create(admin,
                new TaskRequest("Dán nhãn kệ B", null, 1L, nv03.id(), 30, MAIN.plusDays(1), "THAP"));
        TaskView done2 = taskService.changeStatus(nv03, created2.id(), new TaskStatusRequest("XONG", "Ảnh chụp kệ sách"));
        assertFalse(done2.closedWithoutEvidence());
    }

    @Test
    void nguoiKhongPhaiPhuTrachVaKhongQuanLy_khongDoiDuocTrangThai() {
        TaskView created = taskService.create(admin,
                new TaskRequest("Nhiệm vụ của NV-03", null, 1L, nv03.id(), 60, MAIN.plusDays(1), "THAP"));
        ApiException ex = assertThrows(ApiException.class,
                () -> taskService.changeStatus(nv07, created.id(), new TaskStatusRequest("DANG_LAM", null)));
        assertEquals(403, ex.status());
    }

    @Test
    void chuyenVienKhongTaoDuocNhiemVu() {
        ApiException ex = assertThrows(ApiException.class, () -> taskService.create(nv03,
                new TaskRequest("Tự giao việc", null, 1L, nv03.id(), 60, MAIN, "THAP")));
        assertEquals(403, ex.status());
    }

    // ================================================================= KPI

    @Test
    void k1_tinhTuDuLieuCaTruc() {
        KpiResult r = k1.compute(MAIN);
        assertEquals(KpiStatus.CHUA_DAT, r.status());
        assertTrue(r.rawValue() > 36.0 && r.rawValue() < 48.0);
    }

    @Test
    void k2_demCaTrongTrongVong24Gio() {
        KpiResult r = k2.compute(MAIN);
        // "now" = 18/10 15:00; chỉ ca D2 thứ Hai 14:00-18:00 (19/10) khởi đầu trong vòng 24 giờ
        assertEquals(1.0, r.rawValue());
        assertEquals(KpiStatus.DAT, r.status());
    }

    @Test
    void k3_tuanLichSu_coDuLieuDungVaTreHan() {
        KpiResult r = k3.compute(HIST);
        assertEquals(KpiStatus.CHUA_DAT, r.status());
        assertEquals(1.0 / 3 * 100, r.rawValue(), 0.5); // 1 đúng hạn / 3 đã đến hạn
    }

    @Test
    void k3_tuanChinh_chuaDenHan_traVeNA() {
        KpiResult r = k3.compute(MAIN);
        assertEquals(KpiStatus.NA, r.status());
        assertNull(r.rawValue());
    }

    @Test
    void k5_phanAnhMatCanBangDoHoiSach() {
        KpiResult r = k5.compute(MAIN);
        assertEquals(KpiStatus.CHUA_DAT, r.status());
        assertTrue(r.rawValue() > 1.5);
    }

    @Test
    void k4_khongCoYeuCauDoiCa_traVeNA() {
        KpiResult r = k4.compute(MAIN);
        assertEquals(KpiStatus.NA, r.status());
    }

    @Test
    void dashboard_mocTuan4Va8_coGoiY_tuanKhacThiKhong() {
        DashboardView atWeek1 = dashboard.get(MAIN);
        assertNull(atWeek1.milestoneWeek());
        assertTrue(atWeek1.suggestions().isEmpty());

        DashboardView atWeek4 = dashboard.get(MAIN.plusWeeks(3));
        assertEquals(4, atWeek4.milestoneWeek());
        assertFalse(atWeek4.suggestions().isEmpty());
    }
}
