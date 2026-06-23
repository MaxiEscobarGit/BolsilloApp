package bolsilloApp.api.purchases.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        String description,
        Long totalAmountCents,
        Integer installments,
        LocalDate startDate,
        LocalDateTime createdAt
) {}
