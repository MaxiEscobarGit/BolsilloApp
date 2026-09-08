package bolsilloApp.api.transactions.dto;

import bolsilloApp.api.transactions.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank String description,
        @NotNull UUID categoryId,
        @NotNull LocalDate transactionDate,
        String currency
) {}
