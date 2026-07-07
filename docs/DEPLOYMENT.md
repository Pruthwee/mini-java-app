# Deployment Guide – mini-java-app on Azure AKS

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Structure](#project-structure)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build and Push Docker Image](#build-and-push-docker-image)
6. [Azure AKS Deployment](#azure-aks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Configuration & Environment Variables](#configuration--environment-variables)
9. [Health Checks & Monitoring](#health-checks--monitoring)
10. [Scaling & Management](#scaling--management)
11. [Troubleshooting](#troubleshooting)
12. [Security Considerations](#security-considerations)
13. [Rollback Procedure](#rollback-procedure)

---

## Overview

**Application**: mini-java-app  
**Technology**: Java 11, Spring Boot 2.7.0, Maven  
**Package**: Executable JAR  
**Application Port**: 8080  
**Health Endpoint**: `GET /health`  
**Target Platform**: Azure Kubernetes Service (AKS)  
**Base Runtime Image**: `amazoncorretto:11`

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 11+ | Local build/run |
| Maven | 3.8+ | Build tool |
| Docker | 20.10+ | Container build & run |
| Docker Compose | 2.x | Local multi-container orchestration |

### Azure AKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| Azure CLI (`az`) | 2.40+ | Azure resource management |
| kubectl | 1.25+ | Kubernetes cluster management |
| Docker | 20.10+ | Image build & push |
| Azure Subscription | — | AKS cluster hosting |

Install Azure CLI:
```bash
# macOS
brew install azure-cli

# Ubuntu/Debian
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# Windows
winget install Microsoft.AzureCLI
```

Install kubectl:
```bash
az aks install-cli
```

---

## Project Structure

```
azure-mono/
├── Dockerfile                  # Multi-stage Docker build
├── docker-compose.yml          # Local development compose file
├── .dockerignore               # Files excluded from Docker context
├── pom.xml                     # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/test/
│       │   ├── MiniApp.java
│       │   ├── HealthController.java
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
│   ├── deploy-image.sh         # Linux/macOS AKS deploy
│   └── deploy-image.bat        # Windows AKS deploy
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root (never commit this file):

```dotenv
# Database
DATABASE_URL=jdbc:mysql://your-db-host:3306/mini_app_db
DATABASE_USERNAME=your_db_user
DATABASE_PASSWORD=your_db_password

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# External API
EXTERNAL_API_BASE_URL=http://api.example.com:8080/v1
EXTERNAL_API_KEY=your_api_key

# Payment Service
PAYMENT_SERVICE_URL=https://payment.example.com/process
PAYMENT_SERVICE_USERNAME=payment_user
PAYMENT_SERVICE_PASSWORD=payment_password

# Security
SECURITY_JWT_SECRET=your_jwt_secret_min_32_chars

# Messaging
MESSAGING_RABBITMQ_HOST=your-rabbitmq-host
MESSAGING_RABBITMQ_PORT=5672
MESSAGING_RABBITMQ_USERNAME=rabbitmq_user
MESSAGING_RABBITMQ_PASSWORD=rabbitmq_password
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
# Health check
curl http://localhost:8080/health

# Expected response:
# {"status":"UP","application":"mini-java-app"}
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
1. Enter an image tag (default: `latest`)
2. Select registry type (Azure ACR or Docker Hub)
3. Provide registry credentials

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (without script)

```bash
# Build image
docker build -t mini-java-app:latest .

# Tag for ACR
docker tag mini-java-app:latest <acr-name>.azurecr.io/mini-java-app:latest

# Login to ACR
az acr login --name <acr-name>

# Push
docker push <acr-name>.azurecr.io/mini-java-app:latest
```

---

## Azure AKS Deployment

### Step 1: Create Azure Resources (if not existing)

```bash
# Login to Azure
az login

# Create resource group
az group create --name my-resource-group --location eastus

# Create ACR
az acr create --resource-group my-resource-group \
  --name myregistry --sku Basic

# Create AKS cluster
az aks create \
  --resource-group my-resource-group \
  --name my-aks-cluster \
  --node-count 2 \
  --enable-addons monitoring \
  --generate-ssh-keys \
  --attach-acr myregistry

# Enable Application Gateway Ingress Controller (AGIC)
az aks enable-addons \
  --resource-group my-resource-group \
  --name my-aks-cluster \
  --addons ingress-appgw \
  --appgw-name my-app-gateway \
  --appgw-subnet-cidr "10.225.0.0/16"
```

### Step 2: Build and Push Image

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
# Select ACR, enter: myregistry
# Tag: 1.0.0
# Full image: myregistry.azurecr.io/mini-java-app:1.0.0
```

### Step 3: Deploy to AKS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
- Azure Resource Group
- AKS Cluster name
- Full Docker image URI
- All application environment variables

### Step 4: Verify Deployment

```bash
# Check pods
kubectl get pods -n mini-java-app

# Check services
kubectl get svc -n mini-java-app

# Check ingress
kubectl get ingress -n mini-java-app

# View pod logs
kubectl logs -l app=mini-java-app -n mini-java-app --tail=100

# Describe deployment
kubectl describe deployment mini-java-app -n mini-java-app
```

### Step 5: Access the Application

```bash
# Get ingress IP
kubectl get ingress mini-java-app-ingress -n mini-java-app

# Test health endpoint
curl http://<INGRESS_IP>/health
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `mini-java-app` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (high availability)
- **Image**: `{{IMAGE_URI}}` (replaced at deploy time)
- **Resources**: requests 250m CPU / 512Mi RAM; limits 500m CPU / 1Gi RAM
- **Liveness Probe**: `GET /health` — starts after 60s, every 30s
- **Readiness Probe**: `GET /health` — starts after 30s, every 15s
- **Security**: runs as non-root user (UID 1000)
- **Graceful Shutdown**: 30s termination grace period

### service.yaml
- **Type**: ClusterIP (internal only)
- **Port**: 80 → 8080 (container)

### ingress.yaml
- **Class**: `azure/application-gateway` (AGIC)
- **Host**: `mini-java-app.example.com` (update to your domain)
- **Path**: `/` (all traffic routed to the service)

To use a custom domain, update `ingress.yaml`:
```yaml
spec:
  rules:
    - host: your-actual-domain.com
```

---

## Configuration & Environment Variables

All sensitive configuration is injected via environment variables. **Never hardcode secrets in manifests.**

| Variable | Description | Required |
|----------|-------------|----------|
| `DATABASE_URL` | JDBC connection string | Yes |
| `DATABASE_USERNAME` | Database user | Yes |
| `DATABASE_PASSWORD` | Database password | Yes |
| `REDIS_HOST` | Redis hostname | Yes |
| `REDIS_PORT` | Redis port (default: 6379) | No |
| `REDIS_PASSWORD` | Redis password | No |
| `EXTERNAL_API_BASE_URL` | External API base URL | Yes |
| `EXTERNAL_API_KEY` | External API key | Yes |
| `EXTERNAL_API_TIMEOUT` | API timeout ms (default: 30000) | No |
| `PAYMENT_SERVICE_URL` | Payment service URL | Yes |
| `PAYMENT_SERVICE_USERNAME` | Payment service user | Yes |
| `PAYMENT_SERVICE_PASSWORD` | Payment service password | Yes |
| `SECURITY_JWT_SECRET` | JWT signing secret (min 32 chars) | Yes |
| `MONITORING_ENDPOINT` | Metrics push endpoint | No |
| `MONITORING_USERNAME` | Monitoring user | No |
| `MONITORING_PASSWORD` | Monitoring password | No |
| `MESSAGING_RABBITMQ_HOST` | RabbitMQ hostname | No |
| `MESSAGING_RABBITMQ_PORT` | RabbitMQ port (default: 5672) | No |
| `MESSAGING_RABBITMQ_USERNAME` | RabbitMQ user | No |
| `MESSAGING_RABBITMQ_PASSWORD` | RabbitMQ password | No |
| `CONFIG_FILE_PATH` | App config file path | No |
| `LOG_FILE_PATH` | Log file path | No |
| `JAVA_OPTS` | JVM options | No |
| `SPRING_PROFILES_ACTIVE` | Spring profile (default: docker) | No |

### Using Kubernetes Secrets (Recommended for Production)

```bash
kubectl create secret generic mini-java-app-secrets \
  --from-literal=DATABASE_PASSWORD=your_password \
  --from-literal=SECURITY_JWT_SECRET=your_jwt_secret \
  --from-literal=REDIS_PASSWORD=your_redis_password \
  -n mini-java-app
```

Then reference in `deployment.yaml`:
```yaml
env:
  - name: DATABASE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: mini-java-app-secrets
        key: DATABASE_PASSWORD
```

---

## Health Checks & Monitoring

### Health Endpoint

The application exposes a custom health endpoint:

```
GET /health
Response: {"status":"UP","application":"mini-java-app"}
```

Spring Boot Actuator also provides:
```
GET /actuator/health   → Spring Boot health aggregation
GET /actuator/info     → Application info
```

### Kubernetes Probes

| Probe | Endpoint | Initial Delay | Period |
|-------|----------|---------------|--------|
| Liveness | `GET /health` | 60s | 30s |
| Readiness | `GET /health` | 30s | 15s |

The 60-second initial delay accounts for JVM startup time.

### View Metrics

```bash
# Pod resource usage
kubectl top pods -n mini-java-app

# Node resource usage
kubectl top nodes
```

---

## Scaling & Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment mini-java-app --replicas=3 -n mini-java-app

# Scale back to 2
kubectl scale deployment mini-java-app --replicas=2 -n mini-java-app
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

### Rolling Update

```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=myregistry.azurecr.io/mini-java-app:2.0.0 \
  -n mini-java-app

# Monitor rollout
kubectl rollout status deployment/mini-java-app -n mini-java-app
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

| Issue | Cause | Solution |
|-------|-------|---------|
| `ImagePullBackOff` | Cannot pull image | Check ACR credentials: `az acr login --name <acr>` |
| `CrashLoopBackOff` | App crashes on start | Check logs: `kubectl logs <pod> -n mini-java-app` |
| `Pending` pods | Insufficient resources | Scale node pool or reduce resource requests |
| Health probe failing | App not ready | Increase `initialDelaySeconds` in deployment.yaml |
| Database connection refused | Wrong DB URL/credentials | Verify `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` env vars |
| OOMKilled | Insufficient memory | Increase memory limit or tune `JAVA_OPTS` heap settings |

### JVM Memory Issues

If pods are OOMKilled, adjust JVM heap in `deployment.yaml`:
```yaml
- name: JAVA_OPTS
  value: "-Xms256m -Xmx768m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

And increase memory limits:
```yaml
resources:
  limits:
    memory: "1.5Gi"
```

### Ingress Not Accessible

```bash
# Check ingress status
kubectl describe ingress mini-java-app-ingress -n mini-java-app

# Check AGIC logs
kubectl logs -l app=ingress-appgw -n kube-system --tail=50

# Verify service endpoints
kubectl get endpoints mini-java-app-service -n mini-java-app
```

---

## Security Considerations

1. **Non-root container**: The application runs as UID 1000 (non-root).
2. **Secrets management**: Use Kubernetes Secrets or Azure Key Vault for sensitive values.
3. **Network policies**: Consider adding NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Enable ACR vulnerability scanning:
   ```bash
   az acr task create --registry myregistry --name scan-on-push \
     --image mini-java-app:{{.Run.ID}} --context /dev/null \
     --file /dev/null --commit-trigger-enabled false
   ```
5. **RBAC**: Use least-privilege service accounts for the application pods.
6. **TLS**: Configure TLS termination at the Application Gateway level for HTTPS.
7. **Pod Security**: The deployment enforces `runAsNonRoot: true` and `runAsUser: 1000`.

---

## Rollback Procedure

### Immediate Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/mini-java-app -n mini-java-app

# Rollback to specific revision
kubectl rollout history deployment/mini-java-app -n mini-java-app
kubectl rollout undo deployment/mini-java-app --to-revision=2 -n mini-java-app

# Verify rollback
kubectl rollout status deployment/mini-java-app -n mini-java-app
kubectl get pods -n mini-java-app
```

### Full Cleanup

```bash
# Remove all application resources
kubectl delete namespace mini-java-app

# This removes: deployment, service, ingress, pods, and the namespace
```

---

## Java-Specific Notes

- **JVM Startup**: Spring Boot applications typically take 30–60 seconds to start. The liveness probe `initialDelaySeconds: 60` accounts for this.
- **Container Support**: `-XX:+UseContainerSupport` ensures the JVM respects container memory limits rather than host memory.
- **MaxRAMPercentage**: Set to 75% so the JVM heap uses up to 75% of the container memory limit.
- **G1GC**: Used for balanced throughput and latency in containerized environments.
- **Graceful Shutdown**: Spring Boot 2.7+ supports graceful shutdown. The 30-second `terminationGracePeriodSeconds` allows in-flight requests to complete.
- **Timezone**: Set to UTC (`-Duser.timezone=UTC`) for consistent log timestamps across environments.
- **File Encoding**: UTF-8 enforced via `-Dfile.encoding=UTF-8`.
