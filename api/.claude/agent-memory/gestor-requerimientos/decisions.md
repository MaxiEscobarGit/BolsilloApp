# Architectural Decisions

## D1: Enum case strategy
- Decision: Use UPPERCASE Java enums + V2 migration to update DB CHECK constraints
- Alternative rejected: lowercase enums (violates Java convention)
- Alternative rejected: JPA AttributeConverter (adds complexity)

## D2: Exchange rate handling
- Decision: `TransactionService.resolveExchangeRate(currency, requestRate, date, userId)` resolves the rate:
  1. currency == "ARS" -> 1
  2. request provides `exchangeRate` -> use it
  3. otherwise lookup in `exchange_rates` (currency -> ARS, transaction date, `users.preferred_exchange_source`, default "blue")
  4. no rate found -> ApiException 422 `MISSING_EXCHANGE_RATE`
- `amount_ars_cents = round(amount_cents * rate, HALF_UP)` via `computeAmountArsCents`
- PurchaseService delegates to the same method; PATCH recalculates when currency/rate changes
- History in `exchange_rates` is never recalculated
- (Superseded: the original MVP rule "rate=1.0 for every currency")

## D3: Category deletion with existing transactions
- Decision: Reassign transactions to system "Otros" category of same type, then delete
- This prevents data loss while allowing category cleanup

## D4: Refresh token reuse detection
- Decision: If a revoked token is reused, revoke ALL tokens for that user
- This is a security measure against token theft

## D5: AI intent detection
- Decision: Rule-based detection in `ChatService.detectIntent` (regex/keyword patterns)
- Not AI-based: saves API calls and latency
- Patterns: numbers+money words -> REGISTER, question marks -> QUERY, correction phrases -> CORRECT

## D6: Metrics native SQL
- Decision: Use EntityManager.createNativeQuery for all metrics
- Reason: Complex aggregations that cross tables, not suitable for JPQL
- All sums on amount_ars_cents column for currency normalization

## D7: Chat message persistence
- Decision: Save both user and assistant messages to chat_messages table
- Includes model_used, tokens_used for cost tracking
- transactions_created array links to created transaction IDs

## D8: Transaction listing — JPA Specifications over stream filtering
- Decision: Use JpaSpecificationExecutor + dynamic Specification building for findAll()
- Previous approach loaded all user transactions into memory and filtered via Java streams
- New approach builds WHERE predicates dynamically only for non-null filters (no IS NULL OR pattern)
- Pagination via PageRequest (SQL LIMIT/OFFSET), COUNT via Page.getTotalElements()
- Response changed from List<TransactionResponse> to TransactionListResponse (data, total, limit, offset)
- TransactionSpecification utility class composes predicates conditionally

## D9: AI message quota
- Decision: `ChatService` increments `users.ai_messages_used` per message, cap `MAX_AI_MESSAGES_PER_MONTH = 100`
- Reset when `ai_messages_reset_at + 1 month` has passed; over the cap -> 429 `RATE_LIMITED`
- Pending: CORRECT intent is detected but not yet processed (no update of transaction + user_aliases)
