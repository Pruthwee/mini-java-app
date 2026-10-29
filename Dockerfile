# ---- Build stage: Maven on Java 11 ----
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Dependencies first, so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Source, then the jar and its runtime dependencies (the jar has no Main-Class)
COPY src ./src
RUN mvn -B clean package -DskipTests \
    && mvn -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

# ---- Runtime stage ----
FROM eclipse-temurin:11-jre

ENV TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    APP_PORT=8080 \
    HEALTH_PORT=8081 \
    DATA_PATH=/app/data \
    CONFIG_PATH=/app/config

# Non-root user; UID/GID 10001 must match the EFS access point's POSIX user
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app/data /app/config /app/lib \
    && chown -R app:app /app

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/target/mini-java-app-*.jar app.jar
COPY --from=builder --chown=app:app /workspace/target/lib/ lib/

USER app

# 8080: application port (APP_PORT), 8081: health endpoint GET /health (HEALTH_PORT)
EXPOSE 8080 8081

# exec: java is the first process and receives SIGTERM for a graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp \"app.jar:lib/*\" com.test.MiniApp"]
