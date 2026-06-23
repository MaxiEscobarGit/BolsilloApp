package bolsilloApp.api.chat.dto;

import bolsilloApp.api.chat.ChatIntent;

public record ChatResponse(
        String message,
        ChatIntent intent,
        Object data
) {}
