package bolsilloApp.api.transactions;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.ok(transactionService.create(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> findAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(transactionService.findAll(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.findById(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        transactionService.softDelete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
