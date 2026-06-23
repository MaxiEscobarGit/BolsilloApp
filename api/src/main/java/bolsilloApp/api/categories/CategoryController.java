package bolsilloApp.api.categories;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.categories.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.create(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(categoryService.findAll(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id) {
        categoryService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
