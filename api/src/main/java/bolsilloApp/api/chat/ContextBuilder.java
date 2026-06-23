package bolsilloApp.api.chat;

import bolsilloApp.api.metrics.MetricsService;
import bolsilloApp.api.metrics.dto.MetricsContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContextBuilder {

    private final MetricsService metricsService;

    public MetricsContext build(UUID userId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
