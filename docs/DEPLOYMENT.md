# mini-java-app – Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Building and Pushing the Docker Image](#building-and-pushing-the-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Configuration Reference](#configuration-reference)
8. [Troubleshooting](#troubleshooting)
9. [Scaling and Management](#scaling-and-management)
10. [Security Considerations](#security-considerations)

---

## Overview

**Application**: mini-java-app  
**Technology**: Java 11, Spring Boot 2.7, Maven  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Container Registry**: AWS ECR or Docker Hub  
**Health Endpoint**: `/actuator/health`  
**Application Port**: `8080`

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Build and run containers |
| Docker Compose | 2.x | Local multi-container orchestration |
| Java JDK | 11+ | Local builds (optional) |
| Maven | 3.9+ | Local builds (optional) |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | AWS authentication and ECR access |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.150+ | EKS cluster creation (optional) |

### AWS IAM Permissions Required
```
ecr:GetAuthorizationToken
ecr:BatchCheckLayerAvailability
ecr:GetDownloadUrlForLayer
ecr:BatchGetImage
ecr:CreateRepository
ecr:DescribeRepositories
ecr:PutImage
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
DB_HOST=your-database-host
DB_PORT=3306
DB_NAME=mini_app_db
DB_USERNAME=your-db-user
DB_PASSWORD=your-db-password

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# File paths (local dev)
APP_CONFIG_DIR=./config
APP_LOG_DIR=./logs
APP_TEMP_DIR=./tmp
APP_UPLOAD_DIR=./uploads
APP_CONFIG_FILE_PATH=./config/app.properties
APP_LOG_FILE_PATH=./logs/mini-app.log
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

# Expected response:
# {"status":"UP"}
```

---

## Building and Pushing the Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

### Script Prompts

The script will interactively ask for:

1. **Registry type**: `1` for AWS ECR, `2` for Docker Hub
2. **Image tag**: e.g., `1.0.0`, `latest`, `v2024-01-15`
3. **Registry-specific details**:
   - **ECR**: AWS region, AWS account ID, ECR repository name
   - **Docker Hub**: username, password/token, repository name

### Manual Build (Advanced)

```bash
# Build
docker build -t mini-java-app:latest .

# Tag for ECR
docker tag mini-java-app:latest \
  123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest

# Push to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest
```

---

## AWS EKS Deployment

### Step 1: Set Up AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

### Step 2: Create or Connect to EKS Cluster

```bash
# Create a new cluster (if needed)
eksctl create cluster \
  --name my-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4

# Connect to existing cluster
aws eks update-kubeconfig --region us-east-1 --name my-cluster

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### Step 3: Install AWS Load Balancer Controller (for Ingress)

```bash
# Add EKS Helm repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=my-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 4: Deploy the Application

#### Using the Deploy Script (Recommended)

```bash
# Linux/macOS
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh

# Windows
scripts\deploy-image.bat
```

The script will prompt for:
- AWS region
- EKS cluster name
- Full Docker image URI (e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest`)
- Optional environment variables (DB_HOST, DB_PASSWORD, REDIS_HOST, etc.)

#### Manual Deployment

```bash
# 1. Update the image URI in deployment.yaml
sed -i 's|{{IMAGE_URI}}|123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest|g' \
  kubernetes/deployment.yaml

# 2. Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 3. Wait for rollout
kubectl rollout status deployment/mini-java-app -n mini-java-app

# 4. Verify
kubectl get pods,svc,ingress -n mini-java-app
```

### Step 5: Access the Application

```bash
# Get the ingress hostname (ALB DNS name)
kubectl get ingress mini-java-app-ingress -n mini-java-app \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Health check
curl http://<ALB_DNS>/actuator/health
```

---

## Configuration Reference

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `SPRING_PROFILES_ACTIVE` | `docker` | Active Spring profile |
| `DB_HOST` | `localhost` | Database hostname |
| `DB_PORT` | `3306` | Database port |
| `DB_NAME` | `mini_app_db` | Database name |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `DB_URL` | _(auto-built)_ | Full JDBC URL (overrides host/port/name) |
| `REDIS_HOST` | `redis.internal.svc.cluster.local` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `APP_CONFIG_DIR` | `/mnt/efs/app/config` | Config directory (EFS mount) |
| `APP_LOG_DIR` | `/mnt/efs/logs` | Log directory (EFS mount) |
| `APP_TEMP_DIR` | `/mnt/efs/tmp/mini-app` | Temp directory (EFS mount) |
| `APP_UPLOAD_DIR` | `/mnt/efs/uploads` | Upload directory (EFS mount) |
| `JAVA_OPTS` | _(JVM flags)_ | JVM tuning options |

### JVM Options (Default)

```
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+UnlockExperimentalVMOptions
-Xms256m
-Xmx512m
-Dfile.encoding=UTF-8
-Duser.timezone=UTC
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

| Issue | Cause | Fix |
|-------|-------|-----|
| `ImagePullBackOff` | Wrong image URI or missing ECR permissions | Verify image URI and IAM role |
| `CrashLoopBackOff` | Application startup failure | Check logs for DB connection errors |
| `Pending` pods | Insufficient cluster resources | Scale node group or reduce resource requests |
| Health probe failing | App not ready in time | Increase `initialDelaySeconds` in deployment.yaml |
| Ingress not getting hostname | ALB controller not installed | Install AWS Load Balancer Controller |

### Database Connection Issues

```bash
# Test DB connectivity from within the cluster
kubectl run -it --rm debug --image=mysql:8 --restart=Never -n mini-java-app -- \
  mysql -h $DB_HOST -u $DB_USERNAME -p$DB_PASSWORD $DB_NAME
```

### View Application Logs

```bash
# Stream logs from all pods
kubectl logs -f -l app=mini-java-app -n mini-java-app

# Logs from specific pod
kubectl logs -f <pod-name> -n mini-java-app
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment mini-java-app --replicas=3 -n mini-java-app

# Verify
kubectl get pods -n mini-java-app
```

### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA (scale between 2-10 pods at 70% CPU)
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

## Security Considerations

1. **Non-root container**: The application runs as a non-root user (`appuser`) inside the container.
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager for sensitive values (DB passwords, JWT secrets). Never store secrets in environment variables in plain text in production.
3. **Network policies**: Apply Kubernetes NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities.
5. **RBAC**: Apply least-privilege IAM roles to EKS node groups and service accounts.
6. **TLS**: Configure HTTPS on the ALB ingress using AWS Certificate Manager (ACM).

### Example: Using Kubernetes Secret for DB Password

```bash
# Create secret
kubectl create secret generic mini-java-app-secrets \
  --from-literal=DB_PASSWORD=your-secure-password \
  --from-literal=REDIS_PASSWORD=your-redis-password \
  -n mini-java-app
```

Then reference in `deployment.yaml`:
```yaml
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: mini-java-app-secrets
      key: DB_PASSWORD
```

---

## Java-Specific Notes

- **Spring Boot Actuator** is enabled and exposes `/actuator/health` for liveness and readiness probes.
- **JVM container support** (`-XX:+UseContainerSupport`) ensures the JVM respects container memory limits rather than host memory.
- **MaxRAMPercentage=75.0** allows the JVM to use up to 75% of the container's memory limit for the heap.
- **Graceful shutdown**: The deployment is configured with `terminationGracePeriodSeconds: 30` to allow in-flight requests to complete.
- **Spring profile**: Set `SPRING_PROFILES_ACTIVE=docker` to activate Docker-specific configuration.
- **EFS mounts**: The application expects EFS-backed PersistentVolumeClaims for config, logs, temp, and upload directories in production EKS deployments.
