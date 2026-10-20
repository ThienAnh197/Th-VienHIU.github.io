package vn.hiu.staffflow.repository.memory;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.SurveyResponse;
import vn.hiu.staffflow.repository.SurveyRepository;

@Repository
@Profile("memory")
public class InMemorySurveyRepository implements SurveyRepository {
    private final MemTable<SurveyResponse> table = new MemTable<>(SurveyResponse::id, SurveyResponse::withId);

    @Override
    public SurveyResponse save(SurveyResponse r) {
        return table.save(r);
    }

    @Override
    public List<SurveyResponse> findAll() {
        return table.all();
    }
}
