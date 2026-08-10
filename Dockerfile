# ============================================================
# Multi-stage Dockerfile for mini-java-app
# Build tool : Maven (system mvn — no wrapper)
# Builder    : maven:3.9.4-eclipse-temurin-11
# Runtime    : eclipse-temurin:11-jdk  (explicit base image)
# Target     : AWS EKS
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer-cache optimisation
COPY pom.xml .

# Pre-download all dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy application source
COPY src ./src

# Build the fat JAR (skip tests — tests run in CI pipeline)
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:11-jdk

# Timezone & locale
ENV TZ=UTC \
    LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

# JVM tuning for containerised workloads
ENV JAVA_OPTS="-Xms256m -Xmx512m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

# Spring profile
ENV SPRING_PROFILES_ACTIVE=docker

# Application environment variables (overridden at runtime via K8s env/secrets)
ENV SERVER_PORT=8080 \
    DB_HOST=localhost \
    DB_PORT=3306 \
    DB_NAME=mini_app_db \
    DB_USERNAME=root \
    DB_PASSWORD="" \
    REDIS_HOST=redis.default.svc.cluster.local \
    REDIS_PORT=6379 \
    APP_LOG_DIR=/app/logs \
    APP_LOG_FILE_PATH=/app/logs/mini-app.log

WORKDIR /app

# Create non-root user for security
RUN groupadd --system appgroup && \
    useradd  --system --gid appgroup --no-create-home appuser && \
    mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# Copy the built JAR from the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Ensure the non-root user owns the JAR
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
