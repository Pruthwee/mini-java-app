#!/bin/bash
# Builds the mini-java-app image and pushes it to AWS ECR or Docker Hub.
# Run from the repository root: ./scripts/build-push.sh
set -e
set -o pipefail

PROJECT_NAME="mini-java-app"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== Build and push ${IMAGE_NAME} ==="

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR - docker is not installed or not in PATH"
  exit 1
fi

echo "Choose the registry"
echo "  1. AWS ECR (target platform)"
echo "  2. Docker Hub"
read -r -p "Enter 1 or 2: " REGISTRY_CHOICE

case "$REGISTRY_CHOICE" in
  1)
    if ! command -v aws >/dev/null 2>&1; then
      echo "ERROR - the AWS CLI is not installed or not in PATH"
      exit 1
    fi
    read -r -p "AWS region (for example us-east-1): " AWS_REGION
    if [ -z "$AWS_REGION" ]; then
      echo "ERROR - the AWS region is required"
      exit 1
    fi
    read -r -p "ECR repository name [${IMAGE_NAME}]: " ECR_REPO
    ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
    ECR_REPO=$(echo "$ECR_REPO" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9/_-' '-' | sed 's/^-*//;s/-*$//')
    echo "Looking up the AWS account..."
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    REGISTRY_URL="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    REPO_PATH="$ECR_REPO"
    ;;
  2)
    read -r -p "Docker Hub username: " DOCKER_USERNAME
    read -r -s -p "Docker Hub password or access token: " DOCKER_PASSWORD
    echo
    if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_PASSWORD" ]; then
      echo "ERROR - the Docker Hub username and password are required"
      exit 1
    fi
    REGISTRY_URL="docker.io"
    REPO_PATH="$(echo "$DOCKER_USERNAME" | tr '[:upper:]' '[:lower:]')/${IMAGE_NAME}"
    ;;
  *)
    echo "ERROR - choose 1 or 2"
    exit 1
    ;;
esac

read -r -p "IMAGE_TAG [latest]: " IMAGE_TAG
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr -cs 'A-Za-z0-9._-' '-' | sed 's/^[-.]*//;s/[-.]*$//')
IMAGE_TAG=${IMAGE_TAG:-latest}

FULL_IMAGE_NAME="${REGISTRY_URL}/${REPO_PATH}:${IMAGE_TAG}"
echo "Image to build: ${FULL_IMAGE_NAME}"

echo "Step 1 - building the image..."
docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .

echo "Step 2 - logging in to the registry..."
if [ "$REGISTRY_CHOICE" = "1" ]; then
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
  aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
else
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
fi

echo "Step 3 - pushing the image..."
docker push "$FULL_IMAGE_NAME"

echo "=== Done. Pushed ${FULL_IMAGE_NAME} ==="
echo "Deploy it with ./scripts/deploy-image.sh using this image URI"
