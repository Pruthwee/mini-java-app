#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="mini-java-app"

echo "--- AWS EKS Deployment Script ---"
read -p "Enter AWS Region (e.g. us-east-1): " AWS_REGION
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
read -p "Enter Full Docker Image URI (e.g. account.dkr.ecr.region.amazonaws.com/repo:tag): " IMAGE_URI

# Prompt for application-specific environment variables
echo "Enter environment variables (press Enter to skip):"
read -p "DATABASE_URL: " DATABASE_URL
read -p "DATABASE_USERNAME: " DATABASE_USERNAME
read -p "DATABASE_PASSWORD: " DATABASE_PASSWORD
read -p "CACHE_REDIS_HOST: " CACHE_REDIS_HOST
read -p "EXTERNAL_API_BASE_URL: " EXTERNAL_API_BASE_URL
read -p "MESSAGING_RABBITMQ_HOST: " MESSAGING_RABBITMQ_HOST

# Update manifests
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{DATABASE_URL}}|$DATABASE_URL|g" kubernetes/deployment.yaml
sed -i "s|{{DATABASE_USERNAME}}|$DATABASE_USERNAME|g" kubernetes/deployment.yaml
sed -i "s|{{DATABASE_PASSWORD}}|$DATABASE_PASSWORD|g" kubernetes/deployment.yaml
sed -i "s|{{CACHE_REDIS_HOST}}|$CACHE_REDIS_HOST|g" kubernetes/deployment.yaml
sed -i "s|{{EXTERNAL_API_BASE_URL}}|$EXTERNAL_API_BASE_URL|g" kubernetes/deployment.yaml
sed -i "s|{{MESSAGING_RABBITMQ_HOST}}|$MESSAGING_RABBITMQ_HOST|g" kubernetes/deployment.yaml

echo "Configuring kubectl..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Cluster connectivity failed"; exit 1; }

echo "Applying manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo "Waiting for rollout..."
kubectl rollout status deployment/$PROJECT_NAME -n $PROJECT_NAME

echo "Verifying resources..."
kubectl get pods,svc,ingress -n $PROJECT_NAME

echo "Deployment complete. Application URL: http://mini-java-app.example.com"
echo "To rollback: kubectl rollout undo deployment/$PROJECT_NAME -n $PROJECT_NAME"
