package bolsilloApp.api.categories;

import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.categories.model.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE c.user IS NULL OR c.user.id = :userId ORDER BY c.isSystem DESC, c.name ASC")
    List<Category> findAllForUser(UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndType(UUID userId, String name, CategoryType type);

    boolean existsByIsSystemTrueAndNameIgnoreCaseAndType(String name, CategoryType type);

    @Query("SELECT c FROM Category c WHERE c.isSystem = true AND c.name = 'Otros' AND c.type = :type")
    Optional<Category> findSystemOtrosByType(CategoryType type);
}
