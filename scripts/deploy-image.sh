#!/usr/bin/env bash
# =============================================================
# deploy-image.sh — Deploy mini-java-app to AWS EKS
# =============================================================
set -e
set -o pipefail

NAMESPACE="mini-java-app"
APP_NAME="mini-java-app"
K8S_DIR="kubernetes"

echo "============================================="
echo "  mini-java-app — Deploy to AWS EKS"
echo "============================================="
echo ""

# ── AWS / EKS configuration ──────────────────────────────────
read -rp "Enter AWS Region [default: us-east-1]: " AWS_REGION_INPUT
AWS_REGION="${AWS_REGION_INPUT:-us-east-1}"

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS cluster name is required."
  exit 1
fi

# ── Docker image URI ─────────────────────────────────────────
read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

# ── Application environment variables ────────────────────────
echo ""
echo "--- Application Environment Variables ---"
echo "Press Enter to skip any variable (placeholder will remain in manifest)."
echo ""

read -rp "Enter DB_HOST (database host) [or Enter to skip]: " DB_HOST
read -rp "Enter DB_PORT (database port) [default: 3306]: " DB_PORT_INPUT
DB_PORT="${DB_PORT_INPUT:-3306}"
read -rp "Enter DB_NAME (database name) [default: mini_app_db]: " DB_NAME_INPUT
DB_NAME="${DB_NAME_INPUT:-mini_app_db}"
read -rp "Enter DB_USERNAME [default: root]: " DB_USERNAME_INPUT
DB_USERNAME="${DB_USERNAME_INPUT:-root}"
read -rsp "Enter DB_PASSWORD [or Enter to skip]: " DB_PASSWORD
echo ""

read -rp "Enter REDIS_HOST [default: redis.default.svc.cluster.local]: " REDIS_HOST_INPUT
REDIS_HOST="${REDIS_HOST_INPUT:-redis.default.svc.cluster.local}"
read -rp "Enter REDIS_PORT [default: 6379]: " REDIS_PORT_INPUT
REDIS_PORT="${REDIS_PORT_INPUT:-6379}"

read -rp "Enter EXTERNAL_API_BASE_URL [or Enter to skip]: " EXTERNAL_API_BASE_URL
read -rp "Enter EXTERNAL_API_KEY [or Enter to skip]: " EXTERNAL_API_KEY

read -rp "Enter PAYMENT_SERVICE_URL [or Enter to skip]: " PAYMENT_SERVICE_URL
read -rp "Enter PAYMENT_SERVICE_USERNAME [or Enter to skip]: " PAYMENT_SERVICE_USERNAME
read -rsp "Enter PAYMENT_SERVICE_PASSWORD [or Enter to skip]: " PAYMENT_SERVICE_PASSWORD
echo ""

read -rsp "Enter SECURITY_JWT_SECRET [or Enter to skip]: " SECURITY_JWT_SECRET
echo ""
read -rp "Enter SECURITY_ADMIN_USERNAME [default: admin]: " SECURITY_ADMIN_USERNAME_INPUT
SECURITY_ADMIN_USERNAME="${SECURITY_ADMIN_USERNAME_INPUT:-admin}"
read -rsp "Enter SECURITY_ADMIN_PASSWORD [or Enter to skip]: " SECURITY_ADMIN_PASSWORD
echo ""
read -rsp "Enter SECURITY_ENCRYPTION_KEY [or Enter to skip]: " SECURITY_ENCRYPTION_KEY
echo ""

read -rp "Enter MONITORING_ENDPOINT [or Enter to skip]: " MONITORING_ENDPOINT
read -rp "Enter MONITORING_USERNAME [or Enter to skip]: " MONITORING_USERNAME
read -rsp "Enter MONITORING_PASSWORD [or Enter to skip]: " MONITORING_PASSWORD
echo ""

read -rp "Enter MESSAGING_RABBITMQ_HOST [or Enter to skip]: " MESSAGING_RABBITMQ_HOST
read -rp "Enter MESSAGING_RABBITMQ_PORT [default: 5672]: " MESSAGING_RABBITMQ_PORT_INPUT
MESSAGING_RABBITMQ_PORT="${MESSAGING_RABBITMQ_PORT_INPUT:-5672}"
read -rp "Enter MESSAGING_RABBITMQ_USERNAME [or Enter to skip]: " MESSAGING_RABBITMQ_USERNAME
read -rsp "Enter MESSAGING_RABBITMQ_PASSWORD [or Enter to skip]: " MESSAGING_RABBITMQ_PASSWORD
echo ""

# ── Configure kubectl ─────────────────────────────────────────
echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster."; exit 1; }

# ── Patch manifests ───────────────────────────────────────────
echo ""
echo "Updating Kubernetes manifests with provided values..."

DEPLOY_FILE="${K8S_DIR}/deployment.yaml"

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                                   "$DEPLOY_FILE"
sed -i "s|{{DB_HOST}}|${DB_HOST}|g"                                       "$DEPLOY_FILE"
sed -i "s|{{DB_PORT}}|${DB_PORT}|g"                                       "$DEPLOY_FILE"
sed -i "s|{{DB_NAME}}|${DB_NAME}|g"                                       "$DEPLOY_FILE"
sed -i "s|{{DB_USERNAME}}|${DB_USERNAME}|g"                               "$DEPLOY_FILE"
sed -i "s|{{DB_PASSWORD}}|${DB_PASSWORD}|g"                               "$DEPLOY_FILE"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"                                 "$DEPLOY_FILE"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g"                                 "$DEPLOY_FILE"
sed -i "s|{{EXTERNAL_API_BASE_URL}}|${EXTERNAL_API_BASE_URL}|g"           "$DEPLOY_FILE"
sed -i "s|{{EXTERNAL_API_KEY}}|${EXTERNAL_API_KEY}|g"                     "$DEPLOY_FILE"
sed -i "s|{{PAYMENT_SERVICE_URL}}|${PAYMENT_SERVICE_URL}|g"               "$DEPLOY_FILE"
sed -i "s|{{PAYMENT_SERVICE_USERNAME}}|${PAYMENT_SERVICE_USERNAME}|g"     "$DEPLOY_FILE"
sed -i "s|{{PAYMENT_SERVICE_PASSWORD}}|${PAYMENT_SERVICE_PASSWORD}|g"     "$DEPLOY_FILE"
sed -i "s|{{SECURITY_JWT_SECRET}}|${SECURITY_JWT_SECRET}|g"               "$DEPLOY_FILE"
sed -i "s|{{SECURITY_ADMIN_USERNAME}}|${SECURITY_ADMIN_USERNAME}|g"       "$DEPLOY_FILE"
sed -i "s|{{SECURITY_ADMIN_PASSWORD}}|${SECURITY_ADMIN_PASSWORD}|g"       "$DEPLOY_FILE"
sed -i "s|{{SECURITY_ENCRYPTION_KEY}}|${SECURITY_ENCRYPTION_KEY}|g"       "$DEPLOY_FILE"
sed -i "s|{{MONITORING_ENDPOINT}}|${MONITORING_ENDPOINT}|g"               "$DEPLOY_FILE"
sed -i "s|{{MONITORING_USERNAME}}|${MONITORING_USERNAME}|g"               "$DEPLOY_FILE"
sed -i "s|{{MONITORING_PASSWORD}}|${MONITORING_PASSWORD}|g"               "$DEPLOY_FILE"
sed -i "s|{{MESSAGING_RABBITMQ_HOST}}|${MESSAGING_RABBITMQ_HOST}|g"       "$DEPLOY_FILE"
sed -i "s|{{MESSAGING_RABBITMQ_PORT}}|${MESSAGING_RABBITMQ_PORT}|g"       "$DEPLOY_FILE"
sed -i "s|{{MESSAGING_RABBITMQ_USERNAME}}|${MESSAGING_RABBITMQ_USERNAME}|g" "$DEPLOY_FILE"
sed -i "s|{{MESSAGING_RABBITMQ_PASSWORD}}|${MESSAGING_RABBITMQ_PASSWORD}|g" "$DEPLOY_FILE"

# ── Apply manifests ───────────────────────────────────────────
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f "${K8S_DIR}/namespace.yaml"

echo "  [2/4] Applying deployment..."
kubectl apply -f "${K8S_DIR}/deployment.yaml"

echo "  [3/4] Applying service..."
kubectl apply -f "${K8S_DIR}/service.yaml"

echo "  [4/4] Applying ingress..."
kubectl apply -f "${K8S_DIR}/ingress.yaml"

# ── Wait for rollout ──────────────────────────────────────────
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

# ── Verify ────────────────────────────────────────────────────
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "$NAMESPACE"

# ── Display URL ───────────────────────────────────────────────
echo ""
INGRESS_HOST=$(kubectl get ingress "${APP_NAME}-ingress" -n "$NAMESPACE" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "<pending>")
echo "============================================="
echo "  Deployment complete!"
echo "  Application URL: http://${INGRESS_HOST}"
echo "  Health check   : http://${INGRESS_HOST}/actuator/health"
echo "============================================="
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
