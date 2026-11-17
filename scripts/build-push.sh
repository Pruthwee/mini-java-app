#!/bin/bash
set -e

# Build and push script for minijava-bach-con-1439
# Builds the Docker image and pushes to AWS ECR

PROJECT_NAME="minijava_bach_con_1439"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== Build and Push Script for $IMAGE_NAME ==="
echo ""

# Prompt for target platform
echo "Select target platform:"
echo "1. AWS ECR"
echo "2. Docker Hub"
echo ""
read -p "Enter choice (1 or 2): " PLATFORM_CHOICE

if [ "$PLATFORM_CHOICE" = "1" ]; then
    # AWS ECR
    read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
    read -p "Enter ECR registry URL (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com): " REGISTRY_URL
    read -p "Enter ECR repository name: " ECR_REPO

    # Login to ECR
    echo "Logging in to AWS ECR..."
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"

    # Create repository if it doesn't exist
    echo "Checking if ECR repository exists..."
    aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"

    REGISTRY_URL="$REGISTRY_URL"
    REPO="$ECR_REPO"

elif [ "$PLATFORM_CHOICE" = "2" ]; then
    # Docker Hub
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -s -p "Enter Docker Hub password: " DOCKER_PASSWORD
    echo ""

    echo "Logging in to Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin

    REGISTRY_URL="docker.io"
    REPO="$DOCKER_USERNAME/$IMAGE_NAME"
else
    echo "Invalid choice. Exiting."
    exit 1
fi

# Prompt for image tag
read -p "Enter image tag (or press Enter for 'latest'): " IMAGE_TAG
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
fi

# Sanitize image tag
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr -cs 'a-z0-9-.' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
fi

# Build full image name
FULL_IMAGE_NAME="$REGISTRY_URL/$REPO:$IMAGE_TAG"

echo ""
echo "Building Docker image: $FULL_IMAGE_NAME"
echo ""

# Build the Docker image
docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .

echo ""
echo "Pushing Docker image: $FULL_IMAGE_NAME"
echo ""

# Push the image
docker push "$FULL_IMAGE_NAME"

echo ""
echo "=== Build and push complete ==="
echo "Image: $FULL_IMAGE_NAME"
echo ""
echo "To deploy, run: scripts/deploy-image.sh"
