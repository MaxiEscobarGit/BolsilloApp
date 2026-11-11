# BolsilloApp — estado, agentes y plan a seguir

Documento de traspaso para cualquier sesión de Claude (o persona) que siga el proyecto.
Última actualización: 2026-11-11.

## 1. Qué es y dónde está cada cosa

App de finanzas personales con asistente IA: registrás gastos, ingresos y deudas hablando
("gasté 12 lucas en el súper") y te dice cómo venís.

| Parte | Dónde | Guía obligatoria |
|---|---|---|
| Backend (Spring Boot 4.1 · Java 21 · Postgres · Flyway) | `api/` | `api/CLAUDE.md` |
| App (Expo SDK 58 · React Native · mobile first, también web) | `mobile/` | `mobile/CLAUDE.md` |
| Skills de Claude para todo el repo | `.claude/skills/` | `ponytail`, `apple-design`, `animate-expo` |
| Subagentes del backend | `api/.claude/agents/` | `gestor-requerimientos`, `chat-ai`, `seguridad`, `tipo-cambio`, `infra` |

Reglas de trabajo: **ponytail siempre** (el cambio más chico que resuelve todo, sin dependencias ni
abstracciones de más). Para la UI, **apple-design** y **animate-expo**. Textos en español rioplatense.

## 2. Estado actual (todo en `main`)

**Backend — completo y testeado (159 tests verdes):**
auth con JWT y refresh rotativo (detección de reuso, límite de intentos de login), categorías,
movimientos (filtros, paginación, soft delete), compras en cuotas, deudas, métricas, tipo de cambio
(se carga solo desde dolarapi.com con fallback a la fecha anterior), chat con IA (REGISTER / QUERY /
CORRECT con aprendizaje de alias, cuota de 100 mensajes/mes atómica, llamada a Anthropic fuera de la
transacción), perfiles `dev`/`prod` sin secretos por defecto, CORS, Swagger, Dockerfile y Railway.

**App — 5 pantallas funcionando contra la API:**

| Pantalla | Archivo | Qué hace |
|---|---|---|
| Login / registro | `mobile/src/app/login.tsx`, `register.tsx` | Sesión con refresh automático |
| Inicio | `mobile/src/app/(app)/index.tsx` | Tarjetas de estadística (balance destacado en verde, ingresos, gastos, % ahorrado) + gastos por categoría |
| Movimientos | `(app)/movimientos.tsx`, `movimiento.tsx` (hoja de alta/edición) | Lista con filtros, alta, edición, borrado |
| Asistente | `(app)/asistente.tsx` | Chat: historial, recibo de lo registrado, "escribiendo…", sugerencias |
| Deudas y cuotas | `(app)/deudas/` | Totales, marcar pagada, compras en cuotas con progreso |
| Perfil | `(app)/perfil.tsx` | Nombre, email, cerrar sesión |

**Diseño:** inspirado en tibdigital.com. Fondo `#0A0C10`, superficies `#141B23`, verde `#00FF85`,
coral `#FF6B50` para gastos/deudas/errores, Geist + Geist Mono en mayúsculas para etiquetas.
**Minimalista y sin bordes**: las superficies se separan por tono, nunca con contornos. Barra de
navegación abajo estilo Lemon Cash con el Asistente como botón central. Tokens en `mobile/src/theme.ts`,
componentes base en `mobile/src/components/ui.tsx`.

## 3. Cómo correr todo en local

```bash
# 1. Base de datos (desde la raíz). .env con DB_PASSWORD=postgres123 para el perfil dev
docker compose up -d postgres

# 2. API (perfil dev). Para que el chat responda, poner ANTHROPIC_API_KEY en api/.env
cd api && ./mvnw spring-boot:run          # Swagger: http://localhost:8080/swagger-ui.html

# 3. App
cd mobile && npm install
npx expo start --web                       # navegador
EXPO_PUBLIC_API_URL=http://<IP-de-la-PC>:8080 npx expo start   # celular en la misma red
```

Verificación antes de cerrar cualquier cambio: `cd mobile && npx tsc --noEmit` y
`npx expo export --platform web`; en el backend `cd api && ./mvnw test` (necesita Docker).

## 4. Cómo se trabajó con agentes (y cómo seguir haciéndolo)

Se usaron sesiones de Claude en paralelo, **una por tarea, cada una en su rama y con su PR**, y un
coordinador que revisa, prueba y mergea **de a uno**:

1. Cada agente tiene un **alcance de archivos** propio y no toca archivos compartidos
   (`ui.tsx`, `theme.ts`, `lib/*`, layouts). Si necesita algo compartido, lo pide o lo deja anotado en el PR.
2. **Migraciones Flyway**: reservar el número antes de empezar. V5 está usada, V4 y V6 se saltaron
   (no reutilizarlas). **La próxima libre es V7.**
3. `application.properties`: cada módulo agrega su propio bloque (`# Chat`, `# Security`…), sin reordenar.
4. Antes de mergear: integrar `main` en la rama, `tsc` + `expo export` (app) o `mvn test` (API) y una
   prueba de punta a punta con Playwright en tamaño celular contra la API local.
5. El CI de GitHub Actions está configurado (`.github/workflows/ci.yml`) pero **no arranca** por un
   bloqueo de facturación de la cuenta de GitHub: hasta resolverlo con el soporte de GitHub, los tests
   se corren a mano antes de cada merge.

### Agentes que ya se usaron (todos terminados y mergeados)

| Tanda | Agente | Resultado |
|---|---|---|
| Backend | chat, seguridad, tipo de cambio, infra, docs | PRs #1–#6 |
| App | movimientos, asistente, deudas | PRs #11–#13 |

### Plantilla para lanzar un agente nuevo

```
Sos el agente de <TAREA> de BolsilloApp. Hay otros agentes en paralelo.
Antes de escribir código leé <api|mobile>/CLAUDE.md y las skills de .claude/skills/
(ponytail obligatoria; apple-design y animate-expo para UI). Seguilas.

Qué construir: <lista concreta, con endpoints/DTOs reales a leer en api/...>.

Reglas:
- Solo tocá <archivos/carpetas propios>. No modifiques <archivos compartidos>; si hace falta, avisá en el PR.
- <Migración Flyway reservada: V7 / ninguna>.
- Montos en centavos en las respuestas; formatear con formatCents. Textos en español rioplatense.
- Estilo minimalista sin bordes (superficies por tono).

Verificación: <tsc + expo export | mvn test> y prueba de punta a punta. Describí lo probado en el PR.
Commits en español, push a tu rama y PR contra main. No mergees.
```

## 5. Plan a seguir (por prioridad)

1. **Probar en un celular real.** Hacer un development build (`npx expo run:android` / `run:ios`
   o `eas build --profile development`) y revisar gestos, sheets, teclado en el chat y haptics.
   Es lo único que no se pudo validar desde la nube.
2. **Probar el asistente con IA real.** Poner `ANTHROPIC_API_KEY` en `api/.env` y recorrer
   registrar → corregir ("no, eso era comida") → consultar ("¿cuánto gasté en comida?").
3. **Perfil con ajustes.** Hoy solo muestra nombre/email. Falta en el backend un endpoint de usuario
   (por ejemplo `GET/PATCH /api/users/me`) para cambiar moneda por defecto y cotización preferida
   (`users.preferred_exchange_source` ya existe) y mostrar la cuota de mensajes del asistente.
   Agente de backend + agente de app, en ese orden.
4. **Categorías propias.** El backend ya tiene CRUD de categorías custom; falta la pantalla para
   crearlas/editarlas (emoji y color).
5. **Pulido de UI con animate-expo.** Entradas de listas, feedback de "registrado" en el chat,
   gráfico de gasto diario en Inicio (los datos ya vienen en `dailyTotals` de `/api/metrics/summary`).
   Respetar reduced motion.
6. **Tipo de cambio:** limitar la antigüedad del fallback (hoy usa la última cotización aunque sea vieja)
   y avisar en la UI cuando se usó una cotización de otro día.
7. **Calidad:** configurar `expo lint` (en la nube falla porque `api.expo.dev` está bloqueado; en local
   anda) y sumar tests de la app (al menos de `lib/api.ts`: refresh único y reintento).
8. **CI:** pedir al soporte de GitHub (Billing) que levante el bloqueo de Actions; el workflow ya está listo.
9. **Deploy cuando haga falta:** backend en Railway con `SPRING_PROFILES_ACTIVE=prod` y las variables
   `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `ANTHROPIC_API_KEY`,
   `CORS_ALLOWED_ORIGINS`; app con EAS (`EXPO_PUBLIC_API_URL` apuntando a la API pública).

## 6. Cosas a saber

- La sesión de la nube usó un usuario demo local (`maxi@demo.dev` / `bolsillo123`); en tu base local
  no existe: creá tu cuenta desde la app.
- El repo es público: nunca subir `.env` ni keys (los `.env*` ya están en `.gitignore`).
- Bocetos de diseño previos en Figma (versiones anteriores al estilo actual): archivo
  "BolsilloApp — Bocetos web".
