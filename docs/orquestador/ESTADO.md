# Estado del orquestador — BolsilloApp

**Última actualización:** 2026-11-08 01:40 UTC

**Resumen:**
1. Los 5 agentes terminaron y abrieron PR. Revisión hecha: no hay bugs bloqueantes y el merge de prueba de las 5 ramas compila.
2. Ajuste pedido a infra (#3): que la imagen Docker arranque con `SPRING_PROFILES_ACTIVE=prod`. Sin eso, si en Railway falta la variable, la app usa el JWT secret de dev.
3. Pendiente de Maxi: mergear los PRs en el orden de abajo.

## Agentes

| Agente | session_id | Rama | Tarea | Estado | PR | Tests | Próximos pasos |
|---|---|---|---|---|---|---|---|
| Docs | session_01BV8vsLdSQ5Emq8KFffys3s | claude/agente-docs | REQ-005 | esperando revisión | [#1](https://github.com/MaxiEscobarGit/BolsilloApp/pull/1) | n/a (solo docs) | Mergear. Al final, actualizar las notas de "estado actual" del chat |
| Infra | session_01BV8vsLeHzdyrbqfhTZojsA | claude/agente-infra | REQ-004 | ajuste pedido | [#3](https://github.com/MaxiEscobarGit/BolsilloApp/pull/3) | 85 ✅ + docker build ✅ | Agregar `SPRING_PROFILES_ACTIVE=prod` a la imagen y luego mergear |
| Seguridad | session_01BV8vsLhbBVooffm4nh7J6t | claude/agente-seguridad | REQ-002 | esperando revisión | [#4](https://github.com/MaxiEscobarGit/BolsilloApp/pull/4) | 115 ✅ | Rebase sobre main después de infra (conflicto en `application.properties`) |
| Tipo de cambio | session_01BV8vsM89o2BeEhCkWN4DBL | claude/agente-tipo-cambio | REQ-003 | esperando revisión | [#2](https://github.com/MaxiEscobarGit/BolsilloApp/pull/2) | unit ✅, integración sin correr (no tenía Docker) | Rebase y que el CI corra la integración |
| Chat | session_01BV8vsKoKwRKX1NpUZerqzT | claude/agente-chat | REQ-001 | esperando revisión | [#5](https://github.com/MaxiEscobarGit/BolsilloApp/pull/5) | 114 ✅ | Rebase al final |

## Migraciones Flyway

| Versión | Dueño | Estado |
|---|---|---|
| V4 | Chat | reservada, **no usada** (el chat no la necesitó) |
| V5 | Tipo de cambio | usada en #2 (índice de lookup) |
| V6 | Seguridad | reservada, **no usada** |
| **V7** | — | **próxima libre** (no reutilizar V4/V6, para evitar errores de orden en Flyway) |

## Orden de merge recomendado

1. docs (#1) → 2. infra (#3, con el ajuste) → 3. seguridad (#4) → 4. tipo de cambio (#2) → 5. chat (#5)

Después de cada merge, el PR siguiente necesita rebase: todos agregan un bloque al final de `application.properties`. El conflicto es trivial, porque se conservan todos los bloques y se saca el default `sk-ant-placeholder`. Ya probé el merge de las 5 ramas y compila (`mvn test-compile` OK).

## Observaciones de la revisión (no bloqueantes)

- **Tipo de cambio:** el fallback a una fecha anterior no tiene límite de antigüedad. Si el job deja de andar, se usaría una cotización vieja sin avisar. Sugerencia a futuro: limitarlo a ~7 días.
- **Seguridad:** el límite de login es por email+IP, así que un atacante que rota IPs no queda frenado por email. Alcanza para el MVP.
- **Infra:** confirmar en Railway si el servicio tiene "Root Directory = api". Si es así, `dockerfilePath` en `railway.json` tiene que ser `Dockerfile`.
- **Docs:** `CLAUDE.md` dice que CORRECT no está implementado; queda desactualizado cuando se mergee el chat. Hay que retocarlo al final.

## Bloqueos que necesitan a Maxi

- Mergear los PRs en orden.
- En Railway: definir las variables obligatorias (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `ANTHROPIC_API_KEY`, `CORS_ALLOWED_ORIGINS`).
