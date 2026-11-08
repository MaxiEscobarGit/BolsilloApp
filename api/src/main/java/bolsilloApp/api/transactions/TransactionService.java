package bolsilloApp.api.transactions;

import bolsilloApp.api.auth.AuthRepository;
import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.exchangerates.ExchangeRateService;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionListResponse;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import bolsilloApp.api.transactions.dto.UpdateTransactionRequest;
import bolsilloApp.api.transactions.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final ExchangeRateService exchangeRateService;
    private final AuthRepository authRepository;

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request) {
        return create(userId, request, TransactionSource.MANUAL);
    }

    /**
     * Same as {@link #create(UUID, CreateTransactionRequest)} but lets internal callers
     * (e.g. the chat assistant) set the origin of the transaction.
     */
    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request, TransactionSource source) {
        validateTransactionDate(request.transactionDate());
        validateCategory(userId, request.categoryId());

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().toUpperCase()
                : "ARS";

        Long amountCents = request.amount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        BigDecimal exchangeRate = resolveExchangeRate(
                currency, request.exchangeRate(), request.transactionDate(), userId);

        Long amountArsCents = computeAmountArsCents(amountCents, exchangeRate);

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
                .source(source)
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    public TransactionListResponse findAll(UUID userId, LocalDate from, LocalDate to,
                                              UUID categoryId, TransactionType type,
                                              int limit, int offset) {
        if (from != null && to != null && from.plusYears(1).isBefore(to)) {
            throw new ApiException("Date range cannot exceed 1 year", "INVALID_RANGE", 422);
        }

        int effectiveLimit = Math.min(limit, 200);

        Specification<Transaction> spec = TransactionSpecification.buildSpec(userId, from, to, categoryId, type);

        // Page number is derived from offset and limit (offset-based pagination)
        int pageNumber = offset / effectiveLimit;
        var pageable = PageRequest.of(pageNumber, effectiveLimit,
                Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt")));

        var page = transactionRepository.findAll(spec, pageable);

        List<TransactionResponse> data = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new TransactionListResponse(data, page.getTotalElements(), effectiveLimit, offset);
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

        boolean needsRateRecalc = false;

        if (request.amount() != null) {
            Long amountCents = request.amount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            transaction.setAmountCents(amountCents);
            needsRateRecalc = true;
        }

        if (request.currency() != null && !request.currency().isBlank()) {
            transaction.setCurrency(request.currency().toUpperCase());
            needsRateRecalc = true;
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
            needsRateRecalc = true;
        }

        if (needsRateRecalc) {
            BigDecimal rate = resolveExchangeRate(
                    transaction.getCurrency(), request.exchangeRate(),
                    transaction.getTransactionDate(), userId);
            transaction.setExchangeRate(rate);
            transaction.setAmountArsCents(computeAmountArsCents(transaction.getAmountCents(), rate));
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

    /**
     * Resolves the exchange rate for a transaction based on these rules:
     * a) If currency == "ARS" -> rate = 1.0
     * b) If the request provides an exchangeRate (non-null) -> use that
     * c) Otherwise -> look up from exchange_rates table by (from=currency, to="ARS", date=transactionDate, source=user.preferredExchangeSource)
     * d) If no rate found -> throw ApiException 422
     */
    public BigDecimal resolveExchangeRate(String currency, BigDecimal requestExchangeRate,
                                    LocalDate transactionDate, UUID userId) {
        if ("ARS".equals(currency)) {
            return BigDecimal.ONE;
        }

        if (requestExchangeRate != null) {
            return requestExchangeRate;
        }

        User user = authRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", "NOT_FOUND", 404));

        return exchangeRateService.getRate(currency, "ARS", transactionDate, user.getPreferredExchangeSource())
                .orElseThrow(() -> new ApiException(
                        "No exchange rate found for " + currency + "/ARS on " + transactionDate
                                + " (source: " + user.getPreferredExchangeSource() + "). "
                                + "You can provide the exchange rate in the request body as 'exchangeRate'.",
                        "MISSING_EXCHANGE_RATE", 422));
    }

    /**
     * Computes amount_ars_cents = round(amount_cents * rate) with HALF_UP rounding.
     */
    public Long computeAmountArsCents(Long amountCents, BigDecimal exchangeRate) {
        return BigDecimal.valueOf(amountCents)
                .multiply(exchangeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
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
