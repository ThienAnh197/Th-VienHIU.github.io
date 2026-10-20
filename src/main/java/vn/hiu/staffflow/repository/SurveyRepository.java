package vn.hiu.staffflow.repository;

import java.util.List;

import vn.hiu.staffflow.domain.Domain.SurveyResponse;

public interface SurveyRepository {
    SurveyResponse save(SurveyResponse r);

    List<SurveyResponse> findAll();
}
