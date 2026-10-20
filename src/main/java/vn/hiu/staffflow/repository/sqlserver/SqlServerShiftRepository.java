package vn.hiu.staffflow.repository.sqlserver;

import java.sql.Statement;
import java.time.LocalDate;
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

import vn.hiu.staffflow.domain.Domain.Shift;
import vn.hiu.staffflow.domain.Domain.ShiftStatus;
import vn.hiu.staffflow.repository.ShiftRepository;

/**
 * PHASE 5 - CHƯA KIỂM CHỨNG trên SQL Server thật (môi trường sandbox không có SQL Server/Maven Central,
 * xem README "Trạng thái kiểm chứng"). {@link #findBetweenForUpdate} là nơi thật sự áp dụng
 * {@code WITH (UPDLOCK, HOLDLOCK)} theo yêu cầu "chống race condition khi kiểm tra trùng ca" (mục 2.1, 6.1):
 * khóa các dòng ca trực trong khoảng ngày cho đến khi transaction bao quanh (ở ScheduleService, qua
 * {@code tx.run(...)} + {@code @Transactional} ở JdbcTemplate/DataSourceTransactionManager của Spring) kết
 * thúc, nên hai request tạo/sửa ca cùng lúc không thể cùng đọc "chưa trùng" rồi cùng ghi đè.
 */
@Repository
@Profile("sqlserver")
public class SqlServerShiftRepository implements ShiftRepository {
    private final JdbcTemplate jdbc;

    public SqlServerShiftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Shift> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT id, branch_id, date, start_time, end_time, user_id, status, note, test_data "
                + "FROM dbo.shifts WHERE date BETWEEN ? AND ? "
                + "ORDER BY date, start_time, branch_id", MAPPER, from, to);
    }

    @Override
    @Transactional
    public List<Shift> findBetweenForUpdate(LocalDate from, LocalDate to) {
        // UPDLOCK: giữ khóa ghi ngay từ lúc đọc (không chỉ khóa đọc). HOLDLOCK (tương đương SERIALIZABLE cho
        // câu lệnh này): giữ khóa đến hết transaction thay vì nhả ngay sau khi đọc xong câu lệnh.
        return jdbc.query("SELECT id, branch_id, date, start_time, end_time, user_id, status, note, test_data "
                + "FROM dbo.shifts WITH (UPDLOCK, HOLDLOCK) WHERE date BETWEEN ? AND ? "
                + "ORDER BY date, start_time, branch_id", MAPPER, from, to);
    }

    @Override
    public Optional<Shift> findById(long id) {
        return jdbc.query("SELECT id, branch_id, date, start_time, end_time, user_id, status, note, test_data "
                + "FROM dbo.shifts WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    @Override
    public Shift save(Shift shift) {
        if (shift.id() == 0) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            PreparedStatementCreator psc = con -> {
                var ps = con.prepareStatement(
                        "INSERT INTO dbo.shifts (branch_id, date, start_time, end_time, user_id, status, note, test_data) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, shift.branchId());
                ps.setObject(2, shift.date());
                ps.setObject(3, shift.start());
                ps.setObject(4, shift.end());
                if (shift.userId() == null) {
                    ps.setNull(5, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(5, shift.userId());
                }
                ps.setString(6, shift.status().name());
                ps.setString(7, shift.note());
                ps.setBoolean(8, shift.testData());
                return ps;
            };
            jdbc.update(psc, keyHolder);
            long newId = keyHolder.getKey().longValue();
            return shift.withId(newId);
        }
        jdbc.update("UPDATE dbo.shifts SET branch_id=?, date=?, start_time=?, end_time=?, user_id=?, status=?, "
                        + "note=?, test_data=? WHERE id=?",
                shift.branchId(), shift.date(), shift.start(), shift.end(), shift.userId(), shift.status().name(),
                shift.note(), shift.testData(), shift.id());
        return shift;
    }

    @Override
    public void delete(long id) {
        jdbc.update("DELETE FROM dbo.shifts WHERE id = ?", id);
    }

    private static final RowMapper<Shift> MAPPER = (rs, i) -> {
        long userId = rs.getLong("user_id");
        // wasNull() phản ánh CỘT ĐỌC GẦN NHẤT -> phải chốt ngay sau getLong("user_id"), trước khi đọc cột khác.
        Long userIdOrNull = rs.wasNull() ? null : userId;
        return new Shift(rs.getLong("id"), rs.getLong("branch_id"), rs.getDate("date").toLocalDate(),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                userIdOrNull, ShiftStatus.valueOf(rs.getString("status")),
                rs.getString("note"), rs.getBoolean("test_data"));
    };
}
