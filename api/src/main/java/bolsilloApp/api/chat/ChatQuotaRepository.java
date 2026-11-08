package bolsilloApp.api.chat;

import bolsilloApp.api.auth.model.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Atomic operations over users.ai_messages_used, owned by the chat module.
 */
public interface ChatQuotaRepository extends Repository<User, UUID> {

    @Query(value = "SELECT ai_messages_used FROM users WHERE id = :userId", nativeQuery = true)
    Optional<Integer> findAiMessagesUsed(UUID userId);

    /** Resets the counter if the last reset is older than {@code threshold}. */
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE users SET ai_messages_used = 0, ai_messages_reset_at = :now
        WHERE id = :userId AND ai_messages_reset_at < :threshold
        """, nativeQuery = true)
    int resetIfExpired(UUID userId, LocalDateTime threshold, LocalDateTime now);

    /**
     * Consumes one AI message only if the user is still below {@code limit}.
     * Returns 1 if consumed, 0 if the quota is exhausted. Safe under concurrency.
     */
    @Modifying
    @Query(value = """
        UPDATE users SET ai_messages_used = ai_messages_used + 1
        WHERE id = :userId AND ai_messages_used < :limit
        """, nativeQuery = true)
    int tryConsume(UUID userId, int limit);
}
