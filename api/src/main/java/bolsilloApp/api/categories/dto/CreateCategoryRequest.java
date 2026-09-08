package bolsilloApp.api.categories.dto;

import bolsilloApp.api.categories.model.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        @NotBlank String emoji,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Must be a valid hex color like #FF5733") String colorHex,
        @NotNull CategoryType type
) {}
