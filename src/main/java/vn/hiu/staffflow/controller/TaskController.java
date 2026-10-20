package vn.hiu.staffflow.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.dto.Requests.TaskRequest;
import vn.hiu.staffflow.dto.Requests.TaskStatusRequest;
import vn.hiu.staffflow.dto.Views.TaskEventView;
import vn.hiu.staffflow.dto.Views.TaskTemplateView;
import vn.hiu.staffflow.dto.Views.TaskView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.TaskService;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/task-templates")
    public List<TaskTemplateView> templates() {
        return tasks.listTemplates();
    }

    @GetMapping("/tasks")
    public List<TaskView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                               @RequestParam(required = false) Long branchId,
                               @RequestParam(required = false) Long assigneeId,
                               @RequestParam(required = false) String status,
                               @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        TaskStatus st = status == null || status.isBlank() ? null : TaskStatus.valueOf(status);
        return tasks.list(actor, week, branchId, assigneeId, st);
    }

    @GetMapping("/tasks/{id}/history")
    public List<TaskEventView> history(@PathVariable long id) {
        return tasks.history(id);
    }

    @PostMapping("/tasks")
    public TaskView create(@Valid @RequestBody TaskRequest in,
                           @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return tasks.create(actor, in);
    }

    @PutMapping("/tasks/{id}")
    public TaskView update(@PathVariable long id, @Valid @RequestBody TaskRequest in,
                           @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return tasks.update(actor, id, in);
    }

    @PostMapping("/tasks/{id}/status")
    public TaskView changeStatus(@PathVariable long id, @Valid @RequestBody TaskStatusRequest in,
                                 @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return tasks.changeStatus(actor, id, in);
    }
}
