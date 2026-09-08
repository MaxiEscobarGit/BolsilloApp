# BolsilloApp Patterns & Gotchas

## Spring Boot 4.x / Jackson 3.x
- Jackson moved from `com.fasterxml.jackson` to `tools.jackson` package
- Import `tools.jackson.databind.ObjectMapper` and `tools.jackson.databind.JsonNode`
- The old `com.fasterxml.jackson` is only available at RUNTIME scope (via jjwt-jackson)
- Do NOT use ObjectMapper in SecurityConfig -- write JSON manually via response.getWriter()

## Enum Case Convention
- All Java enums use UPPERCASE: EXPENSE, INCOME, DEBT, CHAT, MANUAL, etc.
- DB CHECK constraints updated in V2 migration to match UPPERCASE
- Native SQL queries MUST use uppercase string literals: `'EXPENSE'`, not `'expense'`
- CategoryType: EXPENSE, INCOME (was lowercase in V1, fixed in V2)

## Security
- AuthenticationEntryPoint must be explicitly configured to return 401 JSON
- Without it, Spring Security defaults to 403 for unauthenticated requests
- Permit only specific auth paths: /api/auth/register, /api/auth/login, /api/auth/refresh, /api/auth/logout
- BCrypt cost set to 10 explicitly

## API Conventions
- POST endpoints return 201, not 200
- DELETE endpoints return 204 No Content
- PATCH for partial updates (UpdateTransactionRequest with all-optional fields)
- Amounts in API: BigDecimal (decimal). Amounts in DB/internal: Long centavos
- Conversion: amount * 100, RoundingMode.HALF_UP

## Flyway
- Latest migration: V2__fix_enum_case_constraints.sql
- Next migration number: V3
