# BolsilloApp Patterns & Gotchas

## Spring Boot 4.x / Jackson 3.x
- Jackson moved from `com.fasterxml.jackson` to `tools.jackson` package
- Import `tools.jackson.databind.ObjectMapper`, `tools.jackson.databind.JsonNode`, `tools.jackson.core.type.TypeReference`
- The old `com.fasterxml.jackson` is only available at RUNTIME scope (via jjwt-jackson)
- Do NOT use ObjectMapper in SecurityConfig -- write JSON manually via response.getWriter()

## Spring Boot 4.x tests
- `@SpyBean` / `@MockBean` were removed: use `@MockitoSpyBean` / `@MockitoBean`
  (`org.springframework.test.context.bean.override.mockito`)
- Put spy-based tests in their own class (see `PurchaseAtomicityTest`) to avoid polluting the cached context
- `@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`
- Modular starters: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, each with its `*-test` starter
- Integration tests extend `AbstractIntegrationTest` (Testcontainers `postgres:16-alpine`, TZ UTC,
  `cleanDatabase()` in `@BeforeEach` — add new tables there)

## Enum Case Convention
- All Java enums use UPPERCASE: EXPENSE, INCOME, DEBT, CHAT, MANUAL, etc.
- DB CHECK constraints updated in V2 migration to match UPPERCASE
- Native SQL queries MUST use uppercase string literals: `'EXPENSE'`, not `'expense'`
- CategoryType: EXPENSE, INCOME (was lowercase in V1, fixed in V2)

## Security
- AuthenticationEntryPoint must be explicitly configured to return 401 JSON
- Without it, Spring Security defaults to 403 for unauthenticated requests
- Permit only specific auth paths: /api/auth/register, /api/auth/login, /api/auth/refresh, /api/auth/logout
- /refresh and /logout read the token from the `Refresh-Token` header (not the body)
- BCrypt cost set to 10 explicitly

## API Conventions
- POST endpoints return 201, not 200
- DELETE endpoints return 204 No Content
- PATCH for partial updates (UpdateTransactionRequest with all-optional fields)
- Amounts in API: BigDecimal (decimal). Amounts in DB/internal: Long centavos
- Conversion: amount * 100, RoundingMode.HALF_UP

## Flyway
- In `main`: V1__crear_tablas_base.sql, V2__fix_enum_case_constraints.sql, V3__add_preferred_exchange_source_to_users.sql
- V5 merged (exchange rates lookup index); V4/V6 skipped, never reuse them. Next free: V7
- Next free number: V7 — reserve it in CLAUDE.md ("Trabajo en paralelo con varios agentes") before using it
- Never edit or renumber a migration already merged
