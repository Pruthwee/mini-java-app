FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy pom.xml and download dependencies first (better layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the project
RUN mvn clean package -DskipTests

# Copy runtime dependencies for classpath
RUN mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

# Runtime stage
FROM eclipse-temurin:11-jdk

WORKDIR /app

# Create non-root user
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy the built jar and dependencies
COPY --from=builder /workspace/target/*.jar app.jar
COPY --from=builder /workspace/target/lib/ lib/

# Create directories for config, logs, and uploads
RUN mkdir -p /app/config /app/logs /app/uploads /tmp/mini-app && \
    chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port (APP_PORT) and health check port (HEALTH_PORT)
EXPOSE 8080 8081

# Environment variables with defaults
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    APP_PORT=8080 \
    HEALTH_PORT=8081 \
    CONFIG_PATH=/app/config/app.properties \
    LOG_PATH=/app/logs/mini-app.log \
    LOG_DIR=/app/logs

# Entry point with signal handling for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp \"app.jar:lib/*\" com.test.MiniApp"]
