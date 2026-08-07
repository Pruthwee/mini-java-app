# mini-java-app – Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Building and Pushing the Docker Image](#building-and-pushing-the-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Environment Variables Reference](#environment-variables-reference)
9. [Secrets Management](#secrets-management)
10. [Scaling and Rolling Updates](#scaling-and-rolling-updates)
11. [Troubleshooting](#troubleshooting)
12. [Security Considerations](#security-considerations)

---

## Overview

**mini-java-app** is a Spring Boot 2.7.0 / Java 11 microservice packaged as an executable JAR and deployed to AWS EKS (Elastic Kubernetes Service).

| Property | Value |
|---|---|
| Framework | Spring Boot 2.7.0 |
| Java Version | 11 |
| Build Tool | Maven |
| Package Type | Executable JAR |
| Application Port | 8080 |
| Health Endpoint | `/actuator/health` |
| Runtime Base Image | `eclipse-temurin:11-jdk` |

---

## Prerequisites

### Local Development
| Tool | Minimum Version | Install |
|---|---|---|
| Docker | 24.x | https://docs.docker.com/get-docker/ |
| Docker Compose | 2.x | Bundled with Docker Desktop |
| Java JDK | 11 | https://adoptium.net/ |
| Maven | 3.9.x | https://maven.apache.org/download.cgi |

### AWS EKS Deployment
| Tool | Minimum Version | Install |
|---|---|---|
| AWS CLI | 2.x | https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html |
| kubectl | 1.28+ | https://kubernetes.io/docs/tasks/tools/ |
| eksctl | 0.170+ | https://eksctl.io/installation/ |

### Required IAM Permissions
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
├── Dockerfile                    # Multi-stage build (builder + runtime)
├── .dockerignore                 # Excludes target/, wrapper files, IDE files
├── docker-compose.yml            # Local development (app only)
├── pom.xml                       # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/test/
│       │   ├── MiniAppApplication.java   # Spring Boot entry point
│       │   ├── HealthController.java     # /health endpoint
│       │   ├── DatabaseService.java      # DB connection (env-var driven)
│       │   └── MiniApp.java              # Core application logic
│       └── resources/
│           └── application.properties   # Spring Boot configuration
├── kubernetes/
│   ├── namespace.yaml            # Kubernetes namespace
│   ├── deployment.yaml           # Deployment with 2 replicas
│   ├── service.yaml              # ClusterIP service
│   └── ingress.yaml              # AWS ALB ingress
├── scripts/
│   ├── build-push.sh             # Linux/macOS build & push
│   ├── build-push.bat            # Windows build & push
│   ├── deploy-image.sh           # Linux/macOS EKS deploy
│   └── deploy-image.bat          # Windows EKS deploy
└── docs/
    └── DEPLOYMENT.md             # This file
```

---

## Local Development with Docker Compose

### 1. Configure environment variables

Create a `.env` file in the project root (never commit this file):

```bash
# Database
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379

# External services
EXTERNAL_API_URL=https://api.example.com/v1
PAYMENT_SERVICE_URL=https://payment.example.com/process

# Security
SECURITY_JWT_SECRET=your_jwt_secret_here
SECURITY_ADMIN_USERNAME=admin
SECURITY_ADMIN_PASSWORD=your_admin_password

# Monitoring
MONITORING_ENDPOINT=http://monitoring.example.com:9090/metrics
MONITORING_USERNAME=monitor_user
MONITORING_PASSWORD=monitor_password

# Application
APP_ENV=development
APP_LOG_LEVEL=DEBUG
APP_CONTEXT_PATH=/mini-app
```

### 2. Build and start the application

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

### 3. Verify the application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Custom health endpoint
curl http://localhost:8080/health
```

---

## Building and Pushing the Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
bash scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (defaults to `latest`)
2. Select a registry: **1) AWS ECR** or **2) Docker Hub**
3. Provide registry credentials

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

# Authenticate
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

### Step 2: Create or connect to an EKS cluster

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
```

### Step 3: Install AWS Load Balancer Controller (for ALB Ingress)

```bash
# Add IAM policy for ALB controller
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.1/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Install via Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=my-cluster \
  --set serviceAccount.create=true
```

### Step 4: Create Kubernetes Secrets

Before deploying, create the required secret:

```bash
kubectl create namespace mini-java-app

kubectl create secret generic mini-java-app-secrets \
  --namespace mini-java-app \
  --from-literal=db-username='your_db_user' \
  --from-literal=db-password='your_db_password' \
  --from-literal=jwt-secret='your_jwt_secret' \
  --from-literal=admin-username='admin' \
  --from-literal=admin-password='your_admin_password' \
  --from-literal=monitoring-username='monitor_user' \
  --from-literal=monitoring-password='monitor_password'
```

### Step 5: Run the deployment script

```bash
# Linux / macOS
chmod +x scripts/deploy-image.sh
bash scripts/deploy-image.sh

# Windows
scripts\deploy-image.bat
```

The script will prompt for:
- AWS region and EKS cluster name
- Full Docker image URI (e.g. `123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:1.0.0`)
- Optional environment variable overrides (DB_URL, REDIS_HOST, etc.)

### Step 6: Verify the deployment

```bash
# Check pods
kubectl get pods -n mini-java-app

# Check services
kubectl get svc -n mini-java-app

# Check ingress (wait for ALB to provision)
kubectl get ingress -n mini-java-app

# View pod logs
kubectl logs -l app=mini-java-app -n mini-java-app --tail=100

# Describe a pod for events
kubectl describe pod -l app=mini-java-app -n mini-java-app
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `mini-java-app` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (high availability)
- **Image**: Placeholder `{{IMAGE_URI}}` replaced by deploy script
- **Resources**: requests: 250m CPU / 512Mi RAM; limits: 500m CPU / 1Gi RAM
- **Liveness Probe**: `GET /actuator/health` — starts after 60s, every 30s
- **Readiness Probe**: `GET /actuator/health` — starts after 30s, every 15s
- **Graceful Shutdown**: `terminationGracePeriodSeconds: 30`

### service.yaml
- **Type**: ClusterIP (internal only)
- **Port mapping**: 80 → 8080

### ingress.yaml
- **Controller**: AWS ALB (via `kubernetes.io/ingress.class: alb`)
- **Scheme**: internet-facing
- **Health check path**: `/actuator/health`
- **Host**: `mini-java-app.example.com` (update to your domain)

---

## Environment Variables Reference

| Variable | Description | Default | Source |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `docker` | Deployment |
| `SERVER_PORT` | Application port | `8080` | Deployment |
| `TZ` | Timezone | `UTC` | Deployment |
| `JAVA_OPTS` | JVM options | See Dockerfile | Deployment |
| `DB_URL` | JDBC connection URL | `jdbc:mysql://db-host:3306/mini_app_db` | ConfigMap |
| `DB_USERNAME` | Database username | — | Secret |
| `DB_PASSWORD` | Database password | — | Secret |
| `DB_DRIVER_CLASS` | JDBC driver class | `com.mysql.cj.jdbc.Driver` | Deployment |
| `DB_QUERY_TIMEOUT_SECONDS` | Query timeout | `30` | Deployment |
| `REDIS_HOST` | Redis hostname | `redis-service` | ConfigMap |
| `REDIS_PORT` | Redis port | `6379` | ConfigMap |
| `EXTERNAL_API_URL` | External API base URL | — | ConfigMap |
| `PAYMENT_SERVICE_URL` | Payment service URL | — | ConfigMap |
| `APP_ENV` | Application environment | `production` | ConfigMap |
| `APP_LOG_LEVEL` | Logging level | `INFO` | ConfigMap |
| `APP_CONTEXT_PATH` | Context path | `/mini-app` | ConfigMap |
| `SECURITY_JWT_SECRET` | JWT signing secret | — | Secret |
| `SECURITY_ADMIN_USERNAME` | Admin username | — | Secret |
| `SECURITY_ADMIN_PASSWORD` | Admin password | — | Secret |
| `MONITORING_ENDPOINT` | Metrics endpoint URL | — | ConfigMap |
| `MONITORING_USERNAME` | Monitoring username | — | Secret |
| `MONITORING_PASSWORD` | Monitoring password | — | Secret |

---

## Secrets Management

**Never store secrets in plain text or commit them to version control.**

### Using AWS Secrets Manager with EKS

```bash
# Store secret in AWS Secrets Manager
aws secretsmanager create-secret \
  --name mini-java-app/db-credentials \
  --secret-string '{"username":"dbuser","password":"dbpass"}'

# Use External Secrets Operator to sync to Kubernetes
# https://external-secrets.io/
```

### Rotating Secrets

```bash
# Update Kubernetes secret
kubectl create secret generic mini-java-app-secrets \
  --namespace mini-java-app \
  --from-literal=db-password='new_password' \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart pods to pick up new secret
kubectl rollout restart deployment/mini-java-app -n mini-java-app
```

---

## Scaling and Rolling Updates

### Manual Scaling

```bash
# Scale to 4 replicas
kubectl scale deployment mini-java-app --replicas=4 -n mini-java-app
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment mini-java-app \
  --namespace mini-java-app \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

### Rolling Update (new image)

```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:2.0.0 \
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

### Pod not starting

```bash
# Check pod status and events
kubectl describe pod -l app=mini-java-app -n mini-java-app

# Check logs
kubectl logs -l app=mini-java-app -n mini-java-app --previous
```

**Common causes:**
- `ImagePullBackOff`: ECR authentication issue or wrong image URI
- `CrashLoopBackOff`: Application startup failure — check logs for stack trace
- `OOMKilled`: Increase memory limits in `deployment.yaml`
- `Pending`: Insufficient cluster resources — scale node group

### Health check failing

```bash
# Test health endpoint from within the cluster
kubectl exec -it $(kubectl get pod -l app=mini-java-app -n mini-java-app -o jsonpath='{.items[0].metadata.name}') \
  -n mini-java-app -- sh -c 'wget -qO- http://localhost:8080/actuator/health'
```

**Common causes:**
- JVM startup time exceeds `initialDelaySeconds` — increase to 90s for slow starts
- Database connection failure — verify `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Missing Kubernetes Secret — verify `mini-java-app-secrets` exists

### Ingress not getting an address

```bash
# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Verify ingress annotations
kubectl describe ingress mini-java-app-ingress -n mini-java-app
```

**Common causes:**
- AWS Load Balancer Controller not installed
- Missing IAM permissions for ALB controller
- Subnet tags missing: `kubernetes.io/role/elb: 1`

### JVM memory issues

```bash
# Check container memory usage
kubectl top pods -n mini-java-app

# Increase limits in deployment.yaml if needed:
# limits:
#   cpu: "1000m"
#   memory: "2Gi"
```

---

## Security Considerations

1. **Non-root container**: The Dockerfile creates and uses a non-root `appuser` account.
2. **No secrets in images**: All credentials are injected via Kubernetes Secrets at runtime.
3. **Network policies**: `k8s/network-policy.yaml` enforces least-privilege network access between services.
4. **Read-only filesystem**: Consider adding `readOnlyRootFilesystem: true` to the security context.
5. **Image scanning**: Enable ECR image scanning on push:
   ```bash
   aws ecr put-image-scanning-configuration \
     --repository-name mini-java-app \
     --image-scanning-configuration scanOnPush=true \
     --region us-east-1
   ```
6. **IRSA (IAM Roles for Service Accounts)**: Use IRSA instead of node-level IAM roles for fine-grained pod permissions.
7. **TLS**: The ingress is configured to redirect HTTP → HTTPS. Ensure an ACM certificate ARN is added to the ingress annotations for production:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789:certificate/xxx
   ```

---

## Java-Specific Notes

### JVM Container Awareness
The Dockerfile sets `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` so the JVM respects container memory limits rather than using host memory. This prevents OOMKilled events.

### Spring Boot Actuator
The `/actuator/health` endpoint is exposed via `management.endpoints.web.exposure.include=health` in `application.properties`. This endpoint is used by both Kubernetes liveness/readiness probes and the AWS ALB health check.

### Graceful Shutdown
Spring Boot 2.7.0 supports graceful shutdown. To enable it, add to `application.properties`:
```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```
The Kubernetes `terminationGracePeriodSeconds: 30` is set to allow in-flight requests to complete.

### Spring Profiles
Set `SPRING_PROFILES_ACTIVE=prod` in the deployment environment variables to activate production-specific configuration. Create `application-prod.properties` for production overrides.
