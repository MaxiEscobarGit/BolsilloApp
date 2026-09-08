package bolsilloApp.api.debts.dto;

import bolsilloApp.api.debts.DebtDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDebtRequest(
        @NotBlank String counterpartName,
        @NotNull @Positive BigDecimal amount,
        @NotNull DebtDirection direction,
        String description,
        LocalDate dueDate,
        String currency
) {}
