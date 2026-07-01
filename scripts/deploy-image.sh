#!/bin/bash
set -e
set -o pipefail

echo "=========================================="
echo "AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Project configuration
PROJECT_NAME="mini-java-app"
NAMESPACE="mini-java-app"

echo "Project: $PROJECT_NAME"
echo "Namespace: $NAMESPACE"
echo ""

# Prompt for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "Error: AWS region is required"
    exit 1
fi

# Prompt for EKS cluster name
read -p "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Error: EKS cluster name is required"
    exit 1
fi

# Prompt for Docker image URI
echo ""
echo "Enter the full Docker image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest)"
read -p "Image URI: " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Error: Image URI is required"
    exit 1
fi

echo ""
echo "=== Collecting Environment Variables ==="
echo "Enter values for application environment variables (press Enter to skip optional ones)"
echo ""

# Database configuration
read -p "DATABASE_URL (e.g., jdbc:mysql://db-host:3306/mini_app_db): " DATABASE_URL
read -p "DB_USERNAME: " DB_USERNAME
read -sp "DB_PASSWORD: " DB_PASSWORD
echo ""

# Redis configuration
read -p "REDIS_HOST (e.g., redis.example.com): " REDIS_HOST
read -sp "REDIS_PASSWORD (optional): " REDIS_PASSWORD
echo ""

# External API configuration
read -p "EXTERNAL_API_URL (optional): " EXTERNAL_API_URL
read -p "EXTERNAL_API_KEY (optional): " EXTERNAL_API_KEY

# Payment service configuration
read -p "PAYMENT_SERVICE_URL (optional): " PAYMENT_SERVICE_URL
read -p "PAYMENT_SERVICE_USERNAME (optional): " PAYMENT_SERVICE_USERNAME
read -sp "PAYMENT_SERVICE_PASSWORD (optional): " PAYMENT_SERVICE_PASSWORD
echo ""

# S3 configuration
read -p "CONFIG_S3_BUCKET (e.g., app-config-bucket): " CONFIG_S3_BUCKET
read -p "LOG_S3_BUCKET (e.g., app-logs-bucket): " LOG_S3_BUCKET
read -p "UPLOAD_S3_BUCKET (e.g., app-uploads-bucket): " UPLOAD_S3_BUCKET

# Security configuration
read -sp "JWT_SECRET: " JWT_SECRET
echo ""
read -p "ADMIN_USERNAME: " ADMIN_USERNAME
read -sp "ADMIN_PASSWORD: " ADMIN_PASSWORD
echo ""
read -sp "ENCRYPTION_KEY: " ENCRYPTION_KEY
echo ""

# Monitoring configuration
read -p "MONITORING_ENDPOINT (optional): " MONITORING_ENDPOINT
read -p "MONITORING_USERNAME (optional): " MONITORING_USERNAME
read -sp "MONITORING_PASSWORD (optional): " MONITORING_PASSWORD
echo ""

# RabbitMQ configuration
read -p "RABBITMQ_HOST (optional): " RABBITMQ_HOST
read -p "RABBITMQ_USERNAME (optional): " RABBITMQ_USERNAME
read -sp "RABBITMQ_PASSWORD (optional): " RABBITMQ_PASSWORD
echo ""

echo ""
echo "=== Configuring kubectl for EKS ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

if [ $? -ne 0 ]; then
    echo "Error: Failed to configure kubectl for EKS cluster"
    exit 1
fi

echo "kubectl configured successfully"

echo ""
echo "=== Verifying cluster connectivity ==="
kubectl cluster-info || {
    echo "Error: Cannot connect to Kubernetes cluster"
    exit 1
}

echo ""
echo "=== Updating Kubernetes manifests ==="

# Create temporary directory for modified manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* "$TEMP_DIR/"

# Update IMAGE_URI placeholder
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"

# Update environment variable placeholders
[ -n "$DATABASE_URL" ] && sed -i "s|{{DATABASE_URL}}|$DATABASE_URL|g" "$TEMP_DIR/deployment.yaml"
[ -n "$DB_USERNAME" ] && sed -i "s|{{DB_USERNAME}}|$DB_USERNAME|g" "$TEMP_DIR/deployment.yaml"
[ -n "$DB_PASSWORD" ] && sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
[ -n "$REDIS_HOST" ] && sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
[ -n "$REDIS_PASSWORD" ] && sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
[ -n "$EXTERNAL_API_URL" ] && sed -i "s|{{EXTERNAL_API_URL}}|$EXTERNAL_API_URL|g" "$TEMP_DIR/deployment.yaml"
[ -n "$EXTERNAL_API_KEY" ] && sed -i "s|{{EXTERNAL_API_KEY}}|$EXTERNAL_API_KEY|g" "$TEMP_DIR/deployment.yaml"
[ -n "$PAYMENT_SERVICE_URL" ] && sed -i "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g" "$TEMP_DIR/deployment.yaml"
[ -n "$PAYMENT_SERVICE_USERNAME" ] && sed -i "s|{{PAYMENT_SERVICE_USERNAME}}|$PAYMENT_SERVICE_USERNAME|g" "$TEMP_DIR/deployment.yaml"
[ -n "$PAYMENT_SERVICE_PASSWORD" ] && sed -i "s|{{PAYMENT_SERVICE_PASSWORD}}|$PAYMENT_SERVICE_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
[ -n "$CONFIG_S3_BUCKET" ] && sed -i "s|{{CONFIG_S3_BUCKET}}|$CONFIG_S3_BUCKET|g" "$TEMP_DIR/deployment.yaml"
[ -n "$LOG_S3_BUCKET" ] && sed -i "s|{{LOG_S3_BUCKET}}|$LOG_S3_BUCKET|g" "$TEMP_DIR/deployment.yaml"
[ -n "$UPLOAD_S3_BUCKET" ] && sed -i "s|{{UPLOAD_S3_BUCKET}}|$UPLOAD_S3_BUCKET|g" "$TEMP_DIR/deployment.yaml"
[ -n "$JWT_SECRET" ] && sed -i "s|{{JWT_SECRET}}|$JWT_SECRET|g" "$TEMP_DIR/deployment.yaml"
[ -n "$ADMIN_USERNAME" ] && sed -i "s|{{ADMIN_USERNAME}}|$ADMIN_USERNAME|g" "$TEMP_DIR/deployment.yaml"
[ -n "$ADMIN_PASSWORD" ] && sed -i "s|{{ADMIN_PASSWORD}}|$ADMIN_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
[ -n "$ENCRYPTION_KEY" ] && sed -i "s|{{ENCRYPTION_KEY}}|$ENCRYPTION_KEY|g" "$TEMP_DIR/deployment.yaml"
[ -n "$MONITORING_ENDPOINT" ] && sed -i "s|{{MONITORING_ENDPOINT}}|$MONITORING_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
[ -n "$MONITORING_USERNAME" ] && sed -i "s|{{MONITORING_USERNAME}}|$MONITORING_USERNAME|g" "$TEMP_DIR/deployment.yaml"
[ -n "$MONITORING_PASSWORD" ] && sed -i "s|{{MONITORING_PASSWORD}}|$MONITORING_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
[ -n "$RABBITMQ_HOST" ] && sed -i "s|{{RABBITMQ_HOST}}|$RABBITMQ_HOST|g" "$TEMP_DIR/deployment.yaml"
[ -n "$RABBITMQ_USERNAME" ] && sed -i "s|{{RABBITMQ_USERNAME}}|$RABBITMQ_USERNAME|g" "$TEMP_DIR/deployment.yaml"
[ -n "$RABBITMQ_PASSWORD" ] && sed -i "s|{{RABBITMQ_PASSWORD}}|$RABBITMQ_PASSWORD|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated successfully"

echo ""
echo "=== Deploying to AWS EKS ==="

# Apply namespace
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

# Apply deployment
echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

# Apply service
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

# Apply ingress
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/$PROJECT_NAME -n $NAMESPACE --timeout=5m

if [ $? -ne 0 ]; then
    echo "Warning: Deployment rollout did not complete within timeout"
    echo "Check deployment status with: kubectl get pods -n $NAMESPACE"
fi

echo ""
echo "=== Verifying deployment ==="
kubectl get pods,svc,ingress -n $NAMESPACE

echo ""
echo "=== Getting Application URL ==="
INGRESS_HOST=$(kubectl get ingress $PROJECT_NAME-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")

echo ""
echo "=========================================="
echo "Deployment Completed Successfully!"
echo "=========================================="
echo "Namespace: $NAMESPACE"
echo "Image: $IMAGE_URI"
echo ""
if [ "$INGRESS_HOST" != "pending" ]; then
    echo "Application URL: http://$INGRESS_HOST"
else
    echo "Ingress is being provisioned. Check status with:"
    echo "kubectl get ingress -n $NAMESPACE"
fi
echo ""
echo "Useful commands:"
echo "  View pods: kubectl get pods -n $NAMESPACE"
echo "  View logs: kubectl logs -f deployment/$PROJECT_NAME -n $NAMESPACE"
echo "  Describe deployment: kubectl describe deployment $PROJECT_NAME -n $NAMESPACE"
echo "  Scale deployment: kubectl scale deployment $PROJECT_NAME --replicas=3 -n $NAMESPACE"
echo ""
echo "To rollback deployment:"
echo "  kubectl rollout undo deployment/$PROJECT_NAME -n $NAMESPACE"
echo "=========================================="

# Cleanup temporary directory
rm -rf "$TEMP_DIR"
