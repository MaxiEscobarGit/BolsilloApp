package bolsilloApp.api.purchases;

import bolsilloApp.api.purchases.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @Query("SELECT p FROM Purchase p WHERE p.user.id = :userId ORDER BY p.purchaseDate DESC")
    List<Purchase> findAllByUserId(UUID userId);

    Optional<Purchase> findByIdAndUserId(UUID id, UUID userId);
}
