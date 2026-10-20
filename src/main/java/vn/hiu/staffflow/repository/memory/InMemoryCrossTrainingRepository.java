package vn.hiu.staffflow.repository.memory;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.CrossTraining;
import vn.hiu.staffflow.repository.CrossTrainingRepository;

@Repository
@Profile("memory")
public class InMemoryCrossTrainingRepository implements CrossTrainingRepository {
    private final MemTable<CrossTraining> table =
            new MemTable<>(CrossTraining::id, (r, id) -> new CrossTraining(id, r.userId(), r.canSubstituteFor(), r.status()));

    @Override
    public List<CrossTraining> findByUser(long userId) {
        return table.all().stream().filter(c -> c.userId() == userId).toList();
    }

    @Override
    public List<CrossTraining> findAll() {
        return table.all();
    }

    @Override
    public CrossTraining save(CrossTraining row) {
        return table.save(row);
    }
}
