package bolsilloApp.api.chat;

import bolsilloApp.api.chat.model.UserAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAliasRepository extends JpaRepository<UserAlias, UUID> {

    List<UserAlias> findByUserId(UUID userId);

    Optional<UserAlias> findByUserIdAndAliasIgnoreCase(UUID userId, String alias);
}
