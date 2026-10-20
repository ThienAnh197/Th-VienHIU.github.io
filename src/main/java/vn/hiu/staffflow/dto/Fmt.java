package vn.hiu.staffflow.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Định dạng chuỗi ngày giờ cố định để frontend không phải đoán. */
public final class Fmt {
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Fmt() {
    }

    public static String d(LocalDate d) {
        return d == null ? null : d.toString();
    }

    public static String t(LocalTime t) {
        return t == null ? null : t.format(T);
    }

    public static String dt(LocalDateTime x) {
        return x == null ? null : x.format(DT);
    }
}
