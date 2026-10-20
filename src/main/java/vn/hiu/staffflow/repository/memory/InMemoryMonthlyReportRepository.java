package vn.hiu.staffflow.repository.memory;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.MonthlyReport;
import vn.hiu.staffflow.repository.MonthlyReportRepository;

@Repository
@Profile("memory")
public class InMemoryMonthlyReportRepository implements MonthlyReportRepository {
    private final MemTable<MonthlyReport> table = new MemTable<>(MonthlyReport::id, MonthlyReport::withId);

    @Override
    public MonthlyReport save(MonthlyReport r) {
        return table.save(r);
    }

    @Override
    public List<MonthlyReport> findAll() {
        return table.all();
    }
}
