package bolsilloApp.api.debts.dto;

import bolsilloApp.api.debts.DebtDirection;
import bolsilloApp.api.debts.DebtStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DebtResponse(
        UUID id,
        String counterpartName,
        BigDecimal amount,
        Long amountCents,
        String currency,
        DebtDirection direction,
        DebtStatus status,
        String description,
        LocalDate dueDate,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
