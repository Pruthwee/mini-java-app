# Deployment Guide for mbasic Application on AWS EKS

## Overview
This guide provides instructions for containerizing and deploying the `mbasic` Java Spring Boot application to AWS Elastic Kubernetes Service (EKS).

## Prerequisites
- Java 11 JDK
- Maven 3.9+
- Docker
- AWS CLI configured with appropriate IAM permissions
- kubectl installed
- AWS EKS Cluster

## Local Development Setup
To run the application locally using Docker Compose:
1. Create a `config` directory and add your `application.properties`.
2. Run:
   ```bash
   docker-compose up --build
   ```
3. The application will be available at `http://localhost:8080`.

## Build and Push Instructions
Use the provided scripts to build the Docker image and push it to a registry.

### Linux/macOS
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows
```cmd
scripts\build-push.bat
```

## AWS EKS Deployment Walkthrough

### 1. EKS Cluster Setup
Ensure your EKS cluster is running and you have the necessary permissions.

### 2. Deployment Process
Use the deployment scripts to configure and apply Kubernetes manifests.

#### Linux/macOS
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows
```cmd
scripts\deploy-image.bat
```

### 3. Manifest Descriptions
- `namespace.yaml`: Creates a dedicated namespace `mbasic` for the application.
- `deployment.yaml`: Defines the application pods, replicas (2), resource limits, and health probes.
- `service.yaml`: Exposes the application internally within the cluster on port 80.
- `ingress.yaml`: Configures an AWS Application Load Balancer (ALB) to route external traffic to the service.

## EKS-Specific Troubleshooting
- **Pod Failures**: Check logs using `kubectl logs -f <pod-name> -n mbasic`.
- **Service Issues**: Verify service selector matches deployment labels.
- **Ingress Problems**: Check AWS ALB controller logs and ensure the security groups allow traffic on port 80/443.

## Configuration Management
The application uses environment variables for configuration. These are prompted during the execution of `deploy-image.sh/bat` and injected into the `deployment.yaml`.

Key variables include:
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `CACHE_REDIS_HOST`, `CACHE_REDIS_PORT`, `CACHE_REDIS_PASSWORD`
- `EXTERNAL_API_BASE_URL`, `EXTERNAL_API_KEY`
- `MESSAGING_RABBITMQ_HOST`, `MESSAGING_RABBITMQ_USERNAME`, `MESSAGING_RABBITMQ_PASSWORD`

## Security Considerations
- Use AWS Secrets Manager or Kubernetes Secrets for sensitive data instead of plain environment variables in production.
- The Docker image runs as a non-root user (`appuser`) for improved security.
- Resource limits are set to prevent a single pod from consuming all node resources.

## Java Specific Notes
- **JVM Memory**: Configured with `-Xmx512m -Xms256m` and `MaxRAMPercentage=75.0` for container awareness.
- **Health Checks**: Uses Spring Boot Actuator `/actuator/health` for liveness and readiness probes.
- **Timezone**: Set to `UTC` for consistency across environments.
