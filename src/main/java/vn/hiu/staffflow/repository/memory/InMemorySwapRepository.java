package vn.hiu.staffflow.repository.memory;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.hiu.staffflow.domain.Domain.SwapRequest;
import vn.hiu.staffflow.repository.SwapRepository;

@Repository
@Profile("memory")
public class InMemorySwapRepository implements SwapRepository {
    private final MemTable<SwapRequest> table = new MemTable<>(SwapRequest::id, SwapRequest::withId);

    @Override
    public SwapRequest save(SwapRequest request) {
        return table.save(request);
    }

    @Override
    public Optional<SwapRequest> findById(long id) {
        return table.find(id);
    }

    @Override
    public List<SwapRequest> findAll() {
        return table.all();
    }

    @Override
    public List<SwapRequest> findByShift(long shiftId) {
        return table.all().stream().filter(r -> r.shiftId() == shiftId).toList();
    }
}
