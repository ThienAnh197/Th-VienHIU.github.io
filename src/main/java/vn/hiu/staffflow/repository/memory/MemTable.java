package vn.hiu.staffflow.repository.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

/** "Bảng" trong bộ nhớ: tự tăng id như IDENTITY(1,1) của SQL Server. */
final class MemTable<T> {
    private final Map<Long, T> rows = new LinkedHashMap<>();
    private final ToLongFunction<T> idOf;
    private final BiFunction<T, Long, T> withId;
    private long seq = 0;

    MemTable(ToLongFunction<T> idOf, BiFunction<T, Long, T> withId) {
        this.idOf = idOf;
        this.withId = withId;
    }

    synchronized T save(T row) {
        long id = idOf.applyAsLong(row);
        if (id == 0) {
            id = ++seq;
            row = withId.apply(row, id);
        } else if (id > seq) {
            seq = id;
        }
        rows.put(id, row);
        return row;
    }

    synchronized Optional<T> find(long id) {
        return Optional.ofNullable(rows.get(id));
    }

    synchronized List<T> all() {
        return new ArrayList<>(rows.values());
    }

    synchronized void remove(long id) {
        rows.remove(id);
    }
}
