package bolsilloApp.api.transactions.dto;

import java.util.List;

public record TransactionListResponse(
        List<TransactionResponse> data,
        long total,
        int limit,
        int offset
) {}
