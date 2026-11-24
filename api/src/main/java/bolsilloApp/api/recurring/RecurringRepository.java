package bolsilloApp.api.recurring;

import bolsilloApp.api.recurring.model.Recurring;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringRepository extends JpaRepository<Recurring, UUID> {

    @Query("SELECT r FROM Recurring r JOIN FETCH r.category WHERE r.user.id = :userId ORDER BY r.dayOfMonth, r.description")
    List<Recurring> findAllByUserId(UUID userId);

    Optional<Recurring> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT r FROM Recurring r JOIN FETCH r.category JOIN FETCH r.user WHERE r.nextDate <= :today")
    List<Recurring> findDue(LocalDate today);
}
