package vn.hiu.staffflow.repository.memory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.repository.ShiftRepository;

@Repository
@Profile("memory")
public class InMemoryShiftRepository implements ShiftRepository {
    private final MemTable<Shift> table = new MemTable<>(Shift::id, Shift::withId);

    @Override
    public List<Shift> findBetween(LocalDate from, LocalDate to) {
        return table.all().stream()
                .filter(s -> !s.date().isBefore(from) && !s.date().isAfter(to))
                .sorted(Comparator.comparing(Shift::date).thenComparing(Shift::start).thenComparingLong(Shift::branchId))
                .toList();
    }

    @Override
    public Optional<Shift> findById(long id) {
        return table.find(id);
    }

    @Override
    public Shift save(Shift shift) {
        return table.save(shift);
    }

    @Override
    public void delete(long id) {
        table.remove(id);
    }
}
