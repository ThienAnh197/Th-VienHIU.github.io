package vn.hiu.staffflow.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import vn.hiu.staffflow.domain.Domain.Shift;

public interface ShiftRepository {
    /** Các ca có ngày trong [from, to] (gồm cả hai đầu). */
    List<Shift> findBetween(LocalDate from, LocalDate to);

    Optional<Shift> findById(long id);

    /** id == 0 nghĩa là thêm mới; trả về bản ghi đã có id. */
    Shift save(Shift shift);

    void delete(long id);

    /**
     * Giống {@link #findBetween}, nhưng dùng ngay trước khi ghi đè ca trực (tạo/sửa) để chống race condition
     * khi hai yêu cầu cùng kiểm tra trùng ca một lúc (mục 2.1, 6.1 của đặc tả).
     * Hồ sơ "memory": mặc định gọi lại {@link #findBetween} (một luồng xử lý một yêu cầu tại một thời điểm nhờ
     * {@code TxLock}, nên không cần khóa thêm ở tầng dữ liệu).
     * Hồ sơ "sqlserver": cài đè bằng {@code SELECT ... WITH (UPDLOCK, HOLDLOCK)} trong transaction thật.
     */
    default List<Shift> findBetweenForUpdate(LocalDate from, LocalDate to) {
        return findBetween(from, to);
    }
}
