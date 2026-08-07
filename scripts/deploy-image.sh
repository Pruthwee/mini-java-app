#!/bin/bash
# =============================================================================
# deploy-image.sh – Deploy mini-java-app to AWS EKS
# Usage: bash scripts/deploy-image.sh   (run from repository root)
# =============================================================================
set -e
set -o pipefail

APP_NAME="mini-java-app"
NAMESPACE="mini-java-app"

echo "============================================"
echo "  mini-java-app – AWS EKS Deployment"
echo "============================================"
echo ""

# ---------------------------------------------------------------------------
# Collect deployment parameters
# ---------------------------------------------------------------------------
read -rp "Enter AWS region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

read -rp "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS cluster name is required." >&2
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Optional application-specific environment variable overrides
# ---------------------------------------------------------------------------
echo ""
echo "--- Optional environment variable overrides (press Enter to skip) ---"

read -rp "Enter DB_URL (e.g. jdbc:mysql://db-host:3306/mini_app_db): " DB_URL
DB_URL="${DB_URL:-jdbc:mysql://db-host:3306/mini_app_db}"

read -rp "Enter REDIS_HOST [redis-service]: " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-redis-service}"

read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"

read -rp "Enter EXTERNAL_API_URL: " EXTERNAL_API_URL
EXTERNAL_API_URL="${EXTERNAL_API_URL:-}"

read -rp "Enter PAYMENT_SERVICE_URL: " PAYMENT_SERVICE_URL
PAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL:-}"

read -rp "Enter APP_ENV [production]: " APP_ENV
APP_ENV="${APP_ENV:-production}"

read -rp "Enter APP_LOG_LEVEL [INFO]: " APP_LOG_LEVEL
APP_LOG_LEVEL="${APP_LOG_LEVEL:-INFO}"

read -rp "Enter APP_CONTEXT_PATH [/mini-app]: " APP_CONTEXT_PATH
APP_CONTEXT_PATH="${APP_CONTEXT_PATH:-/mini-app}"

read -rp "Enter MONITORING_ENDPOINT: " MONITORING_ENDPOINT
MONITORING_ENDPOINT="${MONITORING_ENDPOINT:-}"

# ---------------------------------------------------------------------------
# Configure kubectl for EKS
# ---------------------------------------------------------------------------
echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in $AWS_REGION ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

# ---------------------------------------------------------------------------
# Substitute placeholders in Kubernetes manifests (pipe delimiter)
# ---------------------------------------------------------------------------
echo ""
echo "Updating Kubernetes manifests with deployment values..."

# Work on copies to avoid modifying originals
cp kubernetes/deployment.yaml /tmp/deployment.yaml
cp kubernetes/service.yaml    /tmp/service.yaml
cp kubernetes/ingress.yaml    /tmp/ingress.yaml
cp kubernetes/namespace.yaml  /tmp/namespace.yaml

sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'                   /tmp/deployment.yaml
sed -i 's|{{APP_ENV}}|'"$APP_ENV"'|g'                       /tmp/deployment.yaml
sed -i 's|{{APP_LOG_LEVEL}}|'"$APP_LOG_LEVEL"'|g'           /tmp/deployment.yaml
sed -i 's|{{APP_CONTEXT_PATH}}|'"$APP_CONTEXT_PATH"'|g'     /tmp/deployment.yaml
sed -i 's|{{DB_URL}}|'"$DB_URL"'|g'                         /tmp/deployment.yaml
sed -i 's|{{REDIS_HOST}}|'"$REDIS_HOST"'|g'                 /tmp/deployment.yaml
sed -i 's|{{REDIS_PORT}}|'"$REDIS_PORT"'|g'                 /tmp/deployment.yaml
sed -i 's|{{EXTERNAL_API_URL}}|'"$EXTERNAL_API_URL"'|g'     /tmp/deployment.yaml
sed -i 's|{{PAYMENT_SERVICE_URL}}|'"$PAYMENT_SERVICE_URL"'|g' /tmp/deployment.yaml
sed -i 's|{{MONITORING_ENDPOINT}}|'"$MONITORING_ENDPOINT"'|g' /tmp/deployment.yaml

# ---------------------------------------------------------------------------
# Apply manifests in order
# ---------------------------------------------------------------------------
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f /tmp/namespace.yaml

echo "  [2/4] Applying deployment..."
kubectl apply -f /tmp/deployment.yaml

echo "  [3/4] Applying service..."
kubectl apply -f /tmp/service.yaml

echo "  [4/4] Applying ingress..."
kubectl apply -f /tmp/ingress.yaml

# ---------------------------------------------------------------------------
# Wait for rollout
# ---------------------------------------------------------------------------
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

# ---------------------------------------------------------------------------
# Verify resources
# ---------------------------------------------------------------------------
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "$NAMESPACE"

# ---------------------------------------------------------------------------
# Display application URL
# ---------------------------------------------------------------------------
echo ""
echo "Fetching application ingress URL..."
INGRESS_HOST=$(kubectl get ingress "$APP_NAME-ingress" -n "$NAMESPACE" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")

echo ""
echo "============================================"
echo "  Deployment complete!"
echo "  Application URL: http://$INGRESS_HOST"
echo "  Health check   : http://$INGRESS_HOST/actuator/health"
echo "============================================"
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
