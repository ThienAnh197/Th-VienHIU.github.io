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

import vn.hiu.staffflow.domain.Domain.ReconciliationCheck;
import vn.hiu.staffflow.domain.Domain.ReconciliationItem;
import vn.hiu.staffflow.repository.ReconciliationRepository;

/**
 * PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (xem README "Trạng thái kiểm chứng").
 * Một {@code ReconciliationCheck} trải trên 3 bảng (reconciliation_checks, _items, _flagged_tasks), nên
 * save() được bọc {@code @Transactional}: xóa hết dòng con cũ của check rồi chèn lại danh sách hiện tại —
 * đơn giản và đúng cho cả hai trường hợp gọi save() của ReconciliationService (create: id=0, items/flagged
 * đầy đủ; recordItem: id đã có, chỉ items đổi) mà không cần phân biệt UPDATE từng dòng.
 */
@Repository
@Profile("sqlserver")
public class SqlServerReconciliationRepository implements ReconciliationRepository {
    private final JdbcTemplate jdbc;

    public SqlServerReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ReconciliationCheck save(ReconciliationCheck r) {
        long checkId = r.id();
        if (checkId == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.reconciliation_checks (week_start, branch_id, created_by, created_at) "
                                + "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setObject(1, r.weekStart());
                if (r.branchId() == null) {
                    ps.setNull(2, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(2, r.branchId());
                }
                ps.setLong(3, r.createdBy());
                ps.setObject(4, r.createdAt());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            checkId = keyHolder.getKey().longValue();
        } else {
            jdbc.update("UPDATE dbo.reconciliation_checks SET week_start=?, branch_id=?, created_by=?, created_at=? "
                            + "WHERE id=?",
                    r.weekStart(), r.branchId(), r.createdBy(), r.createdAt(), checkId);
        }

        jdbc.update("DELETE FROM dbo.reconciliation_items WHERE check_id = ?", checkId);
        for (ReconciliationItem it : r.items()) {
            jdbc.update("INSERT INTO dbo.reconciliation_items (check_id, shift_id, matched, note) VALUES (?, ?, ?, ?)",
                    checkId, it.shiftId(), it.matched(), it.note());
        }

        jdbc.update("DELETE FROM dbo.reconciliation_flagged_tasks WHERE check_id = ?", checkId);
        for (Long taskId : r.flaggedTaskIds()) {
            jdbc.update("INSERT INTO dbo.reconciliation_flagged_tasks (check_id, task_id) VALUES (?, ?)",
                    checkId, taskId);
        }

        return r.withId(checkId);
    }

    @Override
    public Optional<ReconciliationCheck> findById(long id) {
        return query("WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<ReconciliationCheck> findAll() {
        return query("");
    }

    private List<ReconciliationCheck> query(String where, Object... args) {
        List<ReconciliationCheck> heads = jdbc.query(
                "SELECT id, week_start, branch_id, created_by, created_at FROM dbo.reconciliation_checks "
                        + where + " ORDER BY id", HEAD, args);
        return heads.stream().map(h -> {
            List<ReconciliationItem> items = jdbc.query(
                    "SELECT shift_id, matched, note FROM dbo.reconciliation_items WHERE check_id = ? ORDER BY shift_id",
                    (rs, i) -> new ReconciliationItem(rs.getLong("shift_id"),
                            (Boolean) rs.getObject("matched"), rs.getString("note")),
                    h.id());
            List<Long> flagged = jdbc.query(
                    "SELECT task_id FROM dbo.reconciliation_flagged_tasks WHERE check_id = ? ORDER BY task_id",
                    (rs, i) -> rs.getLong("task_id"), h.id());
            return new ReconciliationCheck(h.id(), h.weekStart(), h.branchId(), h.createdBy(), h.createdAt(),
                    items, flagged);
        }).toList();
    }

    private static final RowMapper<ReconciliationCheck> HEAD = (rs, i) -> {
        long branchId = rs.getLong("branch_id");
        Long branch = rs.wasNull() ? null : branchId;
        return new ReconciliationCheck(rs.getLong("id"), rs.getDate("week_start").toLocalDate(), branch,
                rs.getLong("created_by"), rs.getTimestamp("created_at").toLocalDateTime(), List.of(), List.of());
    };
}
