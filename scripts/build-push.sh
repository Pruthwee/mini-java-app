#!/bin/bash
set -e

echo "=== Build and Push Script for mini-java-app ==="

# Project name
PROJECT_NAME="mini-java-app"

# Ask for target platform
echo ""
echo "Select target platform:"
echo "1. AWS ECR"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " PLATFORM_CHOICE

if [ "$PLATFORM_CHOICE" = "1" ]; then
    # AWS ECR
    read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
    read -p "Enter AWS account ID: " AWS_ACCOUNT_ID
    REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    ECR_REPO="${PROJECT_NAME}"
    FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}"
    
    read -p "Enter image tag (press Enter for 'latest'): " IMAGE_TAG
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="latest"
    fi
    
    echo ""
    echo "=== Logging in to Amazon ECR ==="
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
    
    echo ""
    echo "=== Creating ECR repository if it doesn't exist ==="
    aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
    
    TAGGED_IMAGE="${FULL_IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "=== Building Docker image ==="
    docker build -f Dockerfile -t "$TAGGED_IMAGE" .
    
    echo ""
    echo "=== Pushing Docker image to ECR ==="
    docker push "$TAGGED_IMAGE"
    
    echo ""
    echo "=== Build and push complete ==="
    echo "Image: $TAGGED_IMAGE"
    echo "Use this image URI for deployment."

elif [ "$PLATFORM_CHOICE" = "2" ]; then
    # Docker Hub
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -s -p "Enter Docker Hub password: " DOCKER_PASSWORD
    echo ""
    FULL_IMAGE_NAME="docker.io/${DOCKER_USERNAME}/${PROJECT_NAME}"
    
    read -p "Enter image tag (press Enter for 'latest'): " IMAGE_TAG
    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="latest"
    fi
    
    echo ""
    echo "=== Logging in to Docker Hub ==="
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    TAGGED_IMAGE="${FULL_IMAGE_NAME}:${IMAGE_TAG}"
    
    echo ""
    echo "=== Building Docker image ==="
    docker build -f Dockerfile -t "$TAGGED_IMAGE" .
    
    echo ""
    echo "=== Pushing Docker image to Docker Hub ==="
    docker push "$TAGGED_IMAGE"
    
    echo ""
    echo "=== Build and push complete ==="
    echo "Image: $TAGGED_IMAGE"
    echo "Use this image URI for deployment."

else
    echo "Invalid choice. Exiting."
    exit 1
fi
