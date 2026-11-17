#!/bin/bash
set -e
set -o pipefail

# Deploy script for minijava-bach-con-1439 to AWS EKS

PROJECT_NAME="minijava_bach_con_1439"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== Deploy to AWS EKS: $IMAGE_NAME ==="
echo ""

# Prompt for AWS details
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter full image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/minijava-bach-con-1439:latest): " IMAGE_URI

echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in region $AWS_REGION"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || exit 1

echo ""
echo "=== Collecting environment variable values ==="
echo ""

# Prompt for non-credential environment variables
read -p "Enter value for APP_PORT (or press Enter to skip): " APP_PORT
read -p "Enter value for HEALTH_PORT (or press Enter to skip): " HEALTH_PORT
read -p "Enter value for CONFIG_PATH (or press Enter to skip): " CONFIG_PATH
read -p "Enter value for LOG_PATH (or press Enter to skip): " LOG_PATH
read -p "Enter value for LOG_DIR (or press Enter to skip): " LOG_DIR
read -p "Enter value for DB_HOST (or press Enter to skip): " DB_HOST
read -p "Enter value for DB_PORT (or press Enter to skip): " DB_PORT
read -p "Enter value for DB_NAME (or press Enter to skip): " DB_NAME
read -p "Enter value for REDIS_HOST (or press Enter to skip): " REDIS_HOST
read -p "Enter value for REDIS_PORT (or press Enter to skip): " REDIS_PORT

echo ""
echo "=== Collecting credential values (will not be echoed) ==="
echo ""

# Prompt for credential environment variables (without echoing)
read -s -p "Enter value for DB_USERNAME: " DB_USERNAME
echo ""
read -s -p "Enter value for DB_PASSWORD: " DB_PASSWORD
echo ""
read -s -p "Enter value for REDIS_PASSWORD: " REDIS_PASSWORD
echo ""
read -s -p "Enter value for EXTERNAL_API_KEY: " EXTERNAL_API_KEY
echo ""
read -s -p "Enter value for PAYMENT_SERVICE_USERNAME: " PAYMENT_SERVICE_USERNAME
echo ""
read -s -p "Enter value for PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD
echo ""
read -s -p "Enter value for SECURITY_JWT_SECRET: " SECURITY_JWT_SECRET
echo ""
read -s -p "Enter value for SECURITY_ADMIN_USERNAME: " SECURITY_ADMIN_USERNAME
echo ""
read -s -p "Enter value for SECURITY_ADMIN_PASSWORD: " SECURITY_ADMIN_PASSWORD
echo ""
read -s -p "Enter value for SECURITY_ENCRYPTION_KEY: " SECURITY_ENCRYPTION_KEY
echo ""
read -s -p "Enter value for MONITORING_USERNAME: " MONITORING_USERNAME
echo ""
read -s -p "Enter value for MONITORING_PASSWORD: " MONITORING_PASSWORD
echo ""
read -s -p "Enter value for MESSAGING_RABBITMQ_USERNAME: " MESSAGING_RABBITMQ_USERNAME
echo ""
read -s -p "Enter value for MESSAGING_RABBITMQ_PASSWORD: " MESSAGING_RABBITMQ_PASSWORD
echo ""

echo ""
echo "=== Applying Kubernetes manifests ==="
echo ""

# Apply namespace
echo "Applying namespace..."
kubectl apply -f kubernetes/namespace.yaml

# Create Secret with all credentials
echo "Creating Kubernetes Secret..."
kubectl create secret generic ${IMAGE_NAME}-secrets -n ${IMAGE_NAME} \
  --from-literal=db-username="$DB_USERNAME" \
  --from-literal=db-password="$DB_PASSWORD" \
  --from-literal=redis-password="$REDIS_PASSWORD" \
  --from-literal=external-api-key="$EXTERNAL_API_KEY" \
  --from-literal=payment-service-username="$PAYMENT_SERVICE_USERNAME" \
  --from-literal=payment-service-password="$PAYMENT_SERVICE_PASSWORD" \
  --from-literal=security-jwt-secret="$SECURITY_JWT_SECRET" \
  --from-literal=security-admin-username="$SECURITY_ADMIN_USERNAME" \
  --from-literal=security-admin-password="$SECURITY_ADMIN_PASSWORD" \
  --from-literal=security-encryption-key="$SECURITY_ENCRYPTION_KEY" \
  --from-literal=monitoring-username="$MONITORING_USERNAME" \
  --from-literal=monitoring-password="$MONITORING_PASSWORD" \
  --from-literal=messaging-rabbitmq-username="$MESSAGING_RABBITMQ_USERNAME" \
  --from-literal=messaging-rabbitmq-password="$MESSAGING_RABBITMQ_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

# Apply deployment with placeholder substitution
echo "Applying deployment..."
sed -i.bak \
  -e "s|{{IMAGE_URI}}|$IMAGE_URI|g" \
  -e "s|{{CONFIG_PATH}}|$CONFIG_PATH|g" \
  -e "s|{{LOG_PATH}}|$LOG_PATH|g" \
  -e "s|{{LOG_DIR}}|$LOG_DIR|g" \
  -e "s|{{DB_HOST}}|$DB_HOST|g" \
  -e "s|{{DB_PORT}}|$DB_PORT|g" \
  -e "s|{{DB_NAME}}|$DB_NAME|g" \
  -e "s|{{REDIS_HOST}}|$REDIS_HOST|g" \
  -e "s|{{REDIS_PORT}}|$REDIS_PORT|g" \
  kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak
kubectl apply -f kubernetes/deployment.yaml

# Apply service
echo "Applying service..."
kubectl apply -f kubernetes/service.yaml

# Apply ingress
echo "Applying ingress..."
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/${IMAGE_NAME} -n ${IMAGE_NAME} --timeout=300s

echo ""
echo "=== Deployment complete ==="
kubectl get pods,svc,ingress -n ${IMAGE_NAME}
echo ""
echo "Application URL: https://${IMAGE_NAME}.example.com"
echo ""
echo "To rollback: kubectl rollout undo deployment/${IMAGE_NAME} -n ${IMAGE_NAME}"
