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

# Build the application (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:11-jdk

# Set working directory
WORKDIR /app

# Create non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup -s /bin/false appuser

# Set timezone
ENV TZ=UTC

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UnlockExperimentalVMOptions \
               -Xms256m \
               -Xmx512m \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=UTC"

# Spring profile
ENV SPRING_PROFILES_ACTIVE=docker

# Application environment variables (override at runtime)
ENV SERVER_PORT=8080
ENV DB_HOST=localhost
ENV DB_PORT=3306
ENV DB_NAME=mini_app_db
ENV DB_USERNAME=root
ENV DB_PASSWORD=""
ENV DB_URL=""
ENV REDIS_HOST=redis.internal.svc.cluster.local
ENV REDIS_PORT=6379
ENV APP_CONFIG_DIR=/mnt/efs/app/config
ENV APP_LOG_DIR=/mnt/efs/logs
ENV APP_TEMP_DIR=/mnt/efs/tmp/mini-app
ENV APP_UPLOAD_DIR=/mnt/efs/uploads
ENV APP_CONFIG_FILE_PATH=/mnt/efs/app/config/app.properties
ENV APP_LOG_FILE_PATH=/mnt/efs/logs/mini-app.log

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
