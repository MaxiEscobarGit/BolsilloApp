package bolsilloApp.api.transactions.dto;

import bolsilloApp.api.transactions.TransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull @Min(1) Long amountCents,
        @NotNull TransactionType type,
        String description,
        UUID categoryId,
        @NotNull LocalDate transactionDate
) {}
