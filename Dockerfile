# syntax=docker/dockerfile:1

# ---- Build stage: Maven on Java 11 ----
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until the pom changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Build the jar (plain jar without Main-Class: the runtime dependencies are copied beside it)
COPY src ./src
RUN mvn -B clean package -DskipTests \
    && mvn -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

# ---- Runtime stage ----
FROM eclipse-temurin:11-jdk

LABEL org.opencontainers.image.title="minijava-s55-con-1433"

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    APP_PORT=8080 \
    HEALTH_PORT=8081 \
    CONFIG_PATH=/app/config/app.properties \
    FILE_STORAGE_PATH=/app/logs \
    LOG_PATH=/app/logs/mini-app.log

WORKDIR /app

# Non-root user; the config and log directories belong to it
RUN groupadd --system --gid 1001 appgroup \
    && useradd --system --uid 1001 --gid appgroup --no-create-home appuser \
    && mkdir -p /app/config /app/logs \
    && chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar app.jar
COPY --from=builder --chown=appuser:appgroup /workspace/target/lib/ lib/

USER appuser

# 8080: application server socket, 8081: /health endpoint
EXPOSE 8080 8081

# exec: java (not the shell) is the first process and receives SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp \"app.jar:lib/*\" com.test.MiniApp"]
