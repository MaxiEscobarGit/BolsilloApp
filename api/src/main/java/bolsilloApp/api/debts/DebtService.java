package bolsilloApp.api.debts;

import bolsilloApp.api.debts.dto.CreateDebtRequest;
import bolsilloApp.api.debts.dto.DebtResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;

    public DebtResponse create(UUID userId, CreateDebtRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<DebtResponse> findAll(UUID userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public DebtResponse findById(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public DebtResponse markAsPaid(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void delete(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
