#!/bin/bash
# =============================================================================
# deploy-image.sh  –  Deploy mini-java-app to Azure AKS
# Usage: ./scripts/deploy-image.sh
# =============================================================================
set -e
set -o pipefail

APP_NAME="mini-java-app"
NAMESPACE="mini-java-app"
K8S_DIR="kubernetes"

echo "=============================================="
echo "  Deploy ${APP_NAME} to Azure AKS"
echo "=============================================="

# ---------- Validate required tools ----------
for tool in az kubectl sed; do
  if ! command -v "${tool}" &>/dev/null; then
    echo "ERROR: '${tool}' is not installed or not in PATH." >&2
    exit 1
  fi
done

# ---------- Prompt for Azure / AKS details ----------
read -rp "Enter Azure Resource Group name: " RESOURCE_GROUP
if [ -z "${RESOURCE_GROUP}" ]; then
  echo "ERROR: Resource group cannot be empty." >&2
  exit 1
fi

read -rp "Enter AKS Cluster name: " CLUSTER_NAME
if [ -z "${CLUSTER_NAME}" ]; then
  echo "ERROR: AKS cluster name cannot be empty." >&2
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. myregistry.azurecr.io/mini-java-app:latest): " IMAGE_URI
if [ -z "${IMAGE_URI}" ]; then
  echo "ERROR: Image URI cannot be empty." >&2
  exit 1
fi

echo ""
echo "--- Application Environment Variables ---"
echo "Press Enter to skip any variable (placeholder will remain in manifest)."
echo ""

read -rp "DATABASE_URL (e.g. jdbc:mysql://host:3306/db): " DATABASE_URL
read -rp "DATABASE_USERNAME: " DATABASE_USERNAME
read -rsp "DATABASE_PASSWORD: " DATABASE_PASSWORD; echo ""
read -rp "REDIS_HOST: " REDIS_HOST
read -rp "REDIS_PORT [6379]: " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"
read -rsp "REDIS_PASSWORD: " REDIS_PASSWORD; echo ""
read -rp "EXTERNAL_API_BASE_URL: " EXTERNAL_API_BASE_URL
read -rp "EXTERNAL_API_KEY: " EXTERNAL_API_KEY
read -rp "PAYMENT_SERVICE_URL: " PAYMENT_SERVICE_URL
read -rp "PAYMENT_SERVICE_USERNAME: " PAYMENT_SERVICE_USERNAME
read -rsp "PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD; echo ""
read -rsp "SECURITY_JWT_SECRET: " SECURITY_JWT_SECRET; echo ""
read -rp "MONITORING_ENDPOINT: " MONITORING_ENDPOINT
read -rp "MONITORING_USERNAME: " MONITORING_USERNAME
read -rsp "MONITORING_PASSWORD: " MONITORING_PASSWORD; echo ""
read -rp "MESSAGING_RABBITMQ_HOST: " MESSAGING_RABBITMQ_HOST
read -rp "MESSAGING_RABBITMQ_PORT [5672]: " MESSAGING_RABBITMQ_PORT
MESSAGING_RABBITMQ_PORT="${MESSAGING_RABBITMQ_PORT:-5672}"
read -rp "MESSAGING_RABBITMQ_USERNAME: " MESSAGING_RABBITMQ_USERNAME
read -rsp "MESSAGING_RABBITMQ_PASSWORD: " MESSAGING_RABBITMQ_PASSWORD; echo ""

# ---------- Configure kubectl for AKS ----------
echo ""
echo "Configuring kubectl for AKS cluster: ${CLUSTER_NAME} ..."
az aks get-credentials --resource-group "${RESOURCE_GROUP}" --name "${CLUSTER_NAME}" --overwrite-existing

echo "Verifying cluster connectivity ..."
kubectl cluster-info || { echo "ERROR: Cannot connect to AKS cluster." >&2; exit 1; }

# ---------- Substitute placeholders in manifests ----------
echo ""
echo "Updating Kubernetes manifests with provided values ..."

# Work on copies to avoid modifying originals
DEPLOY_DIR=$(mktemp -d)
cp -r "${K8S_DIR}/." "${DEPLOY_DIR}/"

DEPLOYMENT_FILE="${DEPLOY_DIR}/deployment.yaml"

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                                   "${DEPLOYMENT_FILE}"
sed -i "s|{{DATABASE_URL}}|${DATABASE_URL}|g"                             "${DEPLOYMENT_FILE}"
sed -i "s|{{DATABASE_USERNAME}}|${DATABASE_USERNAME}|g"                   "${DEPLOYMENT_FILE}"
sed -i "s|{{DATABASE_PASSWORD}}|${DATABASE_PASSWORD}|g"                   "${DEPLOYMENT_FILE}"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"                                 "${DEPLOYMENT_FILE}"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g"                                 "${DEPLOYMENT_FILE}"
sed -i "s|{{REDIS_PASSWORD}}|${REDIS_PASSWORD}|g"                         "${DEPLOYMENT_FILE}"
sed -i "s|{{EXTERNAL_API_BASE_URL}}|${EXTERNAL_API_BASE_URL}|g"           "${DEPLOYMENT_FILE}"
sed -i "s|{{EXTERNAL_API_KEY}}|${EXTERNAL_API_KEY}|g"                     "${DEPLOYMENT_FILE}"
sed -i "s|{{PAYMENT_SERVICE_URL}}|${PAYMENT_SERVICE_URL}|g"               "${DEPLOYMENT_FILE}"
sed -i "s|{{PAYMENT_SERVICE_USERNAME}}|${PAYMENT_SERVICE_USERNAME}|g"     "${DEPLOYMENT_FILE}"
sed -i "s|{{PAYMENT_SERVICE_PASSWORD}}|${PAYMENT_SERVICE_PASSWORD}|g"     "${DEPLOYMENT_FILE}"
sed -i "s|{{SECURITY_JWT_SECRET}}|${SECURITY_JWT_SECRET}|g"               "${DEPLOYMENT_FILE}"
sed -i "s|{{MONITORING_ENDPOINT}}|${MONITORING_ENDPOINT}|g"               "${DEPLOYMENT_FILE}"
sed -i "s|{{MONITORING_USERNAME}}|${MONITORING_USERNAME}|g"               "${DEPLOYMENT_FILE}"
sed -i "s|{{MONITORING_PASSWORD}}|${MONITORING_PASSWORD}|g"               "${DEPLOYMENT_FILE}"
sed -i "s|{{MESSAGING_RABBITMQ_HOST}}|${MESSAGING_RABBITMQ_HOST}|g"       "${DEPLOYMENT_FILE}"
sed -i "s|{{MESSAGING_RABBITMQ_PORT}}|${MESSAGING_RABBITMQ_PORT}|g"       "${DEPLOYMENT_FILE}"
sed -i "s|{{MESSAGING_RABBITMQ_USERNAME}}|${MESSAGING_RABBITMQ_USERNAME}|g" "${DEPLOYMENT_FILE}"
sed -i "s|{{MESSAGING_RABBITMQ_PASSWORD}}|${MESSAGING_RABBITMQ_PASSWORD}|g" "${DEPLOYMENT_FILE}"

# ---------- Apply manifests in order ----------
echo ""
echo "Applying Kubernetes manifests ..."

echo "  [1/4] Applying namespace ..."
kubectl apply -f "${DEPLOY_DIR}/namespace.yaml"

echo "  [2/4] Applying deployment ..."
kubectl apply -f "${DEPLOY_DIR}/deployment.yaml"

echo "  [3/4] Applying service ..."
kubectl apply -f "${DEPLOY_DIR}/service.yaml"

echo "  [4/4] Applying ingress ..."
kubectl apply -f "${DEPLOY_DIR}/ingress.yaml"

# ---------- Wait for rollout ----------
echo ""
echo "Waiting for deployment rollout ..."
kubectl rollout status deployment/${APP_NAME} -n "${NAMESPACE}" --timeout=300s

# ---------- Verify resources ----------
echo ""
echo "Verifying deployed resources ..."
kubectl get pods,svc,ingress -n "${NAMESPACE}"

# ---------- Display access URL ----------
echo ""
INGRESS_IP=$(kubectl get ingress ${APP_NAME}-ingress -n "${NAMESPACE}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
echo "=============================================="
echo "  DEPLOYMENT COMPLETE"
echo "  Application: ${APP_NAME}"
echo "  Namespace  : ${NAMESPACE}"
echo "  Image      : ${IMAGE_URI}"
if [ "${INGRESS_IP}" != "pending" ] && [ -n "${INGRESS_IP}" ]; then
  echo "  Access URL : http://${INGRESS_IP}/"
else
  echo "  Ingress IP : pending (run: kubectl get ingress -n ${NAMESPACE})"
fi
echo "=============================================="
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"

# ---------- Cleanup temp dir ----------
rm -rf "${DEPLOY_DIR}"
