# CLAUDE.md — Contexto del proyecto

## Qué es esto

App de finanzas personales con un asistente AI conversacional como diferencial central.
Los usuarios registran gastos, ingresos y deudas hablando en lenguaje natural.
El asistente parsea, categoriza y responde preguntas sobre métricas financieras.

Este directorio (`api/`) es el backend. El frontend web y la app mobile viven en repos separados.

---

## Stack técnico

| Capa            | Tecnología                                                                 |
|-----------------|----------------------------------------------------------------------------|
| Backend         | Spring Boot **4.1.0** · Java 21 · Maven (`./mvnw`)                         |
| Web             | `spring-boot-starter-webmvc` (servlet, no reactivo)                        |
| Base de datos   | PostgreSQL 16 (Supabase en prod · `docker-compose.yml` en local)           |
| ORM             | Spring Data JPA · Hibernate (`ddl-auto=validate`)                          |
| Migraciones     | Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`)       |
| Auth            | Spring Security · JWT (jjwt 0.12.6) · refresh tokens rotativos             |
| JSON            | Jackson **3** (paquete `tools.jackson`, ver gotchas)                       |
| HTTP client     | WebFlux `WebClient` (solo para llamar a Anthropic)                         |
| Config local    | `spring-dotenv` 4.0.0 (carga `api/.env` como properties)                   |
| Boilerplate     | Lombok                                                                     |
| AI              | Anthropic API — Haiku para parseo, Sonnet para análisis                    |
| Tests           | JUnit 5 · MockMvc · Testcontainers 1.20.4 (`postgres:16-alpine`)           |
| Frontend web    | Next.js (repo separado)                                                    |
| Mobile          | Expo / React Native (repo separado)                                        |
| Despliegue      | Railway (backend) · Supabase (DB)                                          |

---

## Estructura de paquetes

Monolito modular. Cada dominio es un paquete autónomo con su propio controller,
service, repository, model y dto. Los paquetes no se importan entre sí salvo
dependencias explícitas de negocio (ej. `purchases` → `transactions`,
`transactions` → `exchangerates`, `chat` → `metrics` / `transactions`).

Paquete base: **`bolsilloApp.api`** (`src/main/java/bolsilloApp/api/`).

```text
bolsilloApp.api/
├── ApiApplication
├── auth/
│   ├── AuthController
│   ├── AuthService
│   ├── AuthRepository            (JpaRepository<User, UUID>)
│   ├── RefreshTokenRepository
│   ├── dto/     (LoginRequest, RegisterRequest, AuthResponse)
│   └── model/   (User, RefreshToken)
├── categories/
│   ├── CategoryController
│   ├── CategoryService
│   ├── CategoryRepository
│   ├── dto/     (CreateCategoryRequest, CategoryResponse)
│   └── model/   (Category, CategoryType)
├── transactions/
│   ├── TransactionController
│   ├── TransactionService        ← resolveExchangeRate() / computeAmountArsCents()
│   ├── TransactionRepository     (+ JpaSpecificationExecutor)
│   ├── TransactionSpecification  ← predicados dinámicos para filtros + paginación SQL
│   ├── TransactionType · TransactionSource   (enums)
│   ├── dto/     (CreateTransactionRequest, UpdateTransactionRequest,
│   │             TransactionResponse, TransactionListResponse)
│   └── model/   (Transaction)
├── purchases/
│   ├── PurchaseController
│   ├── PurchaseService           ← genera las N transactions hijas automáticamente
│   ├── PurchaseRepository
│   ├── dto/     (CreatePurchaseRequest, PurchaseResponse)
│   └── model/   (Purchase)
├── debts/
│   ├── DebtController
│   ├── DebtService
│   ├── DebtRepository
│   ├── DebtDirection · DebtStatus  (enums)
│   ├── dto/     (CreateDebtRequest, DebtResponse)
│   └── model/   (Debt)
├── exchangerates/
│   ├── ExchangeRateService       ← getRate(from, to, date, source)
│   ├── ExchangeRateRepository
│   └── model/   (ExchangeRate)
├── metrics/
│   ├── MetricsController
│   ├── MetricsService            ← todas las queries SQL agregadas van acá
│   └── dto/     (SummaryResponse, MetricsContext)
├── chat/
│   ├── ChatController
│   ├── ChatService               ← orquesta el flujo del asistente + detectIntent()
│   ├── AIService                 ← única clase que llama a Anthropic
│   ├── ContextBuilder            ← arma el contexto compacto para el AI
│   ├── ChatIntent                (enum: REGISTER, QUERY, CORRECT, CHAT)
│   ├── ChatMessageRepository
│   ├── UserAliasRepository
│   ├── dto/     (ChatRequest, ChatResponse, ChatHistoryResponse, AiParsedResponse)
│   └── model/   (ChatMessage, UserAlias)
└── shared/
    ├── security/   (JwtFilter, JwtService, SecurityConfig)
    ├── exception/  (GlobalExceptionHandler, ApiException, BusinessRuleException,
    │                ConflictException, ResourceNotFoundException, UnauthorizedException)
    └── config/     (WebClientConfig)
```

Tests en `src/test/java/bolsilloApp/api/`: un `*IntegrationTest` por módulo,
`security/UserIsolationIntegrationTest`, `purchases/PurchaseAtomicityTest` y
`support/` (`AbstractIntegrationTest`, `TestDataFactory`).

---

## Endpoints

Todos bajo `/api`. Todos requieren `Authorization: Bearer <access token>` salvo los de `/api/auth/*`.
El usuario se obtiene con `@AuthenticationPrincipal User currentUser`.

### Auth — `/api/auth` (públicos)

| Método | Ruta        | Body / headers                | Respuesta              |
|--------|-------------|-------------------------------|------------------------|
| POST   | `/register` | `RegisterRequest`             | 201 `AuthResponse`     |
| POST   | `/login`    | `LoginRequest`                | 200 `AuthResponse`     |
| POST   | `/refresh`  | header `Refresh-Token`        | 200 `AuthResponse`     |
| POST   | `/logout`   | header `Refresh-Token`        | 204                    |

### Categories — `/api/categories`

| Método | Ruta    | Notas                                                           | Respuesta                    |
|--------|---------|-----------------------------------------------------------------|------------------------------|
| GET    | `/`     | Sistema + custom del usuario                                    | 200 `List<CategoryResponse>` |
| POST   | `/`     | `CreateCategoryRequest`                                         | 201 `CategoryResponse`       |
| DELETE | `/{id}` | Reasigna sus transactions a "Otros" del mismo tipo antes de borrar | 204                       |

### Transactions — `/api/transactions`

| Método | Ruta    | Notas                                                                 | Respuesta                      |
|--------|---------|-----------------------------------------------------------------------|--------------------------------|
| POST   | `/`     | `CreateTransactionRequest` (`exchangeRate` opcional)                  | 201 `TransactionResponse`      |
| GET    | `/`     | Query: `from`, `to`, `categoryId`, `type`, `limit`=50 (máx 200), `offset`=0. Rango ≤ 1 año | 200 `TransactionListResponse {data, total, limit, offset}` |
| GET    | `/{id}` |                                                                       | 200 `TransactionResponse`      |
| PATCH  | `/{id}` | `UpdateTransactionRequest` (todos los campos opcionales)              | 200 `TransactionResponse`      |
| DELETE | `/{id}` | Soft delete (`deleted_at`)                                            | 204                            |

### Purchases — `/api/purchases`

| Método | Ruta    | Notas                                              | Respuesta                    |
|--------|---------|----------------------------------------------------|------------------------------|
| POST   | `/`     | `CreatePurchaseRequest`; genera N cuotas atómicamente | 201 `PurchaseResponse`    |
| GET    | `/`     |                                                    | 200 `List<PurchaseResponse>` |
| GET    | `/{id}` |                                                    | 200 `PurchaseResponse`       |

### Debts — `/api/debts`

| Método | Ruta        | Notas                                  | Respuesta                |
|--------|-------------|----------------------------------------|--------------------------|
| POST   | `/`         | `CreateDebtRequest`                    | 201 `DebtResponse`       |
| GET    | `/`         | Query opcional: `status`, `direction`  | 200 `List<DebtResponse>` |
| GET    | `/{id}`     |                                        | 200 `DebtResponse`       |
| PATCH  | `/{id}/pay` | Marca como pagada                      | 200 `DebtResponse`       |
| DELETE | `/{id}`     |                                        | 204                      |

### Metrics — `/api/metrics`

| Método | Ruta       | Notas                                                    | Respuesta              |
|--------|------------|----------------------------------------------------------|------------------------|
| GET    | `/summary` | Query: `period`=`month` (default), o `from` / `to`       | 200 `SummaryResponse`  |

### Chat — `/api/chat`

| Método | Ruta       | Notas                                                    | Respuesta                        |
|--------|------------|----------------------------------------------------------|----------------------------------|
| POST   | `/`        | `ChatRequest`                                            | 200 `ChatResponse`               |
| GET    | `/history` | Query: `limit`=20, `before` (`LocalDateTime`, opcional)  | 200 `List<ChatHistoryResponse>`  |

---

## Convenciones obligatorias

### Entidades JPA

- Siempre `@Data @Builder @NoArgsConstructor @AllArgsConstructor` de Lombok.
- IDs son `UUID` generados con `@GeneratedValue(strategy = GenerationType.UUID)`.
- Montos siempre en centavos como `Long` (`amount_cents`). Nunca `Double` ni `Float`.
- Tipos de cambio como `BigDecimal` (`exchange_rate`).
- Timestamps con `@CreationTimestamp` / `@UpdateTimestamp`.
- Soft delete: columna `deleted_at` nullable. Nunca borrar filas de `transactions`.
- Enums mapeados con `@Enumerated(EnumType.STRING)` y valores en MAYÚSCULAS
  (`EXPENSE`, `INCOME`, `OWE`, `PAID`, `REGISTER`…). Los CHECK de la DB también (V2).

### Controllers

- `@RequiredArgsConstructor` en lugar de `@Autowired`.
- Siempre devolver `ResponseEntity<T>`.
- Validación de entrada con `@Valid` + DTOs anotados.
- Un controller por dominio, sin lógica de negocio — solo delega al service.
- POST que crea → 201. DELETE → 204. Actualizaciones parciales → PATCH.

### Services

- Toda la lógica de negocio vive en el service, nunca en el controller.
- Transacciones con `@Transactional` donde corresponda.
- Nunca exponer entidades JPA directamente — siempre mapear a DTOs.
- Toda query filtra por `userId` del usuario autenticado (aislamiento entre usuarios,
  cubierto por `UserIsolationIntegrationTest`).

### DTOs

- Records de Java para requests y responses: `public record LoginRequest(...)`.
- Validaciones con `@NotNull`, `@NotBlank`, `@Email`, `@Min`, `@Max`.
- Separar Request de Response — nunca reutilizar el mismo DTO.
- En la API los montos viajan como `BigDecimal` decimal; se convierten a centavos
  con `amount * 100` y `RoundingMode.HALF_UP`.

### Repositorios

- Extender `JpaRepository<Entity, UUID>`.
- Queries complejas con `@Query` JPQL o queries nativas con `nativeQuery = true`.
- Filtros dinámicos con `JpaSpecificationExecutor` + `Specification` (ver `TransactionSpecification`),
  nunca cargar todo en memoria y filtrar con streams.
- Las queries de métricas van en `MetricsService` con queries nativas SQL, no en
  repositorios — son agregaciones que cruzan múltiples tablas. Siempre sumar sobre
  `amount_ars_cents` y usar literales de enum en MAYÚSCULAS (`'EXPENSE'`).

### Seguridad

- La API key de Anthropic vive SOLO en variables de entorno. Nunca en código.
- JWT access token: 15 minutos. Refresh token: 30 días, rotativo. Si se reutiliza un
  refresh token revocado se revocan todos los tokens del usuario.
- Todo endpoint requiere JWT salvo `/api/auth/register`, `/api/auth/login`,
  `/api/auth/refresh` y `/api/auth/logout`.
- Sin JWT válido la respuesta es 401 JSON (via `AuthenticationEntryPoint` en `SecurityConfig`).
- Rate limiting de mensajes AI por usuario: campo `ai_messages_used` en `users`,
  resetea mensualmente con `ai_messages_reset_at`.

### Manejo de errores

- `GlobalExceptionHandler` con `@RestControllerAdvice` centraliza todos los errores.
- Respuesta de error estándar: `{ "error": "...", "code": "...", "status": 400 }`.
- Hoy los services lanzan `ApiException(message, code, status)`; las subclases de
  `shared/exception/` existen pero todavía no se usan.
- Nunca exponer stack traces en producción.

---

## Tipo de cambio

`transactions` guarda el monto original (`amount_cents`, `currency`) y el convertido
(`amount_ars_cents`, `exchange_rate`). `TransactionService.resolveExchangeRate(...)`
decide la tasa, y `PurchaseService` delega en él:

1. `currency == "ARS"` → `1`.
2. El request trae `exchangeRate` → se usa ese.
3. Si no, se busca en `exchange_rates` por (`currency` → `ARS`, `transaction_date`,
   `users.preferred_exchange_source`, default `"blue"`).
4. Si no hay tasa → `422 MISSING_EXCHANGE_RATE`.

`amount_ars_cents = round(amount_cents * rate, HALF_UP)`. El histórico de
`exchange_rates` nunca se recalcula.

---

## Flujo del asistente AI — decisiones ya tomadas

Este es el flujo que sigue `ChatService` para cada mensaje. **No cambiar sin
consenso** — afecta costos de API y precisión.

1. Recibir mensaje del usuario
2. Consultar `MetricsService.getContext(userId)`  ← SQL, no AI
3. Consultar `user_aliases` del usuario            ← para enriquecer el prompt
4. Detectar intent probable:
   - tiene monto numérico            → intent: `REGISTER` → modelo: Haiku
   - empieza con ¿ o pregunta        → intent: `QUERY`    → modelo: Sonnet
   - menciona "eso era" / "no era"   → intent: `CORRECT`  → modelo: Haiku
   - otro                            → intent: `CHAT`     → modelo: Haiku
5. Llamar a Anthropic con prompt estructurado
6. Parsear respuesta JSON del modelo
7. Si intent == `REGISTER`: insertar en `transactions` via `TransactionService`
8. Si intent == `CORRECT`: actualizar transaction + `user_aliases`
9. Guardar en `chat_messages` con `model_used` y `tokens_used`
10. Decrementar `ai_messages_used` del usuario
11. Devolver `ChatResponse` al cliente

**Regla crítica:** el modelo nunca recibe el historial completo de transacciones.
Solo recibe el JSON compacto de `MetricsContext` (totales, por categoría, cuotas
próximas, aliases). Las sumas y agregaciones las hace SQL, no el AI.

> Estado actual del código (a revisar por el agente de chat): la detección de intent
> es por reglas (`ChatService.detectIntent`); los IDs de modelo están hardcodeados en
> `AIService`; el contador `ai_messages_used` se incrementa por mensaje con tope de
> 100/mes (`MAX_AI_MESSAGES_PER_MONTH`, 429 `RATE_LIMITED` al superarlo); el paso 8
> (`CORRECT`: actualizar transaction + `user_aliases`) todavía no está implementado; y el
> paso 7 hoy guarda con `TransactionRepository` directo en vez de `TransactionService`.

---

## Esquema de base de datos

Migraciones en `src/main/resources/db/migration/`.
Nomenclatura: `V{n}__{descripcion_snake_case}.sql`. Nunca editar una migración ya mergeada.

| Versión | Archivo                                         | Estado                |
|---------|-------------------------------------------------|-----------------------|
| V1      | `V1__crear_tablas_base.sql` (esquema + seeds)   | en `main`             |
| V2      | `V2__fix_enum_case_constraints.sql`             | en `main`             |
| V3      | `V3__add_preferred_exchange_source_to_users.sql`| en `main`             |
| V4      | — chat                                          | reservada             |
| V5      | — tipo de cambio                                | reservada             |
| V6      | — seguridad                                     | reservada             |

### Tablas MVP

- `users` — incluye `ai_messages_used`, `ai_messages_reset_at`, `preferred_exchange_source`
- `refresh_tokens`
- `categories` — sistema (`user_id` NULL) y custom (`user_id` con valor)
- `exchange_rates` — tipo de cambio histórico, nunca se recalcula
- `transactions` — montos en centavos, guarda original + convertido a ARS
- `purchases` — compras en cuotas, genera N transactions hijas
- `debts` — `direction`: `OWE` | `OWED`
- `chat_messages` — auditoría de AI: `model_used`, `tokens_used`
- `user_aliases` — "el chino" → Supermercado, se actualiza al corregir

### Tablas V2 (no implementar ahora)

- `budgets`
- `alerts`

---

## Variables de entorno requeridas

```text
DATABASE_URL          jdbc:postgresql://...supabase.co:5432/postgres
DATABASE_USER         postgres
DATABASE_PASSWORD     ...
JWT_SECRET            string aleatorio de mínimo 256 bits (openssl rand -base64 32)
ANTHROPIC_API_KEY     sk-ant-...
```

- En desarrollo: archivo `api/.env` (ignorado por git, lo carga `spring-dotenv`).
  Plantilla en `api/.env.example`. La DB local se levanta con `docker compose up -d` desde la raíz.
- En producción: variables de entorno en Railway.
- `application.properties` tiene defaults solo aptos para desarrollo; nunca usarlos en prod.

---

## Build y tests

```bash
cd api
./mvnw spring-boot:run      # arranca la API (necesita Postgres local)
./mvnw test                 # tests de integración (necesita Docker para Testcontainers)
```

- Los tests extienden `AbstractIntegrationTest`: levanta `postgres:16-alpine` con
  Testcontainers, fuerza TZ UTC y limpia las tablas en `@BeforeEach`. Si agregás una
  tabla, sumala a `cleanDatabase()`.
- La JVM corre con `-Duser.timezone=UTC` y la URL JDBC con `options=-c timezone=UTC`
  (bug de Windows con `America/Buenos_Aires`).

---

## Gotchas de Spring Boot 4 / Jackson 3

- **Jackson 3 cambió de paquete**: importar `tools.jackson.databind.ObjectMapper`,
  `tools.jackson.databind.JsonNode`, `tools.jackson.core.type.TypeReference`.
  `com.fasterxml.jackson` solo está en runtime (vía `jjwt-jackson`): no compila si lo importás.
- **No usar `ObjectMapper` en `SecurityConfig`**: el JSON de 401/403 se escribe a mano con
  `response.getWriter()`.
- **Tests**: `@SpyBean` / `@MockBean` ya no existen. Usar `@MockitoSpyBean` / `@MockitoBean`
  (`org.springframework.test.context.bean.override.mockito`). Un test con spy en un bean
  compartido va en su propia clase (ver `PurchaseAtomicityTest`) para no ensuciar el contexto cacheado.
- **Starters modulares**: web es `spring-boot-starter-webmvc`, Flyway es
  `spring-boot-starter-flyway`, y cada uno tiene su `*-test` starter.
  `@AutoConfigureMockMvc` se importa de `org.springframework.boot.webmvc.test.autoconfigure`.
- `AuthenticationEntryPoint` explícito: sin él Spring Security devuelve 403 en vez de 401.

---

## Trabajo en paralelo con varios agentes

Cuando varios agentes (o personas) trabajan a la vez sobre el backend:

1. **Una rama y un PR por agente.** Cada agente trabaja en su rama
   (`claude/agente-<modulo>`), con alcance de archivos acotado a su módulo
   (ver `.claude/agents/*.md`). No tocar archivos de otro módulo; si hace falta,
   coordinarlo en el PR.
2. **Numeración de migraciones Flyway reservada antes de empezar.** Dos ramas con la
   misma `V{n}` rompen Flyway al mergear. Estado actual:
   - `V4` → chat
   - `V5` → tipo de cambio
   - `V6` → seguridad
   - siguiente libre: `V7` (reservarla acá antes de usarla)
   Si un agente necesita más de una migración, reserva un número nuevo; nunca reutiliza
   ni renumera una ya mergeada.
3. **Bloques propios en `application.properties`.** Cada módulo agrega sus properties en
   un bloque con comentario de encabezado propio (`# Chat`, `# Exchange rates`,
   `# Security`…) y prefijo propio (`app.chat.*`, `app.exchange-rates.*`,
   `app.security.*`). No reordenar ni editar bloques ajenos: minimiza conflictos.
4. **Mergear de a uno.** Un PR se mergea a `main` solo con `./mvnw test` en verde.
   El resto hace **rebase sobre `main`** (`git fetch origin main && git rebase origin/main`),
   vuelve a correr los tests y recién ahí se mergea el siguiente.
5. Si un cambio toca un contrato compartido (DTO, firma de service, entidad `User`),
   avisarlo en la descripción del PR para que los demás agentes lo absorban en su rebase.

---

## Lo que NO hacer

- No usar microservicios — es un monolito modular, así se queda en el MVP.
- No llamar a Anthropic desde el frontend — siempre pasa por el backend.
- No usar `Double`/`Float` para montos — siempre `Long` en centavos.
- No exponer entidades JPA en los responses — siempre DTOs.
- No hacer `DELETE` físico en `transactions` — usar soft delete con `deleted_at`.
- No pasar el historial de transacciones al AI — solo el contexto compacto.
- No implementar budgets ni alerts en el MVP — están marcadas como V2.
- No usar `@Autowired` en campos — siempre `@RequiredArgsConstructor`.
- No poner lógica de negocio en controllers — solo en services.
- No subir `.env` al repositorio — está en `.gitignore`.

---

## Roadmap de implementación

### MVP (implementar ahora)

- [x] Migraciones Flyway — esquema completo
- [x] Auth — register, login, refresh, logout
- [x] Categories — CRUD
- [x] Transactions — CRUD + filtros por período y categoría
- [x] Purchases — crear compra en cuotas (genera N transactions)
- [x] Debts — CRUD + marcar como pagada
- [x] Metrics — summary por período, context para el AI
- [ ] Chat — integración Anthropic con el flujo descrito arriba (falta procesar el intent `CORRECT`)

### V2 (no tocar ahora)

- Budgets y alerts con `@Scheduled`
- Foto de tickets (visión)
- Cuentas compartidas / familiares
- Modo oscuro (frontend)
- Export CSV
