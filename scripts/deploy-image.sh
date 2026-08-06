#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh – Deploy mini-java-app to AWS EKS
# ============================================================

APP_NAME="mini-java-app"
NAMESPACE="mini-java-app"
K8S_DIR="kubernetes"

echo "============================================"
echo "  mini-java-app – EKS Deployment"
echo "============================================"
echo ""

# ── Collect inputs ───────────────────────────────────────────
read -rp "Enter AWS region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS region is required."
  exit 1
fi

read -rp "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS cluster name is required."
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

echo ""
echo "--- Optional: Application Environment Variables ---"
echo "Press Enter to skip any variable."
echo ""

read -rp "Enter DB_HOST (database hostname): " DB_HOST
read -rp "Enter DB_PORT (default: 3306): " DB_PORT
read -rp "Enter DB_NAME (database name): " DB_NAME
read -rp "Enter DB_USERNAME (database user): " DB_USERNAME
read -rsp "Enter DB_PASSWORD (database password): " DB_PASSWORD
echo ""
read -rp "Enter DB_URL (full JDBC URL, overrides DB_HOST/PORT/NAME if set): " DB_URL
read -rp "Enter REDIS_HOST (Redis hostname): " REDIS_HOST
read -rp "Enter REDIS_PORT (default: 6379): " REDIS_PORT

echo ""

# ── Configure kubectl ────────────────────────────────────────
echo "Configuring kubectl for EKS cluster: ${CLUSTER_NAME} in ${AWS_REGION}..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster."; exit 1; }

echo ""

# ── Update manifests with actual values ──────────────────────
echo "Updating Kubernetes manifests..."

# Work on copies to avoid modifying originals
cp -r "$K8S_DIR" /tmp/mini-java-app-k8s-deploy

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"           /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{DB_HOST}}|${DB_HOST:-localhost}|g"    /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{DB_PORT}}|${DB_PORT:-3306}|g"         /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{DB_NAME}}|${DB_NAME:-mini_app_db}|g"  /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{DB_USERNAME}}|${DB_USERNAME:-root}|g" /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|${DB_PASSWORD:-}|g"     /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{DB_URL}}|${DB_URL:-}|g"               /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST:-redis.internal.svc.cluster.local}|g" /tmp/mini-java-app-k8s-deploy/deployment.yaml
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT:-6379}|g"   /tmp/mini-java-app-k8s-deploy/deployment.yaml

# ── Apply manifests ──────────────────────────────────────────
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f /tmp/mini-java-app-k8s-deploy/namespace.yaml

echo "  [2/4] Applying deployment..."
kubectl apply -f /tmp/mini-java-app-k8s-deploy/deployment.yaml

echo "  [3/4] Applying service..."
kubectl apply -f /tmp/mini-java-app-k8s-deploy/service.yaml

echo "  [4/4] Applying ingress..."
kubectl apply -f /tmp/mini-java-app-k8s-deploy/ingress.yaml

# ── Wait for rollout ─────────────────────────────────────────
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"${APP_NAME}" -n "${NAMESPACE}" --timeout=300s

# ── Verify ───────────────────────────────────────────────────
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "${NAMESPACE}"

# ── Display URL ──────────────────────────────────────────────
echo ""
INGRESS_HOST=$(kubectl get ingress "${APP_NAME}-ingress" -n "${NAMESPACE}" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "<pending>")
echo "============================================"
echo "  Deployment Complete!"
echo "  Application URL: http://${INGRESS_HOST}"
echo "  Health Check:    http://${INGRESS_HOST}/actuator/health"
echo "============================================"
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"

# ── Cleanup temp files ───────────────────────────────────────
rm -rf /tmp/mini-java-app-k8s-deploy
