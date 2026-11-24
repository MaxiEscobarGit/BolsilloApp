package bolsilloApp.api.budgets;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.budgets.dto.BudgetResponse;
import bolsilloApp.api.budgets.dto.SetBudgetRequest;
import bolsilloApp.api.budgets.model.Budget;
import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.categories.model.CategoryType;
import bolsilloApp.api.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<BudgetResponse> findAll(UUID userId) {
        Map<UUID, Long> spent = spentThisMonth(userId);
        return budgetRepository.findAllByUserId(userId).stream()
                .map(b -> toResponse(b, spent.getOrDefault(b.getCategory().getId(), 0L)))
                .toList();
    }

    /** Creates or replaces the monthly cap of an expense category the user can see. */
    @Transactional
    public BudgetResponse set(UUID userId, UUID categoryId, SetBudgetRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> c.getIsSystem() || (c.getUser() != null && c.getUser().getId().equals(userId)))
                .orElseThrow(() -> new ApiException("Category not found", "NOT_FOUND", 404));
        if (category.getType() != CategoryType.EXPENSE) {
            throw new ApiException("Budgets are only for expense categories", "INVALID_CATEGORY", 422);
        }

        long amountCents = request.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        Budget budget = budgetRepository.findByUserIdAndCategoryId(userId, categoryId)
                .orElseGet(() -> Budget.builder().user(User.builder().id(userId).build()).category(category).build());
        budget.setAmountCents(amountCents);
        budget = budgetRepository.save(budget);
        return toResponse(budget, spentThisMonth(userId).getOrDefault(categoryId, 0L));
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        budgetRepository.findByUserIdAndCategoryId(userId, categoryId).ifPresent(budgetRepository::delete);
    }

    private Map<UUID, Long> spentThisMonth(UUID userId) {
        YearMonth month = YearMonth.now();
        Map<UUID, Long> spent = new HashMap<>();
        for (Object[] row : budgetRepository.spentByCategory(userId, month.atDay(1), month.atEndOfMonth())) {
            spent.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return spent;
    }

    private BudgetResponse toResponse(Budget b, long spentCents) {
        Category c = b.getCategory();
        return new BudgetResponse(c.getId(), c.getName(), c.getEmoji(), c.getColorHex(), b.getAmountCents(), spentCents);
    }
}
