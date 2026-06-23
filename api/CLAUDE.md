CLAUDE.md — Contexto del proyecto

Qué es esto

App de finanzas personales con asistente AI conversacional como diferencial central.
Los usuarios registran gastos, ingresos y deudas hablando en lenguaje natural.
El asistente parsea, categoriza y responde preguntas sobre métricas financieras.


Stack técnico

CapaTecnologíaBackendSpring Boot 3.3.x · Java 21 · MavenBase de datosPostgreSQL vía SupabaseORMSpring Data JPA · HibernateMigracionesFlywayAuthSpring Security · JWT (jjwt 0.12.6) · refresh tokens rotativosHTTP clientWebFlux WebClient (para llamar a Anthropic)BoilerplateLombokAIAnthropic API — Haiku para parseo, Sonnet para análisisFrontend webNext.js (repo separado)MobileExpo / React Native (repo separado)DespliegueRailway (backend) · Supabase (DB)


Estructura de paquetes

Monolito modular. Cada dominio es un paquete autónomo con su propio
controller, service, repository, model y dto. Los paquetes no se importan
entre sí salvo dependencias explícitas de negocio.

com.tuapp.api/
├── auth/
│   ├── AuthController
│   ├── AuthService
│   ├── AuthRepository
│   ├── dto/         (LoginRequest, RegisterRequest, AuthResponse)
│   └── model/       (User, RefreshToken)
├── transactions/
│   ├── TransactionController
│   ├── TransactionService
│   ├── TransactionRepository
│   ├── dto/         (CreateTransactionRequest, TransactionResponse)
│   └── model/       (Transaction)
├── chat/
│   ├── ChatController
│   ├── ChatService
│   ├── AIService            ← única clase que llama a Anthropic
│   ├── ContextBuilder       ← arma el contexto compacto para el AI
│   └── dto/         (ChatRequest, ChatResponse)
├── metrics/
│   ├── MetricsController
│   ├── MetricsService       ← todas las queries SQL agregadas van acá
│   └── dto/         (SummaryResponse, MetricsContext)
├── purchases/
│   ├── PurchaseController
│   ├── PurchaseService      ← genera las N transactions hijas automáticamente
│   ├── PurchaseRepository
│   └── model/       (Purchase)
├── debts/
│   ├── DebtController
│   ├── DebtService
│   ├── DebtRepository
│   └── model/       (Debt)
├── categories/
│   ├── CategoryController
│   ├── CategoryService
│   ├── CategoryRepository
│   └── model/       (Category)
└── shared/
    ├── security/    (JwtFilter, JwtService, SecurityConfig)
    ├── exception/   (GlobalExceptionHandler, ApiException)
    └── config/      (WebClientConfig)


Convenciones obligatorias

Entidades JPA


Siempre @Data @Builder @NoArgsConstructor @AllArgsConstructor de Lombok.
IDs son UUID generados con @GeneratedValue(strategy = GenerationType.UUID).
Montos siempre en centavos como Long (amount_cents). Nunca Double ni Float.
Timestamps con @CreationTimestamp / @UpdateTimestamp.
Soft delete: columna deleted_at nullable. Nunca borrar filas de transactions.
Enums mapeados con @Enumerated(EnumType.STRING).


Controllers


@RequiredArgsConstructor en lugar de @Autowired.
Siempre devolver ResponseEntity<T>.
Validación de entrada con @Valid + DTOs anotados.
Un controller por dominio, sin lógica de negocio — solo delega al service.


Services


Toda la lógica de negocio vive en el service, nunca en el controller.
Transacciones con @Transactional donde corresponda.
Nunca exponer entidades JPA directamente — siempre mapear a DTOs.


DTOs


Records de Java para requests y responses: public record LoginRequest(...).
Validaciones con @NotNull, @NotBlank, @Email, @Min, @Max.
Separar Request de Response — nunca reutilizar el mismo DTO.


Repositorios


Extender JpaRepository<Entity, UUID>.
Queries complejas con @Query JPQL o queries nativas con nativeQuery = true.
Las queries de métricas van en MetricsService con queries nativas SQL,
no en repositorios — son agregaciones que cruzan múltiples tablas.


Seguridad


La API key de Anthropic vive SOLO en variables de entorno. Nunca en código.
JWT access token: 15 minutos. Refresh token: 30 días, rotativo.
Todo endpoint requiere JWT salvo /api/auth/register y /api/auth/login.
Rate limiting de mensajes AI por usuario: campo ai_messages_used en users,
resetea mensualmente con ai_messages_reset_at.


Manejo de errores


GlobalExceptionHandler con @RestControllerAdvice centraliza todos los errores.
Respuesta de error estándar: { "error": "...", "code": "...", "status": 400 }.
Nunca exponer stack traces en producción.



Flujo del asistente AI — decisiones ya tomadas

Este es el flujo que sigue ChatService para cada mensaje. No cambiar sin
consenso — afecta costos de API y precisión.

1. Recibir mensaje del usuario
2. Consultar MetricsService.getContext(userId)  ← SQL, no AI
3. Consultar user_aliases del usuario            ← para enriquecer el prompt
4. Detectar intent probable:
     - tiene monto numérico       → intent: REGISTER  → modelo: Haiku
     - empieza con ¿ o pregunta   → intent: QUERY     → modelo: Sonnet
     - menciona "eso era" / "no era" → intent: CORRECT → modelo: Haiku
     - otro                       → intent: CHAT      → modelo: Haiku
5. Llamar a Anthropic con prompt estructurado
6. Parsear respuesta JSON del modelo
7. Si intent == REGISTER: insertar en transactions via TransactionService
8. Si intent == CORRECT: actualizar transaction + user_aliases
9. Guardar en chat_messages con model_used y tokens_used
10. Decrementar ai_messages_used del usuario
11. Devolver ChatResponse al cliente

Regla crítica: el modelo nunca recibe el historial completo de transacciones.
Solo recibe el JSON compacto de MetricsContext (totales, por categoría,
cuotas próximas, aliases). Las sumas y agregaciones las hace SQL, no el AI.


Esquema de base de datos

Migraciones en src/main/resources/db/migration/.
Nomenclatura: V{n}__{descripcion_snake_case}.sql

Tablas MVP:


users — incluye ai_messages_used y ai_messages_reset_at
refresh_tokens
categories — sistema (user_id NULL) y custom (user_id con valor)
exchange_rates — tipo de cambio histórico, nunca se recalcula
transactions — montos en centavos, guarda original + convertido a ARS
purchases — compras en cuotas, genera N transactions hijas
debts — direction: owe | owed
chat_messages — auditoría de AI: model_used, tokens_used
user_aliases — "el chino" → Supermercado, se actualiza al corregir


Tablas V2 (no implementar ahora):


budgets
alerts



Variables de entorno requeridas

DATABASE_URL          jdbc:postgresql://...supabase.co:5432/postgres
DATABASE_USER         postgres
DATABASE_PASSWORD     ...
JWT_SECRET            string aleatorio de mínimo 256 bits
ANTHROPIC_API_KEY     sk-ant-...

En desarrollo: archivo .env en la raíz (ignorado por git).
En producción: variables de entorno en Railway.


Lo que NO hacer


No usar microservicios — es un monolito modular, así se queda en el MVP.
No llamar a Anthropic desde el frontend — siempre pasa por el backend.
No usar Double/Float para montos — siempre Long en centavos.
No exponer entidades JPA en los responses — siempre DTOs.
No hacer DELETE físico en transactions — usar soft delete con deleted_at.
No pasar el historial de transacciones al AI — solo el contexto compacto.
No implementar budgets ni alerts en el MVP — están marcadas como V2.
No usar @Autowired en campos — siempre @RequiredArgsConstructor.
No poner lógica de negocio en controllers — solo en services.
No subir .env al repositorio — está en .gitignore.



Roadmap de implementación

MVP (implementar ahora)


Migraciones Flyway — esquema completo
Auth — register, login, refresh, logout
Categories — CRUD
Transactions — CRUD + filtros por período y categoría
Purchases — crear compra en cuotas (genera N transactions)
Debts — CRUD + marcar como pagada
Metrics — summary por período, context para el AI
Chat — integración Anthropic con el flujo descrito arriba


V2 (no tocar ahora)


Budgets y alerts con @Scheduled
Foto de tickets (visión)
Cuentas compartidas / familiares
Modo oscuro (frontend)
Export CSV