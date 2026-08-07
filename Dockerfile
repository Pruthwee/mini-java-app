# =============================================================================
# Multi-stage Dockerfile for mini-java-app (Spring Boot 2.7.0 / Java 11)
# Builder : maven:3.9.4-eclipse-temurin-11
# Runtime : eclipse-temurin:11-jdk  (explicit base image)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 – Build
# ---------------------------------------------------------------------------
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer-cache optimisation
COPY pom.xml .

# Pre-download all dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy application source
COPY src ./src

# Build the executable JAR (skip tests – tests run in CI pipeline)
RUN mvn clean package -DskipTests -B

# ---------------------------------------------------------------------------
# Stage 2 – Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:11-jdk

# Timezone
ENV TZ=UTC

WORKDIR /app

# Create a non-root user for security
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home appuser

# Copy the fat JAR produced by the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Transfer ownership to the non-root user
RUN chown appuser:appgroup app.jar

USER appuser

# Application port (matches server.port in application.properties)
EXPOSE 8080

# JVM tuning: container-aware memory settings + graceful shutdown signal
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UnlockExperimentalVMOptions \
               -Xms256m \
               -Xmx512m \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=UTC"

# Spring profile (override at runtime with -e SPRING_PROFILES_ACTIVE=prod)
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
