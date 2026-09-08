package bolsilloApp.api.metrics;

import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.metrics.dto.SummaryResponse;
import bolsilloApp.api.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final EntityManager entityManager;

    public SummaryResponse getSummary(UUID userId, String period, LocalDate from, LocalDate to) {
        // Resolve date range from period
        LocalDate[] range = resolveDateRange(period, from, to);
        LocalDate start = range[0];
        LocalDate end = range[1];

        // Total expenses
        Long totalExpenseCents = querySum(userId, start, end, "EXPENSE");

        // Total income
        Long totalIncomeCents = querySum(userId, start, end, "INCOME");

        // Balance
        Long balanceCents = totalIncomeCents - totalExpenseCents;

        // By category
        List<SummaryResponse.CategoryTotal> byCategory = queryCategoryTotals(userId, start, end);

        // Daily totals
        List<SummaryResponse.DailyTotal> dailyTotals = queryDailyTotals(userId, start, end);

        // Vs previous period
        SummaryResponse.PeriodComparison vsPreviousPeriod = queryPreviousPeriodComparison(userId, start, end);

        return new SummaryResponse(start, end, totalExpenseCents, totalIncomeCents, balanceCents,
                byCategory, dailyTotals, vsPreviousPeriod);
    }

    public MetricsContext getContext(UUID userId) {
        // Current month context
        LocalDate start = YearMonth.now().atDay(1);
        LocalDate end = YearMonth.now().atEndOfMonth();

        Long totalExpenseCents = querySum(userId, start, end, "EXPENSE");
        Long totalIncomeCents = querySum(userId, start, end, "INCOME");
        Long balanceCents = totalIncomeCents - totalExpenseCents;

        // Expense by category
        Map<String, Long> expenseByCategory = queryCategoryMap(userId, start, end, "EXPENSE");
        Map<String, Long> incomeByCategory = queryCategoryMap(userId, start, end, "INCOME");

        // Upcoming installments (next 3 months)
        List<MetricsContext.UpcomingInstallment> upcomingInstallments = queryUpcomingInstallments(userId);

        // Active debts
        List<MetricsContext.ActiveDebt> activeDebts = queryActiveDebts(userId);

        return new MetricsContext(totalIncomeCents, totalExpenseCents, balanceCents,
                expenseByCategory, incomeByCategory, upcomingInstallments, activeDebts);
    }

    private LocalDate[] resolveDateRange(String period, LocalDate from, LocalDate to) {
        if (period == null || period.isBlank()) {
            period = "month";
        }

        LocalDate today = LocalDate.now();

        return switch (period.toLowerCase()) {
            case "week" -> new LocalDate[]{
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            };
            case "month" -> new LocalDate[]{
                    YearMonth.now().atDay(1),
                    YearMonth.now().atEndOfMonth()
            };
            case "year" -> new LocalDate[]{
                    today.withDayOfYear(1),
                    today.withMonth(12).withDayOfMonth(31)
            };
            case "custom" -> {
                if (from == null || to == null) {
                    throw new ApiException("Custom period requires 'from' and 'to' parameters", "INVALID_PARAMS", 400);
                }
                if (from.plusYears(1).isBefore(to)) {
                    throw new ApiException("Date range cannot exceed 1 year", "INVALID_RANGE", 400);
                }
                yield new LocalDate[]{from, to};
            }
            default -> throw new ApiException("Invalid period: " + period, "INVALID_PARAMS", 400);
        };
    }

    @SuppressWarnings("unchecked")
    private Long querySum(UUID userId, LocalDate start, LocalDate end, String type) {
        Query query = entityManager.createNativeQuery("""
            SELECT COALESCE(SUM(t.amount_ars_cents), 0)
            FROM transactions t
            WHERE t.user_id = :userId
            AND t.deleted_at IS NULL
            AND t.type = :type
            AND t.transaction_date BETWEEN :start AND :end
            """);
        query.setParameter("userId", userId);
        query.setParameter("type", type);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return ((Number) query.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<SummaryResponse.CategoryTotal> queryCategoryTotals(UUID userId, LocalDate start, LocalDate end) {
        Query query = entityManager.createNativeQuery("""
            SELECT c.name, t.type, COALESCE(SUM(t.amount_ars_cents), 0) as total
            FROM transactions t
            JOIN categories c ON t.category_id = c.id
            WHERE t.user_id = :userId
            AND t.deleted_at IS NULL
            AND t.transaction_date BETWEEN :start AND :end
            GROUP BY c.name, t.type
            ORDER BY total DESC
            """);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(r -> new SummaryResponse.CategoryTotal(
                        (String) r[0],
                        (String) r[1],
                        ((Number) r[2]).longValue()
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<SummaryResponse.DailyTotal> queryDailyTotals(UUID userId, LocalDate start, LocalDate end) {
        Query query = entityManager.createNativeQuery("""
            SELECT t.transaction_date,
                   COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_ars_cents ELSE 0 END), 0) as expense,
                   COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_ars_cents ELSE 0 END), 0) as income
            FROM transactions t
            WHERE t.user_id = :userId
            AND t.deleted_at IS NULL
            AND t.transaction_date BETWEEN :start AND :end
            GROUP BY t.transaction_date
            ORDER BY t.transaction_date
            """);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(r -> new SummaryResponse.DailyTotal(
                        (LocalDate) r[0],
                        ((Number) r[1]).longValue(),
                        ((Number) r[2]).longValue()
                ))
                .toList();
    }

    private SummaryResponse.PeriodComparison queryPreviousPeriodComparison(UUID userId, LocalDate start, LocalDate end) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevStart = start.minusDays(daysBetween);
        LocalDate prevEnd = start.minusDays(1);

        Long prevExpense = querySum(userId, prevStart, prevEnd, "EXPENSE");
        Long prevIncome = querySum(userId, prevStart, prevEnd, "INCOME");

        Long currExpense = querySum(userId, start, end, "EXPENSE");
        Long currIncome = querySum(userId, start, end, "INCOME");

        Double expenseChange = prevExpense == 0 ? null :
                ((double)(currExpense - prevExpense) / prevExpense) * 100;
        Double incomeChange = prevIncome == 0 ? null :
                ((double)(currIncome - prevIncome) / prevIncome) * 100;

        return new SummaryResponse.PeriodComparison(prevExpense, prevIncome, expenseChange, incomeChange);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> queryCategoryMap(UUID userId, LocalDate start, LocalDate end, String type) {
        Query query = entityManager.createNativeQuery("""
            SELECT c.name, COALESCE(SUM(t.amount_ars_cents), 0) as total
            FROM transactions t
            JOIN categories c ON t.category_id = c.id
            WHERE t.user_id = :userId
            AND t.deleted_at IS NULL
            AND t.type = :type
            AND t.transaction_date BETWEEN :start AND :end
            GROUP BY c.name
            ORDER BY total DESC
            """);
        query.setParameter("userId", userId);
        query.setParameter("type", type);
        query.setParameter("start", start);
        query.setParameter("end", end);

        List<Object[]> results = query.getResultList();
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] r : results) {
            map.put((String) r[0], ((Number) r[1]).longValue());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<MetricsContext.UpcomingInstallment> queryUpcomingInstallments(UUID userId) {
        Query query = entityManager.createNativeQuery("""
            SELECT t.description, t.amount_cents, t.transaction_date
            FROM transactions t
            WHERE t.user_id = :userId
            AND t.deleted_at IS NULL
            AND t.purchase_id IS NOT NULL
            AND t.transaction_date > CURRENT_DATE
            AND t.transaction_date <= CURRENT_DATE + INTERVAL '90 days'
            ORDER BY t.transaction_date
            LIMIT 10
            """);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(r -> new MetricsContext.UpcomingInstallment(
                        (String) r[0],
                        ((Number) r[1]).longValue(),
                        r[2].toString()
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<MetricsContext.ActiveDebt> queryActiveDebts(UUID userId) {
        Query query = entityManager.createNativeQuery("""
            SELECT d.counterpart_name, d.direction, d.amount_cents
            FROM debts d
            WHERE d.user_id = :userId
            AND d.status = 'PENDING'
            ORDER BY d.created_at DESC
            LIMIT 10
            """);
        query.setParameter("userId", userId);

        List<Object[]> results = query.getResultList();
        return results.stream()
                .map(r -> new MetricsContext.ActiveDebt(
                        (String) r[0],
                        (String) r[1],
                        ((Number) r[2]).longValue()
                ))
                .toList();
    }
}
