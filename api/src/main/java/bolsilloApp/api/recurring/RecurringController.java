package bolsilloApp.api.recurring;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.recurring.dto.RecurringRequest;
import bolsilloApp.api.recurring.dto.RecurringResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringController {

    private final RecurringService recurringService;

    @GetMapping
    public ResponseEntity<List<RecurringResponse>> findAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(recurringService.findAll(currentUser.getId()));
    }

    @PostMapping
    public ResponseEntity<RecurringResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody RecurringRequest request) {
        return ResponseEntity.status(201).body(recurringService.create(currentUser.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringResponse> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody RecurringRequest request) {
        return ResponseEntity.ok(recurringService.update(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        recurringService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
