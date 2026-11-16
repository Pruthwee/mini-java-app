#!/bin/bash
# Deploys the image of minijava-br55-con-1612 to AWS EKS with kubectl.
# Run from the repository root: ./scripts/deploy-image.sh
set -e
set -o pipefail

APP_NAME="minijava-br55-con-1612"
NAMESPACE="minijava-br55-con-1612"

command -v aws >/dev/null 2>&1 || { echo "ERROR: the AWS CLI is not installed"; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl is not installed"; exit 1; }

echo "=== Deploy $APP_NAME to AWS EKS ==="
read -r -p "Enter the AWS region (for example us-east-1): " AWS_REGION
read -r -p "Enter the EKS cluster name: " CLUSTER_NAME
read -r -p "Enter the image URI (registry, repository and tag): " IMAGE_URI

if [ -z "$AWS_REGION" ] || [ -z "$CLUSTER_NAME" ] || [ -z "$IMAGE_URI" ]; then
    echo "ERROR: the region, the cluster name and the image URI are required"
    exit 1
fi

read -r -p "Enter value for REDIS_HOST (or press Enter to skip): " REDIS_HOST
read -r -p "Enter value for REDIS_PORT (or press Enter to skip): " REDIS_PORT

read_secret() {
    local name="$1" value=""
    while [ -z "$value" ]; do
        read -r -s -p "Enter value for $name (hidden): " value
        echo "" >&2
    done
    printf '%s' "$value"
}

DB_PASSWORD=$(read_secret DB_PASSWORD)
REDIS_PASSWORD=$(read_secret REDIS_PASSWORD)
EXTERNAL_API_KEY=$(read_secret EXTERNAL_API_KEY)
PAYMENT_SERVICE_PASSWORD=$(read_secret PAYMENT_SERVICE_PASSWORD)
JWT_SECRET=$(read_secret JWT_SECRET)
ADMIN_PASSWORD=$(read_secret ADMIN_PASSWORD)
ENCRYPTION_KEY=$(read_secret ENCRYPTION_KEY)
MONITORING_PASSWORD=$(read_secret MONITORING_PASSWORD)
RABBITMQ_PASSWORD=$(read_secret RABBITMQ_PASSWORD)

# The manifests are filled in place; the originals are restored when the script ends
BACKUP_DIR=$(mktemp -d)
cp kubernetes/deployment.yaml "$BACKUP_DIR/deployment.yaml"
restore_manifests() {
    cp "$BACKUP_DIR/deployment.yaml" kubernetes/deployment.yaml
    rm -f kubernetes/deployment.yaml.bak
    rm -rf "$BACKUP_DIR"
}
trap restore_manifests EXIT

echo "Configuring kubectl for cluster $CLUSTER_NAME in $AWS_REGION ..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME || { echo "ERROR: could not configure kubectl for the cluster"; exit 1; }
kubectl cluster-info || exit 1

echo "Applying the namespace ..."
kubectl apply -f kubernetes/namespace.yaml || { echo "ERROR: namespace.yaml failed"; exit 1; }

echo "Creating the Secret $APP_NAME-secrets ..."
kubectl create secret generic $APP_NAME-secrets -n $NAMESPACE \
    --from-literal=DB_PASSWORD="$DB_PASSWORD" \
    --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD" \
    --from-literal=EXTERNAL_API_KEY="$EXTERNAL_API_KEY" \
    --from-literal=PAYMENT_SERVICE_PASSWORD="$PAYMENT_SERVICE_PASSWORD" \
    --from-literal=JWT_SECRET="$JWT_SECRET" \
    --from-literal=ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    --from-literal=ENCRYPTION_KEY="$ENCRYPTION_KEY" \
    --from-literal=MONITORING_PASSWORD="$MONITORING_PASSWORD" \
    --from-literal=RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f - || { echo "ERROR: the Secret could not be created"; exit 1; }

echo "Filling in the placeholders ..."
sed -i.bak 's|{{IMAGE_URI}}|'$IMAGE_URI'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{NAMESPACE}}|'$NAMESPACE'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{REDIS_HOST}}|'$REDIS_HOST'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{REDIS_PORT}}|'$REDIS_PORT'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

echo "Applying the deployment, the service and the ingress ..."
kubectl apply -f kubernetes/deployment.yaml || { echo "ERROR: deployment.yaml failed"; exit 1; }
kubectl apply -f kubernetes/service.yaml || { echo "ERROR: service.yaml failed"; exit 1; }
kubectl apply -f kubernetes/ingress.yaml || { echo "ERROR: ingress.yaml failed"; exit 1; }

echo "Waiting for the rollout ..."
if ! kubectl rollout status deployment/$APP_NAME -n $NAMESPACE --timeout=300s; then
    echo "ERROR: the rollout failed. To go back to the previous version run:"
    echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
    echo "To look into the failure run:"
    echo "  kubectl describe pods -n $NAMESPACE"
    echo "  kubectl logs deployment/$APP_NAME -n $NAMESPACE"
    exit 1
fi

kubectl get pods,svc,ingress -n $NAMESPACE

INGRESS_HOST=$(kubectl get ingress $APP_NAME-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
if [ -n "$INGRESS_HOST" ]; then
    echo "The load balancer address is http://$INGRESS_HOST (host rule: $APP_NAME.example.com)"
else
    echo "The load balancer address is not ready yet. Check later with: kubectl get ingress -n $NAMESPACE"
fi
echo "Deployment finished."
