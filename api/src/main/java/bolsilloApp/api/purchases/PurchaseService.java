package bolsilloApp.api.purchases;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.purchases.dto.CreatePurchaseRequest;
import bolsilloApp.api.purchases.dto.PurchaseResponse;
import bolsilloApp.api.purchases.model.Purchase;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.TransactionRepository;
import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;
import bolsilloApp.api.transactions.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public PurchaseResponse create(UUID userId, CreatePurchaseRequest request) {
        if (request.installments() < 1 || request.installments() > 60) {
            throw new ApiException("Installments must be between 1 and 60", "INVALID_INSTALLMENTS", 422);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException("Category not found", "NOT_FOUND", 404));

        // Validate category belongs to user or is system
        if (!category.getIsSystem() && (category.getUser() == null || !category.getUser().getId().equals(userId))) {
            throw new ApiException("Category not found", "NOT_FOUND", 404);
        }

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().toUpperCase()
                : "ARS";

        Long totalAmountCents = request.totalAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        User userRef = User.builder().id(userId).build();

        // Create purchase
        var purchase = Purchase.builder()
                .user(userRef)
                .description(request.description())
                .totalAmountCents(totalAmountCents)
                .currency(currency)
                .totalInstallments(request.installments())
                .category(category)
                .purchaseDate(request.purchaseDate())
                .build();
        purchase = purchaseRepository.save(purchase);

        // Create N transaction children with exact installment split
        int n = request.installments();
        long baseAmount = totalAmountCents / n;
        long remainder = totalAmountCents - (baseAmount * n);

        List<Transaction> transactions = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            long installmentAmount = (i == n) ? baseAmount + remainder : baseAmount;
            LocalDate installmentDate = request.purchaseDate().plusMonths(i - 1);

            var transaction = Transaction.builder()
                    .user(userRef)
                    .category(category)
                    .type(TransactionType.EXPENSE)
                    .amountCents(installmentAmount)
                    .currency(currency)
                    .amountArsCents(installmentAmount) // Same for ARS, TODO: exchange rate
                    .exchangeRate(BigDecimal.ONE)
                    .description(request.description() + " (" + i + "/" + n + ")")
                    .transactionDate(installmentDate)
                    .source(TransactionSource.MANUAL)
                    .purchase(purchase)
                    .installmentNumber(i)
                    .build();
            transactions.add(transaction);
        }
        transactionRepository.saveAll(transactions);

        return toResponse(purchase);
    }

    public List<PurchaseResponse> findAll(UUID userId) {
        return purchaseRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PurchaseResponse findById(UUID userId, UUID id) {
        var purchase = purchaseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Purchase not found", "NOT_FOUND", 404));
        return toResponse(purchase);
    }

    private PurchaseResponse toResponse(Purchase p) {
        int paidInstallments = transactionRepository.countPaidInstallments(p.getId());
        long paidAmountCents = transactionRepository.findPaidInstallments(p.getId()).stream()
                .mapToLong(Transaction::getAmountCents)
                .sum();
        long remainingAmountCents = p.getTotalAmountCents() - paidAmountCents;

        LocalDate nextInstallmentDate = null;
        if (paidInstallments < p.getTotalInstallments()) {
            nextInstallmentDate = p.getPurchaseDate().plusMonths(paidInstallments);
        }

        BigDecimal totalAmount = BigDecimal.valueOf(p.getTotalAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new PurchaseResponse(
                p.getId(),
                p.getDescription(),
                p.getTotalAmountCents(),
                totalAmount,
                p.getCurrency(),
                p.getTotalInstallments(),
                paidInstallments,
                remainingAmountCents,
                nextInstallmentDate,
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getPurchaseDate(),
                p.getCreatedAt()
        );
    }
}
