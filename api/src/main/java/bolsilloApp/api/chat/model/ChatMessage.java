package bolsilloApp.api.chat.model;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.chat.ChatIntent;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private ChatIntent intent;

    @Column(name = "transactions_created", columnDefinition = "uuid[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] transactionsCreated;

    @Column(name = "model_used", length = 50)
    private String modelUsed;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
