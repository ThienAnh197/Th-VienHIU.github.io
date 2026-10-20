package vn.hiu.staffflow.repository;

import java.util.List;

import vn.hiu.staffflow.domain.Domain.TaskEvent;

public interface TaskEventRepository {
    TaskEvent add(TaskEvent event);

    List<TaskEvent> findByTask(long taskId);

    List<TaskEvent> findAll();
}
