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
import vn.hiu.staffflow.dto.Requests.SwapDecisionRequest;
import vn.hiu.staffflow.dto.Requests.SwapRequestIn;
import vn.hiu.staffflow.dto.Views.CandidateView;
import vn.hiu.staffflow.dto.Views.SwapView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.SwapService;

@RestController
@RequestMapping("/api/swaps")
public class SwapController {
    private final SwapService swaps;

    public SwapController(SwapService swaps) {
        this.swaps = swaps;
    }

    @GetMapping
    public List<SwapView> list(@RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return swaps.list(actor);
    }

    @PostMapping
    public SwapView create(@Valid @RequestBody SwapRequestIn in,
                           @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return swaps.create(actor, in);
    }

    @GetMapping("/candidates/{shiftId}")
    public List<CandidateView> candidates(@PathVariable long shiftId) {
        return swaps.candidatesForShift(shiftId);
    }

    /** Không đủ quyền -> 403 (kiểm tra ở AccessPolicy, không tin role từ frontend). */
    @PostMapping("/{id}/approve")
    public SwapView approve(@PathVariable long id, @Valid @RequestBody SwapDecisionRequest in,
                            @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return swaps.approve(actor, id, in.note());
    }

    @PostMapping("/{id}/reject")
    public SwapView reject(@PathVariable long id, @Valid @RequestBody SwapDecisionRequest in,
                           @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return swaps.reject(actor, id, in.note());
    }
}
