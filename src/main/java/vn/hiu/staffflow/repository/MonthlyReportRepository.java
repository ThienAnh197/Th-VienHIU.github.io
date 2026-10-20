package vn.hiu.staffflow.repository;

import java.util.List;

import vn.hiu.staffflow.domain.Domain.MonthlyReport;

public interface MonthlyReportRepository {
    MonthlyReport save(MonthlyReport r);

    List<MonthlyReport> findAll();
}
