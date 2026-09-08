package bolsilloApp.api.transactions;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import bolsilloApp.api.transactions.dto.UpdateTransactionRequest;
import bolsilloApp.api.transactions.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request) {
        validateTransactionDate(request.transactionDate());
        validateCategory(userId, request.categoryId());

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().toUpperCase()
                : "ARS";

        Long amountCents = request.amount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        // For now, simple 1:1 conversion if ARS, otherwise store as-is
        // Real exchange rate integration would use exchange_rates table
        BigDecimal exchangeRate = BigDecimal.ONE;
        Long amountArsCents = amountCents;

        if (!"ARS".equals(currency)) {
            // TODO: Look up exchange rate from exchange_rates table
            // For MVP, store the same amount and rate 1:1, to be enhanced later
            exchangeRate = BigDecimal.ONE;
            amountArsCents = amountCents;
        }

        var transaction = Transaction.builder()
                .user(User.builder().id(userId).build())
                .category(categoryRepository.findById(request.categoryId())
                        .orElseThrow(() -> new ApiException("Category not found", "NOT_FOUND", 404)))
                .type(request.type())
                .amountCents(amountCents)
                .currency(currency)
                .amountArsCents(amountArsCents)
                .exchangeRate(exchangeRate)
                .description(request.description())
                .transactionDate(request.transactionDate())
                .source(TransactionSource.MANUAL)
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    public List<TransactionResponse> findAll(UUID userId, LocalDate from, LocalDate to,
                                              UUID categoryId, TransactionType type,
                                              int limit, int offset) {
        if (from != null && to != null && from.plusYears(1).isBefore(to)) {
            throw new ApiException("Date range cannot exceed 1 year", "INVALID_RANGE", 422);
        }

        int effectiveLimit = Math.min(limit, 200);

        // ponytail: filter in Java — PostgreSQL can't infer types for nullable JPQL params.
        // Upgrade to CriteriaBuilder when query volume justifies it.
        return transactionRepository.findAllByUserIdAndNotDeleted(userId)
                .stream()
                .filter(t -> from == null || !t.getTransactionDate().isBefore(from))
                .filter(t -> to == null || !t.getTransactionDate().isAfter(to))
                .filter(t -> categoryId == null || t.getCategory().getId().equals(categoryId))
                .filter(t -> type == null || t.getType() == type)
                .skip(offset)
                .limit(effectiveLimit)
                .map(this::toResponse)
                .toList();
    }

    public TransactionResponse findById(UUID userId, UUID id) {
        var transaction = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ApiException("Transaction not found", "NOT_FOUND", 404));
        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, UpdateTransactionRequest request) {
        var transaction = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ApiException("Transaction not found", "NOT_FOUND", 404));

        boolean amountOrCurrencyChanged = false;

        if (request.amount() != null) {
            Long amountCents = request.amount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            transaction.setAmountCents(amountCents);
            amountOrCurrencyChanged = true;
        }

        if (request.currency() != null && !request.currency().isBlank()) {
            transaction.setCurrency(request.currency().toUpperCase());
            amountOrCurrencyChanged = true;
        }

        if (request.type() != null) {
            transaction.setType(request.type());
        }

        if (request.description() != null) {
            transaction.setDescription(request.description());
        }

        if (request.categoryId() != null) {
            validateCategory(userId, request.categoryId());
            transaction.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ApiException("Category not found", "NOT_FOUND", 404)));
        }

        if (request.transactionDate() != null) {
            validateTransactionDate(request.transactionDate());
            transaction.setTransactionDate(request.transactionDate());
        }

        // Recalculate ARS conversion if amount or currency changed
        if (amountOrCurrencyChanged) {
            if ("ARS".equals(transaction.getCurrency())) {
                transaction.setExchangeRate(BigDecimal.ONE);
                transaction.setAmountArsCents(transaction.getAmountCents());
            } else {
                // TODO: Look up exchange rate
                transaction.setExchangeRate(BigDecimal.ONE);
                transaction.setAmountArsCents(transaction.getAmountCents());
            }
        }

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void softDelete(UUID userId, UUID id) {
        var transaction = transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ApiException("Transaction not found", "NOT_FOUND", 404));
        transaction.setDeletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    private void validateTransactionDate(LocalDate date) {
        if (date.isAfter(LocalDate.now().plusDays(1))) {
            throw new ApiException("Transaction date cannot be more than 1 day in the future", "INVALID_DATE", 422);
        }
    }

    private void validateCategory(UUID userId, UUID categoryId) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException("Category not found", "INVALID_CATEGORY", 422));
        if (!category.getIsSystem() && (category.getUser() == null || !category.getUser().getId().equals(userId))) {
            throw new ApiException("Category not found", "INVALID_CATEGORY", 422);
        }
    }

    TransactionResponse toResponse(Transaction t) {
        BigDecimal amount = BigDecimal.valueOf(t.getAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new TransactionResponse(
                t.getId(),
                amount,
                t.getAmountCents(),
                t.getCurrency(),
                t.getAmountArsCents(),
                t.getExchangeRate(),
                t.getType(),
                t.getSource(),
                t.getDescription(),
                t.getCategory().getId(),
                t.getCategory().getName(),
                t.getTransactionDate(),
                t.getPurchase() != null ? t.getPurchase().getId() : null,
                t.getInstallmentNumber(),
                t.getCreatedAt()
        );
    }
}
