package bolsilloApp.api.metrics;

import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.metrics.dto.SummaryResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final EntityManager entityManager;

    public SummaryResponse getSummary(UUID userId, YearMonth period) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public MetricsContext getContext(UUID userId) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
