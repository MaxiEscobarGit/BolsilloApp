# Architectural Decisions

## D1: Enum case strategy
- Decision: Use UPPERCASE Java enums + V2 migration to update DB CHECK constraints
- Alternative rejected: lowercase enums (violates Java convention)
- Alternative rejected: JPA AttributeConverter (adds complexity)

## D2: Exchange rate handling
- Decision: MVP stores rate=1.0 for all currencies, amount_ars_cents = amount_cents
- TODO for post-MVP: look up exchange_rates table when currency != ARS

## D3: Category deletion with existing transactions
- Decision: Reassign transactions to system "Otros" category of same type, then delete
- This prevents data loss while allowing category cleanup

## D4: Refresh token reuse detection
- Decision: If a revoked token is reused, revoke ALL tokens for that user
- This is a security measure against token theft

## D5: AI intent detection
- Decision: Rule-based detection in ChatService (regex/keyword patterns)
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
