package vn.hiu.staffflow.repository;

import java.util.List;

import vn.hiu.staffflow.domain.Domain.TaskTemplate;

public interface TaskTemplateRepository {
    List<TaskTemplate> findAll();

    TaskTemplate save(TaskTemplate t);
}
