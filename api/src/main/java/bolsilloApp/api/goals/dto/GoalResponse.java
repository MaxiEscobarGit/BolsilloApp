package bolsilloApp.api.goals.dto;

import java.time.LocalDate;
import java.util.UUID;

// monthlyCents: what's left divided by the months until the deadline (null without deadline or once reached).
public record GoalResponse(
        UUID id,
        String name,
        long targetCents,
        long savedCents,
        LocalDate deadline,
        Long monthlyCents
) {}
