# Estado del orquestador — BolsilloApp

**Última actualización:** 2026-11-08 02:10 UTC

**Resumen:**
1. Tanda 1 terminada: los 5 PRs de los agentes más el de docs final (#1–#6) están mergeados en `main`. La suite completa pasa: **159 tests, 0 fallos**.
2. Corregido durante la integración: un bug en tipo de cambio (`DolarApiClient` sin `@Autowired`) que impedía arrancar la app.
3. Pendiente de Maxi: el CI de GitHub Actions falla al arrancar (`startup_failure`) por la configuración de la cuenta, y hay que cargar las variables en Railway antes del primer deploy.

## Agentes

| Agente | Rama | Tarea | Estado | PR |
|---|---|---|---|---|
| Docs | claude/agente-docs | REQ-005 | ✅ mergeado, sesión archivada | [#1](https://github.com/MaxiEscobarGit/BolsilloApp/pull/1) |
| Infra | claude/agente-infra | REQ-004 | ✅ mergeado, sesión archivada | [#3](https://github.com/MaxiEscobarGit/BolsilloApp/pull/3) |
| Seguridad | claude/agente-seguridad | REQ-002 | ✅ mergeado, sesión archivada | [#4](https://github.com/MaxiEscobarGit/BolsilloApp/pull/4) |
| Tipo de cambio | claude/agente-tipo-cambio | REQ-003 | ✅ mergeado (con fix), sesión archivada | [#2](https://github.com/MaxiEscobarGit/BolsilloApp/pull/2) |
| Chat | claude/agente-chat | REQ-001 | ✅ mergeado, sesión archivada | [#5](https://github.com/MaxiEscobarGit/BolsilloApp/pull/5) |
| Orquestador | claude/elegant-clarke-bheqvx | REQ-006 docs finales | ✅ mergeado | [#6](https://github.com/MaxiEscobarGit/BolsilloApp/pull/6) |

No hay agentes activos.

## Migraciones Flyway

| Versión | Estado |
|---|---|
| V1–V3 | en `main` |
| V4 | saltada (reservada para chat, no se usó). No reutilizar |
| V5 | en `main` (índice de `exchange_rates`) |
| V6 | saltada (reservada para seguridad, no se usó). No reutilizar |
| **V7** | **próxima libre** |

## Pendientes que necesitan a Maxi

- **CI de GitHub Actions:** todas las corridas terminan en `startup_failure` antes de ejecutar ningún paso. El YAML es válido, así que probablemente sea la configuración de Actions o de la facturación de la cuenta: Settings → Actions → General (permisos de actions) y Billing (minutos o límite de gasto). Mientras tanto, los tests se corren localmente con Docker antes de cada merge.
- **Railway, antes del primer deploy:** definir `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 32`), `ANTHROPIC_API_KEY` y `CORS_ALLOWED_ORIGINS`. La imagen ya arranca en perfil `prod`. Si el servicio tiene "Root Directory = api", ajustar `dockerfilePath` en `railway.json`.

## Mejoras sugeridas (próxima tanda)

- Tipo de cambio: limitar la antigüedad del fallback (por ejemplo, 7 días) y avisar cuando se usa una cotización vieja.
- Frontend web (Next.js) y app mobile (Expo).
