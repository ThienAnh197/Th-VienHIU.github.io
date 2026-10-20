package vn.hiu.staffflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Requests.SurveyRequest;
import vn.hiu.staffflow.dto.Views.SurveyView;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.SurveyService;

/** K7 - khảo sát hài lòng nhân sự (mục 7, Bảng 22). */
@RestController
@RequestMapping("/api/survey")
public class SurveyController {
    private final SurveyService service;

    public SurveyController(SurveyService service) {
        this.service = service;
    }

    @GetMapping
    public List<SurveyView> list(@RequestParam(required = false) String period) {
        return service.list(period);
    }

    /** Mỗi người chỉ gửi được cho chính mình; nộp lại cùng kỳ sẽ ghi đè. */
    @PostMapping
    public SurveyView submit(@Valid @RequestBody SurveyRequest in,
                             @RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor) {
        return service.submit(actor, in);
    }
}
