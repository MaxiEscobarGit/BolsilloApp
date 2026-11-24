package bolsilloApp.api.chat;

import bolsilloApp.api.metrics.MetricsService;
import bolsilloApp.api.metrics.dto.SummaryResponse;
import bolsilloApp.api.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * "¿Cómo vengo este mes?": the assistant compares this month (so far) with last month and writes a short summary.
 * Costs one message of the monthly AI quota, charged only when the AI answered.
 */
@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    private final MetricsService metricsService;
    private final AIService aiService;
    private final ChatQuotaRepository chatQuotaRepository;
    private final TransactionTemplate transactionTemplate;

    public record MonthlySummaryResponse(String summary) {}

    public MonthlySummaryResponse summarize(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(s -> chatQuotaRepository.resetIfExpired(userId, now.minusMonths(1), now));
        int used = chatQuotaRepository.findAiMessagesUsed(userId)
                .orElseThrow(() -> new ApiException("User not found", "NOT_FOUND", 404));
        if (used >= ChatService.MAX_AI_MESSAGES_PER_MONTH) throw rateLimited();

        YearMonth month = YearMonth.now();
        LocalDate today = LocalDate.now();
        SummaryResponse current = metricsService.getSummary(userId, "custom", month.atDay(1), today);
        // Same span of last month (1st to today's day), so a half month isn't compared with a whole one.
        YearMonth last = month.minusMonths(1);
        SummaryResponse previous = metricsService.getSummary(userId, "custom", last.atDay(1),
                last.atDay(Math.min(today.getDayOfMonth(), last.lengthOfMonth())));

        String text = aiService.complete(SYSTEM_PROMPT, describe(current, "este mes, del 1 a hoy " + today)
                + "\n" + describe(previous, "el mes pasado, mismo tramo de días"));

        Integer charged = transactionTemplate.execute(s -> chatQuotaRepository.tryConsume(userId, ChatService.MAX_AI_MESSAGES_PER_MONTH));
        if (charged == null || charged == 0) throw rateLimited();
        return new MonthlySummaryResponse(text);
    }

    static final String SYSTEM_PROMPT = """
            Sos el asistente de finanzas personales de la app Bolsillo. Escribí en español rioplatense, de vos, cálido y directo.
            Con los datos que te paso, hacé un resumen de cómo viene el mes comparado con el mismo tramo del mes pasado:
            - Máximo 4 oraciones cortas, sin títulos, sin listas, sin emojis y sin markdown.
            - Arrancá por lo más importante: si gastó más o menos y cuánto.
            - Nombrá la categoría que más cambió, con montos en pesos redondeados (por ejemplo $45.000).
            - Cerrá con un consejo concreto y amable. No inventes datos que no estén.
            - Si no hay movimientos, decilo y sugerí empezar a cargarlos.""";

    private static String describe(SummaryResponse s, String label) {
        StringBuilder sb = new StringBuilder("Datos de ").append(label).append(" (en pesos):\n")
                .append("- Ingresos: $").append(s.totalIncomeCents() / 100).append('\n')
                .append("- Gastos: $").append(s.totalExpenseCents() / 100).append('\n')
                .append("- Gastos por categoría:");
        s.byCategory().stream()
                .filter(c -> "EXPENSE".equals(c.type()))
                .forEach(c -> sb.append(' ').append(c.categoryName()).append("=$").append(c.totalCents() / 100));
        return sb.append('\n').toString();
    }

    private static ApiException rateLimited() {
        return new ApiException("Monthly AI message limit reached", "RATE_LIMITED", 429);
    }
}
