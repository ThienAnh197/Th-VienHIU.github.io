package vn.hiu.staffflow.repository.memory;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.TaskEvent;
import vn.hiu.staffflow.repository.TaskEventRepository;

@Repository
@Profile("memory")
public class InMemoryTaskEventRepository implements TaskEventRepository {
    private final MemTable<TaskEvent> table = new MemTable<>(TaskEvent::id, TaskEvent::withId);

    @Override
    public TaskEvent add(TaskEvent event) {
        return table.save(event);
    }

    @Override
    public List<TaskEvent> findByTask(long taskId) {
        return table.all().stream().filter(e -> e.taskId() == taskId).toList();
    }

    @Override
    public List<TaskEvent> findAll() {
        return table.all();
    }
}
