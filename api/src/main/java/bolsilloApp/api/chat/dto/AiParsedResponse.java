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
    /**
     * Transaction fields extracted by the AI. Every field is optional: for CORRECT
     * only the corrected fields are expected.
     *
     * @param currency ISO code (ARS, USD, ...). Null means "not specified".
     * @param alias    the term the user used for the merchant/category (e.g. "el chino"),
     *                 used to learn user_aliases when correcting.
     */
    public record TransactionData(
            BigDecimal amount,
            String categoryName,
            String description,
            String type,
            LocalDate date,
            String currency,
            String alias
    ) {}
}
