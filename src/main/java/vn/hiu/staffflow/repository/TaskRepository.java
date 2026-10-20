package vn.hiu.staffflow.repository;

import java.util.List;
import java.util.Optional;

import vn.hiu.staffflow.domain.Domain.Task;

public interface TaskRepository {
    Task save(Task task);

    Optional<Task> findById(long id);

    List<Task> findAll();
}
