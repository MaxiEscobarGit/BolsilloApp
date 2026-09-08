package bolsilloApp.api.chat.dto;

import bolsilloApp.api.chat.ChatIntent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents the structured JSON response expected from Anthropic.
 * Used internally by ChatService to process AI outputs.
 */
public record AiParsedResponse(
        String message,
        ChatIntent intent,
        TransactionData transaction
) {
    public record TransactionData(
            BigDecimal amount,
            String categoryName,
            String description,
            String type,
            LocalDate date
    ) {}
}
