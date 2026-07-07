#!/bin/bash
# =============================================================================
# build-push.sh  –  Build and push Docker image for mini-java-app
# Usage: ./scripts/build-push.sh
# =============================================================================
set -e
set -o pipefail

PROJECT_NAME="mini-java-app"

echo "=============================================="
echo "  Docker Build & Push – ${PROJECT_NAME}"
echo "=============================================="

# ---------- Sanitize image name ----------
IMAGE_NAME=$(echo "${PROJECT_NAME}" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# ---------- Prompt for image tag ----------
read -rp "Enter image tag [latest]: " IMAGE_TAG_INPUT
IMAGE_TAG=$(echo "${IMAGE_TAG_INPUT}" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
if [ -z "${IMAGE_TAG}" ]; then
  IMAGE_TAG="latest"
fi

echo ""
echo "Select container registry:"
echo "  1) Azure Container Registry (ACR)"
echo "  2) Docker Hub"
read -rp "Enter choice [1]: " REGISTRY_CHOICE
REGISTRY_CHOICE="${REGISTRY_CHOICE:-1}"

case "${REGISTRY_CHOICE}" in
  1)
    # ---- Azure ACR ----
    read -rp "Enter ACR name (e.g. myregistry): " ACR_NAME
    if [ -z "${ACR_NAME}" ]; then
      echo "ERROR: ACR name cannot be empty." >&2
      exit 1
    fi
    REGISTRY="${ACR_NAME}.azurecr.io"
    FULL_IMAGE_NAME="${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

    echo ""
    echo "Logging in to Azure ACR: ${ACR_NAME} ..."
    az acr login --name "${ACR_NAME}"
    ;;
  2)
    # ---- Docker Hub ----
    read -rp "Enter Docker Hub username: " DOCKER_USERNAME
    read -rsp "Enter Docker Hub password/token: " DOCKER_PASSWORD
    echo ""
    if [ -z "${DOCKER_USERNAME}" ] || [ -z "${DOCKER_PASSWORD}" ]; then
      echo "ERROR: Docker Hub credentials cannot be empty." >&2
      exit 1
    fi
    REGISTRY="docker.io"
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

    echo ""
    echo "Logging in to Docker Hub ..."
    echo "${DOCKER_PASSWORD}" | docker login --username "${DOCKER_USERNAME}" --password-stdin
    ;;
  *)
    echo "ERROR: Invalid choice '${REGISTRY_CHOICE}'. Please enter 1 or 2." >&2
    exit 1
    ;;
esac

echo ""
echo "Building Docker image: ${FULL_IMAGE_NAME}"
echo "Build context: . (repository root)"
docker build -f Dockerfile -t "${FULL_IMAGE_NAME}" .

echo ""
echo "Pushing image: ${FULL_IMAGE_NAME} ..."
docker push "${FULL_IMAGE_NAME}"

echo ""
echo "=============================================="
echo "  SUCCESS"
echo "  Image pushed: ${FULL_IMAGE_NAME}"
echo "=============================================="
