package bolsilloApp.api.purchases.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        String description,
        Long totalAmountCents,
        BigDecimal totalAmount,
        String currency,
        Integer totalInstallments,
        Integer paidInstallments,
        Long remainingAmountCents,
        LocalDate nextInstallmentDate,
        UUID categoryId,
        String categoryName,
        LocalDate purchaseDate,
        LocalDateTime createdAt
) {}
