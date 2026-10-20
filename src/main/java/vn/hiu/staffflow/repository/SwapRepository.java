package vn.hiu.staffflow.repository;

import java.util.List;
import java.util.Optional;

import vn.hiu.staffflow.domain.Domain.SwapRequest;

public interface SwapRepository {
    SwapRequest save(SwapRequest request);

    Optional<SwapRequest> findById(long id);

    List<SwapRequest> findAll();

    List<SwapRequest> findByShift(long shiftId);

    /**
     * Giống {@link #findById}, nhưng dùng ngay trước khi duyệt/từ chối để chống duyệt trùng hai lần cùng lúc
     * (mục 6.4 của đặc tả). Hồ sơ "memory": mặc định gọi lại {@link #findById}. Hồ sơ "sqlserver": cài đè bằng
     * {@code SELECT ... WITH (UPDLOCK, HOLDLOCK)} trong transaction thật.
     */
    default java.util.Optional<SwapRequest> findByIdForUpdate(long id) {
        return findById(id);
    }
}
