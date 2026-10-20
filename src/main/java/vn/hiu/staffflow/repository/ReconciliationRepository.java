package vn.hiu.staffflow.repository;

import java.util.List;
import java.util.Optional;

import vn.hiu.staffflow.domain.Domain.ReconciliationCheck;

public interface ReconciliationRepository {
    ReconciliationCheck save(ReconciliationCheck r);

    Optional<ReconciliationCheck> findById(long id);

    List<ReconciliationCheck> findAll();
}
