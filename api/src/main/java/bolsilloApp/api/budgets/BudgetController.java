package bolsilloApp.api.budgets;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.budgets.dto.BudgetResponse;
import bolsilloApp.api.budgets.dto.SetBudgetRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// One budget per expense category, addressed by the category: PUT sets it, DELETE removes it.
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> findAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(budgetService.findAll(currentUser.getId()));
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<BudgetResponse> set(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID categoryId,
            @Valid @RequestBody SetBudgetRequest request) {
        return ResponseEntity.ok(budgetService.set(currentUser.getId(), categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID categoryId) {
        budgetService.delete(currentUser.getId(), categoryId);
        return ResponseEntity.noContent().build();
    }
}
