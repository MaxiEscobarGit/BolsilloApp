package bolsilloApp.api.chat;

import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.categories.model.CategoryType;
import bolsilloApp.api.metrics.MetricsService;
import bolsilloApp.api.metrics.dto.MetricsContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContextBuilder {

    private final MetricsService metricsService;
    private final UserAliasRepository userAliasRepository;
    private final CategoryRepository categoryRepository;

    public MetricsContext buildMetricsContext(UUID userId) {
        return metricsService.getContext(userId);
    }

    public List<Map<String, String>> buildAliases(UUID userId) {
        return userAliasRepository.findByUserId(userId).stream()
                .map(a -> Map.of(
                        "alias", a.getAlias(),
                        "category", a.getCategory().getName()
                ))
                .toList();
    }

    /** System categories + the user's custom categories. */
    public List<Category> buildCategories(UUID userId) {
        return categoryRepository.findAllForUser(userId);
    }

    public static List<String> namesOfType(List<Category> categories, CategoryType type) {
        return categories.stream()
                .filter(c -> c.getType() == type)
                .map(Category::getName)
                .distinct()
                .toList();
    }
}
