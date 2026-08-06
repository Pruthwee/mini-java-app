# Deployment Guide — mini-java-app on AWS EKS

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build and Push Docker Image](#build-and-push-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Configuration Management](#configuration-management)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Java-Specific Notes](#java-specific-notes)

---

## Overview

**Application**: mini-java-app  
**Framework**: Spring Boot 2.7.0  
**Java Version**: 11  
**Build Tool**: Maven  
**Package Type**: Executable JAR  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Application Port**: 8080  
**Management Port**: 8081 (Spring Boot Actuator)  
**Health Endpoint**: `/actuator/health` (port 8081)  
**Context Path**: `/mini-app`

---

## Prerequisites

### Local Development
- Docker Desktop 24.x or later
- Docker Compose v2.x or later
- Java 11 JDK (for local builds outside Docker)
- Maven 3.9.x (for local builds outside Docker)

### AWS EKS Deployment
- AWS CLI v2 (`aws --version`)
- `kubectl` v1.28+ (`kubectl version --client`)
- `eksctl` v0.160+ (optional, for cluster creation)
- AWS IAM permissions:
  - `eks:DescribeCluster`, `eks:UpdateKubeconfig`
  - `ecr:GetAuthorizationToken`, `ecr:CreateRepository`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`
  - `ec2:DescribeVpcs`, `ec2:DescribeSubnets` (for ALB Ingress)
- AWS Load Balancer Controller installed on the EKS cluster

---

## Project Structure

```
mini-java-app/
├── Dockerfile                    # Multi-stage Docker build
├── .dockerignore                 # Files excluded from Docker context
├── docker-compose.yml            # Local development compose file
├── pom.xml                       # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/test/        # Application source code
│       └── resources/
│           └── application.properties  # Spring Boot configuration
├── kubernetes/
│   ├── namespace.yaml            # Kubernetes Namespace
│   ├── deployment.yaml           # Kubernetes Deployment
│   ├── service.yaml              # Kubernetes Service (ClusterIP)
│   └── ingress.yaml              # Kubernetes Ingress (AWS ALB)
├── k8s/
│   └── network-policy.yaml       # Kubernetes NetworkPolicy (least-privilege)
├── scripts/
│   ├── build-push.sh             # Linux/macOS: build & push image
│   ├── build-push.bat            # Windows: build & push image
│   ├── deploy-image.sh           # Linux/macOS: deploy to EKS
│   └── deploy-image.bat          # Windows: deploy to EKS
└── docs/
    └── DEPLOYMENT.md             # This guide
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
REDIS_PASSWORD=your-redis-password

# External API
EXTERNAL_API_BASE_URL=https://api.example.com/v1
EXTERNAL_API_KEY=your-api-key

# Payment Service
PAYMENT_SERVICE_URL=https://payment.example.com/process
PAYMENT_SERVICE_USERNAME=your-payment-user
PAYMENT_SERVICE_PASSWORD=your-payment-password

# Security
JWT_SECRET=your-jwt-secret
ADMIN_USERNAME=admin
ADMIN_PASSWORD=your-admin-password
ENCRYPTION_KEY=your-encryption-key

# Messaging
RABBITMQ_HOST=your-rabbitmq-host
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=your-rabbitmq-user
RABBITMQ_PASSWORD=your-rabbitmq-password
```

### 2. Build and Start the Application

```bash
# Build and start
docker compose up --build

# Start in background
docker compose up --build -d

# View logs
docker compose logs -f mini-java-app

# Stop
docker compose down
```

### 3. Verify the Application

```bash
# Health check (Actuator)
curl http://localhost:8081/actuator/health

# Application health endpoint
curl http://localhost:8080/mini-app/health

# Actuator info
curl http://localhost:8081/actuator/info
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (defaults to `latest`)
2. Select a registry (AWS ECR or Docker Hub)
3. Provide registry credentials and details

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (AWS ECR)

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
ECR_REPO=mini-java-app
IMAGE_TAG=1.0.0

# Authenticate
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# Create repository (if not exists)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION

# Build and push
FULL_IMAGE=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}:${IMAGE_TAG}
docker build -f Dockerfile -t $FULL_IMAGE .
docker push $FULL_IMAGE
```

---

## AWS EKS Deployment

### Step 1: Set Up EKS Cluster (if not already created)

```bash
# Create cluster using eksctl
eksctl create cluster \
  --name my-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

### Step 2: Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name my-cluster
kubectl cluster-info
```

### Step 3: Install AWS Load Balancer Controller

```bash
# Install cert-manager
kubectl apply --validate=false -f \
  https://github.com/jetstack/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Install AWS Load Balancer Controller
helm repo add eks https://aws.github.io/eks-charts
helm repo update
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=my-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 4: Deploy the Application

#### Linux / macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS region and EKS cluster name
- Full Docker image URI
- All required environment variables (database, Redis, API keys, etc.)

### Step 5: Verify Deployment

```bash
# Check pods
kubectl get pods -n mini-java-app

# Check services
kubectl get svc -n mini-java-app

# Check ingress (wait for ALB to provision)
kubectl get ingress -n mini-java-app

# View pod logs
kubectl logs -f deployment/mini-java-app -n mini-java-app

# Describe deployment
kubectl describe deployment mini-java-app -n mini-java-app
```

### Step 6: Apply Network Policies

```bash
kubectl apply -f k8s/network-policy.yaml -n mini-java-app
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `mini-java-app` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (high availability)
- **Image**: `{{IMAGE_URI}}` — replaced by deploy script
- **Ports**: 8080 (HTTP), 8081 (management/actuator)
- **Resources**:
  - Requests: CPU 250m, Memory 512Mi
  - Limits: CPU 500m, Memory 1Gi
- **Liveness Probe**: `GET /actuator/health` on port 8081 (initial delay: 60s)
- **Readiness Probe**: `GET /actuator/health` on port 8081 (initial delay: 30s)
- **JVM Options**: Container-aware heap sizing with `UseContainerSupport`

### service.yaml
- **Type**: ClusterIP (internal cluster access only)
- **Port 80** → container port 8080 (HTTP)
- **Port 8081** → container port 8081 (management)

### ingress.yaml
- **Class**: AWS ALB (Application Load Balancer)
- **Scheme**: internet-facing
- **Host**: `mini-java-app.example.com` (update to your domain)
- **Health Check**: `/actuator/health` on port 8081

---

## Configuration Management

### Environment Variables Reference

| Variable | Source | Description | Default |
|---|---|---|---|
| `DB_HOST` | ConfigMap | Database hostname | `localhost` |
| `DB_PORT` | ConfigMap | Database port | `3306` |
| `DB_NAME` | ConfigMap | Database name | `mini_app_db` |
| `DB_USERNAME` | Secret | Database username | `root` |
| `DB_PASSWORD` | Secret | Database password | _(empty)_ |
| `REDIS_HOST` | ConfigMap | Redis hostname | `redis.local` |
| `REDIS_PORT` | ConfigMap | Redis port | `6379` |
| `REDIS_PASSWORD` | Secret | Redis password | _(empty)_ |
| `EXTERNAL_API_BASE_URL` | ConfigMap | External API base URL | `http://api.example.com:8080/v1` |
| `EXTERNAL_API_KEY` | Secret | External API key | _(empty)_ |
| `PAYMENT_SERVICE_URL` | ConfigMap | Payment service URL | `https://payment.internal.company.com/process` |
| `JWT_SECRET` | Secret | JWT signing secret | _(empty)_ |
| `RABBITMQ_HOST` | ConfigMap | RabbitMQ hostname | `rabbitmq.internal.company.com` |
| `RABBITMQ_PORT` | ConfigMap | RabbitMQ port | `5672` |
| `MONITORING_ENDPOINT` | ConfigMap | Monitoring endpoint | `http://monitoring.internal.company.com:9090/metrics` |

### Using Kubernetes Secrets (Recommended for Production)

```bash
kubectl create secret generic mini-java-app-secrets \
  --from-literal=DB_PASSWORD=your-db-password \
  --from-literal=DB_USERNAME=your-db-user \
  --from-literal=REDIS_PASSWORD=your-redis-password \
  --from-literal=JWT_SECRET=your-jwt-secret \
  --from-literal=EXTERNAL_API_KEY=your-api-key \
  --from-literal=ENCRYPTION_KEY=your-encryption-key \
  -n mini-java-app
```

### Using Kubernetes ConfigMaps

```bash
kubectl create configmap mini-java-app-config \
  --from-literal=DB_HOST=your-db-host \
  --from-literal=DB_PORT=3306 \
  --from-literal=DB_NAME=mini_app_db \
  --from-literal=REDIS_HOST=your-redis-host \
  --from-literal=RABBITMQ_HOST=your-rabbitmq-host \
  -n mini-java-app
```

---

## Scaling and Management

### Horizontal Pod Autoscaling

```bash
kubectl autoscale deployment mini-java-app \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n mini-java-app

kubectl get hpa -n mini-java-app
```

### Manual Scaling

```bash
kubectl scale deployment mini-java-app --replicas=4 -n mini-java-app
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=<new-image-uri> \
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

# View previous container logs (if crashed)
kubectl logs <pod-name> -n mini-java-app --previous
```

### Common Issues

**CrashLoopBackOff**
- Check logs: `kubectl logs <pod-name> -n mini-java-app`
- Verify environment variables are set correctly
- Ensure database/Redis/external services are reachable
- Check JVM heap settings vs container memory limits

**OOMKilled (Out of Memory)**
- Increase memory limits in `kubernetes/deployment.yaml`
- Adjust JVM heap: `-Xmx` should be ≤ 75% of container memory limit
- `MaxRAMPercentage=75.0` handles this automatically with `UseContainerSupport`

**Readiness Probe Failing**
- Verify `/actuator/health` returns HTTP 200 on port 8081
- Increase `initialDelaySeconds` if JVM startup is slow (Spring Boot can take 30-60s)
- Check `management.server.port=8081` in application.properties

**Ingress Not Getting External IP**
- Verify AWS Load Balancer Controller is installed
- Check ingress annotations: `kubernetes.io/ingress.class: alb`
- Verify IAM permissions for ALB creation
- Check: `kubectl describe ingress mini-java-app-ingress -n mini-java-app`

**Database Connection Refused**
- Verify `DB_HOST`, `DB_PORT`, `DB_NAME` environment variables
- Check NetworkPolicy allows egress to MySQL on port 3306
- Ensure RDS security group allows inbound from EKS node security group

### Health Check Endpoints

```bash
# Port-forward for local testing
kubectl port-forward deployment/mini-java-app 8080:8080 8081:8081 -n mini-java-app

# Test health
curl http://localhost:8081/actuator/health
curl http://localhost:8080/mini-app/health
```

---

## Security Considerations

1. **Non-root Container**: The application runs as `appuser` (non-root) inside the container.
2. **Secrets Management**: Use Kubernetes Secrets (or AWS Secrets Manager with External Secrets Operator) for sensitive values — never hardcode credentials.
3. **Network Policies**: Apply `k8s/network-policy.yaml` to enforce least-privilege network access between services.
4. **Image Scanning**: Enable ECR image scanning to detect vulnerabilities in the container image.
5. **RBAC**: Apply least-privilege RBAC roles for the application's service account.
6. **TLS**: Configure HTTPS on the ALB Ingress using ACM certificates:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789:certificate/xxx
   alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
   ```
7. **Pod Security**: Consider applying Pod Security Standards (restricted profile) to the namespace.

---

## Java-Specific Notes

### JVM Container Awareness
The Dockerfile and deployment use `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0`, which allow the JVM to automatically detect container memory limits and size the heap accordingly. This prevents OOMKilled errors.

### Spring Boot Actuator
- Health endpoint: `http://<host>:8081/actuator/health`
- Info endpoint: `http://<host>:8081/actuator/info`
- Configured via `management.endpoints.web.exposure.include=health,info`

### Spring Profiles
Set `SPRING_PROFILES_ACTIVE=docker` (or `production`) to activate environment-specific configuration. Create `application-docker.properties` or `application-production.properties` for profile-specific overrides.

### Startup Time
Spring Boot applications typically take 20-60 seconds to start. The Kubernetes probes are configured with appropriate `initialDelaySeconds` (60s for liveness, 30s for readiness) to accommodate JVM warm-up time.

### Graceful Shutdown
The deployment uses `terminationGracePeriodSeconds: 30` to allow in-flight requests to complete before the pod is terminated. Spring Boot 2.3+ supports graceful shutdown via `server.shutdown=graceful`.

### Logging
Configure structured JSON logging for better observability in EKS:
```properties
logging.pattern.console={"timestamp":"%d","level":"%p","logger":"%c","message":"%m"}%n
```
