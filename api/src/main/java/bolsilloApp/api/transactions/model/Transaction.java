package bolsilloApp.api.transactions.model;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.purchases.model.Purchase;
import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

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

    @Column(name = "amount_ars_cents", nullable = false)
    private Long amountArsCents;

    @Builder.Default
    @Column(name = "exchange_rate", nullable = false)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(nullable = false)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
