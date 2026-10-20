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

import vn.hiu.staffflow.domain.Domain.MonthlyReport;
import vn.hiu.staffflow.repository.MonthlyReportRepository;

/**
 * PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng").
 * save(): "nộp lại ghi đè" theo year_month, giống SqlServerSurveyRepository — khớp ràng buộc UNIQUE
 * (year_month) của bảng monthly_reports.
 */
@Repository
@Profile("sqlserver")
public class SqlServerMonthlyReportRepository implements MonthlyReportRepository {
    private final JdbcTemplate jdbc;

    public SqlServerMonthlyReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MonthlyReport save(MonthlyReport r) {
        if (r.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.monthly_reports (year_month, due_date, submitted_date, note, "
                                + "recorded_by, recorded_at) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, r.yearMonth());
                ps.setObject(2, r.dueDate());
                ps.setObject(3, r.submittedDate());
                ps.setString(4, r.note());
                ps.setLong(5, r.recordedBy());
                ps.setObject(6, r.recordedAt());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            return r.withId(keyHolder.getKey().longValue());
        }
        jdbc.update("UPDATE dbo.monthly_reports SET year_month=?, due_date=?, submitted_date=?, note=?, "
                        + "recorded_by=?, recorded_at=? WHERE id=?",
                r.yearMonth(), r.dueDate(), r.submittedDate(), r.note(), r.recordedBy(), r.recordedAt(), r.id());
        return r;
    }

    @Override
    public List<MonthlyReport> findAll() {
        return jdbc.query("SELECT id, year_month, due_date, submitted_date, note, recorded_by, recorded_at "
                + "FROM dbo.monthly_reports ORDER BY year_month", MAPPER);
    }

    private static final RowMapper<MonthlyReport> MAPPER = (rs, i) -> {
        java.sql.Date submitted = rs.getDate("submitted_date");
        return new MonthlyReport(rs.getLong("id"), rs.getString("year_month"), rs.getDate("due_date").toLocalDate(),
                submitted == null ? null : submitted.toLocalDate(), rs.getString("note"), rs.getLong("recorded_by"),
                rs.getTimestamp("recorded_at").toLocalDateTime());
    };
}
