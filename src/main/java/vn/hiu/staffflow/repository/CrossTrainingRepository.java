package vn.hiu.staffflow.repository;

import java.util.List;

import vn.hiu.staffflow.domain.Domain.CrossTraining;

public interface CrossTrainingRepository {
    List<CrossTraining> findByUser(long userId);

    List<CrossTraining> findAll();

    CrossTraining save(CrossTraining row);
}
