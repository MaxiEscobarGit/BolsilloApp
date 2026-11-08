# BolsilloApp Gestor-Requerimientos Memory

## Project State (last updated: 2026-11-08)
- **MVP modules implemented**: Auth, Categories, Transactions, Purchases, Debts, Metrics, Chat/AI, ExchangeRates
  (Chat: CORRECT intent detected but not processed yet)
- **Flyway migrations in main**: V1 (schema + seeds), V2 (fix enum case to UPPERCASE), V3 (preferred_exchange_source on users)
- **Reserved**: V4 chat, V5 exchange rates, V6 security — next free V7
- **81 tests pass** on Spring Boot 4.1.0 + Java 21
- **BUG-001 fixed**: AuthenticationEntryPoint returning 401 JSON
- **PERF-001 fixed**: TransactionService.findAll() uses JPA Specifications + SQL pagination

## Exchange Rate Feature (added 2026-09-10)
- New package: `exchangerates` (entity, repo, service)
- Resolution: ARS=1 -> request rate -> DB lookup -> 422 error
- PurchaseService delegates to TransactionService.resolveExchangeRate()
- User.preferredExchangeSource (default "blue") via V3 migration
- CreateTransactionRequest/CreatePurchaseRequest: optional `BigDecimal exchangeRate` (7th param)

## Critical Patterns
- See [patterns.md](patterns.md) for detailed patterns
- `@MockitoSpyBean` (NOT `@SpyBean`) for Spring Boot 4.x test bean overrides
- Jackson 3: `tools.jackson.*`, not `com.fasterxml.jackson.*`

## Parallel work
- Specialist agents in `.claude/agents/`: chat-ai, seguridad, tipo-cambio, infra (one branch + PR each)
- Rules in CLAUDE.md section "Trabajo en paralelo con varios agentes"

## Key Decisions
- See [decisions.md](decisions.md) for architectural decisions

## File Counts by Module (.java under src/main, 2026-11-08)
- auth: 9 | categories: 7 | transactions: 11 | purchases: 6 | debts: 8
- metrics: 4 | chat: 13 | exchangerates: 3 | shared: 10

## Test Counts (81 executions in last run, including parameterized cases)
- Auth:16, Category:8, Debt:5, Metrics:7, PurchaseAtomicity:1, Purchase:10, Security:10, Transaction:23, App:1

## API Notes
- GET /api/transactions returns `TransactionListResponse {data, total, limit, offset}`
- AbstractIntegrationTest.cleanDatabase() cleans exchange_rates table
