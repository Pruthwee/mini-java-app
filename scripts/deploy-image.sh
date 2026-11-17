#!/bin/bash
# Deploys the pushed image to AWS EKS with kubectl.
# Run from the repository root: ./scripts/deploy-image.sh
set -e
set -o pipefail

APP_NAME="minijava-s55-con-1433"
NAMESPACE="$APP_NAME"
SECRET_NAME="$APP_NAME-secrets"

echo "=== Deploying $APP_NAME to AWS EKS ==="

for tool in aws kubectl; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Error: $tool is not installed or not on the PATH"
    exit 1
  fi
done

if [ ! -f kubernetes/deployment.yaml ]; then
  echo "Error: run this script from the repository root (kubernetes/deployment.yaml not found)"
  exit 1
fi

read -r -p "Enter the AWS region (for example us-east-1): " AWS_REGION
read -r -p "Enter the EKS cluster name: " CLUSTER_NAME
read -r -p "Enter the image URI (registry/repository:tag, as printed by build-push.sh): " IMAGE_URI

if [ -z "$AWS_REGION" ] || [ -z "$CLUSTER_NAME" ] || [ -z "$IMAGE_URI" ]; then
  echo "Error: the AWS region, the cluster name and the image URI are required"
  exit 1
fi

read -r -p "Enter value for REDIS_HOST (or press Enter to skip): " REDIS_HOST
read -r -p "Enter value for REDIS_PORT (or press Enter to skip): " REDIS_PORT
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}

echo "Credentials are not echoed and are stored in the Kubernetes Secret $SECRET_NAME."
read -r -s -p "Enter value for DB_PASSWORD: " DB_PASSWORD; echo ""
read -r -s -p "Enter value for REDIS_PASSWORD: " REDIS_PASSWORD; echo ""
read -r -s -p "Enter value for EXTERNAL_API_KEY: " EXTERNAL_API_KEY; echo ""
read -r -s -p "Enter value for PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD; echo ""
read -r -s -p "Enter value for JWT_SECRET: " JWT_SECRET; echo ""
read -r -s -p "Enter value for ADMIN_PASSWORD: " ADMIN_PASSWORD; echo ""
read -r -s -p "Enter value for ENCRYPTION_KEY: " ENCRYPTION_KEY; echo ""
read -r -s -p "Enter value for MONITORING_PASSWORD: " MONITORING_PASSWORD; echo ""
read -r -s -p "Enter value for RABBITMQ_PASSWORD: " RABBITMQ_PASSWORD; echo ""

# Keep the placeholders in the tracked manifest: restore it when the script ends
DEPLOYMENT_COPY=$(mktemp)
cp kubernetes/deployment.yaml "$DEPLOYMENT_COPY"
restore_manifest() {
  cp "$DEPLOYMENT_COPY" kubernetes/deployment.yaml
  rm -f "$DEPLOYMENT_COPY" kubernetes/deployment.yaml.bak
}
trap restore_manifest EXIT

rollback_help() {
  echo ""
  echo "The deployment failed. To roll back:"
  echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
  echo "  kubectl rollout status deployment/$APP_NAME -n $NAMESPACE"
  echo "To inspect the problem:"
  echo "  kubectl describe pods -n $NAMESPACE"
  echo "  kubectl logs deployment/$APP_NAME -n $NAMESPACE"
}

echo "Configuring kubectl for the cluster $CLUSTER_NAME in $AWS_REGION..."
if ! aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"; then
  echo "Error: could not configure kubectl (aws eks update-kubeconfig failed)"
  exit 1
fi

kubectl cluster-info || exit 1

echo "Applying the namespace..."
kubectl apply -f kubernetes/namespace.yaml || { echo "Error: namespace apply failed"; exit 1; }

echo "Creating the Secret $SECRET_NAME..."
kubectl create secret generic "$SECRET_NAME" -n "$NAMESPACE" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
  --from-literal=EXTERNAL_API_KEY="$EXTERNAL_API_KEY" \
  --from-literal=PAYMENT_SERVICE_PASSWORD="$PAYMENT_SERVICE_PASSWORD" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  --from-literal=ENCRYPTION_KEY="$ENCRYPTION_KEY" \
  --from-literal=MONITORING_PASSWORD="$MONITORING_PASSWORD" \
  --from-literal=RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f - || { echo "Error: secret creation failed"; exit 1; }

echo "Replacing the placeholders in kubernetes/deployment.yaml..."
sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak
sed -i.bak 's|{{NAMESPACE}}|'"$NAMESPACE"'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak
sed -i.bak 's|{{REDIS_HOST}}|'"$REDIS_HOST"'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak
sed -i.bak 's|{{REDIS_PORT}}|'"$REDIS_PORT"'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

echo "Applying the deployment, the service and the ingress..."
kubectl apply -f kubernetes/deployment.yaml || { rollback_help; exit 1; }
kubectl apply -f kubernetes/service.yaml || { rollback_help; exit 1; }
kubectl apply -f kubernetes/ingress.yaml || { rollback_help; exit 1; }

echo "Waiting for the rollout..."
if ! kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s; then
  echo "Error: the rollout did not finish"
  rollback_help
  exit 1
fi

kubectl get pods,svc,ingress -n "$NAMESPACE"

INGRESS_HOST=$(kubectl get ingress "$APP_NAME-ingress" -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
if [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://$INGRESS_HOST"
else
  echo "The load balancer address is not ready yet. Check later with:"
  echo "  kubectl get ingress -n $NAMESPACE"
fi
echo "The ingress host rule is $APP_NAME.example.com: point your DNS name at the load balancer address."
echo "Deployment finished."
