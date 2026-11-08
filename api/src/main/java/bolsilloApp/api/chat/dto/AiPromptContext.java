package bolsilloApp.api.chat.dto;

import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.transactions.dto.TransactionResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Compact context sent to the AI. Never contains the transaction history:
 * only aggregated metrics, aliases, the available categories and, for CORRECT,
 * the single transaction being corrected.
 */
public record AiPromptContext(
        MetricsContext metrics,
        List<Map<String, String>> aliases,
        List<String> expenseCategories,
        List<String> incomeCategories,
        TransactionResponse correctionTarget,
        LocalDate today
) {}
