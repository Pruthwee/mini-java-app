# ============================================================
# Multi-stage Dockerfile for mini-java-app
# Builder : maven:3.9.4-eclipse-temurin-11
# Runtime : eclipse-temurin:11-jdk  (explicit base image)
# ============================================================

# ---- Stage 1: Build ----
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy application source
COPY src ./src

# Build the application JAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:11-jdk

# Metadata labels
LABEL maintainer="mini-java-app" \
      app="mini-java-app" \
      version="1.0.0"

# Set timezone
ENV TZ=UTC

# Create non-root user for security
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --shell /bin/false appuser

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Application port
EXPOSE 8080

# JVM optimizations for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UnlockExperimentalVMOptions \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8"

# Spring profile
ENV SPRING_PROFILES_ACTIVE=docker

# Entrypoint with JVM options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
