package bolsilloApp.api.exchangerates;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.exchangerates.dto.ExchangeRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /** Última cotización USD→ARS. Sin {@code source} usa la fuente preferida del usuario. */
    @GetMapping("/latest")
    public ResponseEntity<ExchangeRateResponse> latest(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String source) {
        String effective = (source == null || source.isBlank()) ? currentUser.getPreferredExchangeSource() : source;
        return ResponseEntity.ok(exchangeRateService.getLatest(effective));
    }

    /** Historial USD→ARS entre fechas (ISO yyyy-MM-dd). Sin {@code source} devuelve todas las fuentes. */
    @GetMapping
    public ResponseEntity<List<ExchangeRateResponse>> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(exchangeRateService.getHistory(from, to, source));
    }
}
