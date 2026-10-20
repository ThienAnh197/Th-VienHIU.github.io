package vn.hiu.staffflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Requests.AbsenceRequest;
import vn.hiu.staffflow.dto.Views.AbsenceView;
import vn.hiu.staffflow.dto.Views.CandidateView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.AbsenceService;

@RestController
@RequestMapping("/api/absences")
public class AbsenceController {
    private final AbsenceService absences;

    public AbsenceController(AbsenceService absences) {
        this.absences = absences;
    }

    @GetMapping
    public List<AbsenceView> list() {
        return absences.list();
    }

    @PostMapping
    public AbsenceView report(@Valid @RequestBody AbsenceRequest in,
                              @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return absences.report(actor, in);
    }

    /** Gợi ý người thay cho một ca cụ thể (dùng chung cho màn hình báo vắng và đổi ca). */
    @GetMapping("/candidates/{shiftId}")
    public List<CandidateView> candidates(@PathVariable long shiftId) {
        return absences.candidatesFor(shiftId);
    }
}
