package bolsilloApp.api.purchases.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePurchaseRequest(
        @NotBlank @Size(max = 500) String description,
        @NotNull @Positive @DecimalMax("10000000000") BigDecimal totalAmount,
        @NotNull Integer installments,
        @NotNull UUID categoryId,
        @NotNull LocalDate purchaseDate,
        @Pattern(regexp = "^([A-Za-z]{3})?$", message = "Must be a 3-letter currency code") String currency,
        @Positive @DecimalMax("1000000") BigDecimal exchangeRate
) {}
