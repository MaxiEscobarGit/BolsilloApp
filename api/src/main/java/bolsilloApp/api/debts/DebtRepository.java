package bolsilloApp.api.debts;

import bolsilloApp.api.debts.model.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID> {
}
