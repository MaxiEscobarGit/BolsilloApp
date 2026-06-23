package bolsilloApp.api.transactions;

import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionResponse create(UUID userId, CreateTransactionRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<TransactionResponse> findAll(UUID userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public TransactionResponse findById(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public TransactionResponse update(UUID userId, UUID id, CreateTransactionRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void softDelete(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
