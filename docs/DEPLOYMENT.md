# Deployment Guide - Mini Java App on AWS EKS

## Overview
This guide provides instructions for containerizing and deploying the Mini Java App to AWS Elastic Kubernetes Service (EKS).

## Prerequisites
- Java 11 JDK
- Maven 3.9+
- Docker
- AWS CLI configured with appropriate permissions
- kubectl installed
- AWS EKS Cluster

## Local Development Setup
1. Clone the repository.
2. Use Docker Compose for local testing:
   ```bash
   docker-compose up --build
   ```
   The application will be available at `http://localhost:8080`.

## Build and Push Image
1. Run the build script:
   - Linux/macOS: `./scripts/build-push.sh`
   - Windows: `scripts\build-push.bat`
2. Follow the prompts to select your registry (AWS ECR or Docker Hub) and provide credentials.
3. The script will build the image and push it to the selected registry.

## AWS EKS Deployment
1. Ensure your AWS CLI is configured for the correct account.
2. Run the deployment script:
   - Linux/macOS: `./scripts/deploy-image.sh`
   - Windows: `scripts\deploy-image.bat`
3. Provide the EKS cluster name, region, and the full image URI pushed in the previous step.
4. Enter the required environment variables when prompted.
5. The script will:
   - Update Kubernetes manifests.
   - Configure `kubectl` for your EKS cluster.
   - Apply the namespace, deployment, service, and ingress.
   - Wait for the rollout to complete.

## Kubernetes Manifests
- `kubernetes/namespace.yaml`: Creates a dedicated namespace for the app.
- `kubernetes/deployment.yaml`: Defines the pod spec, replicas, resource limits, and health probes.
- `kubernetes/service.yaml`: Exposes the app internally within the cluster.
- `kubernetes/ingress.yaml`: Configures the AWS Application Load Balancer (ALB) for external access.

## Troubleshooting
- **Pod Failures**: Check logs using `kubectl logs -f <pod-name> -n mini-java-app`.
- **Health Check Failures**: Ensure Spring Boot Actuator is enabled and the `/actuator/health` endpoint is reachable.
- **Ingress Issues**: Verify that the AWS Load Balancer Controller is installed in your EKS cluster.

## Configuration Management
Environment variables are used to override hardcoded values in `application.properties`. These are passed via the `deployment.yaml` manifest.

## Security Considerations
- The container runs as a non-root user (`appuser`).
- Resource limits are set to prevent noisy neighbor issues.
- Secrets should be managed using AWS Secrets Manager or Kubernetes Secrets in production.
