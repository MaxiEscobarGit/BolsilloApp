package bolsilloApp.api.purchases;

import bolsilloApp.api.purchases.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
}
