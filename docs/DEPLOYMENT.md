# Deployment Guide — mini-java-app on AWS EKS

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Building and Pushing the Docker Image](#building-and-pushing-the-docker-image)
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
**Language / Runtime**: Java 11  
**Build Tool**: Maven  
**Packaging**: JAR  
**Application Port**: 8080  
**Health Check Port**: 8081 (`/health`)  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)

This guide covers everything needed to build, containerize, and deploy the `mini-java-app` Java application to AWS EKS.

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
| AWS CLI | 2.x | AWS authentication and ECR interaction |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.150+ | EKS cluster creation (optional) |

### AWS IAM Permissions Required
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:PutImage`
- `ecr:InitiateLayerUpload`
- `ecr:UploadLayerPart`
- `ecr:CompleteLayerUpload`
- `ecr:CreateRepository`
- `eks:DescribeCluster`
- `eks:UpdateKubeconfig`

---

## Project Structure

```
mini_java_comp_mcont_1452/
├── Dockerfile                  # Multi-stage Docker build
├── docker-compose.yml          # Local development compose file
├── .dockerignore               # Docker build exclusions
├── pom.xml                     # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/test/
│       │   ├── MiniApp.java
│       │   ├── HealthCheckServer.java
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

```env
# Database
DB_HOST=your-db-host
DB_PORT=3306
DB_NAME=mini_app_db
DB_USERNAME=your-db-user
DB_PASSWORD=your-db-password

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# External Services
EXTERNAL_API_URL=http://api.example.com:8080/v1
PAYMENT_SERVICE_URL=https://payment.internal.company.com/process
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
# Check health endpoint
curl http://localhost:8081/health

# Expected response:
# {"status":"UP","service":"mini-java-app"}

# Check application port
curl http://localhost:8080/
```

---

## Building and Pushing the Docker Image

### Linux / macOS

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run from project root
./scripts/build-push.sh
```

The script will prompt for:
1. Image tag (default: `latest`)
2. Registry type: `1` for AWS ECR, `2` for Docker Hub
3. Registry-specific credentials

### Windows

```cmd
# Run from project root
scripts\build-push.bat
```

### Manual Build (Advanced)

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
  --nodes-max 4
```

**Connect to an existing cluster:**
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

### Step 4: Run the Deployment Script

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
- AWS region
- EKS cluster name
- Full Docker image URI
- Database connection details
- Redis connection details
- External service URLs

### Step 5: Verify Deployment

```bash
# Check all resources in the namespace
kubectl get all -n mini-java-app

# Check pod status
kubectl get pods -n mini-java-app

# Check pod logs
kubectl logs -l app=mini-java-app -n mini-java-app --tail=100

# Check ingress
kubectl get ingress -n mini-java-app

# Describe deployment
kubectl describe deployment mini-java-app -n mini-java-app
```

### Step 6: Access the Application

```bash
# Get the ingress hostname
kubectl get ingress mini-java-app-ingress -n mini-java-app \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `mini-java-app` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (high availability)
- **Image**: Placeholder `{{IMAGE_URI}}` replaced at deploy time
- **Resources**: 250m CPU / 512Mi memory (requests); 500m CPU / 1Gi memory (limits)
- **Liveness Probe**: `GET /health` on port 8081 — restarts unhealthy pods
- **Readiness Probe**: `GET /health` on port 8081 — gates traffic routing
- **Security Context**: Runs as non-root user (UID 1000)

### service.yaml
- **Type**: ClusterIP — internal cluster access only
- **Port 80** → container port 8080 (application)
- **Port 8081** → container port 8081 (health)

### ingress.yaml
- **Class**: AWS ALB (Application Load Balancer)
- **Scheme**: internet-facing
- **Health check**: `/health` on port 8081
- **Host**: `mini-java-app.example.com` (update to your actual domain)

---

## Configuration Management

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `HEALTH_CHECK_PORT` | `8081` | Health check HTTP port |
| `APP_CONFIG_FILE_PATH` | `/opt/app/config/app.properties` | Config file path |
| `APP_LOG_FILE_PATH` | `/mnt/efs/logs/mini-app/app.log` | Log file path |
| `APP_LOG_DIRECTORY` | `/mnt/efs/logs/mini-app` | Log directory |
| `DB_HOST` | `localhost` | Database hostname |
| `DB_PORT` | `3306` | Database port |
| `DB_NAME` | `mini_app_db` | Database name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `REDIS_HOST` | `redis-service.default.svc.cluster.local` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `EXTERNAL_API_URL` | `http://api.example.com:8080/v1` | External API URL |
| `PAYMENT_SERVICE_URL` | `https://payment.internal.company.com/process` | Payment service URL |
| `JAVA_OPTS` | JVM flags | JVM memory and GC settings |

### Using Kubernetes Secrets for Sensitive Values

```bash
# Create a secret for database credentials
kubectl create secret generic mini-java-app-db-secret \
  --from-literal=DB_PASSWORD=your-actual-password \
  -n mini-java-app

# Reference in deployment.yaml:
# - name: DB_PASSWORD
#   valueFrom:
#     secretKeyRef:
#       name: mini-java-app-db-secret
#       key: DB_PASSWORD
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment mini-java-app --replicas=3 -n mini-java-app
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment mini-java-app \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n mini-java-app

# Check HPA status
kubectl get hpa -n mini-java-app
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:v2.0.0 \
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
# Check pod events
kubectl describe pod -l app=mini-java-app -n mini-java-app

# Check pod logs
kubectl logs -l app=mini-java-app -n mini-java-app --previous

# Check resource constraints
kubectl top pods -n mini-java-app
```

### Health Check Failing

```bash
# Port-forward to test health endpoint directly
kubectl port-forward deployment/mini-java-app 8081:8081 -n mini-java-app

# In another terminal:
curl http://localhost:8081/health
# Expected: {"status":"UP","service":"mini-java-app"}
```

### Database Connection Issues

```bash
# Verify environment variables in running pod
kubectl exec -it $(kubectl get pod -l app=mini-java-app -n mini-java-app -o jsonpath='{.items[0].metadata.name}') \
  -n mini-java-app -- env | grep DB_

# Check pod logs for connection errors
kubectl logs -l app=mini-java-app -n mini-java-app | grep -i "database\|connection\|sql"
```

### Ingress Not Accessible

```bash
# Check ingress status
kubectl describe ingress mini-java-app-ingress -n mini-java-app

# Verify AWS Load Balancer Controller is running
kubectl get pods -n kube-system | grep aws-load-balancer

# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

### OOMKilled (Out of Memory)

```bash
# Check pod resource usage
kubectl top pods -n mini-java-app

# Increase memory limits in deployment.yaml:
# resources:
#   limits:
#     memory: "2Gi"
# Also increase JAVA_OPTS: -Xmx1g -Xms512m
```

---

## Security Considerations

1. **Non-root container**: The application runs as UID 1000 (non-root) for security.
2. **Secrets management**: Use Kubernetes Secrets (or AWS Secrets Manager) for passwords and API keys — never hardcode them.
3. **Network policies**: Consider adding Kubernetes NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Scan Docker images with Amazon ECR image scanning or Trivy before deployment.
5. **RBAC**: Apply least-privilege IAM roles to EKS node groups and service accounts.
6. **TLS**: Configure HTTPS on the ALB ingress using AWS Certificate Manager (ACM).

```yaml
# Example: Add TLS to ingress.yaml
annotations:
  alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/xxx
  alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
  alb.ingress.kubernetes.io/ssl-redirect: '443'
```

---

## Java-Specific Notes

### JVM Memory Configuration

The container uses these JVM flags (set via `JAVA_OPTS`):

```
-XX:+UseContainerSupport      # Respect container memory limits
-XX:MaxRAMPercentage=75.0     # Use 75% of container memory for heap
-Xms256m                      # Initial heap size
-Xmx512m                      # Maximum heap size
-Dfile.encoding=UTF-8         # Character encoding
-Duser.timezone=UTC           # Timezone
```

Adjust `JAVA_OPTS` in `deployment.yaml` if the application requires more memory.

### JVM Startup Time

Java applications have a longer startup time than native binaries. The Kubernetes probes are configured with:
- **Liveness probe**: `initialDelaySeconds: 30` — gives JVM time to start
- **Readiness probe**: `initialDelaySeconds: 20` — gates traffic until app is ready

If the application takes longer to start, increase `initialDelaySeconds` in `kubernetes/deployment.yaml`.

### Graceful Shutdown

The deployment is configured with `terminationGracePeriodSeconds: 30`, giving the JVM 30 seconds to complete in-flight requests before the pod is forcefully terminated.

### Logging

Logs are written to `/mnt/efs/logs/mini-app/` (configurable via `APP_LOG_DIRECTORY`). In EKS, mount an EFS PersistentVolume at this path for persistent log storage across pod restarts.

```bash
# Stream logs from all pods
kubectl logs -l app=mini-java-app -n mini-java-app -f --max-log-requests=10
```
