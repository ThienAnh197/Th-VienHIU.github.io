package vn.hiu.staffflow.repository.memory;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.repository.TaskRepository;

@Repository
@Profile("memory")
public class InMemoryTaskRepository implements TaskRepository {
    private final MemTable<Task> table = new MemTable<>(Task::id, Task::withId);

    @Override
    public Task save(Task task) {
        return table.save(task);
    }

    @Override
    public Optional<Task> findById(long id) {
        return table.find(id);
    }

    @Override
    public List<Task> findAll() {
        return table.all();
    }
}
