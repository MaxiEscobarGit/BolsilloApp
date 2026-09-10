# BolsilloApp Gestor-Requerimientos Memory

## Project State (last updated: 2026-09-10)
- **All MVP modules implemented**: Auth, Categories, Transactions, Purchases, Debts, Metrics, Chat/AI, ExchangeRates
- **Flyway migrations**: V1 (schema + seeds), V2 (fix enum case to UPPERCASE), V3 (preferred_exchange_source on users)
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

## Key Decisions
- See [decisions.md](decisions.md) for architectural decisions

## File Counts by Module
- auth: 8 | categories: 6 | transactions: 9 | purchases: 5 | debts: 7
- metrics: 3 | chat: 10 | exchangerates: 3 | shared: 7

## Test Counts (81 total)
- Auth:16, Category:8, Debt:5, Metrics:7, PurchaseAtomicity:1, Purchase:10, Security:10, Transaction:23, App:1

## API Notes
- GET /api/transactions returns `TransactionListResponse {data, total, limit, offset}`
- AbstractIntegrationTest.cleanDatabase() cleans exchange_rates table
