package vn.hiu.staffflow.repository.memory;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.TaskTemplate;
import vn.hiu.staffflow.repository.TaskTemplateRepository;

@Repository
@Profile("memory")
public class InMemoryTaskTemplateRepository implements TaskTemplateRepository {
    private final MemTable<TaskTemplate> table = new MemTable<>(TaskTemplate::id,
            (t, id) -> new TaskTemplate(id, t.ord(), t.category(), t.name(), t.mainGroup(), t.backupGroup(), t.estimatedMinutes()));

    @Override
    public List<TaskTemplate> findAll() {
        return table.all().stream().sorted((a, b) -> Integer.compare(a.ord(), b.ord())).toList();
    }

    @Override
    public TaskTemplate save(TaskTemplate t) {
        return table.save(t);
    }
}
