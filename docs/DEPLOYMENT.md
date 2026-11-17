# Deployment Guide: minijava-bach-con-1439

## Prerequisites and System Requirements

### Local Development (Docker Compose)
- Docker Engine 20.10+
- Docker Compose 1.29+

### AWS EKS Deployment
- AWS CLI 2.x
- kubectl 1.21+
- eksctl 0.90+
- Docker Engine (for building images)
- IAM permissions for ECR, EKS, and Secrets Manager

### System Requirements
- Java 11 (runtime)
- Minimum 1 GB RAM for the container
- 1 vCPU minimum

## Local Setup with Docker Compose

1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and set the required values for your local environment:
   ```bash
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=mini_app_db
   DB_USERNAME=root
   DB_PASSWORD=your_db_password
   REDIS_HOST=localhost
   REDIS_PORT=6379
   ```

3. Start the application with Docker Compose:
   ```bash
   docker-compose up
   ```

4. Verify the application is running:
   - Application port: http://localhost:8080
   - Health check endpoint: http://localhost:8081/health

## Building and Pushing the Docker Image

### Prerequisites
- AWS CLI configured with appropriate permissions
- Docker Engine running

### Build and Push Script

#### Linux/macOS:
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

#### Windows:
```cmd
scripts\build-push.bat
```

The script will:
1. Prompt for the target platform (AWS ECR or Docker Hub)
2. Prompt for registry details and image tag
3. Build the Docker image
4. Push the image to the selected registry

## Configuration Management

The application reads all configuration from environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `APP_PORT` | Application server port | 8080 |
| `HEALTH_PORT` | Health check server port | 8081 |
| `CONFIG_PATH` | Path to configuration file | /app/config/app.properties |
| `LOG_PATH` | Path to log file | /app/logs/mini-app.log |
| `LOG_DIR` | Log directory path | /app/logs |
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 3306 |
| `DB_NAME` | Database name | mini_app_db |
| `DB_USERNAME` | Database username | (from Secret) |
| `DB_PASSWORD` | Database password | (from Secret) |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `REDIS_PASSWORD` | Redis password | (from Secret) |

### Credentials
All credentials are stored in a Kubernetes Secret named `minijava-bach-con-1439-secrets`.
The deploy script prompts for each credential value and creates the Secret before deploying.

## Deploying to AWS EKS

### EKS Cluster Setup

1. Create an EKS cluster (if you don't have one):
   ```bash
   eksctl create cluster \
     --name minijava-bach-con-1439-cluster \
     --region us-east-1 \
     --nodegroup-name standard-workers \
     --node-type t3.medium \
     --nodes 2 \
     --nodes-max 4
   ```

2. Configure kubectl:
   ```bash
   aws eks update-kubeconfig --region us-east-1 --name minijava-bach-con-1439-cluster
   ```

3. Install the AWS Load Balancer Controller (for Ingress support):
   ```bash
   helm repo add eks https://aws.github.io/eks-helm-charts
   helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
     -n kube-system \
     -n kube-system \
     --set clusterName=minijava-bach-con-1439-cluster \
     --set serviceAccount.create=false \
     --set serviceAccount.name=aws-load-balancer-controller \
     --set region=us-east-1 \
     --set vpcId=vpc-xxxxxxxxx
   ```

### Deploy the Application

1. Run the deploy script:
   ```bash
   chmod +x scripts/deploy-image.sh
   ./scripts/deploy-image.sh
   ```

2. The script will:
   - Prompt for AWS region and EKS cluster name
   - Configure kubectl for the EKS cluster
   - Prompt for the image URI (from the build-push step)
   - Prompt for each environment variable value
   - Prompt for each credential value (without echoing)
   - Create the Kubernetes Secret with credentials
   - Apply namespace, deployment, service, and ingress manifests
   - Wait for the deployment rollout to complete
   - Display pod/service/ingress status and application URL

### Manual Deployment

If you prefer to deploy manually:

```bash
# Create namespace
kubectl apply -f kubernetes/namespace.yaml

# Create Secret with credentials
kubectl create secret generic minijava-bach-con-1439-secrets -n minijava-bach-con-1439 \
  --from-literal=db-username="$DB_USERNAME" \
  --from-literal=db-password="$DB_PASSWORD" \
  --from-literal=redis-password="$REDIS_PASSWORD" \
  --from-literal=external-api-key="$EXTERNAL_API_KEY" \
  --from-literal=payment-service-username="$PAYMENT_SERVICE_USERNAME" \
  --from-literal=payment-service-password="$PAYMENT_SERVICE_PASSWORD" \
  --from-literal=security-jwt-secret="$SECURITY_JWT_SECRET" \
  --from-literal=security-admin-username="$SECURITY_ADMIN_USERNAME" \
  --from-literal=security-admin-password="$SECURITY_ADMIN_PASSWORD" \
  --from-literal=security-encryption-key="$SECURITY_ENCRYPTION_KEY" \
  --from-literal=monitoring-username="$MONITORING_USERNAME" \
  --from-literal=monitoring-password="$MONITORING_PASSWORD" \
  --from-literal=messaging-rabbitmq-username="$MESSAGING_RABBITMQ_USERNAME" \
  --from-literal=messaging-rabbitmq-password="$MESSAGING_RABBITMQ_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

# Apply deployment (replace placeholders first)
sed -e "s|{{IMAGE_URI}}|$IMAGE_URI|g" \
    -e "s|{{CONFIG_PATH}}|$CONFIG_PATH|g" \
    kubernetes/deployment.yaml | kubectl apply -f -

# Apply service and ingress
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/minijava-bach-con-1439 -n minijava-bach-con-1439
```

## Kubernetes Manifests

### Namespace (`kubernetes/namespace.yaml`)
Creates the `minijava-bach-con-1439` namespace for all application resources.

### Deployment (`kubernetes/deployment.yaml`)
- **Replicas**: 2 (for high availability)
- **Container**: Runs the application jar with JVM options
- **Ports**: 8080 (application), 8081 (health check)
- **Resources**: 250m CPU / 512Mi memory requests, 500m CPU / 1Gi memory limits
- **Liveness Probe**: HTTP GET on /health port 8081 (initial delay 15s, interval 30s)
- **Readiness Probe**: HTTP GET on /health port 8081 (initial delay 10s, interval 10s)
- **Secrets**: All credentials are mounted from the `minijava-bach-con-1439-secrets` Secret

### Service (`kubernetes/service.yaml`)
- **Type**: ClusterIP
- **Ports**: 80 → 8080 (application), 8081 → 8081 (health check)

### Ingress (`kubernetes/ingress.yaml`)
- **Ingress Class**: alb (AWS Load Balancer Controller)
- **Scheme**: internet-facing
- **Host**: minijava-bach-con-1439.example.com
- **Health Check**: /health on port 8081

## Troubleshooting

### Pod Failures
```bash
# Check pod status
kubectl get pods -n minijava-bach-con-1439

# Check pod logs
kubectl logs -n minijava-bach-con-1439 <pod-name>

# Describe pod for events
kubectl describe pod -n minijava-bach-con-1439 <pod-name>
```

### Service Issues
```bash
# Check service endpoints
kubectl get endpoints -n minijava-bach-con-1439

# Test connectivity from within a pod
kubectl exec -it -n minijava-bach-con-1439 <pod-name> -- curl http://localhost:8081/health
```

### Ingress Problems
```bash
# Check ingress status
kubectl get ingress -n minijava-bach-con-1439

# Check ALB logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

### Common Issues
- **Image pull errors**: Verify ECR authentication and image URI
- **CrashLoopBackOff**: Check application logs and environment variables
- **Pending pods**: Check resource quotas and node capacity
- **Ingress not reachable**: Verify the AWS Load Balancer Controller is installed and the ingress class annotation is correct

## Scaling and Management

### Horizontal Pod Autoscaler (HPA)
```bash
kubectl autoscale deployment minijava-bach-con-1439 \
  -n minijava-bach-con-1439 \
  --cpu-percent=60 \
  --min=2 \
  --max=10
```

### Rolling Updates
```bash
# Update image
kubectl set image deployment/minijava-bach-con-1439 \
  -n minijava-bach-con-1439 \
  minijava-bach-con-1439=<new-image-uri>

# Monitor rollout
kubectl rollout status deployment/minijava-bach-con-1439 -n minijava-bach-con-1439
```

### Rollback
```bash
# Rollback to previous revision
kubectl rollout undo deployment/minijava-bach-con-1439 -n minijava-bach-con-1439

# Rollback to specific revision
kubectl rollout undo deployment/minijava-bach-con-1439 -n minijava-bach-con-1439 --to-revision=2
```

## Security Considerations

- All credentials are stored in Kubernetes Secrets, never in plain text in manifests
- The application runs as a non-root user in the container
- The container image uses a minimal base image (eclipse-temurin:11-jdk)
- No package managers or unnecessary tools are installed in the runtime image
- The application's own health endpoint is used for probes (no curl/wget in the image)
- Read-only root filesystem for the config volume mount

## Java Notes

### JVM Settings
The container is configured with the following JVM options:
- `-Xmx512m`: Maximum heap size of 512MB
- `-Xms256m`: Initial heap size of 256MB
- `-XX:+UseContainerSupport`: Enables container-aware memory management
- `-XX:MaxRAMPercentage=75.0`: Uses up to 75% of container memory for the heap

These settings are tuned for the container's resource limits (512Mi requests, 1Gi limits).

### Application Type
This is a plain Java application (not Spring Boot). The main class `com.test.MiniApp` does not use `SpringApplication.run()`. The application:
1. Loads configuration from a file path specified by the `CONFIG_PATH` environment variable
2. Initializes logging to a file path specified by the `LOG_PATH` environment variable
3. Connects to a database using credentials from environment variables
4. Starts a ServerSocket on the port specified by `APP_PORT`
5. Starts a health check HTTP server on `HEALTH_PORT`

The application's own work (starting the ServerSocket) ends after start-up. The health check server keeps the process running so Kubernetes can manage the pod lifecycle.

### Health Check Endpoint
The health check endpoint is served by `com.test.HealthCheckServer` on port 8081 (configurable via `HEALTH_PORT` environment variable). It responds to HTTP GET requests on `/health` with:
```json
{"status":"UP"}
```

The Kubernetes liveness and readiness probes use this endpoint to manage pod lifecycle.
