package bolsilloApp.api.goals;

import bolsilloApp.api.goals.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);
}
