package bolsilloApp.api.categories.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank String name,
        String icon
) {}
