package bolsilloApp.api.transactions.dto;

import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        Long amountCents,
        String currency,
        Long amountArsCents,
        BigDecimal exchangeRate,
        TransactionType type,
        TransactionSource source,
        String description,
        UUID categoryId,
        String categoryName,
        LocalDate transactionDate,
        UUID purchaseId,
        Integer installmentNumber,
        LocalDateTime createdAt
) {}
