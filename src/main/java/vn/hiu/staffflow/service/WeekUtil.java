package vn.hiu.staffflow.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class WeekUtil {
    private WeekUtil() {
    }

    /** Thứ Hai của tuần chứa ngày d. */
    public static LocalDate mondayOf(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
