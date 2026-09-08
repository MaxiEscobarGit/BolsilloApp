package bolsilloApp.api.chat;

import bolsilloApp.api.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.user.id = :userId
        AND (:before IS NULL OR m.createdAt < :before)
        ORDER BY m.createdAt DESC
        """)
    List<ChatMessage> findHistoryBefore(UUID userId, LocalDateTime before, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.user.id = :userId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentByUserId(UUID userId, Pageable pageable);
}
