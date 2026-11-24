package bolsilloApp.api.recurring.model;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.transactions.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recurring {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    @Column(nullable = false)
    private String description;

    @Column(name = "day_of_month", nullable = false)
    private Integer dayOfMonth;

    /** Date of the next movement to create; moves one month ahead each time one is created. */
    @Column(name = "next_date", nullable = false)
    private LocalDate nextDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
