#!/bin/bash
set -e
set -o pipefail

echo "=== Deploy mini-java-app to AWS EKS ==="

# Ask for AWS EKS cluster details
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter the full image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): " IMAGE_URI

echo ""
echo "=== Configuring kubectl for EKS cluster ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo ""
echo "=== Verifying cluster connectivity ==="
kubectl cluster-info
if [ $? -ne 0 ]; then
    echo "Failed to connect to cluster. Exiting."
    exit 1
fi

echo ""
echo "=== Collecting environment variable values ==="
echo "Enter values for non-credential environment variables."
echo "Press Enter to skip (use default or leave unset)."

read -p "Enter value for CONFIG_PATH (or press Enter to skip): " CONFIG_PATH
read -p "Enter value for LOG_PATH (or press Enter to skip): " LOG_PATH
read -p "Enter value for LOG_DIR (or press Enter to skip): " LOG_DIR
read -p "Enter value for DB_HOST (or press Enter to skip): " DB_HOST
read -p "Enter value for DB_PORT (or press Enter to skip): " DB_PORT
read -p "Enter value for DB_NAME (or press Enter to skip): " DB_NAME
read -p "Enter value for REDIS_HOST (or press Enter to skip): " REDIS_HOST
read -p "Enter value for REDIS_PORT (or press Enter to skip): " REDIS_PORT
read -p "Enter value for EXTERNAL_API_URL (or press Enter to skip): " EXTERNAL_API_URL
read -p "Enter value for PAYMENT_SERVICE_URL (or press Enter to skip): " PAYMENT_SERVICE_URL

echo ""
echo "=== Collecting credential values (input hidden) ==="
read -s -p "Enter value for DB_USERNAME: " DB_USERNAME_VAL
echo ""
read -s -p "Enter value for DB_PASSWORD: " DB_PASSWORD_VAL
echo ""
read -s -p "Enter value for REDIS_PASSWORD: " REDIS_PASSWORD_VAL
echo ""
read -s -p "Enter value for EXTERNAL_API_KEY: " EXTERNAL_API_KEY_VAL
echo ""
read -s -p "Enter value for PAYMENT_SERVICE_USERNAME: " PAYMENT_SERVICE_USERNAME_VAL
echo ""
read -s -p "Enter value for PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD_VAL
echo ""

echo ""
echo "=== Creating Kubernetes namespace ==="
kubectl apply -f kubernetes/namespace.yaml

echo ""
echo "=== Creating Kubernetes Secret for credentials ==="
kubectl create secret generic mini-java-app-secrets -n mini-java-app \
    --from-literal=DB_USERNAME="$DB_USERNAME_VAL" \
    --from-literal=DB_PASSWORD="$DB_PASSWORD_VAL" \
    --from-literal=REDIS_PASSWORD="$REDIS_PASSWORD_VAL" \
    --from-literal=EXTERNAL_API_KEY="$EXTERNAL_API_KEY_VAL" \
    --from-literal=PAYMENT_SERVICE_USERNAME="$PAYMENT_SERVICE_USERNAME_VAL" \
    --from-literal=PAYMENT_SERVICE_PASSWORD="$PAYMENT_SERVICE_PASSWORD_VAL" \
    --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "=== Deploying application ==="
# Replace placeholders in deployment.yaml
sed -i.bak "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i.bak "s|{{CONFIG_PATH}}|$CONFIG_PATH|g" kubernetes/deployment.yaml
sed -i.bak "s|{{LOG_PATH}}|$LOG_PATH|g" kubernetes/deployment.yaml
sed -i.bak "s|{{LOG_DIR}}|$LOG_DIR|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DB_HOST}}|$DB_HOST|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DB_PORT}}|$DB_PORT|g" kubernetes/deployment.yaml
sed -i.bak "s|{{DB_NAME}}|$DB_NAME|g" kubernetes/deployment.yaml
sed -i.bak "s|{{REDIS_HOST}}|$REDIS_HOST|g" kubernetes/deployment.yaml
sed -i.bak "s|{{REDIS_PORT}}|$REDIS_PORT|g" kubernetes/deployment.yaml
sed -i.bak "s|{{EXTERNAL_API_URL}}|$EXTERNAL_API_URL|g" kubernetes/deployment.yaml
sed -i.bak "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g" kubernetes/deployment.yaml

# Remove .bak files
rm -f kubernetes/deployment.yaml.bak

kubectl apply -f kubernetes/deployment.yaml

echo ""
echo "=== Creating Service ==="
kubectl apply -f kubernetes/service.yaml

echo ""
echo "=== Creating Ingress ==="
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/mini-java-app -n mini-java-app

echo ""
echo "=== Deployment status ==="
kubectl get pods,svc,ingress -n mini-java-app

echo ""
echo "=== Application URL ==="
kubectl get ingress mini-java-app-ingress -n mini-java-app -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

echo ""
echo ""
echo "=== Deployment complete ==="
echo ""
echo "If the deployment fails, rollback with:"
echo "  kubectl rollout undo deployment/mini-java-app -n mini-java-app"
