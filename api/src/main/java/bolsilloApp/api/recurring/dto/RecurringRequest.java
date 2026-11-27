package bolsilloApp.api.recurring.dto;

import bolsilloApp.api.transactions.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

// Days stop at 28 so every month has the date.
public record RecurringRequest(
        @NotNull TransactionType type,
        @NotNull @Positive @DecimalMax("10000000000") BigDecimal amount,
        @Pattern(regexp = "ARS|USD", message = "Must be ARS or USD") String currency,
        @NotBlank @Size(max = 500) String description,
        @NotNull UUID categoryId,
        @NotNull @Min(1) @Max(28) Integer dayOfMonth
) {}
