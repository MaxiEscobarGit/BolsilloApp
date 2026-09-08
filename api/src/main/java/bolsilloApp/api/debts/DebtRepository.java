package bolsilloApp.api.debts;

import bolsilloApp.api.debts.model.Debt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID> {

    @Query("""
        SELECT d FROM Debt d
        WHERE d.user.id = :userId
        AND (:status IS NULL OR d.status = :status)
        AND (:direction IS NULL OR d.direction = :direction)
        ORDER BY d.createdAt DESC
        """)
    List<Debt> findAllFiltered(UUID userId, DebtStatus status, DebtDirection direction);

    Optional<Debt> findByIdAndUserId(UUID id, UUID userId);
}
