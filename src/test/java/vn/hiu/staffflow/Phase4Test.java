package vn.hiu.staffflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.dto.Requests.MonthlyReportRequest;
import vn.hiu.staffflow.dto.Requests.ReconciliationCreateRequest;
import vn.hiu.staffflow.dto.Requests.ReconciliationItemRequest;
import vn.hiu.staffflow.dto.Requests.SurveyRequest;
import vn.hiu.staffflow.dto.Views.DashboardView;
import vn.hiu.staffflow.dto.Views.ImportSummary;
import vn.hiu.staffflow.dto.Views.KpiResult;
import vn.hiu.staffflow.dto.Views.KpiStatus;
import vn.hiu.staffflow.dto.Views.ReconciliationView;
import vn.hiu.staffflow.dto.Views.WeekView;
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
import vn.hiu.staffflow.repository.memory.InMemoryReconciliationRepository;
import vn.hiu.staffflow.repository.memory.InMemoryShiftRepository;
import vn.hiu.staffflow.repository.memory.InMemorySurveyRepository;
import vn.hiu.staffflow.repository.memory.InMemorySwapRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskEventRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskRepository;
import vn.hiu.staffflow.repository.memory.InMemoryTaskTemplateRepository;
import vn.hiu.staffflow.service.AccessPolicy;
import vn.hiu.staffflow.service.AuditService;
import vn.hiu.staffflow.service.CalendarService;
import vn.hiu.staffflow.service.CsvUtil;
import vn.hiu.staffflow.service.DashboardService;
import vn.hiu.staffflow.service.ImportService;
import vn.hiu.staffflow.service.MonthlyReportService;
import vn.hiu.staffflow.service.ReconciliationService;
import vn.hiu.staffflow.service.ReportExportService;
import vn.hiu.staffflow.service.ScheduleService;
import vn.hiu.staffflow.service.SurveyService;
import vn.hiu.staffflow.service.TaskService;
import vn.hiu.staffflow.service.TxLock;
import vn.hiu.staffflow.service.WorkloadService;

/** Kiểm thử Phase 4: T6 (xuất báo cáo tuần/tháng), nhập CSV, K7, K8, đối soát hằng tuần (mục 6.6). */
class Phase4Test {
    static final LocalDate MAIN = LocalDate.parse("2026-10-19");

    InMemoryOrgRepository org;
    InMemoryShiftRepository shifts;
    InMemoryTaskRepository taskRepo;
    InMemoryTaskTemplateRepository templateRepo;
    InMemorySurveyRepository surveyRepo;
    InMemoryMonthlyReportRepository monthlyReportRepo;
    InMemoryReconciliationRepository reconciliationRepo;
    AppClock clock;
    AccessPolicy policy;
    ScheduleService scheduleService;
    TaskService taskService;
    DashboardService dashboard;
    SurveyService surveyService;
    MonthlyReportService monthlyReportService;
    ReconciliationService reconciliationService;
    ImportService importService;
    ReportExportService exportService;
    AppUser admin, deputy, nv03, nv07;

    @BeforeEach
    void setUp() {
        org = new InMemoryOrgRepository();
        shifts = new InMemoryShiftRepository();
        InMemoryCrossTrainingRepository crossTraining = new InMemoryCrossTrainingRepository();
        templateRepo = new InMemoryTaskTemplateRepository();
        taskRepo = new InMemoryTaskRepository();
        InMemoryTaskEventRepository eventRepo = new InMemoryTaskEventRepository();
        InMemorySwapRepository swapRepo = new InMemorySwapRepository();
        surveyRepo = new InMemorySurveyRepository();
        monthlyReportRepo = new InMemoryMonthlyReportRepository();
        reconciliationRepo = new InMemoryReconciliationRepository();
        new DemoDataSeeder(org, shifts, crossTraining, templateRepo, taskRepo, eventRepo, surveyRepo,
                monthlyReportRepo, "2026-10-19").seed();

        clock = new AppClock("2026-10-18T15:00");
        CalendarService calendar = new CalendarService(org);
        policy = new AccessPolicy();
        AuditService audit = new AuditService(new InMemoryAuditRepository(), clock);
        TxLock tx = new TxLock();

        scheduleService = new ScheduleService(shifts, org, calendar, policy, audit, tx);
        taskService = new TaskService(taskRepo, eventRepo, templateRepo, org, policy, audit, clock, tx);
        WorkloadService workload = new WorkloadService(org, shifts, taskRepo);
        surveyService = new SurveyService(surveyRepo, org, audit, clock);
        monthlyReportService = new MonthlyReportService(monthlyReportRepo, org, policy, audit, clock);
        reconciliationService = new ReconciliationService(reconciliationRepo, shifts, taskRepo, org, policy, audit,
                taskService, clock);
        importService = new ImportService(scheduleService, taskService, org, templateRepo);
        exportService = new ReportExportService();

        K1CoverageKpi k1 = new K1CoverageKpi(calendar, shifts, org);
        K2EmptyShiftKpi k2 = new K2EmptyShiftKpi(shifts, clock);
        K3TaskOnTimeKpi k3 = new K3TaskOnTimeKpi(taskRepo, eventRepo, clock);
        K4SwapProcessingKpi k4 = new K4SwapProcessingKpi(swapRepo);
        K5BalanceKpi k5 = new K5BalanceKpi(workload);
        K6UpdateRateKpi k6 = new K6UpdateRateKpi(taskRepo, eventRepo);
        K7SatisfactionKpi k7 = new K7SatisfactionKpi(surveyService, "2026-10-19");
        K8MonthlyReportKpi k8 = new K8MonthlyReportKpi(monthlyReportService);
        dashboard = new DashboardService(k1, k2, k3, k4, k5, k6, k7, k8, workload, "2026-10-19");

        admin = org.userByCode("NV-01").orElseThrow();
        deputy = org.userByCode("NV-02").orElseThrow();
        nv03 = org.userByCode("NV-03").orElseThrow();
        nv07 = org.userByCode("NV-07").orElseThrow();
    }

    // ================================================================= K7

    @Test
    void k7_tuan4_coDuLieuSeedDat() {
        KpiResult r = dashboard.get(MAIN.plusWeeks(3)).kpis().stream()
                .filter(k -> k.code().equals("K7")).findFirst().orElseThrow();
        assertEquals(KpiStatus.DAT, r.status());
        assertTrue(r.rawValue() >= 3.5);
    }

    @Test
    void k7_tuan8_chuaCoPhanHoi_traVeNA() {
        KpiResult r = dashboard.get(MAIN.plusWeeks(7)).kpis().stream()
                .filter(k -> k.code().equals("K7")).findFirst().orElseThrow();
        assertEquals(KpiStatus.NA, r.status());
        assertNull(r.rawValue());
    }

    @Test
    void k7_khongPhaiTuan4Hay8_traVeNA() {
        KpiResult r = dashboard.get(MAIN).kpis().stream().filter(k -> k.code().equals("K7")).findFirst().orElseThrow();
        assertEquals(KpiStatus.NA, r.status());
    }

    @Test
    void k7_nopLaiCungKy_ghiDeKhongTaoBanGhiMoi() {
        surveyService.submit(nv03, new SurveyRequest("TUAN_4", 2, 2, 2, "điểm thấp hơn"));
        int countForNv03 = (int) surveyService.list("TUAN_4").stream().filter(v -> v.userCode().equals("NV-03")).count();
        assertEquals(1, countForNv03, "nộp lại cùng kỳ phải ghi đè, không tạo thêm bản ghi");
    }

    // ================================================================= K8

    @Test
    void k8_seedCoMotDungHanMotChuaNop_50PhanTram_ChuaDat() {
        KpiResult r = dashboard.get(MAIN).kpis().stream().filter(k -> k.code().equals("K8")).findFirst().orElseThrow();
        assertEquals(KpiStatus.CHUA_DAT, r.status());
        assertEquals(50.0, r.rawValue(), 0.01);
    }

    @Test
    void k8_ghiNhanNopDungHanThangConLai_thanh100PhanTram() {
        monthlyReportService.record(admin, new MonthlyReportRequest("2026-10", LocalDate.parse("2026-11-05"),
                LocalDate.parse("2026-11-02"), "Nộp sớm"));
        KpiResult r = dashboard.get(MAIN).kpis().stream().filter(k -> k.code().equals("K8")).findFirst().orElseThrow();
        assertEquals(KpiStatus.DAT, r.status());
        assertEquals(100.0, r.rawValue(), 0.01);
    }

    @Test
    void k8_phoGiamDocKhongDuocGhiNhan() {
        ApiException ex = assertThrows(ApiException.class, () -> monthlyReportService.record(deputy,
                new MonthlyReportRequest("2026-11", LocalDate.parse("2026-12-05"), null, null)));
        assertEquals(403, ex.status());
    }

    // ================================================================= Đối soát

    @Test
    void reconciliation_random10PhanTramLamTronLen() {
        List<Shift> weekShifts = shifts.findBetween(MAIN, MAIN.plusDays(6));
        int expected = (int) Math.ceil(weekShifts.size() * 0.10);
        if (expected < 1) {
            expected = 1;
        }
        ReconciliationView v = reconciliationService.create(admin, new ReconciliationCreateRequest(MAIN, null));
        assertEquals(expected, v.items().size());
    }

    @Test
    void reconciliation_ganCoNhiemVuDongKhongMinhChung() {
        ReconciliationView v = reconciliationService.create(admin, new ReconciliationCreateRequest(MAIN, 1L));
        assertTrue(v.flaggedTasks().stream().anyMatch(t -> t.title().contains("Sắp xếp lại giá sách")
                && t.closedWithoutEvidence()));
    }

    @Test
    void reconciliation_ghiNhanKetQuaTungCa() {
        ReconciliationView v = reconciliationService.create(admin, new ReconciliationCreateRequest(MAIN, null));
        long shiftId = v.items().get(0).shiftId();
        ReconciliationView updated = reconciliationService.recordItem(admin, v.id(), shiftId,
                new ReconciliationItemRequest(true, "Khớp với sổ trực"));
        assertEquals(Boolean.TRUE, updated.items().stream().filter(i -> i.shiftId() == shiftId).findFirst().orElseThrow().matched());
    }

    @Test
    void reconciliation_pgdKhongTaoDuocPhieuToanHeThong() {
        ApiException ex = assertThrows(ApiException.class,
                () -> reconciliationService.create(deputy, new ReconciliationCreateRequest(MAIN, null)));
        assertEquals(403, ex.status());
    }

    @Test
    void reconciliation_pgdTaoDuocPhieuChoDamSen() {
        ReconciliationView v = reconciliationService.create(deputy, new ReconciliationCreateRequest(MAIN, 2L));
        assertEquals(2L, v.branchId());
    }

    // ================================================================= CSV

    @Test
    void csv_ghiVaDocLaiKhopNoiDung() {
        List<String[]> rows = List.of(
                new String[] { "Cơ sở", "Ghi chú" },
                new String[] { "Điện Biên Phủ", "Có dấu phẩy, dấu \"ngoặc\" và tiếng Việt" });
        byte[] bytes = CsvUtil.write(rows);
        // BOM UTF-8
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        List<String[]> parsed = CsvUtil.parse(new ByteArrayInputStream(bytes));
        assertEquals(2, parsed.size());
        assertEquals("Điện Biên Phủ", parsed.get(1)[0]);
        assertEquals("Có dấu phẩy, dấu \"ngoặc\" và tiếng Việt", parsed.get(1)[1]);
    }

    @Test
    void t6_xuatBaoCaoTuan_coDuSoLieuKhongVietCung() {
        WeekView week = scheduleService.week(MAIN, null, admin);
        var taskList = taskService.list(admin, MAIN, null, null, null);
        DashboardView d = dashboard.get(MAIN);
        byte[] csv = exportService.weekCsv(week, taskList, d);
        String text = new String(csv, StandardCharsets.UTF_8);
        assertTrue(text.contains("BAO PHU GIO-CO SO"));
        assertTrue(text.contains("207"), "phải có tổng 207 giờ-cơ sở lấy từ dữ liệu thật, không viết cứng");
        assertTrue(text.contains("K1,"));
        assertTrue(text.contains("K7,"));
        assertTrue(text.contains("K8,"));
    }

    @Test
    void xuatBaoCaoThang_coCaHaiThangDaGhiNhan() {
        byte[] csv = exportService.monthCsv(monthlyReportService.list());
        String text = new String(csv, StandardCharsets.UTF_8);
        assertTrue(text.contains("2026-09"));
        assertTrue(text.contains("2026-10"));
    }

    // ================================================================= Nhập CSV

    @Test
    void nhapCsvCaTruc_dongLoiKhongLamHongDongHopLe() {
        String csv = "branchCode,date,start,end,userCode,note\n"
                + "DBP,2026-10-20,09:00,11:00,NV-05,ca thử nghiệm nhập CSV\n"
                + "KHONGTONTAI,2026-10-20,09:00,11:00,,\n";
        ImportSummary s = importService.importShifts(admin, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, s.totalRows());
        assertEquals(1, s.okRows());
        assertEquals(1, s.errorRows());
        assertTrue(s.rows().get(1).message().contains("Không tìm thấy cơ sở"));
    }

    @Test
    void nhapCsvNhiemVu_dongHopLeDuocTao() {
        String csv = "title,templateOrd,branchCode,assigneeCode,estimatedMinutes,dueDate,priority\n"
                + "Việc nhập từ CSV,,DBP,NV-03,90,2026-10-30,THAP\n";
        ImportSummary s = importService.importTasks(admin, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, s.okRows());
        assertTrue(taskService.list(admin, null, null, null, null).stream()
                .anyMatch(t -> t.title().equals("Việc nhập từ CSV")));
    }

    @Test
    void nhapCsvCaTruc_chuyenVienKhongDuQuyen_baoLoiTungDong() {
        String csv = "branchCode,date,start,end,userCode,note\n"
                + "DBP,2026-10-20,09:00,11:00,,\n";
        ImportSummary s = importService.importShifts(nv07, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, s.okRows());
        assertEquals(1, s.errorRows());
    }
}
