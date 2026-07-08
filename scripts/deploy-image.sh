#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="mbasic"

echo "--- AWS EKS Deployment Script ---"

# Prompt for AWS and EKS details
read -p "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-us-east-1}
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Cluster name is required"
    exit 1
fi

# Prompt for Docker image URI
read -p "Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/mbasic:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Image URI is required"
    exit 1
fi

# Prompt for application-specific environment variables
echo "Enter values for environment variables (press Enter to skip):"
read -p "DATABASE_URL: " DATABASE_URL
read -p "DATABASE_USERNAME: " DATABASE_USERNAME
read -p "DATABASE_PASSWORD: " DATABASE_PASSWORD
read -p "CACHE_REDIS_HOST: " CACHE_REDIS_HOST
read -p "CACHE_REDIS_PORT: " CACHE_REDIS_PORT
read -p "CACHE_REDIS_PASSWORD: " CACHE_REDIS_PASSWORD
read -p "EXTERNAL_API_BASE_URL: " EXTERNAL_API_BASE_URL
read -p "EXTERNAL_API_KEY: " EXTERNAL_API_KEY
read -p "PAYMENT_SERVICE_URL: " PAYMENT_SERVICE_URL
read -p "PAYMENT_SERVICE_USERNAME: " PAYMENT_SERVICE_USERNAME
read -p "PAYMENT_SERVICE_PASSWORD: " PAYMENT_SERVICE_PASSWORD
read -p "SECURITY_JWT_SECRET: " SECURITY_JWT_SECRET
read -p "SECURITY_ADMIN_USERNAME: " SECURITY_ADMIN_USERNAME
read -p "SECURITY_ADMIN_PASSWORD: " SECURITY_ADMIN_PASSWORD
read -p "MESSAGING_RABBITMQ_HOST: " MESSAGING_RABBITMQ_HOST
read -p "MESSAGING_RABBITMQ_USERNAME: " MESSAGING_RABBITMQ_USERNAME
read -p "MESSAGING_RABBITMQ_PASSWORD: " MESSAGING_RABBITMQ_PASSWORD

# Update manifests with sed using pipe delimiter
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{DATABASE_URL}}|$DATABASE_URL|g" kubernetes/deployment.yaml
sed -i "s|{{DATABASE_USERNAME}}|$DATABASE_USERNAME|g" kubernetes/deployment.yaml
sed -i "s|{{DATABASE_PASSWORD}}|$DATABASE_PASSWORD|g" kubernetes/deployment.yaml
sed -i "s|{{CACHE_REDIS_HOST}}|$CACHE_REDIS_HOST|g" kubernetes/deployment.yaml
sed -i "s|{{CACHE_REDIS_PORT}}|$CACHE_REDIS_PORT|g" kubernetes/deployment.yaml
sed -i "s|{{CACHE_REDIS_PASSWORD}}|$CACHE_REDIS_PASSWORD|g" kubernetes/deployment.yaml
sed -i "s|{{EXTERNAL_API_BASE_URL}}|$EXTERNAL_API_BASE_URL|g" kubernetes/deployment.yaml
sed -i "s|{{EXTERNAL_API_KEY}}|$EXTERNAL_API_KEY|g" kubernetes/deployment.yaml
sed -i "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g" kubernetes/deployment.yaml
sed -i "s|{{PAYMENT_SERVICE_USERNAME}}|$PAYMENT_SERVICE_USERNAME|g" kubernetes/deployment.yaml
sed -i "s|{{PAYMENT_SERVICE_PASSWORD}}|$PAYMENT_SERVICE_PASSWORD|g" kubernetes/deployment.yaml
sed -i "s|{{SECURITY_JWT_SECRET}}|$SECURITY_JWT_SECRET|g" kubernetes/deployment.yaml
sed -i "s|{{SECURITY_ADMIN_USERNAME}}|$SECURITY_ADMIN_USERNAME|g" kubernetes/deployment.yaml
sed -i "s|{{SECURITY_ADMIN_PASSWORD}}|$SECURITY_ADMIN_PASSWORD|g" kubernetes/deployment.yaml
sed -i "s|{{MESSAGING_RABBITMQ_HOST}}|$MESSAGING_RABBITMQ_HOST|g" kubernetes/deployment.yaml
sed -i "s|{{MESSAGING_RABBITMQ_USERNAME}}|$MESSAGING_RABBITMQ_USERNAME|g" kubernetes/deployment.yaml
sed -i "s|{{MESSAGING_RABBITMQ_PASSWORD}}|$MESSAGING_RABBITMQ_PASSWORD|g" kubernetes/deployment.yaml

# Configure kubectl
echo "Configuring kubectl for EKS cluster $CLUSTER_NAME..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

# Verify connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Cluster connectivity failed"; exit 1; }

# Apply manifests in order
echo "Applying Kubernetes manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/$PROJECT_NAME -n $PROJECT_NAME

# Verify resources
echo "Verifying resources..."
kubectl get pods,svc,ingress -n $PROJECT_NAME

echo "Deployment complete!"
echo "Application URL: http://mbasic.example.com"
echo "In case of failure, use: kubectl rollout undo deployment/$PROJECT_NAME -n $PROJECT_NAME"
