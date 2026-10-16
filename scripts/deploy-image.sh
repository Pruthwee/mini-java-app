#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh — Deploy mini-java-app to AWS EKS
# ============================================================

APP_NAME="mini-java-app"
NAMESPACE="mini-java-app"
MANIFESTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/kubernetes"

echo "============================================"
echo "  Deploy to AWS EKS: $APP_NAME"
echo "============================================"

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

# Prompt for application-specific environment variables
echo ""
echo "--- Application Environment Variables (press Enter to keep placeholder) ---"

read -rp "Enter DB_HOST (database host): " DB_HOST
DB_HOST="${DB_HOST:-localhost}"

read -rp "Enter DB_PORT (database port, default 3306): " DB_PORT
DB_PORT="${DB_PORT:-3306}"

read -rp "Enter DB_NAME (database name, default mini_app_db): " DB_NAME
DB_NAME="${DB_NAME:-mini_app_db}"

read -rp "Enter DB_USERNAME (database username): " DB_USERNAME
DB_USERNAME="${DB_USERNAME:-root}"

read -rsp "Enter DB_PASSWORD (database password): " DB_PASSWORD
echo ""
DB_PASSWORD="${DB_PASSWORD:-}"

read -rp "Enter REDIS_HOST (Redis host): " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-redis-service.default.svc.cluster.local}"

read -rp "Enter REDIS_PORT (Redis port, default 6379): " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"

read -rp "Enter EXTERNAL_API_URL (external API base URL): " EXTERNAL_API_URL
EXTERNAL_API_URL="${EXTERNAL_API_URL:-http://api.example.com:8080/v1}"

read -rp "Enter PAYMENT_SERVICE_URL (payment service URL): " PAYMENT_SERVICE_URL
PAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL:-https://payment.internal.company.com/process}"

# Configure kubectl for EKS
echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in $AWS_REGION..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Failed to connect to EKS cluster. Exiting."; exit 1; }

# Update deployment.yaml with actual values using pipe delimiter
echo ""
echo "Updating Kubernetes manifests with deployment values..."

DEPLOY_YAML="$MANIFESTS_DIR/deployment.yaml"

sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g"                       "$DEPLOY_YAML"
sed -i "s|{{DB_HOST}}|$DB_HOST|g"                           "$DEPLOY_YAML"
sed -i "s|{{DB_PORT}}|$DB_PORT|g"                           "$DEPLOY_YAML"
sed -i "s|{{DB_NAME}}|$DB_NAME|g"                           "$DEPLOY_YAML"
sed -i "s|{{DB_USERNAME}}|$DB_USERNAME|g"                   "$DEPLOY_YAML"
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g"                   "$DEPLOY_YAML"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g"                     "$DEPLOY_YAML"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g"                     "$DEPLOY_YAML"
sed -i "s|{{EXTERNAL_API_URL}}|$EXTERNAL_API_URL|g"         "$DEPLOY_YAML"
sed -i "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g"   "$DEPLOY_YAML"

# Apply Kubernetes manifests in order
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f "$MANIFESTS_DIR/namespace.yaml"

echo "  [2/4] Applying deployment..."
kubectl apply -f "$MANIFESTS_DIR/deployment.yaml"

echo "  [3/4] Applying service..."
kubectl apply -f "$MANIFESTS_DIR/service.yaml"

echo "  [4/4] Applying ingress..."
kubectl apply -f "$MANIFESTS_DIR/ingress.yaml"

# Wait for rollout
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

# Verify resources
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "$NAMESPACE"

# Display application URL
echo ""
echo "Fetching application ingress URL..."
INGRESS_HOST=$(kubectl get ingress "$APP_NAME-ingress" -n "$NAMESPACE" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")

echo "============================================"
echo "  Deployment complete!"
echo "  Application: $APP_NAME"
echo "  Namespace:   $NAMESPACE"
echo "  Image:       $IMAGE_URI"
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo "  URL:         http://$INGRESS_HOST"
else
  echo "  URL:         (ingress hostname pending — check 'kubectl get ingress -n $NAMESPACE')"
fi
echo "============================================"
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
