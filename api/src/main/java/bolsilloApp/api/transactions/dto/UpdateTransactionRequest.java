package bolsilloApp.api.transactions.dto;

import bolsilloApp.api.transactions.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
        BigDecimal amount,
        TransactionType type,
        String description,
        UUID categoryId,
        LocalDate transactionDate,
        String currency
) {}
