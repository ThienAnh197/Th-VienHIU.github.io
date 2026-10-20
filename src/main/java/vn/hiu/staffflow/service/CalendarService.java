package vn.hiu.staffflow.service;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

import vn.hiu.staffflow.repository.OrgRepository;

/** Lịch mở cửa theo cơ sở: Chủ nhật + ngày lễ (bảng holidays) là đóng cửa. */
@Component
public class CalendarService {
    private final OrgRepository org;

    public CalendarService(OrgRepository org) {
        this.org = org;
    }

    public boolean isClosed(long branchId, LocalDate date) {
        if (OpeningHours.closeTime(date.getDayOfWeek()) == null) {
            return true;
        }
        return org.holidays().stream()
                .anyMatch(h -> h.date().equals(date) && (h.branchId() == null || h.branchId() == branchId));
    }

    /** Giờ đóng cửa của ngày đó, null nếu đóng cửa cả ngày. */
    public LocalTime closeTime(long branchId, LocalDate date) {
        return isClosed(branchId, date) ? null : OpeningHours.closeTime(date.getDayOfWeek());
    }

    public int openMinutes(long branchId, LocalDate date) {
        return isClosed(branchId, date) ? 0 : OpeningHours.openMinutes(date.getDayOfWeek());
    }

    /** Số phút cần phủ của một cơ sở trong tuần bắt đầu từ thứ Hai. */
    public int requiredMinutes(long branchId, LocalDate monday) {
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            sum += openMinutes(branchId, monday.plusDays(i));
        }
        return sum;
    }
}
