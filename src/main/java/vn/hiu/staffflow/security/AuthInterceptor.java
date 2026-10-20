package vn.hiu.staffflow.security;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.repository.OrgRepository;

/**
 * Bảo vệ mọi /api/** (trừ đăng nhập):
 * - 401 nếu chưa đăng nhập (session HTTP);
 * - 403 nếu request thay đổi dữ liệu (POST/PUT/PATCH/DELETE) thiếu hoặc sai header X-CSRF-Token.
 * Người dùng hiện tại được lấy từ session phía server, không tin dữ liệu từ frontend.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String SESSION_UID = "uid";
    public static final String SESSION_CSRF = "csrf";
    public static final String ATTR_USER = "currentUser";

    private final OrgRepository org;

    public AuthInterceptor(OrgRepository org) {
        this.org = org;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        HttpSession session = req.getSession(false);
        Object uid = session == null ? null : session.getAttribute(SESSION_UID);
        AppUser user = uid instanceof Long id ? org.user(id).orElse(null) : null;
        if (user == null) {
            return deny(res, 401, "Chưa đăng nhập hoặc phiên đã hết hạn");
        }
        String method = req.getMethod();
        boolean safe = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
        if (!safe) {
            String token = req.getHeader("X-CSRF-Token");
            Object expected = session.getAttribute(SESSION_CSRF);
            if (token == null || expected == null || !token.equals(expected.toString())) {
                return deny(res, 403, "Thiếu hoặc sai CSRF token");
            }
        }
        req.setAttribute(ATTR_USER, user);
        return true;
    }

    private boolean deny(HttpServletResponse res, int status, String message) throws Exception {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        String json = "{\"status\":" + status + ",\"error\":\"" + message + "\",\"details\":[]}";
        res.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
