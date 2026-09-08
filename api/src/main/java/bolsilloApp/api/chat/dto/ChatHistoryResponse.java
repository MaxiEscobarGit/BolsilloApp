package bolsilloApp.api.chat.dto;

import bolsilloApp.api.chat.ChatIntent;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatHistoryResponse(
        UUID id,
        String role,
        String content,
        ChatIntent intent,
        String modelUsed,
        Integer tokensUsed,
        LocalDateTime createdAt
) {}
