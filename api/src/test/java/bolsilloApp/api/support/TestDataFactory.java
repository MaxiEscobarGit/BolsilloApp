package bolsilloApp.api.support;

import bolsilloApp.api.categories.dto.CreateCategoryRequest;
import bolsilloApp.api.categories.model.CategoryType;
import bolsilloApp.api.debts.DebtDirection;
import bolsilloApp.api.debts.dto.CreateDebtRequest;
import bolsilloApp.api.purchases.dto.CreatePurchaseRequest;
import bolsilloApp.api.transactions.TransactionType;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static CreateTransactionRequest expenseRequest(UUID categoryId, BigDecimal amount) {
        return new CreateTransactionRequest(amount, TransactionType.EXPENSE,
                "Test expense", categoryId, LocalDate.now(), null, null);
    }

    public static CreateTransactionRequest expenseRequest(UUID categoryId, String amount) {
        return expenseRequest(categoryId, new BigDecimal(amount));
    }

    public static CreateTransactionRequest incomeRequest(UUID categoryId, BigDecimal amount) {
        return new CreateTransactionRequest(amount, TransactionType.INCOME,
                "Test income", categoryId, LocalDate.now(), null, null);
    }

    public static CreateTransactionRequest transactionRequest(UUID categoryId, BigDecimal amount,
                                                               TransactionType type, LocalDate date) {
        return new CreateTransactionRequest(amount, type,
                "Test transaction", categoryId, date, null, null);
    }

    public static CreateTransactionRequest transactionRequestWithCurrency(UUID categoryId, BigDecimal amount,
                                                                           TransactionType type, LocalDate date,
                                                                           String currency, BigDecimal exchangeRate) {
        return new CreateTransactionRequest(amount, type,
                "Test transaction", categoryId, date, currency, exchangeRate);
    }

    public static CreateCategoryRequest categoryRequest(String name) {
        return new CreateCategoryRequest(name, "\uD83D\uDD35", "#1A2B3C", CategoryType.EXPENSE);
    }

    public static CreateCategoryRequest categoryRequest(String name, CategoryType type) {
        return new CreateCategoryRequest(name, "\uD83D\uDD35", "#1A2B3C", type);
    }

    public static CreatePurchaseRequest purchaseRequest(UUID categoryId, BigDecimal total, int installments) {
        return new CreatePurchaseRequest("Test purchase", total, installments,
                categoryId, LocalDate.now(), null, null);
    }

    public static CreatePurchaseRequest purchaseRequest(UUID categoryId, BigDecimal total,
                                                         int installments, LocalDate date) {
        return new CreatePurchaseRequest("Test purchase", total, installments,
                categoryId, date, null, null);
    }

    public static CreatePurchaseRequest purchaseRequestWithCurrency(UUID categoryId, BigDecimal total,
                                                                      int installments, LocalDate date,
                                                                      String currency, BigDecimal exchangeRate) {
        return new CreatePurchaseRequest("Test purchase", total, installments,
                categoryId, date, currency, exchangeRate);
    }

    public static CreateDebtRequest debtRequest(BigDecimal amount, DebtDirection direction) {
        return new CreateDebtRequest("Juan", amount, direction, "Test debt", null, null);
    }

    public static CreateDebtRequest oweRequest(BigDecimal amount) {
        return debtRequest(amount, DebtDirection.OWE);
    }

    public static CreateDebtRequest owedRequest(BigDecimal amount) {
        return debtRequest(amount, DebtDirection.OWED);
    }
}
