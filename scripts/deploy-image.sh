#!/bin/bash
# =============================================================================
# deploy-image.sh — Deploy mini-java-app to AWS EKS
# =============================================================================
set -e
set -o pipefail

APP_NAME="mini-java-app"
NAMESPACE="mini-java-app"

echo "=============================================="
echo "  Deploy $APP_NAME to AWS EKS"
echo "=============================================="
echo ""

# Prompt for AWS region
read -rp "Enter AWS region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "AWS region is required. Exiting."
  exit 1
fi

# Prompt for EKS cluster name
read -rp "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "EKS cluster name is required. Exiting."
  exit 1
fi

# Prompt for Docker image URI
read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "Docker image URI is required. Exiting."
  exit 1
fi

echo ""
echo "--- Optional: Environment Variable Configuration ---"
echo "Press Enter to skip any variable and use the placeholder value."
echo ""

# Prompt for application-specific environment variables
read -rp "Enter DB_HOST (database hostname): " DB_HOST
read -rp "Enter DB_PORT (default: 3306): " DB_PORT
read -rp "Enter DB_NAME (database name): " DB_NAME
read -rp "Enter DB_USERNAME: " DB_USERNAME
read -rsp "Enter DB_PASSWORD: " DB_PASSWORD; echo ""
read -rp "Enter REDIS_HOST (default: redis.local): " REDIS_HOST
read -rp "Enter REDIS_PORT (default: 6379): " REDIS_PORT
read -rp "Enter REDIS_DATABASE (default: 0): " REDIS_DATABASE
read -rsp "Enter REDIS_PASSWORD: " REDIS_PASSWORD; echo ""
read -rp "Enter EXTERNAL_API_BASE_URL: " EXTERNAL_API_BASE_URL
read -rp "Enter EXTERNAL_API_TIMEOUT (default: 30000): " EXTERNAL_API_TIMEOUT
read -rsp "Enter EXTERNAL_API_KEY: " EXTERNAL_API_KEY; echo ""
read -rp "Enter PAYMENT_SERVICE_URL: " PAYMENT_SERVICE_URL
read -rp "Enter PAYMENT_SERVICE_USERNAME: " PAYMENT_SERVICE_USERNAME
read -rsp "Enter PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD; echo ""
read -rsp "Enter JWT_SECRET: " JWT_SECRET; echo ""
read -rp "Enter ADMIN_USERNAME: " ADMIN_USERNAME
read -rsp "Enter ADMIN_PASSWORD: " ADMIN_PASSWORD; echo ""
read -rsp "Enter ENCRYPTION_KEY: " ENCRYPTION_KEY; echo ""
read -rp "Enter RABBITMQ_HOST: " RABBITMQ_HOST
read -rp "Enter RABBITMQ_PORT (default: 5672): " RABBITMQ_PORT
read -rp "Enter RABBITMQ_USERNAME: " RABBITMQ_USERNAME
read -rsp "Enter RABBITMQ_PASSWORD: " RABBITMQ_PASSWORD; echo ""
read -rp "Enter MONITORING_ENDPOINT: " MONITORING_ENDPOINT
read -rp "Enter MONITORING_USERNAME: " MONITORING_USERNAME
read -rsp "Enter MONITORING_PASSWORD: " MONITORING_PASSWORD; echo ""

echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in $AWS_REGION ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Cannot connect to cluster. Exiting."; exit 1; }

echo ""
echo "Updating Kubernetes manifests with provided values..."

# Replace IMAGE_URI placeholder
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" kubernetes/deployment.yaml

# Replace environment variable placeholders (use defaults if empty)
sed -i "s|{{DB_HOST}}|${DB_HOST:-localhost}|g"                                   kubernetes/deployment.yaml
sed -i "s|{{DB_PORT}}|${DB_PORT:-3306}|g"                                        kubernetes/deployment.yaml
sed -i "s|{{DB_NAME}}|${DB_NAME:-mini_app_db}|g"                                 kubernetes/deployment.yaml
sed -i "s|{{DB_USERNAME}}|${DB_USERNAME:-root}|g"                                kubernetes/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|${DB_PASSWORD}|g"                                      kubernetes/deployment.yaml
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST:-redis.local}|g"                           kubernetes/deployment.yaml
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT:-6379}|g"                                  kubernetes/deployment.yaml
sed -i "s|{{REDIS_DATABASE}}|${REDIS_DATABASE:-0}|g"                             kubernetes/deployment.yaml
sed -i "s|{{REDIS_PASSWORD}}|${REDIS_PASSWORD}|g"                                kubernetes/deployment.yaml
sed -i "s|{{EXTERNAL_API_BASE_URL}}|${EXTERNAL_API_BASE_URL:-http://api.example.com:8080/v1}|g" kubernetes/deployment.yaml
sed -i "s|{{EXTERNAL_API_TIMEOUT}}|${EXTERNAL_API_TIMEOUT:-30000}|g"             kubernetes/deployment.yaml
sed -i "s|{{EXTERNAL_API_KEY}}|${EXTERNAL_API_KEY}|g"                            kubernetes/deployment.yaml
sed -i "s|{{PAYMENT_SERVICE_URL}}|${PAYMENT_SERVICE_URL:-https://payment.internal.company.com/process}|g" kubernetes/deployment.yaml
sed -i "s|{{PAYMENT_SERVICE_USERNAME}}|${PAYMENT_SERVICE_USERNAME}|g"            kubernetes/deployment.yaml
sed -i "s|{{PAYMENT_SERVICE_PASSWORD}}|${PAYMENT_SERVICE_PASSWORD}|g"            kubernetes/deployment.yaml
sed -i "s|{{JWT_SECRET}}|${JWT_SECRET}|g"                                        kubernetes/deployment.yaml
sed -i "s|{{ADMIN_USERNAME}}|${ADMIN_USERNAME}|g"                                kubernetes/deployment.yaml
sed -i "s|{{ADMIN_PASSWORD}}|${ADMIN_PASSWORD}|g"                                kubernetes/deployment.yaml
sed -i "s|{{ENCRYPTION_KEY}}|${ENCRYPTION_KEY}|g"                                kubernetes/deployment.yaml
sed -i "s|{{RABBITMQ_HOST}}|${RABBITMQ_HOST:-rabbitmq.internal.company.com}|g"  kubernetes/deployment.yaml
sed -i "s|{{RABBITMQ_PORT}}|${RABBITMQ_PORT:-5672}|g"                            kubernetes/deployment.yaml
sed -i "s|{{RABBITMQ_USERNAME}}|${RABBITMQ_USERNAME}|g"                          kubernetes/deployment.yaml
sed -i "s|{{RABBITMQ_PASSWORD}}|${RABBITMQ_PASSWORD}|g"                          kubernetes/deployment.yaml
sed -i "s|{{MONITORING_ENDPOINT}}|${MONITORING_ENDPOINT:-http://monitoring.internal.company.com:9090/metrics}|g" kubernetes/deployment.yaml
sed -i "s|{{MONITORING_USERNAME}}|${MONITORING_USERNAME}|g"                      kubernetes/deployment.yaml
sed -i "s|{{MONITORING_PASSWORD}}|${MONITORING_PASSWORD}|g"                      kubernetes/deployment.yaml

echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f kubernetes/namespace.yaml

echo "  [2/4] Applying deployment..."
kubectl apply -f kubernetes/deployment.yaml

echo "  [3/4] Applying service..."
kubectl apply -f kubernetes/service.yaml

echo "  [4/4] Applying ingress..."
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "$NAMESPACE"

echo ""
echo "=============================================="
echo "  Deployment complete!"
echo "  Application URL: http://mini-java-app.example.com"
echo "  Health endpoint: http://mini-java-app.example.com/actuator/health"
echo "=============================================="
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
