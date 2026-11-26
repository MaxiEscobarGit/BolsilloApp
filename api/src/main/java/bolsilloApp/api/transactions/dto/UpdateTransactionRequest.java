package bolsilloApp.api.transactions.dto;

import bolsilloApp.api.transactions.TransactionType;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
        @Positive @DecimalMax("10000000000") BigDecimal amount,
        TransactionType type,
        @Size(max = 500) String description,
        UUID categoryId,
        LocalDate transactionDate,
        @Pattern(regexp = "^([A-Za-z]{3})?$", message = "Must be a 3-letter currency code") String currency,
        @Positive @DecimalMax("1000000") BigDecimal exchangeRate
) {}
