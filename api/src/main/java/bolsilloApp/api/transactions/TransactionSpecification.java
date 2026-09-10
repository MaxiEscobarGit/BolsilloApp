package bolsilloApp.api.transactions;

import bolsilloApp.api.transactions.model.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds dynamic JPA Specifications for Transaction queries.
 * Only non-null parameters produce WHERE predicates -- no "param IS NULL OR ..." patterns.
 */
public final class TransactionSpecification {

    private TransactionSpecification() {}

    public static Specification<Transaction> belongsToUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Transaction> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Transaction> dateFrom(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from);
    }

    public static Specification<Transaction> dateTo(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to);
    }

    public static Specification<Transaction> hasCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> hasType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /**
     * Composes a full specification from the given optional filters.
     * Only adds predicates for non-null parameters.
     */
    public static Specification<Transaction> buildSpec(UUID userId, LocalDate from, LocalDate to,
                                                        UUID categoryId, TransactionType type) {
        Specification<Transaction> spec = Specification
                .where(belongsToUser(userId))
                .and(notDeleted());

        if (from != null) {
            spec = spec.and(dateFrom(from));
        }
        if (to != null) {
            spec = spec.and(dateTo(to));
        }
        if (categoryId != null) {
            spec = spec.and(hasCategory(categoryId));
        }
        if (type != null) {
            spec = spec.and(hasType(type));
        }

        return spec;
    }
}
