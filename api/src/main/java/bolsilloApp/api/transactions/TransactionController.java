package bolsilloApp.api.transactions;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionListResponse;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import bolsilloApp.api.transactions.dto.UpdateTransactionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
        return ResponseEntity.status(201).body(transactionService.create(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<TransactionListResponse> findAll(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(transactionService.findAll(currentUser.getId(), from, to, categoryId, type, q, Math.max(1, limit), Math.max(0, offset)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(
            @AuthenticationPrincipal User currentUser,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"bolsillo-" + from + "-a-" + to + ".csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(transactionService.exportCsv(currentUser.getId(), from, to));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> findById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.findById(currentUser.getId(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request) {
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
