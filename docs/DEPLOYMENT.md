# Deployment Guide - Mini Java App

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Project Overview](#project-overview)
3. [Local Development Setup](#local-development-setup)
4. [Docker Deployment](#docker-deployment)
5. [AWS EKS Deployment](#aws-eks-deployment)
6. [Configuration Management](#configuration-management)
7. [Troubleshooting](#troubleshooting)
8. [Security Considerations](#security-considerations)
9. [Monitoring and Logging](#monitoring-and-logging)

---

## Prerequisites

### Required Tools
- **Java Development Kit (JDK) 11** or higher
- **Maven 3.6+** for building the application
- **Docker 20.10+** for containerization
- **Docker Compose 1.29+** for local orchestration
- **AWS CLI 2.x** for AWS operations
- **kubectl 1.24+** for Kubernetes management
- **eksctl** (optional) for EKS cluster management

### AWS Requirements
- AWS Account with appropriate permissions
- IAM user/role with permissions for:
  - ECR (Elastic Container Registry)
  - EKS (Elastic Kubernetes Service)
  - EC2, VPC, IAM (for EKS cluster resources)
- AWS credentials configured (`aws configure`)

### External Services
This application requires the following external services:
- **MySQL Database** (version 8.0+)
- **Redis Cache** (version 6.0+)
- **RabbitMQ Message Broker** (version 3.8+)
- **AWS S3 Buckets** for configuration and logs
- **External APIs** (payment service, monitoring endpoints)

---

## Project Overview

### Technology Stack
- **Framework**: Spring Boot 2.7.0
- **Java Version**: 11
- **Build Tool**: Maven
- **Package Type**: JAR (executable)
- **Application Port**: 8080
- **Health Check Endpoint**: `/actuator/health`

### Application Architecture
```
mini-java-app/
├── src/
│   ├── main/
│   │   ├── java/com/test/
│   │   │   ├── Application.java          # Spring Boot entry point
│   │   │   ├── MiniApp.java              # Main application logic
│   │   │   └── DatabaseService.java      # Database service
│   │   └── resources/
│   │       └── application.properties    # Configuration
├── pom.xml                               # Maven configuration
├── Dockerfile                            # Multi-stage Docker build
├── docker-compose.yml                    # Local development
├── kubernetes/                           # K8s manifests
│   ├── namespace.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
└── scripts/                              # Deployment scripts
    ├── build-push.sh
    ├── build-push.bat
    ├── deploy-image.sh
    └── deploy-image.bat
```

### Key Features
- Externalized configuration via environment variables
- Spring Boot Actuator for health checks
- AWS S3 integration for configuration and logs
- MySQL database connectivity
- Redis caching support
- RabbitMQ messaging integration
- RESTful API endpoints

---

## Local Development Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd containerization-mini
```

### 2. Build the Application
```bash
# Using Maven
mvn clean package -DskipTests

# The JAR file will be created in target/mini-java-app-1.0.0.jar
```

### 3. Run Locally (Without Docker)
```bash
# Set required environment variables
export DATABASE_URL="jdbc:mysql://localhost:3306/mini_app_db"
export DB_USERNAME="root"
export DB_PASSWORD="password"
export REDIS_HOST="localhost"
export CONFIG_S3_BUCKET="app-config-bucket"
export LOG_S3_BUCKET="app-logs-bucket"
export UPLOAD_S3_BUCKET="app-uploads-bucket"

# Run the application
java -jar target/mini-java-app-1.0.0.jar
```

### 4. Verify Application
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

---

## Docker Deployment

### 1. Build Docker Image Locally
```bash
# Build the image
docker build -t mini-java-app:latest .

# Verify the image
docker images | grep mini-java-app
```

### 2. Run with Docker Compose
```bash
# Update docker-compose.yml with your environment variables
# Then start the application
docker-compose up -d

# View logs
docker-compose logs -f mini-java-app

# Stop the application
docker-compose down
```

### 3. Test the Containerized Application
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# View container logs
docker logs mini-java-app

# Access container shell
docker exec -it mini-java-app sh
```

---

## AWS EKS Deployment

### Step 1: Prepare AWS Environment

#### 1.1 Create ECR Repository (if not exists)
```bash
aws ecr create-repository \
  --repository-name mini-java-app \
  --region us-east-1
```

#### 1.2 Create or Use Existing EKS Cluster
```bash
# Option A: Create new cluster with eksctl
eksctl create cluster \
  --name mini-java-app-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3

# Option B: Use existing cluster
aws eks update-kubeconfig \
  --region us-east-1 \
  --name your-existing-cluster
```

#### 1.3 Install AWS Load Balancer Controller
```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.4.7/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Install the controller using Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=mini-java-app-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 2: Build and Push Docker Image

#### Linux/macOS
```bash
cd containerization-mini
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

#### Windows
```cmd
cd containerization-mini
scripts\build-push.bat
```

**Script will prompt for:**
1. Registry type (AWS ECR or Docker Hub)
2. Registry credentials and details
3. Image tag (default: latest)

**Example ECR Image URI:**
```
123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest
```

### Step 3: Deploy to EKS

#### Linux/macOS
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows
```cmd
scripts\deploy-image.bat
```

**Script will prompt for:**
1. AWS region (e.g., us-east-1)
2. EKS cluster name
3. Docker image URI
4. Environment variables:
   - Database connection details
   - Redis configuration
   - S3 bucket names
   - Security credentials
   - External service URLs

### Step 4: Verify Deployment

```bash
# Check namespace
kubectl get namespace mini-java-app

# Check pods
kubectl get pods -n mini-java-app

# Check services
kubectl get svc -n mini-java-app

# Check ingress
kubectl get ingress -n mini-java-app

# View pod logs
kubectl logs -f deployment/mini-java-app -n mini-java-app

# Describe deployment
kubectl describe deployment mini-java-app -n mini-java-app
```

### Step 5: Access the Application

```bash
# Get the Load Balancer URL
kubectl get ingress mini-java-app-ingress -n mini-java-app

# Test the application
INGRESS_URL=$(kubectl get ingress mini-java-app-ingress -n mini-java-app -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
curl http://$INGRESS_URL/actuator/health
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

#### Server Configuration
- `SERVER_PORT`: Application port (default: 8080)
- `SERVER_HOST`: Bind address (default: 0.0.0.0)

#### Database Configuration
- `DATABASE_URL`: JDBC connection string
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password
- `DB_POOL_MAX_CONNECTIONS`: Max connection pool size (default: 20)
- `DB_POOL_TIMEOUT`: Connection timeout in ms (default: 5000)

#### Redis Configuration
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis port (default: 6379)
- `REDIS_PASSWORD`: Redis password (optional)
- `REDIS_DATABASE`: Redis database number (default: 0)

#### S3 Configuration
- `CONFIG_S3_BUCKET`: S3 bucket for configuration files
- `CONFIG_S3_KEY`: S3 key for config file (default: config/app.properties)
- `LOG_S3_BUCKET`: S3 bucket for logs
- `LOG_S3_KEY`: S3 key for log file (default: logs/mini-app.log)
- `UPLOAD_S3_BUCKET`: S3 bucket for file uploads

#### Security Configuration
- `JWT_SECRET`: Secret key for JWT token generation
- `ADMIN_USERNAME`: Admin username
- `ADMIN_PASSWORD`: Admin password
- `ENCRYPTION_KEY`: Encryption key for sensitive data

#### External Services
- `EXTERNAL_API_URL`: External API base URL
- `EXTERNAL_API_KEY`: API key for external service
- `PAYMENT_SERVICE_URL`: Payment service endpoint
- `PAYMENT_SERVICE_USERNAME`: Payment service username
- `PAYMENT_SERVICE_PASSWORD`: Payment service password
- `MONITORING_ENDPOINT`: Monitoring service URL
- `RABBITMQ_HOST`: RabbitMQ server hostname
- `RABBITMQ_PORT`: RabbitMQ port (default: 5672)
- `RABBITMQ_USERNAME`: RabbitMQ username
- `RABBITMQ_PASSWORD`: RabbitMQ password

### Kubernetes ConfigMaps and Secrets

For production deployments, use Kubernetes ConfigMaps and Secrets:

```bash
# Create ConfigMap for non-sensitive data
kubectl create configmap mini-java-app-config \
  --from-literal=SERVER_PORT=8080 \
  --from-literal=REDIS_PORT=6379 \
  -n mini-java-app

# Create Secret for sensitive data
kubectl create secret generic mini-java-app-secrets \
  --from-literal=DB_PASSWORD=your_password \
  --from-literal=JWT_SECRET=your_secret \
  -n mini-java-app
```

Update `kubernetes/deployment.yaml` to reference ConfigMaps and Secrets:

```yaml
env:
- name: SERVER_PORT
  valueFrom:
    configMapKeyRef:
      name: mini-java-app-config
      key: SERVER_PORT
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: mini-java-app-secrets
      key: DB_PASSWORD
```

---

## Troubleshooting

### Common Issues

#### 1. Pod Not Starting
```bash
# Check pod status
kubectl get pods -n mini-java-app

# Describe pod for events
kubectl describe pod <pod-name> -n mini-java-app

# Check logs
kubectl logs <pod-name> -n mini-java-app

# Common causes:
# - Image pull errors (check ECR permissions)
# - Missing environment variables
# - Resource limits too low
# - Health check failures
```

#### 2. Health Check Failures
```bash
# Check if actuator endpoint is accessible
kubectl exec -it <pod-name> -n mini-java-app -- curl localhost:8080/actuator/health

# Increase initialDelaySeconds in deployment.yaml if JVM startup is slow
# Java applications typically need 30-60 seconds to start
```

#### 3. Database Connection Issues
```bash
# Verify database connectivity from pod
kubectl exec -it <pod-name> -n mini-java-app -- sh
# Inside pod:
nc -zv <database-host> 3306

# Check environment variables
kubectl exec <pod-name> -n mini-java-app -- env | grep DATABASE
```

#### 4. Image Pull Errors
```bash
# Verify ECR authentication
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Check if image exists
aws ecr describe-images --repository-name mini-java-app --region us-east-1

# Verify EKS nodes have ECR permissions
kubectl describe pod <pod-name> -n mini-java-app | grep -A 5 Events
```

#### 5. Ingress Not Working
```bash
# Check ingress status
kubectl get ingress -n mini-java-app

# Describe ingress for events
kubectl describe ingress mini-java-app-ingress -n mini-java-app

# Verify AWS Load Balancer Controller is running
kubectl get pods -n kube-system | grep aws-load-balancer-controller

# Check ALB in AWS Console
aws elbv2 describe-load-balancers --region us-east-1
```

### Debugging Commands

```bash
# Get all resources in namespace
kubectl get all -n mini-java-app

# View recent events
kubectl get events -n mini-java-app --sort-by='.lastTimestamp'

# Check resource usage
kubectl top pods -n mini-java-app

# Access pod shell
kubectl exec -it <pod-name> -n mini-java-app -- sh

# Port forward for local testing
kubectl port-forward deployment/mini-java-app 8080:8080 -n mini-java-app
```

---

## Security Considerations

### 1. Container Security
- Application runs as non-root user (appuser)
- Minimal base image (Amazon Corretto 11)
- No unnecessary packages installed
- Read-only root filesystem (can be enabled)

### 2. Kubernetes Security
```yaml
# Add security context to deployment.yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: true
```

### 3. Network Policies
```yaml
# Create network policy to restrict traffic
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: mini-java-app-netpol
  namespace: mini-java-app
spec:
  podSelector:
    matchLabels:
      app: mini-java-app
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
```

### 4. Secrets Management
- Use AWS Secrets Manager or Parameter Store
- Integrate with External Secrets Operator
- Never commit secrets to version control
- Rotate credentials regularly

### 5. IAM Roles for Service Accounts (IRSA)
```bash
# Create IAM role for pod
eksctl create iamserviceaccount \
  --name mini-java-app-sa \
  --namespace mini-java-app \
  --cluster mini-java-app-cluster \
  --attach-policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess \
  --approve

# Update deployment to use service account
kubectl patch deployment mini-java-app -n mini-java-app \
  -p '{"spec":{"template":{"spec":{"serviceAccountName":"mini-java-app-sa"}}}}'
```

---

## Monitoring and Logging

### 1. Application Logs
```bash
# View real-time logs
kubectl logs -f deployment/mini-java-app -n mini-java-app

# View logs from all pods
kubectl logs -l app=mini-java-app -n mini-java-app --all-containers=true

# Export logs to file
kubectl logs deployment/mini-java-app -n mini-java-app > app.log
```

### 2. Spring Boot Actuator Endpoints
```bash
# Health check
curl http://<ingress-url>/actuator/health

# Application info
curl http://<ingress-url>/actuator/info

# Metrics
curl http://<ingress-url>/actuator/metrics
```

### 3. CloudWatch Integration
```bash
# Install Fluent Bit for log forwarding
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml
```

### 4. Prometheus Monitoring
```bash
# Add Prometheus annotations to deployment
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/actuator/prometheus"
```

### 5. Scaling and Performance

#### Horizontal Pod Autoscaler
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: mini-java-app-hpa
  namespace: mini-java-app
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: mini-java-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

#### Apply HPA
```bash
kubectl apply -f hpa.yaml
kubectl get hpa -n mini-java-app
```

---

## Maintenance Operations

### Rolling Updates
```bash
# Update image
kubectl set image deployment/mini-java-app \
  mini-java-app=<new-image-uri> \
  -n mini-java-app

# Check rollout status
kubectl rollout status deployment/mini-java-app -n mini-java-app

# View rollout history
kubectl rollout history deployment/mini-java-app -n mini-java-app
```

### Rollback
```bash
# Rollback to previous version
kubectl rollout undo deployment/mini-java-app -n mini-java-app

# Rollback to specific revision
kubectl rollout undo deployment/mini-java-app --to-revision=2 -n mini-java-app
```

### Scaling
```bash
# Manual scaling
kubectl scale deployment mini-java-app --replicas=5 -n mini-java-app

# Verify scaling
kubectl get pods -n mini-java-app
```

### Cleanup
```bash
# Delete all resources
kubectl delete namespace mini-java-app

# Delete specific resources
kubectl delete deployment mini-java-app -n mini-java-app
kubectl delete service mini-java-app-service -n mini-java-app
kubectl delete ingress mini-java-app-ingress -n mini-java-app
```

---

## Additional Resources

### Documentation
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [AWS EKS User Guide](https://docs.aws.amazon.com/eks/latest/userguide/)
- [Kubernetes Documentation](https://kubernetes.io/docs/home/)
- [Docker Documentation](https://docs.docker.com/)

### Support
For issues or questions:
1. Check application logs: `kubectl logs -f deployment/mini-java-app -n mini-java-app`
2. Review Kubernetes events: `kubectl get events -n mini-java-app`
3. Consult troubleshooting section above
4. Contact DevOps team

---

## Appendix

### A. JVM Tuning for Containers
```bash
# Recommended JVM options for containerized Spring Boot apps
JAVA_OPTS="-Xmx512m -Xms256m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Djava.security.egd=file:/dev/./urandom"
```

### B. Useful Kubectl Aliases
```bash
# Add to ~/.bashrc or ~/.zshrc
alias k='kubectl'
alias kgp='kubectl get pods'
alias kgs='kubectl get svc'
alias kgi='kubectl get ingress'
alias kl='kubectl logs -f'
alias kd='kubectl describe'
alias ke='kubectl exec -it'
```

### C. Health Check Endpoints
- **Liveness**: `/actuator/health` - Checks if application is running
- **Readiness**: `/actuator/health` - Checks if application is ready to serve traffic
- **Info**: `/actuator/info` - Application information

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Maintained By**: DevOps Team
