package bolsilloApp.api.debts.model;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.debts.DebtDirection;
import bolsilloApp.api.debts.DebtStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "debts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "counterpart_name", nullable = false)
    private String counterpartName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtDirection direction;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DebtStatus status = DebtStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
