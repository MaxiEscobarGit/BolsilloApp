package bolsilloApp.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param transactionId optional: transaction to correct when the intent is CORRECT.
 *                      If null, the last transaction created via chat is used.
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String message,
        UUID transactionId
) {
    public ChatRequest(String message) {
        this(message, null);
    }
}
