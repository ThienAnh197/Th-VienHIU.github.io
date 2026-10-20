package vn.hiu.staffflow.repository.memory;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.ReconciliationCheck;
import vn.hiu.staffflow.repository.ReconciliationRepository;

@Repository
@Profile("memory")
public class InMemoryReconciliationRepository implements ReconciliationRepository {
    private final MemTable<ReconciliationCheck> table = new MemTable<>(ReconciliationCheck::id, ReconciliationCheck::withId);

    @Override
    public ReconciliationCheck save(ReconciliationCheck r) {
        return table.save(r);
    }

    @Override
    public Optional<ReconciliationCheck> findById(long id) {
        return table.find(id);
    }

    @Override
    public List<ReconciliationCheck> findAll() {
        return table.all();
    }
}
