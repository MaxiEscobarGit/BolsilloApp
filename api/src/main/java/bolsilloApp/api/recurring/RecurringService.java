package bolsilloApp.api.recurring;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.recurring.dto.RecurringRequest;
import bolsilloApp.api.recurring.dto.RecurringResponse;
import bolsilloApp.api.recurring.model.Recurring;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.TransactionService;
import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringService {

    private final RecurringRepository recurringRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionService transactionService;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public List<RecurringResponse> findAll(UUID userId) {
        return recurringRepository.findAllByUserId(userId).stream().map(this::toResponse).toList();
    }

    /** Creates it and, if today is its day, also today's movement. */
    public RecurringResponse create(UUID userId, RecurringRequest request) {
        Recurring saved = transactionTemplate.execute(status -> {
            Recurring r = Recurring.builder().user(User.builder().id(userId).build()).build();
            apply(r, userId, request, LocalDate.now());
            return recurringRepository.save(r);
        });
        materialize(saved.getId(), LocalDate.now());
        return toResponse(recurringRepository.findById(saved.getId()).orElseThrow());
    }

    @Transactional
    public RecurringResponse update(UUID userId, UUID id, RecurringRequest request) {
        Recurring r = find(userId, id);
        apply(r, userId, request, LocalDate.now());
        return toResponse(recurringRepository.save(r));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        recurringRepository.delete(find(userId, id));
    }

    /**
     * Creates every movement that is due, catching up on missed months (the API may have been off).
     * Each movement and its date bump commit together, so a crash never creates one twice.
     * A movement that can't be created (no exchange rate yet) is retried on the next run.
     */
    public int materializeDue(LocalDate today) {
        int created = 0;
        for (Recurring r : recurringRepository.findDue(today)) {
            created += materialize(r.getId(), today);
        }
        return created;
    }

    private int materialize(UUID id, LocalDate today) {
        int created = 0;
        while (true) {
            Boolean done;
            try {
                done = transactionTemplate.execute(status -> {
                    Recurring r = recurringRepository.findById(id).orElse(null);
                    if (r == null || r.getNextDate().isAfter(today)) return false;
                    BigDecimal amount = BigDecimal.valueOf(r.getAmountCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
                    transactionService.create(r.getUser().getId(), new CreateTransactionRequest(
                            amount, r.getType(), r.getDescription(), r.getCategory().getId(), r.getNextDate(), r.getCurrency(), null),
                            TransactionSource.RECURRING);
                    r.setNextDate(r.getNextDate().plusMonths(1));
                    recurringRepository.save(r);
                    return true;
                    });
            } catch (ApiException e) {
                log.warn("Movimiento fijo {} no creado, se reintenta en la próxima corrida: {}", id, e.getMessage());
                return created;
            }
            if (!Boolean.TRUE.equals(done)) return created;
            created++;
        }
    }

    private void apply(Recurring r, UUID userId, RecurringRequest request, LocalDate today) {
        if (request.type() == TransactionType.DEBT) {
            throw new ApiException("Recurring movements are expenses or income", "INVALID_TYPE", 422);
        }
        Category category = categoryRepository.findById(request.categoryId())
                .filter(c -> c.getIsSystem() || (c.getUser() != null && c.getUser().getId().equals(userId)))
                .orElseThrow(() -> new ApiException("Category not found", "NOT_FOUND", 404));
        if (!category.getType().name().equals(request.type().name())) {
            throw new ApiException("Category type does not match", "INVALID_CATEGORY", 422);
        }
        boolean dayChanged = r.getDayOfMonth() == null || !r.getDayOfMonth().equals(request.dayOfMonth());
        r.setType(request.type());
        r.setAmountCents(request.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue());
        r.setCurrency(request.currency() == null ? "ARS" : request.currency());
        r.setDescription(request.description().trim());
        r.setCategory(category);
        r.setDayOfMonth(request.dayOfMonth());
        if (dayChanged) r.setNextDate(nextOccurrence(request.dayOfMonth(), today));
    }

    /** The first date on or after {@code today} that falls on {@code day}. */
    static LocalDate nextOccurrence(int day, LocalDate today) {
        LocalDate thisMonth = today.withDayOfMonth(day);
        return thisMonth.isBefore(today) ? thisMonth.plusMonths(1) : thisMonth;
    }

    private Recurring find(UUID userId, UUID id) {
        return recurringRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Recurring movement not found", "NOT_FOUND", 404));
    }

    private RecurringResponse toResponse(Recurring r) {
        return new RecurringResponse(r.getId(), r.getType(), r.getAmountCents(), r.getCurrency(), r.getDescription(),
                r.getCategory().getId(), r.getCategory().getName(), r.getDayOfMonth(), r.getNextDate());
    }
}
