package vn.hiu.staffflow.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.dto.Fmt;
import vn.hiu.staffflow.dto.Views.UserView;
import vn.hiu.staffflow.repository.OrgRepository;
import vn.hiu.staffflow.service.UserService;
import vn.hiu.staffflow.service.WeekUtil;

@RestController
@RequestMapping("/api")
public class MetaController {
    private final OrgRepository org;
    private final UserService users;
    private final AppClock clock;
    private final LocalDate demoWeek;

    public MetaController(OrgRepository org, UserService users, AppClock clock,
                          @Value("${staffflow.demo-week-start:2026-10-19}") String demoWeek) {
        this.org = org;
        this.users = users;
        this.clock = clock;
        this.demoWeek = LocalDate.parse(demoWeek);
    }

    @GetMapping("/branches")
    public List<Branch> branches() {
        return org.branches();
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return users.list();
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("banner", "DỮ LIỆU GIẢ ĐỊNH PHỤC VỤ PHÂN TÍCH – không phải dữ liệu thật của thư viện");
        m.put("principle", "StaffFlow dùng để điều phối và làm rõ khối lượng việc, không dùng để chấm điểm cá nhân");
        m.put("now", Fmt.dt(clock.now()));
        m.put("clockFixed", clock.isFixed());
        m.put("defaultWeek", Fmt.d(clock.isFixed() ? demoWeek : WeekUtil.mondayOf(clock.now().toLocalDate())));
        m.put("branches", org.branches());
        m.put("users", users.list());
        return m;
    }
}
