package vn.hiu.staffflow.repository.sqlserver;

import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.Priority;
import vn.hiu.staffflow.domain.Domain.Task;
import vn.hiu.staffflow.domain.Domain.TaskStatus;
import vn.hiu.staffflow.repository.TaskRepository;

/** PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng"). */
@Repository
@Profile("sqlserver")
public class SqlServerTaskRepository implements TaskRepository {
    private static final String COLUMNS = "id, title, template_id, branch_id, assignee_id, estimated_minutes, "
            + "due_date, priority, status, closed_without_evidence, evidence_note, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public SqlServerTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Task save(Task task) {
        if (task.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.tasks (title, template_id, branch_id, assignee_id, estimated_minutes, "
                                + "due_date, priority, status, closed_without_evidence, evidence_note, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, task.title());
                setNullableLong(ps, 2, task.templateId());
                setNullableLong(ps, 3, task.branchId());
                ps.setLong(4, task.assigneeId());
                ps.setInt(5, task.estimatedMinutes());
                ps.setObject(6, task.dueDate());
                ps.setString(7, task.priority().name());
                ps.setString(8, task.status().name());
                ps.setBoolean(9, task.closedWithoutEvidence());
                ps.setString(10, task.evidenceNote());
                ps.setObject(11, task.createdAt());
                ps.setObject(12, task.updatedAt());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            return task.withId(keyHolder.getKey().longValue());
        }
        jdbc.update("UPDATE dbo.tasks SET title=?, template_id=?, branch_id=?, assignee_id=?, estimated_minutes=?, "
                        + "due_date=?, priority=?, status=?, closed_without_evidence=?, evidence_note=?, "
                        + "created_at=?, updated_at=? WHERE id=?",
                task.title(), task.templateId(), task.branchId(), task.assigneeId(), task.estimatedMinutes(),
                task.dueDate(), task.priority().name(), task.status().name(), task.closedWithoutEvidence(),
                task.evidenceNote(), task.createdAt(), task.updatedAt(), task.id());
        return task;
    }

    @Override
    public Optional<Task> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.tasks WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Task> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.tasks ORDER BY id", MAPPER);
    }

    private static void setNullableLong(java.sql.PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static final RowMapper<Task> MAPPER = (rs, i) -> {
        long templateId = rs.getLong("template_id");
        Long template = rs.wasNull() ? null : templateId;
        long branchId = rs.getLong("branch_id");
        Long branch = rs.wasNull() ? null : branchId;
        return new Task(rs.getLong("id"), rs.getString("title"), template, branch, rs.getLong("assignee_id"),
                rs.getInt("estimated_minutes"), rs.getDate("due_date").toLocalDate(),
                Priority.valueOf(rs.getString("priority")), TaskStatus.valueOf(rs.getString("status")),
                rs.getBoolean("closed_without_evidence"), rs.getString("evidence_note"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());
    };
}
