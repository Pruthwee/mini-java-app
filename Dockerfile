FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

RUN mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

FROM eclipse-temurin:11-jdk

WORKDIR /app

ENV TZ=UTC \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

COPY --from=builder /workspace/target/*.jar app.jar
COPY --from=builder /workspace/target/lib/ /app/lib/

RUN addgroup --system appgroup && \
    adduser --system --ingroup appgroup appuser && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp \"app.jar:lib/*\" com.test.MiniApp"]
