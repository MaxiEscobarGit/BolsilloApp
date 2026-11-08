package bolsilloApp.api.exchangerates;

import bolsilloApp.api.exchangerates.config.ExchangeRateProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sincroniza las cotizaciones USD→ARS desde la API pública. Nunca propaga excepciones:
 * un fallo de la API externa no debe tirar abajo el scheduler ni el arranque.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateSyncJob {

    private final ExchangeRateService exchangeRateService;
    private final ExchangeRateProperties properties;

    @Scheduled(cron = "${exchange-rates.cron}", zone = "${exchange-rates.zone}")
    public void syncScheduled() {
        safeRefresh("programada");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.loadOnStartup()) {
            return;
        }
        loadIfMissingForToday();
    }

    /** Carga cotizaciones solo si todavía no hay ninguna con la fecha de hoy. */
    public void loadIfMissingForToday() {
        try {
            if (exchangeRateService.hasRatesForToday()) {
                log.debug("Ya hay cotizaciones del día; no se cargan al arrancar");
                return;
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar si hay cotizaciones del día: {}", e.toString());
            return;
        }
        safeRefresh("al arrancar");
    }

    private void safeRefresh(String trigger) {
        try {
            int saved = exchangeRateService.refreshFromApi();
            log.info("Sincronización de cotizaciones ({}): {} registros", trigger, saved);
        } catch (Exception e) {
            log.error("Falló la sincronización de cotizaciones ({})", trigger, e);
        }
    }
}
