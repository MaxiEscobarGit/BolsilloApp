# Estado del orquestador — BolsilloApp

**Última actualización:** 2026-11-08 01:25 UTC

**Resumen:**
1. 5 agentes activos (chat, seguridad, tipo de cambio, infra, docs), todos trabajando; ninguno abrió PR todavía.
2. Conflicto conocido: seguridad e infra tocan `api/shared/security/SecurityConfig.java` → mergear infra antes que seguridad.
3. Sin bloqueos que necesiten a Maxi por ahora. Próximo check-in ~01:55 UTC.

## Agentes

| Agente | session_id | Rama | Tarea | Estado | PR | Último avance | Próximos pasos |
|---|---|---|---|---|---|---|---|
| Chat | session_01BV8vsKoKwRKX1NpUZerqzT | claude/agente-chat | REQ-001 · Arreglar flujo del asistente IA (V4) | en curso | — | Exploró `chat/`; levantando Docker para tests | Implementar, tests, PR |
| Seguridad | session_01BV8vsLhbBVooffm4nh7J6t | claude/agente-seguridad | REQ-002 · Secretos, perfiles, CORS, rate limit login (V6) | en curso | — | Docker OK; implementando perfiles, CORS y límite de login | Tests, PR |
| Tipo de cambio | session_01BV8vsM89o2BeEhCkWN4DBL | claude/agente-tipo-cambio | REQ-003 · Job de cotizaciones + endpoints (V5) | en curso | — | Leyó módulo exchange-rate; escribiendo cliente, job, endpoints, tests | Tests, PR |
| Infra | session_01BV8vsLeHzdyrbqfhTZojsA | claude/agente-infra | REQ-004 · Dockerfile, Railway, CI, OpenAPI, actuator | en curso | — | Docker levantado; corriendo tests y build de imagen | PR |
| Docs | session_01BV8vsLdSQ5Emq8KFffys3s | claude/agente-docs | REQ-005 · CLAUDE.md, memoria, subagentes | en curso | — | Reescribiendo `api/CLAUDE.md` tras revisar servicios y tests | PR |

## Migraciones Flyway reservadas

| Versión | Dueño | Estado |
|---|---|---|
| V4 | Chat (REQ-001) | reservada, en curso |
| V5 | Tipo de cambio (REQ-003) | reservada, en curso |
| V6 | Seguridad (REQ-002) | reservada, en curso |
| **V7** | — | **próxima libre** |

## Orden de merge recomendado

1. docs → 2. infra → 3. seguridad (rebase sobre infra por `SecurityConfig.java`) → 4. tipo de cambio → 5. chat

## Conflictos / solapamientos conocidos

- `api/src/main/java/bolsilloApp/api/shared/security/SecurityConfig.java`: infra (OpenAPI/actuator permitAll) y seguridad (CORS, perfiles). Resolver al mergear seguridad después de infra.

## Bloqueos que necesitan a Maxi

- Ninguno por ahora.
