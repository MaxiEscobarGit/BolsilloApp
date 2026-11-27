# BolsilloApp

App de finanzas personales con asistente AI conversacional.

## Desarrollo local

**Requisitos:** Docker, Java 21, Maven

### Levantar la base de datos

Copia `.env.example` a `.env` y completá las variables:

```bash
cp .env.example .env
```

Luego levantá los contenedores:

```bash
docker compose up -d
```

Para bajarlos:

```bash
docker compose down
```

### pgAdmin

Accedé a [http://localhost:5050](http://localhost:5050) con las credenciales `PGADMIN_EMAIL` y `PGADMIN_PASSWORD` del `.env`.

Para conectar pgAdmin al contenedor de Postgres:

1. Click en **Add New Server**
2. En la pestaña **General**, poné cualquier nombre (ej: `finanzas-dev`)
3. En la pestaña **Connection**:
   - **Host**: `finanzas-db` (nombre del container, resuelto por la red Docker)
   - **Port**: `5432`
   - **Database**: `finanzas_dev`
   - **Username**: `postgres`
   - **Password**: el valor de `DB_PASSWORD` en tu `.env`
4. Click en **Save**

### Arrancar Spring Boot

```bash
cd api
./mvnw spring-boot:run
```

### App (Expo / React Native)

La app vive en [`mobile/`](mobile/README.md): mobile first, también corre en web. Ver ahí cómo arrancarla.

### Swagger / OpenAPI

Con la API corriendo:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Spec JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Para probar endpoints protegidos: hacé `POST /api/auth/login`, copiá el `accessToken`
y pegalo en el botón **Authorize** (esquema Bearer JWT, sin el prefijo `Bearer`).

Healthcheck público: `GET /actuator/health` (es el único endpoint de actuator expuesto).

## Docker

La imagen se buildea desde `api/` (multi-stage: Maven + JDK 21 → JRE 21 Alpine, usuario no root).

```bash
docker build -t bolsillo-api ./api
docker run --rm -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/finanzas_dev \
  -e DATABASE_USER=postgres -e DATABASE_PASSWORD=... \
  -e JWT_SECRET=... -e ANTHROPIC_API_KEY=... \
  bolsillo-api
```

El puerto se toma de la variable `PORT` (default `8080`); JVM tuneable con `JAVA_OPTS`.

### Todo junto con docker compose

El servicio `api` está bajo el perfil opcional `api`, así que `docker compose up -d` sigue
levantando solo la DB y pgAdmin. Para levantar también la API:

```bash
docker compose --profile api up -d --build
```

`JWT_SECRET` y `ANTHROPIC_API_KEY` se leen del `.env` de la raíz (si están vacíos usa defaults de desarrollo).

## CI

`.github/workflows/ci.yml` corre en cada push a `main` y en cada PR:

- `mvn -B verify` en `api/` con Temurin 21 y cache de Maven (los tests usan Testcontainers, Docker ya viene en los runners de GitHub).
- Build de la imagen Docker (sin push) para detectar roturas del `Dockerfile`.

Para correr lo mismo en local: `cd api && mvn -B verify` (necesita Docker corriendo).

## Deploy (Railway)

`railway.json` en la raíz configura el build con `api/Dockerfile` (contexto `api/`) y el
healthcheck en `/actuator/health`.

La imagen arranca en perfil **`prod`** por defecto (`SPRING_PROFILES_ACTIVE=prod` en el
Dockerfile), que no tiene valores por defecto: si falta alguna variable, la app no arranca.
Variables obligatorias en Railway:

- `DATABASE_URL` (JDBC de Supabase)
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `ANTHROPIC_API_KEY`
- `CORS_ALLOWED_ORIGINS` (orígenes web permitidos, separados por coma)

`PORT` lo inyecta Railway. El `docker compose` local fuerza `SPRING_PROFILES_ACTIVE=dev`.

## Deploy en VPS

Alternativa a Railway: un VPS (Hostinger KVM 2, Ubuntu 24.04) con Docker. Todo en [`deploy/`](deploy/):
Caddy (HTTPS automático + web estática + proxy a `/api/*`), la API y Postgres, solo Caddy expone puertos.
La API tiene `@Scheduled` sin lock: correr **una sola** instancia.

Pasos manuales:

1. **Contratar** el VPS (Ubuntu 24.04) y anotar la IP. Generá una clave SSH propia si no tenés (`ssh-keygen -t ed25519`).
2. **DNS en Cloudflare**: registro `A` de tu dominio hacia la IP, en modo *DNS only* (nube gris) para que Caddy obtenga el certificado.
3. **Bootstrap** (como root, una vez): copiá `deploy/bootstrap-vps.sh` y tu clave pública (`.pub`) al VPS y corré
   `DEPLOY_PUBKEY_FILE=./clave.pub ./bootstrap-vps.sh`. Crea el usuario `deploy`, SSH solo por clave, ufw, fail2ban,
   actualizaciones automáticas y Docker. Probá `ssh deploy@IP` en otra terminal antes de cerrar la sesión.
4. **Cargar `deploy/.env`** en el VPS: `mkdir -p ~/bolsillo/deploy && nano ~/bolsillo/deploy/.env` a partir de
   `deploy/.env.example` (contraseñas solo alfanuméricas; `API_IMAGE=ghcr.io/<owner-en-minúsculas>/bolsillo-api`). `chmod 600`.
5. **Web**: `cd mobile && EXPO_PUBLIC_API_URL=https://<tu-dominio> npx expo export -p web` y subí `mobile/dist/*` a `~/bolsillo/deploy/web/` del VPS (`rsync -a --delete`).
6. **Secrets de GitHub** (Settings > Secrets > Actions; idealmente en el environment `production`):
   `VPS_HOST`, `VPS_USER` (`deploy`), `VPS_SSH_KEY` (clave privada dedicada al deploy, cuya pública está en el VPS),
   `VPS_KNOWN_HOSTS` (salida de `ssh-keyscan -t ed25519 IP`, verificá el fingerprint contra el del panel del proveedor),
   `VPS_DOMAIN` (para el chequeo de health).
   Después creá la **variable** de repo `DEPLOY_ENABLED=true` (Settings > Variables > Actions): sin ella el job de deploy no corre.
7. **Primer deploy**: push a `main` (o *Run workflow* en `Deploy`). El workflow escanea el repo (gitleaks) y la imagen (Trivy),
   publica en GHCR, copia compose/Caddyfile al VPS, hace `pull` + `up -d` y verifica `/actuator/health` por HTTPS.
   Si el paquete en GHCR es privado, el deploy usa el `GITHUB_TOKEN` del job para el pull.
8. **Backups y restore**: configurá el timer de backup y probá el restore siguiendo [`deploy/restore.md`](deploy/restore.md)
   (bucket de R2, claves S3 y clave pública de age en el `.env`).

Probar el stack en local: `DOMAIN=localhost` (Caddy usa certificado interno) y
`docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build`.
