package vn.hiu.staffflow.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.dto.Requests.LoginRequest;
import vn.hiu.staffflow.dto.Views.MeView;
import vn.hiu.staffflow.exception.ApiException;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.security.AuthInterceptor;
import vn.hiu.staffflow.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final OrgRepository org;
    private final BCryptPasswordEncoder encoder;
    private final UserService users;

    public AuthController(OrgRepository org, BCryptPasswordEncoder encoder, UserService users) {
        this.org = org;
        this.encoder = encoder;
        this.users = users;
    }

    @PostMapping("/login")
    public MeView login(@Valid @RequestBody LoginRequest in, HttpServletRequest req) {
        AppUser user = org.userByCode(in.code().trim()).orElse(null);
        if (user == null || !encoder.matches(in.password(), user.passwordHash())) {
            throw ApiException.unauthorized("Sai mã nhân sự hoặc mật khẩu");
        }
        // Đổi session ID khi đăng nhập để chống session fixation
        if (req.getSession(false) != null) {
            req.changeSessionId();
        }
        HttpSession session = req.getSession(true);
        String csrf = UUID.randomUUID().toString();
        session.setAttribute(AuthInterceptor.SESSION_UID, user.id());
        session.setAttribute(AuthInterceptor.SESSION_CSRF, csrf);
        return new MeView(users.view(user), csrf);
    }

    @GetMapping("/me")
    public MeView me(@RequestAttribute(AuthInterceptor.ATTR_USER) AppUser actor, HttpServletRequest req) {
        Object csrf = req.getSession(false).getAttribute(AuthInterceptor.SESSION_CSRF);
        return new MeView(users.view(actor), String.valueOf(csrf));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("ok", true);
    }
}
