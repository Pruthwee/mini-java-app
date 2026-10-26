# Deployment Guide: mini-java-app on AWS EKS

## Prerequisites

### System Requirements
- Java 11 (JDK)
- Maven 3.8+
- Docker 20.10+
- AWS CLI 2.x
- kubectl 1.21+
- eksctl 0.100+

### AWS Permissions
The deploying user/role must have permissions for:
- ECR: `ecr:BatchGetImage`, `ecr:CompleteLayerUpload`, `ecr:UploadLayerPart`, `ecr:InitiateLayerUpload`, `ecr:PutImage`, `ecr:GetAuthorizationToken`, `ecr:DescribeRepositories`, `ecr:CreateRepository`
- EKS: `eks:DescribeCluster`, `eks:ListClusters`
- Kubernetes (via kubectl through the EKS cluster's IAM role mapping)

## Local Setup with Docker Compose

1. Set the required environment variables:
```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=mini_app_db
export DB_USERNAME=root
export DB_PASSWORD=yourpassword
export REDIS_HOST=localhost
export REDIS_PORT=6379
export EXTERNAL_API_URL=http://api.example.com:8080/v1
export PAYMENT_SERVICE_URL=https://payment.internal.company.com/process
```

2. Build and run locally:
```bash
docker-compose up --build
```

The application will be available at:
- Main server: http://localhost:8080
- Health check: http://localhost:8081/health

## Building and Pushing the Container Image

### Using build-push.sh (Linux/macOS)
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```
The script will:
1. Ask you to select AWS ECR or Docker Hub
2. Collect registry/credentials details
3. Build the Docker image
4. Push to the selected registry

### Using build-push.bat (Windows)
```cmd
scripts\build-push.bat
```

## EKS Cluster Setup

### 1. Create an EKS Cluster
```bash
eksctl create cluster \
  --name mini-java-app \
  --region us-east-1 \
  --nodegroup-name mini-java-app-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-max 4
```

### 2. Configure kubectl
```bash
aws eks update-kubeconfig --region us-east-1 --name mini-java-app
```

### 3. Install the AWS Load Balancer Controller
Follow the official guide: https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html

## Deploying to EKS

### Using deploy-image.sh (Linux/macOS)
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```
The script will:
1. Ask for AWS region and EKS cluster name
2. Configure kubectl
3. Collect environment variable values for non-credential settings
4. Collect credential values (hidden input)
5. Create the namespace
6. Create the Kubernetes Secret with credentials
7. Deploy the application with all placeholders replaced
8. Create the Service and Ingress
9. Wait for the rollout to complete
10. Display the deployment status and application URL

### Using deploy-image.bat (Windows)
```cmd
scripts\deploy-image.bat
```

## Kubernetes Manifests

### namespace.yaml
Creates the `mini-java-app` namespace for all resources.

### deployment.yaml
Defines a Deployment with:
- 2 replicas for high availability
- Container image from ECR (placeholder `{{IMAGE_URI}}` replaced at deploy time)
- Ports 8080 (application) and 8081 (health check)
- Environment variables for all external dependencies
- Credentials sourced from Kubernetes Secret `mini-java-app-secrets`
- Resource requests (250m CPU, 512Mi memory) and limits (500m CPU, 1Gi memory)
- Liveness and readiness probes hitting `/health` on port 8081

### service.yaml
Creates a ClusterIP Service routing port 80 to the application's port 8080.

### ingress.yaml
Creates an AWS ALB Ingress with:
- Internet-facing scheme
- Host: `mini-java-app.example.com`
- Health check path: `/health` on port 8081

## Configuration Management

### Environment Variables
All configuration is externalized via environment variables:

| Variable | Description | Source |
|----------|-------------|--------|
| `APP_PORT` | Application server port | ConfigMap/Deployment |
| `HEALTH_PORT` | Health check endpoint port | ConfigMap/Deployment |
| `CONFIG_PATH` | Path to config file | ConfigMap/Deployment |
| `LOG_PATH` | Path to log file | ConfigMap/Deployment |
| `LOG_DIR` | Log directory | ConfigMap/Deployment |
| `DB_HOST` | Database host | ConfigMap/Deployment |
| `DB_PORT` | Database port | ConfigMap/Deployment |
| `DB_NAME` | Database name | ConfigMap/Deployment |
| `DB_USERNAME` | Database username | Kubernetes Secret |
| `DB_PASSWORD` | Database password | Kubernetes Secret |
| `REDIS_HOST` | Redis host | ConfigMap/Deployment |
| `REDIS_PORT` | Redis port | ConfigMap/Deployment |
| `REDIS_PASSWORD` | Redis password | Kubernetes Secret |
| `EXTERNAL_API_URL` | External API base URL | ConfigMap/Deployment |
| `EXTERNAL_API_KEY` | External API key | Kubernetes Secret |
| `PAYMENT_SERVICE_URL` | Payment service URL | ConfigMap/Deployment |
| `PAYMENT_SERVICE_USERNAME` | Payment service username | Kubernetes Secret |
| `PAYMENT_SERVICE_PASSWORD` | Payment service password | Kubernetes Secret |

### Secrets Management
Credentials are stored in a Kubernetes Secret named `mini-java-app-secrets` in the `mini-java-app` namespace. The deploy script creates this secret interactively. For production, consider using AWS Secrets Manager with the External Secrets Operator.

## Security Considerations

1. **No hardcoded credentials**: All passwords, API keys, and secrets are injected via Kubernetes Secrets, never stored in the container image or source code.
2. **Non-root user**: The container runs as a non-root user (`appuser`) for reduced attack surface.
3. **Minimal base image**: The runtime stage uses `eclipse-temurin:11-jdk`, which is a minimal JDK base image.
4. **Read-only config**: Configuration files are mounted as read-only where possible.
5. **Network policies**: Consider adding Kubernetes NetworkPolicies to restrict inter-pod traffic.
6. **IAM roles**: Use IRSA (IAM Roles for Service Accounts) for AWS resource access instead of embedding credentials.

## Java Notes

### JVM Settings
The container uses the following JVM options:
- `-Xmx512m`: Maximum heap size of 512MB
- `-Xms256m`: Initial heap size of 256MB
- `-XX:+UseContainerSupport`: Enables container-aware memory management
- `-XX:MaxRAMPercentage=75.0`: Uses up to 75% of container memory for heap

These settings are tuned for the resource limits defined in the Kubernetes Deployment (512Mi request, 1Gi limit). The JVM will respect container memory limits through `UseContainerSupport`.

### Health Check Endpoint
The application exposes a health check endpoint at `/health` on port 8081. This endpoint returns `{"status":"UP"}` with HTTP 200, suitable for Kubernetes liveness and readiness probes.

### Application Behavior
The application's main work (starting the server, loading configuration, connecting to the database) completes during startup. After startup, the process stays alive primarily to serve the health check endpoint for container orchestration. The `Thread.currentThread().join()` call in the main method keeps the process running indefinitely.

## Troubleshooting

### Pod Fails to Start
```bash
kubectl describe pod -n mini-java-app <pod-name>
kubectl logs -n mini-java-app <pod-name>
```

### Service Issues
```bash
kubectl get svc -n mini-java-app
kubectl describe svc -n mini-java-app mini-java-app-service
```

### Ingress Problems
```bash
kubectl describe ingress -n mini-java-app
kubectl get ingress -n mini-java-app -o wide
```
Ensure the AWS Load Balancer Controller is installed and the ALB annotations are correct.

### Image Pull Errors
```bash
kubectl describe pod -n mini-java-app <pod-name> | grep -A 5 "Failed"
```
Ensure the ECR repository exists and the image tag is correct. Verify ECR authentication.

## Scaling and Management

### Horizontal Pod Autoscaler
```bash
kubectl autoscale deployment mini-java-app \
  --cpu-percent=60 \
  --min=2 \
  --max=10 \
  -n mini-java-app
```

### Rolling Update
```bash
kubectl set image deployment/mini-java-app \
  mini-java-app=<new-image-uri> \
  -n mini-java-app

kubectl rollout status deployment/mini-java-app -n mini-java-app
```

### Rollback
```bash
kubectl rollout undo deployment/mini-java-app -n mini-java-app
```
