package bolsilloApp.api.chat;

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
}
