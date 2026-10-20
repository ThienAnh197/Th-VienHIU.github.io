package vn.hiu.staffflow.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Requests.ShiftRequest;
import vn.hiu.staffflow.dto.Views.ShiftView;
import vn.hiu.staffflow.dto.Views.WeekView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.ScheduleService;

@RestController
@RequestMapping("/api")
public class ScheduleController {
    private final ScheduleService schedule;
    private final AppClock clock;

    public ScheduleController(ScheduleService schedule, AppClock clock) {
        this.schedule = schedule;
        this.clock = clock;
    }

    @GetMapping("/schedule")
    public WeekView week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                         @RequestParam(required = false) Long branchId,
                         @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        LocalDate day = week != null ? week : clock.now().toLocalDate();
        return schedule.week(day, branchId, actor);
    }

    @PostMapping("/shifts/validate")
    public Map<String, Object> validate(@Valid @RequestBody ShiftRequest in) {
        List<String> errors = schedule.validate(in, null);
        return Map.of("valid", errors.isEmpty(), "errors", errors);
    }

    @PostMapping("/shifts")
    public ShiftView create(@Valid @RequestBody ShiftRequest in,
                            @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return schedule.create(actor, in);
    }

    @PutMapping("/shifts/{id}")
    public ShiftView update(@PathVariable long id, @Valid @RequestBody ShiftRequest in,
                            @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return schedule.update(actor, id, in);
    }
}
