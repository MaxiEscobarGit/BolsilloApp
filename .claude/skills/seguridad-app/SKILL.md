---
name: seguridad-app
description: Revisión de seguridad de BolsilloApp (API Spring Boot + app Expo) contra una checklist fija — secretos, auth/JWT, entradas, errores y logs, red/TLS, dependencias, build/firma. Usar cuando se pida "revisá la seguridad", antes de un deploy, o después de tocar auth, secretos, logs o dependencias. Produce un informe con OK / hallazgo / N/A por punto y arregla lo chico en el mismo paso.
---

# Revisión de seguridad — BolsilloApp

Recorré los 7 bloques en orden. Cada punto se cierra con **OK**, **Hallazgo** (qué, dónde, cómo se arregla)
o **N/A** (por qué no aplica todavía). Los comandos son desde la raíz del repo. Lo que sea chico (un
property, un check, un archivo de config) se arregla en la misma pasada (regla ponytail); lo grande se
lista como pendiente con prioridad.

## 1. Secretos y datos

```bash
git ls-files | grep -iE '\.env$|\.env\.|secret|\.pem|\.jks|keystore'          # solo deben aparecer *.example
git grep -nIiE 'sk-ant-[A-Za-z0-9]{10}|password\s*=\s*["\x27][^"\x27$]{4,}' -- ':!*.md' ':!*test*'
grep -n 'JWT_SECRET\|ANTHROPIC\|PASSWORD' docker-compose.yml api/src/main/resources/application*.properties
```
- `.env` y `api/.env` en `.gitignore`; defaults solo en `application-dev.properties` y `docker-compose.yml`.
- `application-prod.properties` sin defaults; `ProdSecretsValidator` exige las variables, rechaza el
  placeholder de Anthropic **y los JWT secrets de desarrollo del repo** (`DEV_JWT_SECRETS`). Si agregás
  un secret de dev nuevo al repo, sumalo a ese set.
- `JwtService.decodeAndValidate`: Base64 y ≥ 256 bits.
- Contraseñas con BCrypt; refresh tokens guardados como SHA-256, rotativos, con detección de reuso
  (`AuthService`).
- App: refresh token en `expo-secure-store` (Keychain/Keystore), access token solo en memoria
  (`mobile/src/lib/api.ts`). Nunca `AsyncStorage`/`localStorage` para tokens:
  `grep -rnE 'AsyncStorage|localStorage' mobile/src` debe dar vacío.

## 2. Auth y privilegios

- `SecurityConfig`: stateless, CSRF off (API con Bearer), `permitAll` solo en `/api/auth/*`,
  `/actuator/health/**` y Swagger. Todo lo demás `authenticated()`.
- Aislamiento entre usuarios: toda query filtra por `userId`; lo cubre
  `api/src/test/.../security/UserIsolationIntegrationTest`. Un endpoint nuevo necesita su caso ahí.
- Login: `LoginAttemptService` (5 intentos / 15 min, en memoria por instancia) y bcrypt con hash dummy
  cuando el usuario no existe (sin fuga por tiempo).
- Pendiente conocido: sin límite de registro por IP; throttling en memoria se pierde al reiniciar.

## 3. Entradas y lógica

```bash
grep -rn 'nativeQuery = true' api/src/main/java | wc -l            # todas con :param, nunca concatenación
grep -rnE '"\s*\+\s*[a-zA-Z]+\s*\+\s*"' api/src/main/java --include=*Repository.java --include=MetricsService.java
grep -rLn '@Valid' api/src/main/java/bolsilloApp/api/*/*Controller.java   # controllers sin validación
```
- DTOs como records con `@NotBlank/@Size/@Email/@Positive`; `ChatRequest.message` ≤ 2000.
- Montos `Long` en centavos; `BigDecimal` → centavos con `HALF_UP`.
- Rango de fechas de `/api/transactions` ≤ 1 año; `limit` ≤ 200.

## 4. Errores y logs

```bash
grep -n 'include-stacktrace\|include-message\|show-details\|exposure' api/src/main/resources/application*.properties
grep -rnE 'log\.(info|warn|error|debug)\(' api/src/main/java | grep -iE 'email|token|password|message\b|content|body'
grep -rnE 'console\.(log|warn|error)' mobile/src
```
- `GlobalExceptionHandler` único; respuesta siempre `{error, code, status}`; stack traces nunca.
- Actuator expone solo `health` con `show-details=never`.
- Logs sin PII ni tokens: se loguea `userId` y `e.getMessage()`, nunca el mensaje del usuario ni el token.
- `server.error.include-message=never`.

## 5. Comunicaciones y red

- Prod: TLS lo termina Railway; `server.forward-headers-strategy=native`.
- App: `mobile/src/lib/api.ts` lanza error si un build de release apunta a `http://`. En dev se usa la
  IP de la LAN por `EXPO_PUBLIC_API_URL`.
- Sin WebView ni `expo-linking` handlers: `grep -rniE 'webview|Linking\.' mobile/src` vacío. Si aparece
  un WebView, JS deshabilitado salvo que haga falta y `originWhitelist` cerrada.
- CORS: orígenes explícitos por `CORS_ALLOWED_ORIGINS`, sin credenciales, headers acotados.
- Swagger/OpenAPI apagado en prod (`springdoc.*.enabled=false` en `application-prod.properties`).

## 6. Dependencias

```bash
cd mobile && npm audit --omit=dev            # vulnerabilidades en runtime (las de @expo/cli son tooling)
cd mobile && npx expo-doctor                 # versiones compatibles con el SDK
grep -n '<version>' api/pom.xml | head       # parent de Spring Boot y jjwt al día
```
- `.github/dependabot.yml` abre PRs semanales (maven, npm, actions, docker). Mergearlos tras `mvn test` / `tsc`.
- Sacar lo que no se usa: `npx depcheck` en `mobile/` (sin instalar nada: `npx --yes depcheck`).

## 7. Build, firma y actualizaciones

- API: imagen multi-stage, usuario no root (`USER app`), perfil `prod` por defecto, sin secretos en la imagen.
- App: builds y firma vía EAS (certificados en la cuenta de Expo, no en el repo). Si se activa
  `expo-updates`, configurar **code signing** y `runtimeVersion` para que las OTA lleguen firmadas por HTTPS.
- N/A hoy: firma con marca de tiempo (no hay binarios de escritorio) y privilegios de administrador
  (la app no los pide).

## Informe

Terminar con una tabla `Punto | Estado | Detalle` para los 7 bloques, la lista de lo arreglado en la
pasada (con commit) y los pendientes ordenados por riesgo. Commit + push + merge según la regla del proyecto.
