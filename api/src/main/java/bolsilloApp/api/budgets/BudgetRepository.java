package bolsilloApp.api.budgets;

import bolsilloApp.api.budgets.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    @Query("SELECT b FROM Budget b JOIN FETCH b.category WHERE b.user.id = :userId ORDER BY b.category.name")
    List<Budget> findAllByUserId(UUID userId);

    Optional<Budget> findByUserIdAndCategoryId(UUID userId, UUID categoryId);

    /** Expenses per category between two dates (inclusive), in ARS cents: [categoryId, total]. */
    @Query(value = """
        SELECT t.category_id, COALESCE(SUM(t.amount_ars_cents), 0)
        FROM transactions t
        WHERE t.user_id = :userId AND t.type = 'EXPENSE' AND t.deleted_at IS NULL
          AND t.transaction_date BETWEEN :from AND :to
        GROUP BY t.category_id
        """, nativeQuery = true)
    List<Object[]> spentByCategory(UUID userId, LocalDate from, LocalDate to);
}
