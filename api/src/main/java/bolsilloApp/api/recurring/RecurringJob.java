package bolsilloApp.api.recurring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Creates the recurring movements that are due: every morning and when the API starts. Never throws. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringJob {

    private final RecurringService recurringService;

    @Scheduled(cron = "0 15 6 * * *", zone = "America/Argentina/Buenos_Aires")
    public void daily() {
        run("diaria");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run("al arrancar");
    }

    private void run(String trigger) {
        try {
            int created = recurringService.materializeDue(LocalDate.now());
            if (created > 0) log.info("Movimientos fijos ({}): {} creados", trigger, created);
        } catch (Exception e) {
            log.error("Falló la carga de movimientos fijos ({})", trigger, e);
        }
    }
}
