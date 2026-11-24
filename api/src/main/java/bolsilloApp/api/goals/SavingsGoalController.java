package bolsilloApp.api.goals;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.goals.dto.ContributionRequest;
import bolsilloApp.api.goals.dto.GoalRequest;
import bolsilloApp.api.goals.dto.GoalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalService service;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> findAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(service.findAll(currentUser.getId()));
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal User currentUser, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(201).body(service.create(currentUser.getId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(
            @AuthenticationPrincipal User currentUser, @PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(service.update(currentUser.getId(), id, request));
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<GoalResponse> contribute(
            @AuthenticationPrincipal User currentUser, @PathVariable UUID id, @Valid @RequestBody ContributionRequest request) {
        return ResponseEntity.ok(service.contribute(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        service.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
