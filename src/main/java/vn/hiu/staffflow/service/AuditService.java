package vn.hiu.staffflow.service;

import java.util.List;

import org.springframework.stereotype.Service;

import vn.hiu.staffflow.config.AppClock;
import vn.hiu.staffflow.domain.Domain.AppUser;
import vn.hiu.staffflow.domain.Domain.AuditLog;
import vn.hiu.staffflow.repository.AuditRepository;

@Service
public class AuditService {
    private final AuditRepository repo;
    private final AppClock clock;

    public AuditService(AuditRepository repo, AppClock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public void log(AppUser actor, String action, String entity, Object entityId, String detail) {
        repo.add(new AuditLog(0, actor.id(), action, entity, String.valueOf(entityId), detail, clock.now()));
    }

    public List<AuditLog> latest(int limit) {
        return repo.latest(limit);
    }
}
