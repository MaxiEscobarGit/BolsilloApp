package bolsilloApp.api.debts.model;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.debts.DebtDirection;
import bolsilloApp.api.debts.DebtStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(nullable = false)
    private String personName;

    @Column(nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DebtStatus status = DebtStatus.PENDING;

    private String description;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
