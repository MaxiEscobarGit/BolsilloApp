package bolsilloApp.api.debts;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.debts.dto.CreateDebtRequest;
import bolsilloApp.api.debts.dto.DebtResponse;
import bolsilloApp.api.debts.model.Debt;
import bolsilloApp.api.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;

    @Transactional
    public DebtResponse create(UUID userId, CreateDebtRequest request) {
        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().toUpperCase()
                : "ARS";

        Long amountCents = request.amount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        var debt = Debt.builder()
                .user(User.builder().id(userId).build())
                .counterpartName(request.counterpartName())
                .direction(request.direction())
                .amountCents(amountCents)
                .currency(currency)
                .description(request.description())
                .dueDate(request.dueDate())
                .status(DebtStatus.PENDING)
                .build();

        return toResponse(debtRepository.save(debt));
    }

    public List<DebtResponse> findAll(UUID userId, DebtStatus status, DebtDirection direction) {
        return debtRepository.findAllFiltered(userId, status, direction).stream()
                .map(this::toResponse)
                .toList();
    }

    public DebtResponse findById(UUID userId, UUID id) {
        var debt = debtRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Debt not found", "NOT_FOUND", 404));
        return toResponse(debt);
    }

    @Transactional
    public DebtResponse markAsPaid(UUID userId, UUID id) {
        var debt = debtRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Debt not found", "NOT_FOUND", 404));

        if (debt.getStatus() == DebtStatus.PAID) {
            throw new ApiException("Debt is already paid", "ALREADY_PAID", 409);
        }

        debt.setStatus(DebtStatus.PAID);
        debt.setPaidAt(LocalDateTime.now());

        return toResponse(debtRepository.save(debt));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        var debt = debtRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("Debt not found", "NOT_FOUND", 404));
        debtRepository.delete(debt);
    }

    private DebtResponse toResponse(Debt d) {
        BigDecimal amount = BigDecimal.valueOf(d.getAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new DebtResponse(
                d.getId(),
                d.getCounterpartName(),
                amount,
                d.getAmountCents(),
                d.getCurrency(),
                d.getDirection(),
                d.getStatus(),
                d.getDescription(),
                d.getDueDate(),
                d.getPaidAt(),
                d.getCreatedAt()
        );
    }
}
