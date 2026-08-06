# =============================================================================
# Multi-stage Dockerfile for mini-java-app (Spring Boot, Java 11, Maven)
# Target: AWS EKS
# Runtime Base Image: eclipse-temurin:11-jdk (explicit)
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Builder
# -----------------------------------------------------------------------------
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build the executable JAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:11-jdk

# Timezone configuration
ENV TZ=UTC

WORKDIR /app

# Create non-root user for security
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home appuser

# Copy the executable JAR from the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Application port (from application.properties: server.port=${APP_SERVER_PORT:8080})
EXPOSE 8080

# Management/Actuator port (from application.properties: management.server.port=${MANAGEMENT_PORT:8081})
EXPOSE 8081

# JVM options optimised for containerised environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UnlockExperimentalVMOptions \
               -Xms256m \
               -Xmx512m \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=UTC"

# Spring Boot profile
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
