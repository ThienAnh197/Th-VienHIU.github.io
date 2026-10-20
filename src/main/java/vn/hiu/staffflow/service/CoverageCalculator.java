package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;

/**
 * Tính số phút "có người phụ trách hợp lệ" của một cơ sở.
 * Hợp lệ = ca có người, trạng thái Đủ người, nằm trong giờ mở cửa.
 * Hai ca chồng nhau tại cùng cơ sở chỉ được tính một lần (hợp các khoảng thời gian).
 * Ca trống hoặc bị ảnh hưởng do vắng KHÔNG được tính.
 */
public final class CoverageCalculator {
    private CoverageCalculator() {
    }

    public static int coveredMinutes(long branchId, List<Shift> shifts, CalendarService calendar) {
        Map<LocalDate, List<int[]>> byDay = new TreeMap<>();
        for (Shift s : shifts) {
            if (s.branchId() != branchId || s.userId() == null || s.status() != ShiftStatus.DU_NGUOI) {
                continue;
            }
            LocalTime close = calendar.closeTime(branchId, s.date());
            if (close == null) {
                continue;
            }
            LocalTime from = s.start().isBefore(OpeningHours.OPEN) ? OpeningHours.OPEN : s.start();
            LocalTime to = s.end().isAfter(close) ? close : s.end();
            if (to.isAfter(from)) {
                byDay.computeIfAbsent(s.date(), k -> new ArrayList<>())
                        .add(new int[]{from.toSecondOfDay() / 60, to.toSecondOfDay() / 60});
            }
        }
        int total = 0;
        for (List<int[]> intervals : byDay.values()) {
            total += unionMinutes(intervals);
        }
        return total;
    }

    static int unionMinutes(List<int[]> intervals) {
        List<int[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingInt(a -> a[0]));
        int total = 0;
        int curStart = -1;
        int curEnd = -1;
        for (int[] iv : sorted) {
            if (curEnd < 0) {
                curStart = iv[0];
                curEnd = iv[1];
            } else if (iv[0] <= curEnd) {
                curEnd = Math.max(curEnd, iv[1]);
            } else {
                total += curEnd - curStart;
                curStart = iv[0];
                curEnd = iv[1];
            }
        }
        if (curEnd >= 0) {
            total += curEnd - curStart;
        }
        return total;
    }
}
