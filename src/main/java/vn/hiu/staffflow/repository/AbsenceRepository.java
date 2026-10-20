package vn.hiu.staffflow.repository;

import java.time.LocalDate;
import java.util.List;

import vn.hiu.staffflow.domain.Domain.Absence;

public interface AbsenceRepository {
    Absence save(Absence absence);

    /** Các bản ghi vắng có khoảng ngày giao với [from, to]. */
    List<Absence> findOverlapping(long userId, LocalDate from, LocalDate to);

    List<Absence> findAll();
}
