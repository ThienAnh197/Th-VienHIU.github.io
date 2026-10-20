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

import vn.hiu.staffflow.domain.Domain.CrossTraining;
import vn.hiu.staffflow.domain.Domain.TitleGroup;
import vn.hiu.staffflow.domain.Domain.TrainingStatus;
import vn.hiu.staffflow.repository.CrossTrainingRepository;

/** PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng"). */
@Repository
@Profile("sqlserver")
public class SqlServerCrossTrainingRepository implements CrossTrainingRepository {
    private final JdbcTemplate jdbc;

    public SqlServerCrossTrainingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CrossTraining> findByUser(long userId) {
        return jdbc.query("SELECT id, user_id, can_substitute_for, status FROM dbo.cross_training "
                + "WHERE user_id = ? ORDER BY id", MAPPER, userId);
    }

    @Override
    public List<CrossTraining> findAll() {
        return jdbc.query("SELECT id, user_id, can_substitute_for, status FROM dbo.cross_training "
                + "ORDER BY id", MAPPER);
    }

    @Override
    public CrossTraining save(CrossTraining row) {
        if (row.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.cross_training (user_id, can_substitute_for, status) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, row.userId());
                ps.setString(2, row.canSubstituteFor().name());
                ps.setString(3, row.status().name());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            long newId = keyHolder.getKey().longValue();
            return new CrossTraining(newId, row.userId(), row.canSubstituteFor(), row.status());
        }
        jdbc.update("UPDATE dbo.cross_training SET user_id=?, can_substitute_for=?, status=? WHERE id=?",
                row.userId(), row.canSubstituteFor().name(), row.status().name(), row.id());
        return row;
    }

    private static final RowMapper<CrossTraining> MAPPER = (rs, i) ->
            new CrossTraining(rs.getLong("id"), rs.getLong("user_id"),
                    TitleGroup.valueOf(rs.getString("can_substitute_for")),
                    TrainingStatus.valueOf(rs.getString("status")));
}
