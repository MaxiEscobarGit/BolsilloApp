package bolsilloApp.api.goals;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.goals.dto.ContributionRequest;
import bolsilloApp.api.goals.dto.GoalRequest;
import bolsilloApp.api.goals.dto.GoalResponse;
import bolsilloApp.api.goals.model.SavingsGoal;
import bolsilloApp.api.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private final SavingsGoalRepository repository;

    @Transactional(readOnly = true)
    public List<GoalResponse> findAll(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GoalResponse create(UUID userId, GoalRequest request) {
        SavingsGoal goal = SavingsGoal.builder().user(User.builder().id(userId).build()).build();
        apply(goal, request);
        return toResponse(repository.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID id, GoalRequest request) {
        SavingsGoal goal = find(userId, id);
        apply(goal, request);
        return toResponse(repository.save(goal));
    }

    @Transactional
    public GoalResponse contribute(UUID userId, UUID id, ContributionRequest request) {
        SavingsGoal goal = find(userId, id);
        long delta = cents(request.amount());
        if (delta == 0) {
            throw new ApiException("Amount cannot be zero", "VALIDATION_ERROR", 400);
        }
        if (goal.getSavedCents() + delta < 0) {
            throw new ApiException("Cannot take out more than what was saved", "NOT_ENOUGH_SAVED", 422);
        }
        goal.setSavedCents(goal.getSavedCents() + delta);
        return toResponse(repository.save(goal));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(find(userId, id));
    }

    private void apply(SavingsGoal goal, GoalRequest request) {
        goal.setName(request.name().trim());
        goal.setTargetCents(cents(request.target()));
        goal.setDeadline(request.deadline());
    }

    private SavingsGoal find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Goal not found", "NOT_FOUND", 404));
    }

    private static long cents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Months left counting the current one, so a deadline this month asks for everything now. */
    static Long monthly(long remainingCents, LocalDate deadline, LocalDate today) {
        if (deadline == null || remainingCents <= 0) return null;
        long months = Math.max(1, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), deadline.withDayOfMonth(1)) + 1);
        return (remainingCents + months - 1) / months;
    }

    private GoalResponse toResponse(SavingsGoal g) {
        return new GoalResponse(g.getId(), g.getName(), g.getTargetCents(), g.getSavedCents(), g.getDeadline(),
                monthly(g.getTargetCents() - g.getSavedCents(), g.getDeadline(), LocalDate.now()));
    }
}
