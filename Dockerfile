FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Resolve dependencies first so that this layer is cached
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Build the jar and collect its runtime dependencies (the jar is not a runnable jar: no Main-Class)
COPY src ./src
RUN mvn -B clean package -DskipTests \
    && mvn -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

FROM eclipse-temurin:11-jdk
WORKDIR /app

RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app/config /app/logs \
    && chown -R app:app /app

COPY --from=builder --chown=app:app /workspace/target/mini-java-app-1.0.0.jar app.jar
COPY --from=builder --chown=app:app /workspace/target/lib/ lib/

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=UTC" \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    APP_PORT=8080 \
    HEALTH_PORT=8081 \
    CONFIG_PATH=/app/config \
    LOG_PATH=/app/logs

USER 10001

EXPOSE 8080 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp 'app.jar:lib/*' com.test.MiniApp"]
