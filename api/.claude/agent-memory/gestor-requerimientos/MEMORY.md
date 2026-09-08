# BolsilloApp Gestor-Requerimientos Memory

## Project State (last updated: 2026-09-05)
- **All MVP modules implemented**: Auth, Categories, Transactions, Purchases, Debts, Metrics, Chat/AI
- **Flyway migrations**: V1 (schema + seeds), V2 (fix enum case constraints to UPPERCASE)
- **67 Java source files** compile successfully on Spring Boot 4.1.0 + Java 21
- **BUG-001 fixed**: Added AuthenticationEntryPoint returning 401 JSON, not default 403

## Critical Patterns
- See [patterns.md](patterns.md) for detailed patterns

## Key Decisions
- See [decisions.md](decisions.md) for architectural decisions made

## File Counts by Module
- auth: 8 files (controller, service, 2 repos, 2 models, 3 DTOs)
- categories: 6 files (controller, service, repo, model, enum, 2 DTOs)
- transactions: 7 files (controller, service, repo, model, 2 enums, 3 DTOs)
- purchases: 5 files (controller, service, repo, model, 2 DTOs)
- debts: 7 files (controller, service, repo, model, 2 enums, 2 DTOs)
- metrics: 3 files (controller, service, 2 DTOs)
- chat: 10 files (controller, 2 services, 2 repos, 2 models, enum, 4 DTOs)
- shared: 7 files (security x3, exception x5, config x1)
