package bolsilloApp.api.auth;

import bolsilloApp.api.auth.model.AuthAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, UUID> {

    /** Timestamps de los intentos mas recientes dentro de la ventana, del mas nuevo al mas viejo. */
    @Query("SELECT a.attemptedAt FROM AuthAttempt a " +
           "WHERE a.kind = :kind AND a.throttleKey = :key AND a.attemptedAt > :since " +
           "ORDER BY a.attemptedAt DESC")
    List<LocalDateTime> findRecent(AuthAttempt.Kind kind, String key, LocalDateTime since, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuthAttempt a WHERE a.kind = :kind AND a.throttleKey = :key")
    void deleteByKindAndKey(AuthAttempt.Kind kind, String key);

    @Modifying
    @Query("DELETE FROM AuthAttempt a WHERE a.attemptedAt < :before")
    void deleteOlderThan(LocalDateTime before);
}
