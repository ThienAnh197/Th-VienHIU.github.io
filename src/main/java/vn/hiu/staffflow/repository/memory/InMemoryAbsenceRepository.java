package vn.hiu.staffflow.repository.memory;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.Absence;
import vn.hiu.staffflow.repository.AbsenceRepository;

@Repository
@Profile("memory")
public class InMemoryAbsenceRepository implements AbsenceRepository {
    private final MemTable<Absence> table = new MemTable<>(Absence::id, Absence::withId);

    @Override
    public Absence save(Absence absence) {
        return table.save(absence);
    }

    @Override
    public List<Absence> findOverlapping(long userId, LocalDate from, LocalDate to) {
        return table.all().stream()
                .filter(a -> a.userId() == userId && !a.from().isAfter(to) && !a.to().isBefore(from))
                .toList();
    }

    @Override
    public List<Absence> findAll() {
        return table.all();
    }
}
