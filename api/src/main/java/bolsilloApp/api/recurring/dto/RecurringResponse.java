package bolsilloApp.api.recurring.dto;

import bolsilloApp.api.transactions.TransactionType;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringResponse(
        UUID id,
        TransactionType type,
        long amountCents,
        String currency,
        String description,
        UUID categoryId,
        String categoryName,
        int dayOfMonth,
        LocalDate nextDate
) {}
