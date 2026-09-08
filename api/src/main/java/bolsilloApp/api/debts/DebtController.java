package bolsilloApp.api.debts;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.debts.dto.CreateDebtRequest;
import bolsilloApp.api.debts.dto.DebtResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

    @PostMapping
    public ResponseEntity<DebtResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateDebtRequest request) {
        return ResponseEntity.status(201).body(debtService.create(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<DebtResponse>> findAll(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) DebtStatus status,
            @RequestParam(required = false) DebtDirection direction) {
        return ResponseEntity.ok(debtService.findAll(currentUser.getId(), status, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DebtResponse> findById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(debtService.findById(currentUser.getId(), id));
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<DebtResponse> markAsPaid(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(debtService.markAsPaid(currentUser.getId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        debtService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
