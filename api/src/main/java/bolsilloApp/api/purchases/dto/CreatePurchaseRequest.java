package bolsilloApp.api.purchases.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePurchaseRequest(
        @NotBlank String description,
        @NotNull @Min(1) Long totalAmountCents,
        @NotNull @Min(1) Integer installments,
        @NotNull LocalDate startDate
) {}
