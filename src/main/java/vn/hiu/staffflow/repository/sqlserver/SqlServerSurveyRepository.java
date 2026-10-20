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

import vn.hiu.staffflow.domain.Domain.SurveyPeriod;
import vn.hiu.staffflow.domain.Domain.SurveyResponse;
import vn.hiu.staffflow.repository.SurveyRepository;

/**
 * PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng").
 * save(): "nộp lại ghi đè" (mục 4, SurveyService.submit) được cài bằng UPDATE nếu đã có bản ghi (period,
 * user_id) — khớp ràng buộc UNIQUE (period, user_id) của bảng survey_responses trong schema.sql — nếu chưa
 * có thì INSERT mới, lấy id do tầng service tự tìm trước khi gọi save() (giống InMemorySurveyRepository, nơi
 * SurveyService tự tìm bản ghi cũ theo (period, userId) rồi gán lại id trước khi save()).
 */
@Repository
@Profile("sqlserver")
public class SqlServerSurveyRepository implements SurveyRepository {
    private final JdbcTemplate jdbc;

    public SqlServerSurveyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SurveyResponse save(SurveyResponse r) {
        if (r.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.survey_responses (period, user_id, q1, q2, q3, note, submitted_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, r.period().name());
                ps.setLong(2, r.userId());
                ps.setInt(3, r.q1());
                ps.setInt(4, r.q2());
                ps.setInt(5, r.q3());
                ps.setString(6, r.note());
                ps.setObject(7, r.submittedAt());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            return r.withId(keyHolder.getKey().longValue());
        }
        jdbc.update("UPDATE dbo.survey_responses SET period=?, user_id=?, q1=?, q2=?, q3=?, note=?, "
                        + "submitted_at=? WHERE id=?",
                r.period().name(), r.userId(), r.q1(), r.q2(), r.q3(), r.note(), r.submittedAt(), r.id());
        return r;
    }

    @Override
    public List<SurveyResponse> findAll() {
        return jdbc.query("SELECT id, period, user_id, q1, q2, q3, note, submitted_at "
                + "FROM dbo.survey_responses ORDER BY id", MAPPER);
    }

    private static final RowMapper<SurveyResponse> MAPPER = (rs, i) ->
            new SurveyResponse(rs.getLong("id"), SurveyPeriod.valueOf(rs.getString("period")), rs.getLong("user_id"),
                    rs.getInt("q1"), rs.getInt("q2"), rs.getInt("q3"), rs.getString("note"),
                    rs.getTimestamp("submitted_at").toLocalDateTime());
}
