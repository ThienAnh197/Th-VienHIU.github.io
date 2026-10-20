package vn.hiu.staffflow.repository.memory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.CrossTraining;
import vn.hiu.staffflow.domain.Domain.Priority;
import vn.hiu.staffflow.domain.Domain.Role;
import vn.hiu.staffflow.domain.Domain.MonthlyReport;
import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;
import vn.hiu.staffflow.domain.Domain.SurveyPeriod;
import vn.hiu.staffflow.domain.Domain.SurveyResponse;
import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.domain.Domain.TaskCategory;
import vn.hiu.staffflow.domain.Domain.TaskEvent;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.domain.Domain.TaskTemplate;
import vn.hiu.staffflow.domain.Domain.TitleGroup;
import vn.hiu.staffflow.domain.Domain.TrainingStatus;
import vn.hiu.staffflow.repository.CrossTrainingRepository;
import vn.hiu.staffflow.repository.MonthlyReportRepository;
import vn.hiu.staffflow.repository.ShiftRepository;
import vn.hiu.staffflow.repository.SurveyRepository;
import vn.hiu.staffflow.repository.TaskEventRepository;
import vn.hiu.staffflow.repository.TaskRepository;
import vn.hiu.staffflow.repository.TaskTemplateRepository;

/**
 * DỮ LIỆU GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH - không phải dữ liệu thật của thư viện.
 * Chỉ dùng mã vị trí NV-01..NV-08, không có họ tên thật.
 *
 * Tuần chính (demo-week-start): phản ánh thực trạng thiếu nhân lực của báo cáo (Bảng 14):
 * 96 / 207 giờ-cơ sở = 46,4% (nằm trong khoảng 36-48%). KHÔNG tự phân ca cho đủ 207 giờ.
 * Tuần kiểm thử (tuần sau): phủ đủ 207 giờ, chỉ để kiểm tra trường hợp K1 = 100%.
 */
@Component
@Profile("memory")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "Demo@2026";

    private final InMemoryOrgRepository org;
    private final ShiftRepository shifts;
    private final CrossTrainingRepository crossTraining;
    private final TaskTemplateRepository taskTemplates;
    private final TaskRepository tasks;
    private final TaskEventRepository taskEvents;
    private final SurveyRepository surveys;
    private final MonthlyReportRepository monthlyReports;
    private final LocalDate mainWeek;

    public DemoDataSeeder(InMemoryOrgRepository org, ShiftRepository shifts, CrossTrainingRepository crossTraining,
                          TaskTemplateRepository taskTemplates, TaskRepository tasks, TaskEventRepository taskEvents,
                          SurveyRepository surveys, MonthlyReportRepository monthlyReports,
                          @Value("${staffflow.demo-week-start:2026-10-19}") String mainWeek) {
        this.org = org;
        this.shifts = shifts;
        this.crossTraining = crossTraining;
        this.taskTemplates = taskTemplates;
        this.tasks = tasks;
        this.taskEvents = taskEvents;
        this.surveys = surveys;
        this.monthlyReports = monthlyReports;
        this.mainWeek = LocalDate.parse(mainWeek);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        seedOrg();
        seedMainWeek(mainWeek);
        seedFullTestWeek(mainWeek.plusDays(7));
        seedCrossTraining();
        seedTaskTemplates();
        seedTasks();
        seedSurveyAndMonthlyReports();
    }

    /**
     * Hai cặp đào tạo chéo hai chiều theo báo cáo (mục 3.2):
     * Xử lý tài liệu <-> Phục vụ bạn đọc (NV-03/04 <-> NV-07/08);
     * Hệ thống <-> Xử lý tài liệu (NV-05/06 <-> NV-03/04).
     * NV-03 "Đang đào tạo" để thay cho hệ thống: minh họa quy tắc chỉ bản ghi "đã hoàn thành" mới tính.
     */
    private void seedCrossTraining() {
        ct(3, TitleGroup.PHUC_VU_BAN_DOC, TrainingStatus.DA_HOAN_THANH);
        ct(7, TitleGroup.XU_LY_TAI_LIEU, TrainingStatus.DA_HOAN_THANH);
        ct(4, TitleGroup.PHUC_VU_BAN_DOC, TrainingStatus.DA_HOAN_THANH);
        ct(8, TitleGroup.XU_LY_TAI_LIEU, TrainingStatus.DA_HOAN_THANH);
        ct(5, TitleGroup.XU_LY_TAI_LIEU, TrainingStatus.DA_HOAN_THANH);
        ct(3, TitleGroup.HE_THONG, TrainingStatus.DANG_DAO_TAO);
        ct(6, TitleGroup.XU_LY_TAI_LIEU, TrainingStatus.DA_HOAN_THANH);
        ct(4, TitleGroup.HE_THONG, TrainingStatus.DA_HOAN_THANH);
    }

    private void ct(long userId, TitleGroup group, TrainingStatus status) {
        crossTraining.save(new CrossTraining(0, userId, group, status));
    }

    private void seedOrg() {
        List<Branch> branches = List.of(
                new Branch(1, "DBP", "Điện Biên Phủ", "215 Điện Biên Phủ, tầng 3 (trụ sở chính)", ""),
                new Branch(2, "DS", "Đầm Sen", "120 Hòa Bình (khai trương 19/8/2026)", ""),
                new Branch(3, "D2", "Cơ sở D2", "36/70 Nguyễn Gia Trí, tầng 1 tòa B",
                        "Địa chỉ là giả định của nhóm, cần xác nhận [GĐ][KC]"));

        String hash = new BCryptPasswordEncoder().encode(DEMO_PASSWORD);
        List<AppUser> users = List.of(
                u(1, "Giám đốc", TitleGroup.GIAM_DOC, Role.ADMIN, 1, hash),
                u(2, "Phó giám đốc", TitleGroup.PHO_GIAM_DOC, Role.BRANCH_MANAGER, 2, hash),
                u(3, "CV xử lý tài liệu", TitleGroup.XU_LY_TAI_LIEU, Role.STAFF, 1, hash),
                u(4, "CV xử lý tài liệu", TitleGroup.XU_LY_TAI_LIEU, Role.STAFF, 2, hash),
                u(5, "CV hệ thống", TitleGroup.HE_THONG, Role.TECH, 1, hash),
                u(6, "CV hệ thống", TitleGroup.HE_THONG, Role.TECH, 3, hash),
                u(7, "CV phục vụ bạn đọc", TitleGroup.PHUC_VU_BAN_DOC, Role.STAFF, 1, hash),
                u(8, "CV phục vụ bạn đọc", TitleGroup.PHUC_VU_BAN_DOC, Role.STAFF, 2, hash));

        org.load(branches, users, List.of());
    }

    private static AppUser u(int n, String title, TitleGroup g, Role role, long branchId, String hash) {
        String code = String.format("NV-%02d", n);
        return new AppUser(n, code, code + " – " + title, g, role, branchId, hash);
    }

    /** Tuần chính: 96 giờ có người / 207 = 46,4%. Có vài ca đã lập nhưng trống. */
    private void seedMainWeek(LocalDate monday) {
        // NV-07 (ĐBP) và NV-08 (Đầm Sen): quầy thứ 2-6, 08:00-14:00  -> 30 + 30 giờ
        pattern(monday, 7, 1, "08:00", "14:00", false, 0, 1, 2, 3, 4);
        pattern(monday, 8, 2, "08:00", "14:00", false, 0, 1, 2, 3, 4);
        // Hỗ trợ quầy buổi chiều
        pattern(monday, 3, 1, "14:00", "18:00", false, 0, 2);      // 8 giờ
        pattern(monday, 5, 1, "14:00", "18:00", false, 1, 3);      // 8 giờ
        pattern(monday, 4, 2, "14:00", "18:00", false, 1, 3);      // 8 giờ
        pattern(monday, 6, 3, "08:00", "12:00", false, 0, 2, 4);   // 12 giờ

        // Ca đã lập nhưng chưa có người (cảnh báo đỏ)
        empty(monday, 3, 0, "14:00", "18:00");  // D2 thứ Hai chiều
        empty(monday, 2, 5, "08:00", "12:00");  // Đầm Sen thứ Bảy sáng
        empty(monday, 1, 5, "08:00", "12:00");  // ĐBP thứ Bảy sáng
    }

    /** Tuần kiểm thử: mỗi cơ sở 69 giờ có người => 207 giờ-cơ sở (K1 = 100%). */
    private void seedFullTestWeek(LocalDate monday) {
        long[][] pairs = {{1, 7, 3}, {2, 8, 4}, {3, 6, 5}}; // {cơ sở, người ca sáng, người ca chiều}
        for (long[] p : pairs) {
            for (int d = 0; d <= 5; d++) {
                LocalDate date = monday.plusDays(d);
                add(p[0], date, "08:00", "14:00", p[1], true);
                add(p[0], date, "14:00", d == 5 ? "17:00" : "20:00", p[2], true);
            }
        }
    }

    private void pattern(LocalDate monday, long userId, long branchId, String s, String e,
                         boolean test, int... days) {
        for (int d : days) {
            add(branchId, monday.plusDays(d), s, e, userId, test);
        }
    }

    private void empty(LocalDate monday, long branchId, int day, String s, String e) {
        add(branchId, monday.plusDays(day), s, e, null, false);
    }

    private void add(long branchId, LocalDate date, String s, String e, Long userId, boolean test) {
        shifts.save(new Shift(0, branchId, date, LocalTime.parse(s), LocalTime.parse(e), userId,
                userId == null ? ShiftStatus.TRONG : ShiftStatus.DU_NGUOI,
                test ? "Dữ liệu kiểm thử K1=100%" : "", test));
    }

    /**
     * 16 nhóm hoạt động theo Điều 7 Thông tư 14/2023/TT-BGDĐT (5 nhóm phục vụ + 11 nhóm nghiệp vụ).
     * TÊN RÚT GỌN theo cách hiểu của nhóm — cần đối chiếu văn bản gốc. Người phụ trách chính/dự phòng suy ra
     * từ ma trận phân công sơ bộ của báo cáo (Bảng 7); định mức giờ là GIẢ ĐỊNH của nhóm để demo tải việc.
     */
    private void seedTaskTemplates() {
        int ord = 1;
        // 5 nhóm hoạt động phục vụ
        tt(ord++, TaskCategory.PHUC_VU, "Phục vụ bạn đọc tại quầy, lưu thông tài liệu",
                TitleGroup.PHUC_VU_BAN_DOC, TitleGroup.XU_LY_TAI_LIEU, 180);
        tt(ord++, TaskCategory.PHUC_VU, "Hướng dẫn, hỗ trợ tra cứu, sử dụng thư viện",
                TitleGroup.PHUC_VU_BAN_DOC, TitleGroup.XU_LY_TAI_LIEU, 120);
        tt(ord++, TaskCategory.PHUC_VU, "Quản lý phòng đọc, phòng học nhóm",
                TitleGroup.PHUC_VU_BAN_DOC, TitleGroup.HE_THONG, 240);
        tt(ord++, TaskCategory.PHUC_VU, "Tổ chức không gian đọc, trưng bày tài liệu",
                TitleGroup.PHUC_VU_BAN_DOC, TitleGroup.PHO_GIAM_DOC, 180);
        tt(ord++, TaskCategory.PHUC_VU, "Hỗ trợ đào tạo kỹ năng thông tin cho bạn đọc",
                TitleGroup.PHUC_VU_BAN_DOC, TitleGroup.XU_LY_TAI_LIEU, 120);
        // 11 nhóm hoạt động nghiệp vụ
        tt(ord++, TaskCategory.NGHIEP_VU, "Xây dựng, phát triển nguồn tài nguyên thông tin",
                TitleGroup.GIAM_DOC, TitleGroup.XU_LY_TAI_LIEU, 360);
        tt(ord++, TaskCategory.NGHIEP_VU, "Xử lý kỹ thuật tài liệu (phân loại DDC, biên mục MARC 21)",
                TitleGroup.XU_LY_TAI_LIEU, TitleGroup.HE_THONG, 300);
        tt(ord++, TaskCategory.NGHIEP_VU, "Bảo quản, kiểm kê tài liệu",
                TitleGroup.XU_LY_TAI_LIEU, TitleGroup.PHUC_VU_BAN_DOC, 240);
        tt(ord++, TaskCategory.NGHIEP_VU, "Xây dựng sản phẩm, dịch vụ thông tin – thư viện",
                TitleGroup.XU_LY_TAI_LIEU, TitleGroup.HE_THONG, 240);
        tt(ord++, TaskCategory.NGHIEP_VU, "Liên thông, chia sẻ nguồn lực giữa các thư viện",
                TitleGroup.GIAM_DOC, TitleGroup.XU_LY_TAI_LIEU, 180);
        tt(ord++, TaskCategory.NGHIEP_VU, "Tổ chức hoạt động văn hóa đọc, truyền thông tri thức",
                TitleGroup.PHO_GIAM_DOC, TitleGroup.PHUC_VU_BAN_DOC, 300);
        tt(ord++, TaskCategory.NGHIEP_VU, "Phát triển thư viện số, số hóa tài liệu",
                TitleGroup.HE_THONG, TitleGroup.XU_LY_TAI_LIEU, 360);
        tt(ord++, TaskCategory.NGHIEP_VU, "Hiện đại hóa hạ tầng, ứng dụng công nghệ (RFID, OPAC, hệ thống)",
                TitleGroup.HE_THONG, TitleGroup.GIAM_DOC, 300);
        tt(ord++, TaskCategory.NGHIEP_VU, "Truyền thông, quảng bá hoạt động thư viện",
                TitleGroup.PHO_GIAM_DOC, TitleGroup.PHUC_VU_BAN_DOC, 180);
        tt(ord++, TaskCategory.NGHIEP_VU, "Hợp tác, liên kết trong nước và quốc tế",
                TitleGroup.GIAM_DOC, TitleGroup.PHO_GIAM_DOC, 240);
        tt(ord++, TaskCategory.NGHIEP_VU, "Đánh giá, báo cáo hiệu quả hoạt động hằng năm",
                TitleGroup.GIAM_DOC, TitleGroup.PHO_GIAM_DOC, 480);
    }

    private void tt(int ord, TaskCategory cat, String name, TitleGroup main, TitleGroup backup, int minutes) {
        taskTemplates.save(new TaskTemplate(0, ord, cat, name, main, backup, minutes));
    }

    /**
     * Nhiệm vụ demo: một tuần lịch sử (2026-10-12) đã có hạn để K3 có số liệu (đúng/trễ hạn), và tuần chính
     * (19-25/10) có nhiệm vụ CHƯA đến hạn so với "bây giờ" (18/10) để minh họa quy tắc không tính vào K3
     * nhưng vẫn tính vào tải việc. Kịch bản T3 (hội sách trùng tuần kiểm kê): hai nhiệm vụ lớn cùng hạn,
     * cùng người phụ trách NV-03, khiến tải việc vượt 40 giờ/tuần.
     */
    private void seedTasks() {
        LocalDate histMonday = LocalDate.parse("2026-10-12");
        // A: đúng hạn
        task("Kiểm kê tài liệu định kỳ quý III", 8L, 1L, 3, 240, histMonday.plusDays(2), Priority.TRUNG_BINH,
                TaskStatus.XONG, false, "Biên bản kiểm kê đính kèm", LocalDateTime.parse("2026-10-12T08:00"),
                LocalDateTime.parse("2026-10-14T10:00"));
        // B: trễ hạn
        task("Biên mục lô sách mới nhập", 7L, 2L, 4, 300, histMonday.plusDays(3), Priority.TRUNG_BINH,
                TaskStatus.XONG, false, "Danh sách biên mục", LocalDateTime.parse("2026-10-12T08:00"),
                LocalDateTime.parse("2026-10-16T09:00"));
        // C: quá hạn, chưa xong
        task("Cập nhật dữ liệu OPAC sau bảo trì", 9L, 1L, 5, 180, histMonday.plusDays(1), Priority.CAO,
                TaskStatus.DANG_LAM, false, null, LocalDateTime.parse("2026-10-12T08:00"),
                LocalDateTime.parse("2026-10-13T09:00"));

        // Kịch bản T3: hai nhiệm vụ lớn cùng hạn thứ Năm tuần chính, cùng người phụ trách NV-03
        task("Hội sách mùa khai trương – chuẩn bị gian trưng bày", 6L, 1L, 3, 1200, mainWeek.plusDays(3),
                Priority.CAO, TaskStatus.DANG_LAM, false, null, mainWeek.atStartOfDay(), mainWeek.atStartOfDay());
        task("Kiểm kê tài liệu tuần khai trương Đầm Sen", 8L, 1L, 3, 960, mainWeek.plusDays(3),
                Priority.CAO, TaskStatus.MOI, false, null, mainWeek.atStartOfDay(), mainWeek.atStartOfDay());

        // Nhiệm vụ khác trong tuần chính để Kanban/dashboard có đủ dữ liệu minh họa
        task("Sắp xếp lại giá sách khu đọc", 2L, 1L, 7, 120, mainWeek.plusDays(2), Priority.THAP,
                TaskStatus.XONG, true, null, mainWeek.atStartOfDay(), mainWeek.plusDays(0).atTime(15, 0));
        task("Hỗ trợ tra cứu cho lớp nhập môn", 2L, 2L, 8, 120, mainWeek.plusDays(4), Priority.TRUNG_BINH,
                TaskStatus.CHO_DUYET, false, "Ảnh buổi hướng dẫn", mainWeek.atStartOfDay(), mainWeek.plusDays(1).atTime(9, 0));
        task("Xây dựng đề án hợp tác thư viện số 2027", null, null, 1, 480, mainWeek.plusDays(14),
                Priority.TRUNG_BINH, TaskStatus.MOI, false, null, mainWeek.atStartOfDay(), mainWeek.atStartOfDay());
    }

    /**
     * K7: 3 phản hồi khảo sát tuần 4 (điểm trung bình > 3,5 để minh họa trạng thái Đạt), CHƯA có phản hồi tuần 8
     * (để minh họa N/A). K8: tháng trước (09/2026) nộp đúng hạn, tháng hiện tại (10/2026) CHƯA nộp (để minh họa
     * trạng thái Chưa đạt) — dữ liệu GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH, không phải khảo sát/báo cáo thật.
     */
    private void seedSurveyAndMonthlyReports() {
        survey(SurveyPeriod.TUAN_4, 3, 4, 5, 4, "Khối lượng việc ổn, mong rõ lịch sớm hơn");
        survey(SurveyPeriod.TUAN_4, 7, 4, 4, 4, null);
        survey(SurveyPeriod.TUAN_4, 8, 3, 3, 4, "Ca cuối tuần hơi dồn");

        monthlyReport("2026-09", LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-03"),
                "Nộp đúng hạn qua hệ thống");
        monthlyReport("2026-10", LocalDate.parse("2026-11-05"), null, null);
    }

    private void survey(SurveyPeriod period, long userId, int q1, int q2, int q3, String note) {
        surveys.save(new SurveyResponse(0, period, userId, q1, q2, q3, note, mainWeek.plusDays(28).atTime(10, 0)));
    }

    private void monthlyReport(String yearMonth, LocalDate due, LocalDate submitted, String note) {
        monthlyReports.save(new MonthlyReport(0, yearMonth, due, submitted, note, 1L, due.atStartOfDay()));
    }

    private void task(String title, Long templateId, Long branchId, long assigneeId, int minutes, LocalDate due,
                      Priority priority, TaskStatus status, boolean closedNoEvidence, String evidenceNote,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        Task saved = tasks.save(new Task(0, title, templateId, branchId, assigneeId, minutes, due, priority,
                TaskStatus.MOI, false, null, createdAt, createdAt));
        if (status != TaskStatus.MOI) {
            saved = tasks.save(saved.withStatus(status, closedNoEvidence, evidenceNote, updatedAt));
        }
        taskEvents.add(new TaskEvent(0, saved.id(), null, saved.status(), assigneeId, updatedAt));
    }
}
