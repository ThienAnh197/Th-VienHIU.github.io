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

import vn.hiu.staffflow.domain.Domain.TaskEvent;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.repository.TaskEventRepository;

/** PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng"). */
@Repository
@Profile("sqlserver")
public class SqlServerTaskEventRepository implements TaskEventRepository {
    private final JdbcTemplate jdbc;

    public SqlServerTaskEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TaskEvent add(TaskEvent event) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator psc = con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO dbo.task_events (task_id, from_status, to_status, by_user_id, at) "
                            + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, event.taskId());
            ps.setString(2, event.from() == null ? null : event.from().name());
            ps.setString(3, event.to().name());
            ps.setLong(4, event.byUserId());
            ps.setObject(5, event.at());
            return ps;
        };
        jdbc.update(psc, keyHolder);
        return event.withId(keyHolder.getKey().longValue());
    }

    @Override
    public List<TaskEvent> findByTask(long taskId) {
        return jdbc.query("SELECT id, task_id, from_status, to_status, by_user_id, at FROM dbo.task_events "
                + "WHERE task_id = ? ORDER BY at, id", MAPPER, taskId);
    }

    @Override
    public List<TaskEvent> findAll() {
        return jdbc.query("SELECT id, task_id, from_status, to_status, by_user_id, at FROM dbo.task_events "
                + "ORDER BY at, id", MAPPER);
    }

    private static final RowMapper<TaskEvent> MAPPER = (rs, i) -> {
        String from = rs.getString("from_status");
        return new TaskEvent(rs.getLong("id"), rs.getLong("task_id"), from == null ? null : TaskStatus.valueOf(from),
                TaskStatus.valueOf(rs.getString("to_status")), rs.getLong("by_user_id"),
                rs.getTimestamp("at").toLocalDateTime());
    };
}
