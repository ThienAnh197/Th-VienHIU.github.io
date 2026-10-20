package vn.hiu.staffflow.repository.sqlserver;

import java.sql.Statement;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.TaskCategory;
import vn.hiu.staffflow.domain.Domain.TaskTemplate;
import vn.hiu.staffflow.domain.Domain.TitleGroup;
import vn.hiu.staffflow.repository.TaskTemplateRepository;

/** PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng"). */
@Repository
@Profile("sqlserver")
public class SqlServerTaskTemplateRepository implements TaskTemplateRepository {
    private final JdbcTemplate jdbc;

    public SqlServerTaskTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TaskTemplate> findAll() {
        return jdbc.query("SELECT id, ord, category, name, main_group, backup_group, estimated_minutes "
                + "FROM dbo.task_templates ORDER BY ord", MAPPER);
    }

    @Override
    public TaskTemplate save(TaskTemplate t) {
        if (t.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.task_templates (ord, category, name, main_group, backup_group, estimated_minutes) "
                                + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, t.ord());
                ps.setString(2, t.category().name());
                ps.setString(3, t.name());
                ps.setString(4, t.mainGroup().name());
                ps.setString(5, t.backupGroup() == null ? null : t.backupGroup().name());
                ps.setInt(6, t.estimatedMinutes());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            long newId = keyHolder.getKey().longValue();
            return new TaskTemplate(newId, t.ord(), t.category(), t.name(), t.mainGroup(), t.backupGroup(),
                    t.estimatedMinutes());
        }
        jdbc.update("UPDATE dbo.task_templates SET ord=?, category=?, name=?, main_group=?, backup_group=?, "
                        + "estimated_minutes=? WHERE id=?",
                t.ord(), t.category().name(), t.name(), t.mainGroup().name(),
                t.backupGroup() == null ? null : t.backupGroup().name(), t.estimatedMinutes(), t.id());
        return t;
    }

    private static final RowMapper<TaskTemplate> MAPPER = (rs, i) -> {
        String backup = rs.getString("backup_group");
        return new TaskTemplate(rs.getLong("id"), rs.getInt("ord"), TaskCategory.valueOf(rs.getString("category")),
                rs.getString("name"), TitleGroup.valueOf(rs.getString("main_group")),
                backup == null ? null : TitleGroup.valueOf(backup), rs.getInt("estimated_minutes"));
    };
}
