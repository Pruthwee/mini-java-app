# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application JAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:11-jdk

# Metadata labels
LABEL maintainer="mini-java-app"
LABEL application="mini-java-app"
LABEL version="1.0.0"

# Set timezone
ENV TZ=UTC

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup -s /bin/false appuser

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Create required directories and set ownership
RUN mkdir -p /mnt/efs/logs/mini-app /opt/app/config && \
    chown -R appuser:appgroup /app /mnt/efs/logs/mini-app /opt/app/config

# Switch to non-root user
USER appuser

# Application port (main server)
EXPOSE 8080

# Health check port
EXPOSE 8081

# JVM options for container-aware memory management
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UnlockExperimentalVMOptions \
               -Xms256m \
               -Xmx512m \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=UTC"

# Default environment variables (override at runtime)
ENV SERVER_PORT=8080
ENV HEALTH_CHECK_PORT=8081
ENV APP_CONFIG_FILE_PATH=/opt/app/config/app.properties
ENV APP_LOG_FILE_PATH=/mnt/efs/logs/mini-app/app.log
ENV APP_LOG_DIRECTORY=/mnt/efs/logs/mini-app

# Database environment variables
ENV DB_HOST=localhost
ENV DB_PORT=3306
ENV DB_NAME=mini_app_db
ENV DB_USERNAME=root
ENV DB_PASSWORD=

# Cache environment variables
ENV REDIS_HOST=redis-service.default.svc.cluster.local
ENV REDIS_PORT=6379

# External service environment variables
ENV EXTERNAL_API_URL=http://api.example.com:8080/v1
ENV PAYMENT_SERVICE_URL=https://payment.internal.company.com/process

# Entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
