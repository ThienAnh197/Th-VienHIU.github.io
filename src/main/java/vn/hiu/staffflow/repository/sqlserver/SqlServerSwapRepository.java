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
import org.springframework.transaction.annotation.Transactional;

import vn.hiu.staffflow.domain.Domain.SwapRequest;
import vn.hiu.staffflow.domain.Domain.SwapStatus;
import vn.hiu.staffflow.repository.SwapRepository;

/**
 * PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng").
 * {@link #findByIdForUpdate} áp dụng {@code WITH (UPDLOCK, HOLDLOCK)} để chống duyệt một yêu cầu đổi ca hai
 * lần cùng lúc (mục 6.4): khóa dòng cho đến khi transaction của SwapService.decide(...) ghi quyết định và
 * commit.
 */
@Repository
@Profile("sqlserver")
public class SqlServerSwapRepository implements SwapRepository {
    private static final String COLUMNS = "id, shift_id, requester_id, proposed_user_id, reason, status, "
            + "submitted_at, created_by_manager, approver_id, decided_at, decision_note";

    private final JdbcTemplate jdbc;

    public SqlServerSwapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SwapRequest save(SwapRequest request) {
        if (request.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.shift_swap_requests (shift_id, requester_id, proposed_user_id, reason, "
                                + "status, submitted_at, created_by_manager, approver_id, decided_at, decision_note) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, request.shiftId());
                ps.setLong(2, request.requesterId());
                setNullableLong(ps, 3, request.proposedUserId());
                ps.setString(4, request.reason());
                ps.setString(5, request.status().name());
                ps.setObject(6, request.submittedAt());
                ps.setBoolean(7, request.createdByManager());
                setNullableLong(ps, 8, request.approverId());
                ps.setObject(9, request.decidedAt());
                ps.setString(10, request.decisionNote());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            return request.withId(keyHolder.getKey().longValue());
        }
        jdbc.update("UPDATE dbo.shift_swap_requests SET shift_id=?, requester_id=?, proposed_user_id=?, reason=?, "
                        + "status=?, submitted_at=?, created_by_manager=?, approver_id=?, decided_at=?, decision_note=? "
                        + "WHERE id=?",
                request.shiftId(), request.requesterId(), request.proposedUserId(), request.reason(),
                request.status().name(), request.submittedAt(), request.createdByManager(), request.approverId(),
                request.decidedAt(), request.decisionNote(), request.id());
        return request;
    }

    @Override
    public Optional<SwapRequest> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.shift_swap_requests WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<SwapRequest> findByIdForUpdate(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.shift_swap_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = ?",
                MAPPER, id).stream().findFirst();
    }

    @Override
    public List<SwapRequest> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.shift_swap_requests ORDER BY id", MAPPER);
    }

    @Override
    public List<SwapRequest> findByShift(long shiftId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM dbo.shift_swap_requests WHERE shift_id = ? ORDER BY id",
                MAPPER, shiftId);
    }

    private static void setNullableLong(java.sql.PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static final RowMapper<SwapRequest> MAPPER = (rs, i) -> {
        long proposedUserId = rs.getLong("proposed_user_id");
        Long proposed = rs.wasNull() ? null : proposedUserId;
        long approverId = rs.getLong("approver_id");
        Long approver = rs.wasNull() ? null : approverId;
        java.sql.Timestamp decidedTs = rs.getTimestamp("decided_at");
        return new SwapRequest(rs.getLong("id"), rs.getLong("shift_id"), rs.getLong("requester_id"), proposed,
                rs.getString("reason"), SwapStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("submitted_at").toLocalDateTime(), rs.getBoolean("created_by_manager"), approver,
                decidedTs == null ? null : decidedTs.toLocalDateTime(), rs.getString("decision_note"));
    };
}
