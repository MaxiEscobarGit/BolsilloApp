# syntax=docker/dockerfile:1

# ─── Build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencias primero para aprovechar la cache de capas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/*.jar app.jar

# ─── Runtime ────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
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

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
