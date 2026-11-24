package bolsilloApp.api.categories.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The type is fixed at creation: changing it would move the category's movements between income and expense.
public record UpdateCategoryRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        @NotBlank String emoji,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Must be a valid hex color like #FF5733") String colorHex
) {}
