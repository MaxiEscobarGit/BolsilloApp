# syntax=docker/dockerfile:1

# ─── Build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /workspace

# Dependencias primero para aprovechar la cache de capas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/*.jar app.jar

# ─── Runtime ────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

# Railway inyecta PORT; la app lo lee vía server.port=${PORT:8080}
# La imagen arranca en perfil prod (sin defaults): faltan variables => no arranca
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080

# wget viene en busybox (alpine). start-period por el arranque de Spring + Flyway
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
