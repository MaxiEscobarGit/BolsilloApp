package bolsilloApp.api.categories.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String icon,
        boolean isSystem
) {}
