package bolsilloApp.api.transactions;

import bolsilloApp.api.transactions.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    @Modifying
    @Query("UPDATE Transaction t SET t.category.id = :newCategoryId WHERE t.category.id = :oldCategoryId AND t.deletedAt IS NULL")
    void reassignCategory(UUID oldCategoryId, UUID newCategoryId);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.deletedAt IS NULL ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findAllByUserIdAndNotDeleted(UUID userId);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.user.id = :userId
        AND t.deletedAt IS NULL
        AND (:from IS NULL OR t.transactionDate >= :from)
        AND (:to IS NULL OR t.transactionDate <= :to)
        AND (:categoryId IS NULL OR t.category.id = :categoryId)
        AND (:type IS NULL OR t.type = :type)
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<Transaction> findAllFiltered(UUID userId, LocalDate from, LocalDate to, UUID categoryId, TransactionType type);

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.purchase.id = :purchaseId AND t.deletedAt IS NULL AND t.transactionDate <= CURRENT_DATE")
    List<Transaction> findPaidInstallments(UUID purchaseId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.purchase.id = :purchaseId AND t.deletedAt IS NULL AND t.transactionDate <= CURRENT_DATE")
    int countPaidInstallments(UUID purchaseId);
}
