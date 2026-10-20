package vn.hiu.staffflow.repository.sqlserver;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.Absence;
import vn.hiu.staffflow.repository.AbsenceRepository;

/** PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng"). */
@Repository
@Profile("sqlserver")
public class SqlServerAbsenceRepository implements AbsenceRepository {
    private final JdbcTemplate jdbc;

    public SqlServerAbsenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Absence save(Absence absence) {
        if (absence.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.absence_records (user_id, from_date, to_date, reason, reported_by, reported_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, absence.userId());
                ps.setObject(2, absence.from());
                ps.setObject(3, absence.to());
                ps.setString(4, absence.reason());
                ps.setLong(5, absence.reportedBy());
                ps.setObject(6, absence.reportedAt());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            return absence.withId(keyHolder.getKey().longValue());
        }
        jdbc.update("UPDATE dbo.absence_records SET user_id=?, from_date=?, to_date=?, reason=?, "
                        + "reported_by=?, reported_at=? WHERE id=?",
                absence.userId(), absence.from(), absence.to(), absence.reason(), absence.reportedBy(),
                absence.reportedAt(), absence.id());
        return absence;
    }

    @Override
    public List<Absence> findOverlapping(long userId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT id, user_id, from_date, to_date, reason, reported_by, reported_at "
                        + "FROM dbo.absence_records WHERE user_id = ? AND from_date <= ? AND to_date >= ?",
                MAPPER, userId, to, from);
    }

    @Override
    public List<Absence> findAll() {
        return jdbc.query("SELECT id, user_id, from_date, to_date, reason, reported_by, reported_at "
                + "FROM dbo.absence_records ORDER BY id", MAPPER);
    }

    private static final RowMapper<Absence> MAPPER = (rs, i) ->
            new Absence(rs.getLong("id"), rs.getLong("user_id"), rs.getDate("from_date").toLocalDate(),
                    rs.getDate("to_date").toLocalDate(), rs.getString("reason"), rs.getLong("reported_by"),
                    rs.getTimestamp("reported_at").toLocalDateTime());
}
