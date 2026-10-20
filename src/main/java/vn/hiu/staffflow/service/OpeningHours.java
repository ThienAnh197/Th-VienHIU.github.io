package vn.hiu.staffflow.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Giờ mở cửa dùng chung cho cả 3 cơ sở (giả định GĐ-07):
 * Thứ Hai-Thứ Sáu 08:00-20:00, Thứ Bảy 08:00-17:00, Chủ Nhật đóng cửa.
 * => 5 x 12 + 9 = 69 giờ/tuần/cơ sở; 3 cơ sở = 207 giờ-cơ sở.
 */
public final class OpeningHours {
    public static final LocalTime OPEN = LocalTime.of(8, 0);
    public static final int WEEKLY_MINUTES_PER_BRANCH = 69 * 60;

    private OpeningHours() {
    }

    /** Giờ đóng cửa theo thứ; null nếu cả ngày đóng cửa. */
    public static LocalTime closeTime(DayOfWeek d) {
        return switch (d) {
            case SATURDAY -> LocalTime.of(17, 0);
            case SUNDAY -> null;
            default -> LocalTime.of(20, 0);
        };
    }

    public static int openMinutes(DayOfWeek d) {
        LocalTime close = closeTime(d);
        return close == null ? 0 : (int) Duration.between(OPEN, close).toMinutes();
    }
}
