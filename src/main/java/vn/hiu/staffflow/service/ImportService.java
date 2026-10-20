package vn.hiu.staffflow.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.TaskTemplate;
import vn.hiu.staffflow.dto.Requests.ShiftRequest;
import vn.hiu.staffflow.dto.Requests.TaskRequest;
import vn.hiu.staffflow.dto.Views.ImportRowResult;
import vn.hiu.staffflow.dto.Views.ImportSummary;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.repository.TaskTemplateRepository;

/**
 * Nhập CSV ca trực và nhiệm vụ (mục 2, mục 8). Kiểm tra và báo lỗi TỪNG DÒNG — một dòng lỗi không làm hỏng
 * các dòng còn lại. Dùng lại đúng các quy tắc nghiệp vụ của ScheduleService/TaskService (không viết lại logic),
 * nên một dòng CSV vi phạm quy tắc (trùng ca, ngoài giờ mở cửa, không đủ quyền...) báo lỗi giống hệt khi thao
 * tác qua giao diện.
 */
@Service
public class ImportService {
    private final ScheduleService schedule;
    private final TaskService tasks;
    private final OrgRepository org;
    private final TaskTemplateRepository templates;

    public ImportService(ScheduleService schedule, TaskService tasks, OrgRepository org,
                         TaskTemplateRepository templates) {
        this.schedule = schedule;
        this.tasks = tasks;
        this.org = org;
        this.templates = templates;
    }

    /** Cột: branchCode,date(yyyy-MM-dd),start(HH:mm),end(HH:mm),userCode(để trống nếu ca trống),note(tùy chọn). */
    public ImportSummary importShifts(AppUser actor, InputStream csv) {
        List<String[]> rows = CsvUtil.parse(csv);
        List<ImportRowResult> results = new ArrayList<>();
        int ok = 0;
        for (int i = 1; i < rows.size(); i++) { // dòng 0 là header
            int rowNo = i + 1; // số dòng thực tế trong file (tính cả header, 1-based)
            String[] r = rows.get(i);
            try {
                if (r.length < 4) {
                    throw new IllegalArgumentException("Thiếu cột (cần tối thiểu branchCode,date,start,end)");
                }
                String branchCode = col(r, 0).trim();
                Branch branch = org.branches().stream().filter(b -> b.code().equalsIgnoreCase(branchCode))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cơ sở mã \"" + branchCode + "\""));
                LocalDate date = parseDate(col(r, 1));
                LocalTime start = parseTime(col(r, 2));
                LocalTime end = parseTime(col(r, 3));
                String userCode = col(r, 4).trim();
                Long userId = null;
                if (!userCode.isBlank()) {
                    AppUser u = org.userByCode(userCode)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự mã \"" + userCode + "\""));
                    userId = u.id();
                }
                String note = col(r, 5);
                schedule.create(actor, new ShiftRequest(branch.id(), date, start, end, userId, note));
                results.add(new ImportRowResult(rowNo, true, "Đã tạo ca " + branchCode + " " + date));
                ok++;
            } catch (ApiException e) {
                results.add(new ImportRowResult(rowNo, false, e.getMessage()
                        + (e.details().isEmpty() ? "" : " (" + String.join("; ", e.details()) + ")")));
            } catch (Exception e) {
                results.add(new ImportRowResult(rowNo, false, "Dòng không hợp lệ: " + e.getMessage()));
            }
        }
        int dataRows = Math.max(0, rows.size() - 1);
        return new ImportSummary(dataRows, ok, dataRows - ok, results);
    }

    /** Cột: title,templateOrd(số thứ tự mẫu, để trống nếu không dùng mẫu),branchCode(để trống = toàn hệ thống),
     *  assigneeCode,estimatedMinutes,dueDate(yyyy-MM-dd),priority(CAO/TRUNG_BINH/THAP). */
    public ImportSummary importTasks(AppUser actor, InputStream csv) {
        List<String[]> rows = CsvUtil.parse(csv);
        List<ImportRowResult> results = new ArrayList<>();
        int ok = 0;
        for (int i = 1; i < rows.size(); i++) {
            int rowNo = i + 1;
            String[] r = rows.get(i);
            try {
                if (r.length < 7) {
                    throw new IllegalArgumentException("Thiếu cột (cần title,templateOrd,branchCode,assigneeCode,estimatedMinutes,dueDate,priority)");
                }
                String title = col(r, 0).trim();
                if (title.isBlank()) {
                    throw new IllegalArgumentException("Thiếu tiêu đề nhiệm vụ");
                }
                String templateOrdStr = col(r, 1).trim();
                Long templateId = null;
                if (!templateOrdStr.isBlank()) {
                    int ord = Integer.parseInt(templateOrdStr);
                    TaskTemplate t = templates.findAll().stream().filter(x -> x.ord() == ord).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mẫu nhiệm vụ số " + ord));
                    templateId = t.id();
                }
                String branchCode = col(r, 2).trim();
                Long branchId = null;
                if (!branchCode.isBlank()) {
                    Branch b = org.branches().stream().filter(x -> x.code().equalsIgnoreCase(branchCode))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cơ sở mã \"" + branchCode + "\""));
                    branchId = b.id();
                }
                String assigneeCode = col(r, 3).trim();
                AppUser assignee = org.userByCode(assigneeCode)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân sự mã \"" + assigneeCode + "\""));
                int minutes = Integer.parseInt(col(r, 4).trim());
                LocalDate due = parseDate(col(r, 5));
                String priority = col(r, 6).trim().toUpperCase(Locale.ROOT);
                tasks.create(actor, new TaskRequest(title, templateId, branchId, assignee.id(), minutes, due, priority));
                results.add(new ImportRowResult(rowNo, true, "Đã tạo nhiệm vụ \"" + title + "\""));
                ok++;
            } catch (ApiException e) {
                results.add(new ImportRowResult(rowNo, false, e.getMessage()
                        + (e.details().isEmpty() ? "" : " (" + String.join("; ", e.details()) + ")")));
            } catch (Exception e) {
                results.add(new ImportRowResult(rowNo, false, "Dòng không hợp lệ: " + e.getMessage()));
            }
        }
        int dataRows = Math.max(0, rows.size() - 1);
        return new ImportSummary(dataRows, ok, dataRows - ok, results);
    }

    private static String col(String[] r, int i) {
        return i < r.length ? (r[i] == null ? "" : r[i]) : "";
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Ngày không hợp lệ (cần yyyy-MM-dd): \"" + s + "\"");
        }
    }

    private static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Giờ không hợp lệ (cần HH:mm): \"" + s + "\"");
        }
    }
}
