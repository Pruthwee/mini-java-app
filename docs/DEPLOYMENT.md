# Deployment Guide — mini-java-app on AWS EKS

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build and Push Docker Image](#build-and-push-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Environment Variables Reference](#environment-variables-reference)
9. [Health Checks and Monitoring](#health-checks-and-monitoring)
10. [Scaling and Rolling Updates](#scaling-and-rolling-updates)
11. [Troubleshooting](#troubleshooting)
12. [Security Considerations](#security-considerations)

---

## Overview

**Application**: mini-java-app  
**Framework**: Spring Boot 2.7.0  
**Java Version**: 11  
**Build Tool**: Maven  
**Package Type**: JAR  
**Application Port**: 8080  
**Health Endpoint**: `/actuator/health` (Spring Boot Actuator)  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Runtime Base Image**: `eclipse-temurin:11-jdk`

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Container build and run |
| Docker Compose | 2.x | Local multi-container orchestration |
| Java JDK | 11 | Local build (optional) |
| Maven | 3.9+ | Local build (optional) |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | AWS authentication and ECR access |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.150+ | EKS cluster creation (optional) |

### AWS IAM Permissions Required
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:CreateRepository",
        "ecr:DescribeRepositories",
        "eks:DescribeCluster",
        "eks:ListClusters"
      ],
      "Resource": "*"
    }
  ]
}
```

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
│       ├── java/com/test/
│       │   ├── MiniAppApplication.java   # Spring Boot entry point
│       │   ├── HealthController.java     # Custom /health endpoint
│       │   ├── DatabaseService.java      # Database connectivity
│       │   └── MiniApp.java              # Core application logic
│       └── resources/
│           └── application.properties   # Application configuration
├── kubernetes/
│   ├── namespace.yaml            # Kubernetes namespace
│   ├── deployment.yaml           # Application deployment
│   ├── service.yaml              # ClusterIP service
│   └── ingress.yaml              # AWS ALB ingress
├── k8s/
│   └── network-policy.yaml       # Network policies (service isolation)
├── scripts/
│   ├── build-push.sh             # Linux/macOS build & push script
│   ├── build-push.bat            # Windows build & push script
│   ├── deploy-image.sh           # Linux/macOS EKS deploy script
│   └── deploy-image.bat          # Windows EKS deploy script
└── docs/
    └── DEPLOYMENT.md             # This file
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
EXTERNAL_API_KEY=your-api-key

# Payment Service
PAYMENT_SERVICE_URL=https://payment.internal.company.com/process
PAYMENT_SERVICE_USERNAME=payment_user
PAYMENT_SERVICE_PASSWORD=payment_password

# Security
SECURITY_JWT_SECRET=your-jwt-secret
SECURITY_ADMIN_USERNAME=admin
SECURITY_ADMIN_PASSWORD=your-admin-password
SECURITY_ENCRYPTION_KEY=your-encryption-key

# Monitoring
MONITORING_ENDPOINT=http://monitoring.internal.company.com:9090/metrics
MONITORING_USERNAME=monitor_user
MONITORING_PASSWORD=monitor_password

# Messaging (RabbitMQ)
MESSAGING_RABBITMQ_HOST=rabbitmq.internal.company.com
MESSAGING_RABBITMQ_PORT=5672
MESSAGING_RABBITMQ_USERNAME=rabbitmq_user
MESSAGING_RABBITMQ_PASSWORD=rabbitmq_password
```

### 2. Build and Start

```bash
# Build and start the application
docker-compose up --build

# Run in background
docker-compose up --build -d

# View logs
docker-compose logs -f mini-java-app

# Stop
docker-compose down
```

### 3. Verify Application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Custom health endpoint
curl http://localhost:8080/health
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

The script will prompt you to:
1. Select registry type (AWS ECR or Docker Hub)
2. Enter registry credentials and details
3. Specify an image tag (defaults to `latest`)

### Windows

```cmd
scripts\build-push.bat
```

### Manual Docker Build

```bash
# Build image
docker build -t mini-java-app:latest .

# Tag for ECR
docker tag mini-java-app:latest \
  123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Push
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest
```

---

## AWS EKS Deployment

### Step 1: Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

### Step 2: Create or Connect to EKS Cluster

**Create a new cluster (if needed):**
```bash
eksctl create cluster \
  --name my-eks-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

**Connect to existing cluster:**
```bash
aws eks update-kubeconfig --region us-east-1 --name my-eks-cluster
kubectl cluster-info
```

### Step 3: Install AWS Load Balancer Controller (for Ingress)

```bash
# Add EKS Helm chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=my-eks-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 4: Deploy Using Script

**Linux / macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region and EKS cluster name
- Full Docker image URI
- All application environment variables

### Step 5: Manual Deployment (Alternative)

```bash
# 1. Apply namespace
kubectl apply -f kubernetes/namespace.yaml

# 2. Update image URI in deployment.yaml
sed -i 's|{{IMAGE_URI}}|123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest|g' \
  kubernetes/deployment.yaml

# 3. Apply remaining manifests
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Apply network policies
kubectl apply -f k8s/network-policy.yaml

# 5. Wait for rollout
kubectl rollout status deployment/mini-java-app -n mini-java-app

# 6. Verify
kubectl get pods,svc,ingress -n mini-java-app
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `mini-java-app` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (high availability)
- **Strategy**: RollingUpdate (zero-downtime deployments)
- **Resources**: requests: 250m CPU / 512Mi RAM; limits: 500m CPU / 1Gi RAM
- **Liveness Probe**: `GET /actuator/health` — starts after 60s, every 30s
- **Readiness Probe**: `GET /actuator/health` — starts after 30s, every 15s
- **Security**: Runs as non-root user (UID 1000)

### service.yaml
- **Type**: ClusterIP (internal cluster access only)
- **Port mapping**: 80 → 8080 (container port)

### ingress.yaml
- **Controller**: AWS ALB (Application Load Balancer)
- **Scheme**: internet-facing
- **Health check path**: `/actuator/health`
- **Host**: `mini-java-app.example.com` (update to your domain)

---

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `SPRING_PROFILES_ACTIVE` | `docker` | Active Spring profile |
| `JAVA_OPTS` | `-Xms256m -Xmx512m ...` | JVM options |
| `DB_HOST` | `localhost` | MySQL database host |
| `DB_PORT` | `3306` | MySQL database port |
| `DB_NAME` | `mini_app_db` | MySQL database name |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | _(empty)_ | MySQL password |
| `REDIS_HOST` | `redis.default.svc.cluster.local` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `EXTERNAL_API_BASE_URL` | `http://api.example.com:8080/v1` | External API base URL |
| `EXTERNAL_API_KEY` | _(empty)_ | External API key |
| `EXTERNAL_API_TIMEOUT` | `30000` | External API timeout (ms) |
| `PAYMENT_SERVICE_URL` | _(company URL)_ | Payment service URL |
| `PAYMENT_SERVICE_USERNAME` | _(empty)_ | Payment service username |
| `PAYMENT_SERVICE_PASSWORD` | _(empty)_ | Payment service password |
| `SECURITY_JWT_SECRET` | _(empty)_ | JWT signing secret |
| `SECURITY_ADMIN_USERNAME` | `admin` | Admin username |
| `SECURITY_ADMIN_PASSWORD` | _(empty)_ | Admin password |
| `SECURITY_ENCRYPTION_KEY` | _(empty)_ | Encryption key |
| `MONITORING_ENDPOINT` | _(company URL)_ | Metrics endpoint |
| `MONITORING_USERNAME` | _(empty)_ | Monitoring username |
| `MONITORING_PASSWORD` | _(empty)_ | Monitoring password |
| `MESSAGING_RABBITMQ_HOST` | _(company host)_ | RabbitMQ host |
| `MESSAGING_RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `MESSAGING_RABBITMQ_USERNAME` | _(empty)_ | RabbitMQ username |
| `MESSAGING_RABBITMQ_PASSWORD` | _(empty)_ | RabbitMQ password |
| `APP_LOG_DIR` | `/app/logs` | Log directory |
| `APP_LOG_FILE_PATH` | `/app/logs/mini-app.log` | Log file path |
| `TZ` | `UTC` | Container timezone |

---

## Health Checks and Monitoring

### Available Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Spring Boot Actuator health (liveness + readiness) |
| `GET /actuator/info` | Application info |
| `GET /health` | Custom health endpoint (returns `{"status":"UP","application":"mini-java-app"}`) |

### Check Pod Health

```bash
# Get pod status
kubectl get pods -n mini-java-app

# Describe pod (shows probe events)
kubectl describe pod <pod-name> -n mini-java-app

# View application logs
kubectl logs -f deployment/mini-java-app -n mini-java-app

# Port-forward for local testing
kubectl port-forward deployment/mini-java-app 8080:8080 -n mini-java-app
curl http://localhost:8080/actuator/health
```

---

## Scaling and Rolling Updates

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment mini-java-app --replicas=3 -n mini-java-app

# Verify
kubectl get pods -n mini-java-app
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment mini-java-app \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n mini-java-app

kubectl get hpa -n mini-java-app
```

### Rolling Update (New Image)

```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:v2.0.0 \
  -n mini-java-app

# Monitor rollout
kubectl rollout status deployment/mini-java-app -n mini-java-app

# Rollback if needed
kubectl rollout undo deployment/mini-java-app -n mini-java-app

# View rollout history
kubectl rollout history deployment/mini-java-app -n mini-java-app
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n mini-java-app

# Check logs
kubectl logs <pod-name> -n mini-java-app --previous

# Check resource constraints
kubectl top pods -n mini-java-app
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `ImagePullBackOff` | ECR auth expired or wrong image URI | Re-authenticate ECR; verify image URI |
| `CrashLoopBackOff` | Application startup failure | Check logs; verify env vars (DB, Redis) |
| `OOMKilled` | Insufficient memory | Increase memory limits in deployment.yaml |
| Liveness probe failing | JVM slow startup | Increase `initialDelaySeconds` (currently 60s) |
| Ingress not resolving | ALB controller not installed | Install AWS Load Balancer Controller |
| Database connection refused | Wrong DB_HOST or network policy | Verify DB_HOST env var; check network policies |

### Ingress / ALB Issues

```bash
# Check ingress status
kubectl describe ingress mini-java-app-ingress -n mini-java-app

# Check ALB controller logs
kubectl logs -n kube-system deployment/aws-load-balancer-controller

# Verify ALB annotations
kubectl get ingress mini-java-app-ingress -n mini-java-app -o yaml
```

### Network Policy Issues

```bash
# View network policies
kubectl get networkpolicies -n mini-java-app

# Describe a specific policy
kubectl describe networkpolicy mini-java-app-default-deny -n mini-java-app
```

---

## Security Considerations

1. **Non-root container**: The application runs as UID 1000 (non-root) for security.
2. **Secrets management**: Use AWS Secrets Manager or Kubernetes Secrets for sensitive values (DB passwords, JWT secrets, API keys). Never hardcode secrets in manifests.
3. **Network policies**: `k8s/network-policy.yaml` enforces least-privilege network access — only allow traffic to/from required services.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities.
5. **RBAC**: Apply least-privilege IAM roles for EKS node groups and service accounts.
6. **TLS**: Configure HTTPS via ACM certificate ARN in ingress annotations:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/xxx
   ```
7. **Resource limits**: Always set CPU and memory limits to prevent resource exhaustion.
8. **Read-only filesystem**: Consider adding `readOnlyRootFilesystem: true` to the security context (requires writable volume mounts for logs).

### Using Kubernetes Secrets (Recommended)

```bash
# Create secret for database credentials
kubectl create secret generic mini-java-app-db-secret \
  --from-literal=DB_PASSWORD=your-password \
  --from-literal=DB_USERNAME=your-username \
  -n mini-java-app

# Reference in deployment.yaml
# env:
#   - name: DB_PASSWORD
#     valueFrom:
#       secretKeyRef:
#         name: mini-java-app-db-secret
#         key: DB_PASSWORD
```

---

## Java-Specific Notes

### JVM Configuration
The application uses the following JVM flags (set via `JAVA_OPTS`):
- `-Xms256m -Xmx512m`: Initial and maximum heap size
- `-XX:+UseContainerSupport`: Enables container-aware JVM (Java 11+)
- `-XX:MaxRAMPercentage=75.0`: Limits heap to 75% of container memory
- `-XX:+ExitOnOutOfMemoryError`: Terminates JVM on OOM (allows Kubernetes to restart)
- `-Djava.security.egd=file:/dev/./urandom`: Faster random number generation

### Spring Boot Actuator
The application exposes `/actuator/health` and `/actuator/info` endpoints, configured in `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

### Startup Time
Spring Boot applications typically take 15–60 seconds to start. The Kubernetes probes are configured with:
- Liveness: `initialDelaySeconds: 60`
- Readiness: `initialDelaySeconds: 30`

Adjust these values based on observed startup times in your environment.
