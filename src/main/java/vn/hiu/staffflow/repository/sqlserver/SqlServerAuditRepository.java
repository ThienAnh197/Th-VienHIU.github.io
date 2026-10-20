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

import vn.hiu.staffflow.domain.Domain.AuditLog;
import vn.hiu.staffflow.repository.AuditRepository;

/** PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng"). */
@Repository
@Profile("sqlserver")
public class SqlServerAuditRepository implements AuditRepository {
    private final JdbcTemplate jdbc;

    public SqlServerAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditLog add(AuditLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator psc = con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO dbo.audit_logs (user_id, action, entity, entity_id, detail, at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, log.userId());
            ps.setString(2, log.action());
            ps.setString(3, log.entity());
            ps.setString(4, log.entityId());
            ps.setString(5, log.detail());
            ps.setObject(6, log.at());
            return ps;
        };
        jdbc.update(psc, keyHolder);
        return log.withId(keyHolder.getKey().longValue());
    }

    @Override
    public List<AuditLog> latest(int limit) {
        return jdbc.query("SELECT TOP (?) id, user_id, action, entity, entity_id, detail, at "
                + "FROM dbo.audit_logs ORDER BY at DESC, id DESC", MAPPER, limit);
    }

    private static final RowMapper<AuditLog> MAPPER = (rs, i) ->
            new AuditLog(rs.getLong("id"), rs.getLong("user_id"), rs.getString("action"), rs.getString("entity"),
                    rs.getString("entity_id"), rs.getString("detail"), rs.getTimestamp("at").toLocalDateTime());
}
