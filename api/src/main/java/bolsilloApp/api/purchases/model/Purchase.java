package bolsilloApp.api.purchases.model;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.model.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String description;

    @Column(name = "total_amount_cents", nullable = false)
    private Long totalAmountCents;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    @Builder.Default
    @Column(name = "total_installments", nullable = false)
    private Integer totalInstallments = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
