package vn.hiu.staffflow.service;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Khóa ghi trong bộ nhớ (một {@link ReentrantLock} dùng chung cho cả ứng dụng) để "kiểm tra rồi ghi" là một
 * bước không bị xen ngang (chống race condition) — dùng cho cả hai hồ sơ "memory" và "sqlserver".
 *
 * <p><b>Phase 5:</b> ở hồ sơ "sqlserver", khóa này chỉ còn vai trò phòng thủ theo chiều sâu (defense-in-depth)
 * bên trong MỘT instance ứng dụng; nó KHÔNG chống được race condition giữa nhiều instance/nhiều kết nối JDBC
 * cùng ghi vào SQL Server — bảo vệ thật sự ở hồ sơ này đến từ transaction thật của cơ sở dữ liệu với
 * {@code SELECT ... WITH (UPDLOCK, HOLDLOCK)}, xem {@link vn.hiu.staffflow.repository.ShiftRepository#findBetweenForUpdate}
 * và {@link vn.hiu.staffflow.repository.SwapRepository#findByIdForUpdate}, cài đặt bởi
 * {@code repository.sqlserver.SqlServerShiftRepository} và {@code SqlServerSwapRepository}. Ở hồ sơ "memory",
 * khóa trong bộ nhớ này vẫn là lớp bảo vệ duy nhất vì không có cơ sở dữ liệu thật phía sau.</p>
 */
@Component
public class TxLock {
    private final ReentrantLock lock = new ReentrantLock();

    public <T> T run(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
