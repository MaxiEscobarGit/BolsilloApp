package bolsilloApp.api.users.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String defaultCurrency,
        String preferredExchangeSource,
        int aiMessagesUsed,
        int aiMessagesLimit,
        LocalDateTime aiMessagesResetAt
) {}
