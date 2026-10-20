package vn.hiu.staffflow.config;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Đồng hồ ứng dụng. Có thể cố định thời điểm demo bằng staffflow.demo-now. */
@Component
public class AppClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LocalDateTime fixed;

    public AppClock(@Value("${staffflow.demo-now:}") String demoNow) {
        this.fixed = (demoNow == null || demoNow.isBlank()) ? null : LocalDateTime.parse(demoNow.trim());
    }

    public LocalDateTime now() {
        return fixed != null ? fixed : LocalDateTime.now(ZONE);
    }

    public boolean isFixed() {
        return fixed != null;
    }
}
