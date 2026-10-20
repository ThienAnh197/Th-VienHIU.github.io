package vn.hiu.staffflow.repository.sqlserver;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.Branch;
import vn.hiu.staffflow.domain.Domain.Holiday;
import vn.hiu.staffflow.domain.Domain.Role;
import vn.hiu.staffflow.domain.Domain.TitleGroup;
import vn.hiu.staffflow.repository.OrgRepository;

/**
 * PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (môi trường sandbox không có SQL Server/Maven Central,
 * xem README "Trạng thái kiểm chứng"). Chỉ đọc danh mục: cơ sở, nhân sự, ngày đóng cửa — dữ liệu nạp sẵn
 * bằng database/seed.sql, không có thao tác ghi nào ở Phase 5 cho các bảng này.
 */
@Repository
@Profile("sqlserver")
public class SqlServerOrgRepository implements OrgRepository {
    private final JdbcTemplate jdbc;

    public SqlServerOrgRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Branch> branches() {
        return jdbc.query("SELECT id, code, name, address, note FROM dbo.branches ORDER BY id", BRANCH);
    }

    @Override
    public Optional<Branch> branch(long id) {
        return jdbc.query("SELECT id, code, name, address, note FROM dbo.branches WHERE id = ?", BRANCH, id)
                .stream().findFirst();
    }

    @Override
    public List<AppUser> users() {
        return jdbc.query("SELECT id, code, display_name, title_group, role, branch_id, password_hash "
                + "FROM dbo.users ORDER BY id", USER);
    }

    @Override
    public Optional<AppUser> user(long id) {
        return jdbc.query("SELECT id, code, display_name, title_group, role, branch_id, password_hash "
                + "FROM dbo.users WHERE id = ?", USER, id).stream().findFirst();
    }

    @Override
    public Optional<AppUser> userByCode(String code) {
        return jdbc.query("SELECT id, code, display_name, title_group, role, branch_id, password_hash "
                + "FROM dbo.users WHERE code = ?", USER, code).stream().findFirst();
    }

    @Override
    public List<Holiday> holidays() {
        return jdbc.query("SELECT id, branch_id, date, note FROM dbo.branch_holidays ORDER BY date", HOLIDAY);
    }

    private static final org.springframework.jdbc.core.RowMapper<Branch> BRANCH = (rs, i) ->
            new Branch(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("address"), rs.getString("note"));

    private static final org.springframework.jdbc.core.RowMapper<AppUser> USER = (rs, i) ->
            new AppUser(rs.getLong("id"), rs.getString("code"), rs.getString("display_name"),
                    TitleGroup.valueOf(rs.getString("title_group")), Role.valueOf(rs.getString("role")),
                    rs.getLong("branch_id"), rs.getString("password_hash"));

    private static final org.springframework.jdbc.core.RowMapper<Holiday> HOLIDAY = (rs, i) -> {
        long branchId = rs.getLong("branch_id");
        return new Holiday(rs.getLong("id"), rs.wasNull() ? null : branchId,
                rs.getDate("date").toLocalDate(), rs.getString("note"));
    };
}
