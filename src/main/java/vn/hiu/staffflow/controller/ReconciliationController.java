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
import vn.hiu.staffflow.dto.Requests.ReconciliationCreateRequest;
import vn.hiu.staffflow.dto.Requests.ReconciliationItemRequest;
import vn.hiu.staffflow.dto.Views.ReconciliationView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.ReconciliationService;

/** Đối soát hằng tuần (mục 6.6): mẫu ngẫu nhiên 10% ca + danh sách nhiệm vụ đóng không có minh chứng. */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReconciliationView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                         @RequestParam(required = false) Long branchId,
                                         @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.list(week, branchId, actor);
    }

    @PostMapping
    public ReconciliationView create(@Valid @RequestBody ReconciliationCreateRequest in,
                                     @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.create(actor, in);
    }

    @PutMapping("/{id}/items/{shiftId}")
    public ReconciliationView recordItem(@PathVariable long id, @PathVariable long shiftId,
                                         @Valid @RequestBody ReconciliationItemRequest in,
                                         @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.recordItem(actor, id, shiftId, in);
    }
}
