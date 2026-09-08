package bolsilloApp.api.categories.dto;

import bolsilloApp.api.categories.model.CategoryType;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String emoji,
        String colorHex,
        CategoryType type,
        boolean isSystem
) {}
