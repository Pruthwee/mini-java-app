# Deployment Guide – mini-java-app on AWS EKS

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build and Push Docker Image](#build-and-push-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Environment Variables Reference](#environment-variables-reference)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Java-Specific Notes](#java-specific-notes)

---

## Overview

**Application**: mini-java-app  
**Technology**: Java 11, Spring Boot 2.7, Maven  
**Package**: JAR  
**Application Port**: 8080  
**Health Endpoint**: `/actuator/health`  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Base Image (Runtime)**: `eclipse-temurin:11-jdk`  

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Build and run containers |
| Docker Compose | 2.x | Local multi-container orchestration |
| Java JDK | 11+ | Local development |
| Maven | 3.9+ | Build tool |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | AWS authentication and ECR operations |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.150+ | EKS cluster creation (optional) |

### AWS IAM Permissions Required
```
ecr:GetAuthorizationToken
ecr:BatchCheckLayerAvailability
ecr:GetDownloadUrlForLayer
ecr:BatchGetImage
ecr:PutImage
ecr:InitiateLayerUpload
ecr:UploadLayerPart
ecr:CompleteLayerUpload
ecr:CreateRepository
ecr:DescribeRepositories
eks:DescribeCluster
eks:ListClusters
```

---

## Project Structure

```
mini-java-app/
├── Dockerfile                  # Multi-stage Docker build
├── docker-compose.yml          # Local development compose file
├── .dockerignore               # Docker build exclusions
├── pom.xml                     # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/test/
│       │   ├── MiniApp.java
│       │   └── DatabaseService.java
│       └── resources/
│           └── application.properties
├── kubernetes/
│   ├── namespace.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
├── scripts/
│   ├── build-push.sh           # Linux/macOS build & push
│   ├── build-push.bat          # Windows build & push
│   ├── deploy-image.sh         # Linux/macOS EKS deploy
│   └── deploy-image.bat        # Windows EKS deploy
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root (never commit this file):

```bash
# Database
DB_HOST=your-db-host
DB_PORT=3306
DB_NAME=mini_app_db
DB_USERNAME=your-db-user
DB_PASSWORD=your-db-password

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# External API
EXTERNAL_API_BASE_URL=http://api.example.com:8080/v1
EXTERNAL_API_TIMEOUT=30000
EXTERNAL_API_KEY=your-api-key

# Payment Service
PAYMENT_SERVICE_URL=https://payment.internal.company.com/process
PAYMENT_SERVICE_USERNAME=payment_user
PAYMENT_SERVICE_PASSWORD=payment_secret

# File Paths
APP_CONFIG_FILE_PATH=/mnt/efs/app/config/app.properties
APP_LOG_DIR=/var/log/mini-app
APP_LOG_FILE_PATH=/mnt/efs/logs/mini-app.log

# Security
SECURITY_JWT_SECRET=your-jwt-secret
SECURITY_ADMIN_USERNAME=admin
SECURITY_ADMIN_PASSWORD=your-admin-password
SECURITY_ENCRYPTION_KEY=your-encryption-key

# Monitoring
MONITORING_ENDPOINT=http://monitoring.internal.company.com:9090/metrics
MONITORING_USERNAME=monitor_user
MONITORING_PASSWORD=monitor_pass

# Messaging
MESSAGING_RABBITMQ_HOST=rabbitmq.internal.company.com
MESSAGING_RABBITMQ_PORT=5672
MESSAGING_RABBITMQ_USERNAME=rabbitmq_user
MESSAGING_RABBITMQ_PASSWORD=rabbitmq_secret
```

### 2. Start the Application

```bash
# Build and start
docker-compose up --build

# Start in background
docker-compose up -d --build

# View logs
docker-compose logs -f mini-java-app

# Stop
docker-compose down
```

### 3. Verify the Application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Application endpoint
curl http://localhost:8080/mini-app
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run from project root
./scripts/build-push.sh
```

The script will prompt for:
1. Image tag (default: `latest`)
2. Registry type: `1) AWS ECR` or `2) Docker Hub`
3. Registry-specific credentials

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (AWS ECR)

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
IMAGE_TAG=1.0.0

# Authenticate to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# Create repository (first time only)
aws ecr create-repository --repository-name mini-java-app --region $AWS_REGION

# Build and push
docker build -t ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/mini-java-app:${IMAGE_TAG} .
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/mini-java-app:${IMAGE_TAG}
```

---

## AWS EKS Deployment

### Step 1: Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

### Step 2: Connect to EKS Cluster

```bash
aws eks update-kubeconfig --region us-east-1 --name your-cluster-name
kubectl cluster-info
```

### Step 3: Run Deployment Script

**Linux / macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

The script will:
1. Prompt for AWS region and EKS cluster name
2. Prompt for the full Docker image URI
3. Prompt for all application environment variable values
4. Configure `kubectl` for the EKS cluster
5. Patch Kubernetes manifests with provided values
6. Apply manifests in order: namespace → deployment → service → ingress
7. Wait for deployment rollout
8. Display the application URL

### Step 4: Manual Deployment (Alternative)

```bash
# Update image in deployment.yaml
sed -i 's|{{IMAGE_URI}}|123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:1.0.0|g' \
  kubernetes/deployment.yaml

# Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Monitor rollout
kubectl rollout status deployment/mini-java-app -n mini-java-app

# Check resources
kubectl get pods,svc,ingress -n mini-java-app
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `mini-java-app` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (high availability)
- **Image**: Pulled from `{{IMAGE_URI}}` placeholder (replaced at deploy time)
- **Resources**:
  - Requests: `cpu: 250m`, `memory: 512Mi`
  - Limits: `cpu: 500m`, `memory: 1Gi`
- **Liveness Probe**: `GET /actuator/health` on port 8080 (initial delay: 60s)
- **Readiness Probe**: `GET /actuator/health` on port 8080 (initial delay: 30s)
- **Graceful Shutdown**: `terminationGracePeriodSeconds: 30`

### service.yaml
- **Type**: `ClusterIP` (internal cluster access)
- **Port**: 80 → 8080 (container port)

### ingress.yaml
- **Controller**: AWS Load Balancer Controller (ALB)
- **Scheme**: `internet-facing`
- **Target Type**: `ip`
- **Health Check Path**: `/actuator/health`
- **Host**: `mini-java-app.example.com` (update to your domain)

---

## Environment Variables Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Application HTTP port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `docker` |
| `JAVA_OPTS` | JVM options | See Dockerfile |
| `DB_HOST` | Database hostname | `localhost` |
| `DB_PORT` | Database port | `3306` |
| `DB_NAME` | Database name | `mini_app_db` |
| `DB_USERNAME` | Database username | _(empty)_ |
| `DB_PASSWORD` | Database password | _(empty)_ |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `EXTERNAL_API_BASE_URL` | External API base URL | _(empty)_ |
| `EXTERNAL_API_TIMEOUT` | External API timeout (ms) | `30000` |
| `EXTERNAL_API_KEY` | External API key | _(empty)_ |
| `PAYMENT_SERVICE_URL` | Payment service URL | _(empty)_ |
| `PAYMENT_SERVICE_USERNAME` | Payment service username | _(empty)_ |
| `PAYMENT_SERVICE_PASSWORD` | Payment service password | _(empty)_ |
| `APP_CONFIG_FILE_PATH` | Config file path (EFS) | `/mnt/efs/app/config/app.properties` |
| `APP_LOG_DIR` | Log directory | `/var/log/mini-app` |
| `APP_LOG_FILE_PATH` | Log file path (EFS) | `/mnt/efs/logs/mini-app.log` |
| `SECURITY_JWT_SECRET` | JWT signing secret | _(empty)_ |
| `SECURITY_ADMIN_USERNAME` | Admin username | `admin` |
| `SECURITY_ADMIN_PASSWORD` | Admin password | _(empty)_ |
| `SECURITY_ENCRYPTION_KEY` | Encryption key | _(empty)_ |
| `MONITORING_ENDPOINT` | Monitoring endpoint URL | _(empty)_ |
| `MONITORING_USERNAME` | Monitoring username | _(empty)_ |
| `MONITORING_PASSWORD` | Monitoring password | _(empty)_ |
| `MESSAGING_RABBITMQ_HOST` | RabbitMQ hostname | _(empty)_ |
| `MESSAGING_RABBITMQ_PORT` | RabbitMQ port | `5672` |
| `MESSAGING_RABBITMQ_USERNAME` | RabbitMQ username | _(empty)_ |
| `MESSAGING_RABBITMQ_PASSWORD` | RabbitMQ password | _(empty)_ |
| `TZ` | Timezone | `UTC` |

---

## Scaling and Management

### Horizontal Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment mini-java-app --replicas=3 -n mini-java-app

# Auto-scaling (HPA)
kubectl autoscale deployment mini-java-app \
  --cpu-percent=70 --min=2 --max=10 -n mini-java-app

# Check HPA status
kubectl get hpa -n mini-java-app
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:2.0.0 \
  -n mini-java-app

# Monitor rollout
kubectl rollout status deployment/mini-java-app -n mini-java-app
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/mini-java-app -n mini-java-app

# Rollback to specific revision
kubectl rollout history deployment/mini-java-app -n mini-java-app
kubectl rollout undo deployment/mini-java-app --to-revision=2 -n mini-java-app
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl get pods -n mini-java-app

# Describe pod for events
kubectl describe pod <pod-name> -n mini-java-app

# View pod logs
kubectl logs <pod-name> -n mini-java-app
kubectl logs <pod-name> -n mini-java-app --previous  # crashed pod
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `ImagePullBackOff` | ECR auth expired or wrong image URI | Re-authenticate to ECR; verify image URI |
| `CrashLoopBackOff` | Application startup failure | Check logs; verify env vars (DB, Redis) |
| `OOMKilled` | Insufficient memory | Increase memory limits in deployment.yaml |
| `Pending` pods | Insufficient cluster resources | Scale node group or reduce resource requests |
| Health probe failing | App not ready / wrong endpoint | Verify `/actuator/health` returns 200 |
| Ingress not resolving | ALB controller not installed | Install AWS Load Balancer Controller |

### Health Check Debugging

```bash
# Port-forward to test health endpoint locally
kubectl port-forward deployment/mini-java-app 8080:8080 -n mini-java-app

# In another terminal
curl http://localhost:8080/actuator/health
```

### Database Connectivity

```bash
# Exec into pod to test connectivity
kubectl exec -it <pod-name> -n mini-java-app -- /bin/sh

# Test DB connection (if netcat available)
nc -zv $DB_HOST $DB_PORT
```

---

## Security Considerations

1. **Secrets Management**: Use AWS Secrets Manager or Kubernetes Secrets for sensitive values (passwords, API keys, JWT secrets). Do not store secrets in plain-text ConfigMaps.

2. **Non-Root User**: The container runs as `appuser` (non-root) for security.

3. **Network Policies**: Apply the existing `k8s-network-policy.yaml` to restrict pod-to-pod communication.

4. **ECR Image Scanning**: Enable ECR image scanning to detect vulnerabilities:
   ```bash
   aws ecr put-image-scanning-configuration \
     --repository-name mini-java-app \
     --image-scanning-configuration scanOnPush=true \
     --region us-east-1
   ```

5. **RBAC**: Apply least-privilege IAM roles for EKS node groups and service accounts.

6. **TLS**: Configure HTTPS on the ALB ingress using AWS Certificate Manager (ACM):
   ```yaml
   annotations:
     alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/xxx
     alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
   ```

---

## Java-Specific Notes

### JVM Memory Configuration

The container is configured with:
```
-Xmx512m -Xms256m
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+UnlockExperimentalVMOptions
```

`UseContainerSupport` ensures the JVM respects container memory limits rather than using host memory. Adjust `Xmx` and `MaxRAMPercentage` based on your memory limit in `deployment.yaml`.

### Spring Boot Actuator

The application exposes `/actuator/health` (configured in `application.properties`):
```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=always
management.server.port=8080
```

### Spring Profiles

Set `SPRING_PROFILES_ACTIVE=docker` (default in all deployment artifacts) to activate Docker/Kubernetes-specific configuration.

### Graceful Shutdown

The deployment is configured with `terminationGracePeriodSeconds: 30`. For Spring Boot graceful shutdown, add to `application.properties`:
```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

### EFS Integration

The application uses EFS-backed paths for configuration and logs:
- `APP_CONFIG_FILE_PATH`: `/mnt/efs/app/config/app.properties`
- `APP_LOG_DIR`: `/var/log/mini-app`
- `APP_LOG_FILE_PATH`: `/mnt/efs/logs/mini-app.log`

To mount EFS in EKS, install the Amazon EFS CSI Driver and create a `PersistentVolumeClaim` referencing your EFS file system.
