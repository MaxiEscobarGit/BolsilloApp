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
