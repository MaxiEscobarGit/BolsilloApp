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
