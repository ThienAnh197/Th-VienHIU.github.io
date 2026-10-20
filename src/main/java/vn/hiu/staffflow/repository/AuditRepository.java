package vn.hiu.staffflow.repository;

import java.util.List;

import vn.hiu.staffflow.domain.Domain.AuditLog;

public interface AuditRepository {
    AuditLog add(AuditLog log);

    /** Mới nhất trước. */
    List<AuditLog> latest(int limit);
}
