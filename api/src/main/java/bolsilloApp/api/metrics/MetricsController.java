package bolsilloApp.api.metrics;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.metrics.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String period) {
        YearMonth yearMonth = period != null ? YearMonth.parse(period) : YearMonth.now();
        return ResponseEntity.ok(metricsService.getSummary(currentUser.getId(), yearMonth));
    }
}
