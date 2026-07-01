# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM amazoncorretto:11
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR from the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Set JVM memory settings and container awareness
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV TZ=UTC
ENV SPRING_PROFILES_ACTIVE=docker

# Expose the application port
EXPOSE 8080

# Use the non-root user
USER appuser

# Entry point to run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
