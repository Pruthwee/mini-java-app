#!/bin/bash
# =============================================================
# deploy-image.sh  –  Deploy mini-java-app to AWS EKS
# =============================================================
set -e
set -o pipefail

APP_NAME="mini-java-app"
NAMESPACE="mini-java-app"
K8S_DIR="kubernetes"

echo "=============================================="
echo "  Deploy $APP_NAME to AWS EKS"
echo "=============================================="

# ── Collect AWS / EKS details ──────────────────────────────────
read -rp "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required." >&2
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required." >&2
  exit 1
fi

# ── Collect application-specific environment variable values ───
echo ""
echo "--- Application Environment Variables (press Enter to skip) ---"

read -rp "Enter DB_HOST: " DB_HOST
read -rp "Enter DB_PORT [3306]: " DB_PORT;          DB_PORT="${DB_PORT:-3306}"
read -rp "Enter DB_NAME [mini_app_db]: " DB_NAME;   DB_NAME="${DB_NAME:-mini_app_db}"
read -rp "Enter DB_USERNAME: " DB_USERNAME
read -rsp "Enter DB_PASSWORD: " DB_PASSWORD;        echo ""
read -rp "Enter REDIS_HOST: " REDIS_HOST
read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT;    REDIS_PORT="${REDIS_PORT:-6379}"
read -rp "Enter EXTERNAL_API_BASE_URL: " EXTERNAL_API_BASE_URL
read -rp "Enter EXTERNAL_API_TIMEOUT [30000]: " EXTERNAL_API_TIMEOUT; EXTERNAL_API_TIMEOUT="${EXTERNAL_API_TIMEOUT:-30000}"
read -rp "Enter EXTERNAL_API_KEY: " EXTERNAL_API_KEY
read -rp "Enter PAYMENT_SERVICE_URL: " PAYMENT_SERVICE_URL
read -rp "Enter PAYMENT_SERVICE_USERNAME: " PAYMENT_SERVICE_USERNAME
read -rsp "Enter PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD; echo ""
read -rp "Enter APP_CONFIG_FILE_PATH [/mnt/efs/app/config/app.properties]: " APP_CONFIG_FILE_PATH
APP_CONFIG_FILE_PATH="${APP_CONFIG_FILE_PATH:-/mnt/efs/app/config/app.properties}"
read -rp "Enter APP_LOG_DIR [/var/log/mini-app]: " APP_LOG_DIR
APP_LOG_DIR="${APP_LOG_DIR:-/var/log/mini-app}"
read -rp "Enter APP_LOG_FILE_PATH [/mnt/efs/logs/mini-app.log]: " APP_LOG_FILE_PATH
APP_LOG_FILE_PATH="${APP_LOG_FILE_PATH:-/mnt/efs/logs/mini-app.log}"
read -rsp "Enter SECURITY_JWT_SECRET: " SECURITY_JWT_SECRET;           echo ""
read -rp "Enter SECURITY_ADMIN_USERNAME [admin]: " SECURITY_ADMIN_USERNAME
SECURITY_ADMIN_USERNAME="${SECURITY_ADMIN_USERNAME:-admin}"
read -rsp "Enter SECURITY_ADMIN_PASSWORD: " SECURITY_ADMIN_PASSWORD;   echo ""
read -rsp "Enter SECURITY_ENCRYPTION_KEY: " SECURITY_ENCRYPTION_KEY;   echo ""
read -rp "Enter MONITORING_ENDPOINT: " MONITORING_ENDPOINT
read -rp "Enter MONITORING_USERNAME: " MONITORING_USERNAME
read -rsp "Enter MONITORING_PASSWORD: " MONITORING_PASSWORD;           echo ""
read -rp "Enter MESSAGING_RABBITMQ_HOST: " MESSAGING_RABBITMQ_HOST
read -rp "Enter MESSAGING_RABBITMQ_PORT [5672]: " MESSAGING_RABBITMQ_PORT
MESSAGING_RABBITMQ_PORT="${MESSAGING_RABBITMQ_PORT:-5672}"
read -rp "Enter MESSAGING_RABBITMQ_USERNAME: " MESSAGING_RABBITMQ_USERNAME
read -rsp "Enter MESSAGING_RABBITMQ_PASSWORD: " MESSAGING_RABBITMQ_PASSWORD; echo ""

# ── Configure kubectl ──────────────────────────────────────────
echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to cluster." >&2; exit 1; }

# ── Patch manifests with actual values ─────────────────────────
echo ""
echo "Updating Kubernetes manifests..."

DEPLOY_YAML="$K8S_DIR/deployment.yaml"

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                                         "$DEPLOY_YAML"
sed -i "s|{{DB_HOST}}|${DB_HOST}|g"                                             "$DEPLOY_YAML"
sed -i "s|{{DB_PORT}}|${DB_PORT}|g"                                             "$DEPLOY_YAML"
sed -i "s|{{DB_NAME}}|${DB_NAME}|g"                                             "$DEPLOY_YAML"
sed -i "s|{{DB_USERNAME}}|${DB_USERNAME}|g"                                     "$DEPLOY_YAML"
sed -i "s|{{DB_PASSWORD}}|${DB_PASSWORD}|g"                                     "$DEPLOY_YAML"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"                                       "$DEPLOY_YAML"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g"                                       "$DEPLOY_YAML"
sed -i "s|{{EXTERNAL_API_BASE_URL}}|${EXTERNAL_API_BASE_URL}|g"                 "$DEPLOY_YAML"
sed -i "s|{{EXTERNAL_API_TIMEOUT}}|${EXTERNAL_API_TIMEOUT}|g"                   "$DEPLOY_YAML"
sed -i "s|{{EXTERNAL_API_KEY}}|${EXTERNAL_API_KEY}|g"                           "$DEPLOY_YAML"
sed -i "s|{{PAYMENT_SERVICE_URL}}|${PAYMENT_SERVICE_URL}|g"                     "$DEPLOY_YAML"
sed -i "s|{{PAYMENT_SERVICE_USERNAME}}|${PAYMENT_SERVICE_USERNAME}|g"           "$DEPLOY_YAML"
sed -i "s|{{PAYMENT_SERVICE_PASSWORD}}|${PAYMENT_SERVICE_PASSWORD}|g"           "$DEPLOY_YAML"
sed -i "s|{{APP_CONFIG_FILE_PATH}}|${APP_CONFIG_FILE_PATH}|g"                   "$DEPLOY_YAML"
sed -i "s|{{APP_LOG_DIR}}|${APP_LOG_DIR}|g"                                     "$DEPLOY_YAML"
sed -i "s|{{APP_LOG_FILE_PATH}}|${APP_LOG_FILE_PATH}|g"                         "$DEPLOY_YAML"
sed -i "s|{{SECURITY_JWT_SECRET}}|${SECURITY_JWT_SECRET}|g"                     "$DEPLOY_YAML"
sed -i "s|{{SECURITY_ADMIN_USERNAME}}|${SECURITY_ADMIN_USERNAME}|g"             "$DEPLOY_YAML"
sed -i "s|{{SECURITY_ADMIN_PASSWORD}}|${SECURITY_ADMIN_PASSWORD}|g"             "$DEPLOY_YAML"
sed -i "s|{{SECURITY_ENCRYPTION_KEY}}|${SECURITY_ENCRYPTION_KEY}|g"             "$DEPLOY_YAML"
sed -i "s|{{MONITORING_ENDPOINT}}|${MONITORING_ENDPOINT}|g"                     "$DEPLOY_YAML"
sed -i "s|{{MONITORING_USERNAME}}|${MONITORING_USERNAME}|g"                     "$DEPLOY_YAML"
sed -i "s|{{MONITORING_PASSWORD}}|${MONITORING_PASSWORD}|g"                     "$DEPLOY_YAML"
sed -i "s|{{MESSAGING_RABBITMQ_HOST}}|${MESSAGING_RABBITMQ_HOST}|g"             "$DEPLOY_YAML"
sed -i "s|{{MESSAGING_RABBITMQ_PORT}}|${MESSAGING_RABBITMQ_PORT}|g"             "$DEPLOY_YAML"
sed -i "s|{{MESSAGING_RABBITMQ_USERNAME}}|${MESSAGING_RABBITMQ_USERNAME}|g"     "$DEPLOY_YAML"
sed -i "s|{{MESSAGING_RABBITMQ_PASSWORD}}|${MESSAGING_RABBITMQ_PASSWORD}|g"     "$DEPLOY_YAML"

# ── Apply manifests ────────────────────────────────────────────
echo ""
echo "Applying Kubernetes manifests..."
kubectl apply -f "$K8S_DIR/namespace.yaml"
kubectl apply -f "$K8S_DIR/deployment.yaml"
kubectl apply -f "$K8S_DIR/service.yaml"
kubectl apply -f "$K8S_DIR/ingress.yaml"

# ── Wait for rollout ───────────────────────────────────────────
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

# ── Verify ────────────────────────────────────────────────────
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "$NAMESPACE"

# ── Display URL ───────────────────────────────────────────────
echo ""
INGRESS_HOST=$(kubectl get ingress "$APP_NAME-ingress" -n "$NAMESPACE" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "<pending>")
echo "=============================================="
echo "  Deployment complete!"
echo "  Application URL: http://$INGRESS_HOST"
echo "  Health check:    http://$INGRESS_HOST/actuator/health"
echo "=============================================="
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
