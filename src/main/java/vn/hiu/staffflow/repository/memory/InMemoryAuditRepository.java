package vn.hiu.staffflow.repository.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.AuditLog;
import vn.hiu.staffflow.repository.AuditRepository;

@Repository
@Profile("memory")
public class InMemoryAuditRepository implements AuditRepository {
    private final MemTable<AuditLog> table = new MemTable<>(AuditLog::id, AuditLog::withId);

    @Override
    public AuditLog add(AuditLog log) {
        return table.save(log);
    }

    @Override
    public List<AuditLog> latest(int limit) {
        List<AuditLog> all = table.all();
        Collections.reverse(all);
        return new ArrayList<>(all.subList(0, Math.min(limit, all.size())));
    }
}
