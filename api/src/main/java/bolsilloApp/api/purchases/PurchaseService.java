package bolsilloApp.api.purchases;

import bolsilloApp.api.purchases.dto.CreatePurchaseRequest;
import bolsilloApp.api.purchases.dto.PurchaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;

    public PurchaseResponse create(UUID userId, CreatePurchaseRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<PurchaseResponse> findAll(UUID userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public PurchaseResponse findById(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
