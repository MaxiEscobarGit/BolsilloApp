package bolsilloApp.api.chat.dto;

import bolsilloApp.api.chat.ChatIntent;

import java.util.UUID;

public record ChatResponse(
        String message,
        ChatIntent intent,
        Object data,
        String modelUsed,
        Integer tokensUsed
) {}
