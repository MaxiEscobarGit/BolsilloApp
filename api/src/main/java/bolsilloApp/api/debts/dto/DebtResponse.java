package bolsilloApp.api.debts.dto;

import bolsilloApp.api.debts.DebtDirection;
import bolsilloApp.api.debts.DebtStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DebtResponse(
        UUID id,
        String personName,
        Long amountCents,
        DebtDirection direction,
        DebtStatus status,
        String description,
        LocalDateTime createdAt
) {}
