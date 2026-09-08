package bolsilloApp.api.purchases;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.purchases.dto.CreatePurchaseRequest;
import bolsilloApp.api.purchases.dto.PurchaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping
    public ResponseEntity<PurchaseResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreatePurchaseRequest request) {
        return ResponseEntity.status(201).body(purchaseService.create(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> findAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(purchaseService.findAll(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseResponse> findById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(purchaseService.findById(currentUser.getId(), id));
    }
}
