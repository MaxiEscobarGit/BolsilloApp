package bolsilloApp.api.purchases.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseRequest(
        @NotBlank String description,
        @NotNull @Positive BigDecimal totalAmount,
        @NotNull Integer installments,
        @NotNull UUID categoryId,
        @NotNull LocalDate purchaseDate,
        String currency,
        BigDecimal exchangeRate
) {}
