package bolsilloApp.api.debts.dto;

import bolsilloApp.api.debts.DebtDirection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDebtRequest(
        @NotBlank String personName,
        @NotNull @Min(1) Long amountCents,
        @NotNull DebtDirection direction,
        String description
) {}
