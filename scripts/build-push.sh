#!/bin/bash
set -e

PROJECT_NAME="mbasic"

echo "--- Docker Build and Push Script ---"

# Prompt for image tag
read -p "Enter image tag [latest]: " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Prompt for registry
echo "Select registry:"
echo "1) AWS ECR"
echo "2) Docker Hub"
read -p "Choice [1-2]: " REGISTRY_CHOICE

# Sanitize project name for image name
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

if [ "$REGISTRY_CHOICE" == "1" ]; then
    read -p "Enter AWS Region [us-east-1]: " AWS_REGION
    AWS_REGION=${AWS_REGION:-us-east-1}
    read -p "Enter ECR Repository Name [$IMAGE_NAME]: " ECR_REPO
    ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
    
    # AWS ECR Login
    AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    
    echo "Logging into AWS ECR..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $REGISTRY_URL
    
    # Ensure ECR repository exists
    echo "Checking if ECR repository $ECR_REPO exists..."
    aws ecr describe-repositories --repository-names $ECR_REPO --region $AWS_REGION >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION
    
    FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"
else
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    read -s -p "Enter Docker Hub Password: " DOCKER_PASSWORD
    echo ""
    
    echo "Logging into Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    FULL_IMAGE_NAME="docker.io/${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
fi

echo "Building Docker image: $FULL_IMAGE_NAME..."
docker build -t "$FULL_IMAGE_NAME" .

echo "Pushing Docker image to registry..."
docker push "$FULL_IMAGE_NAME"

echo "Successfully built and pushed $FULL_IMAGE_NAME"
