package bolsilloApp.api.transactions.dto;

import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        Long amountCents,
        TransactionType type,
        TransactionSource source,
        String description,
        UUID categoryId,
        String categoryName,
        LocalDate transactionDate,
        LocalDateTime createdAt
) {}
