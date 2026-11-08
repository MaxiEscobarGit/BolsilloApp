package bolsilloApp.api.chat;

import bolsilloApp.api.chat.model.UserAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAliasRepository extends JpaRepository<UserAlias, UUID> {

    @Query("SELECT a FROM UserAlias a JOIN FETCH a.category WHERE a.user.id = :userId ORDER BY a.useCount DESC")
    List<UserAlias> findByUserId(UUID userId);

    Optional<UserAlias> findByUserIdAndAliasIgnoreCase(UUID userId, String alias);

    /**
     * Atomic upsert: creates the alias with use_count = 1, or points it to the new
     * category and increments use_count if it already exists (UNIQUE (user_id, alias)).
     */
    @Modifying
    @Query(value = """
        INSERT INTO user_aliases (user_id, alias, category_id, use_count, created_at, updated_at)
        VALUES (:userId, :alias, :categoryId, 1, NOW(), NOW())
        ON CONFLICT (user_id, alias) DO UPDATE
        SET category_id = EXCLUDED.category_id,
            use_count   = user_aliases.use_count + 1,
            updated_at  = NOW()
        """, nativeQuery = true)
    int upsert(UUID userId, String alias, UUID categoryId);
}
