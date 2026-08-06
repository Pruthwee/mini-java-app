@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM build-push.bat - Build and push Docker image for mini-java-app
REM Supports: AWS ECR and Docker Hub
REM =============================================================================

set PROJECT_NAME=mini-java-app
set IMAGE_NAME=mini-java-app

echo ==============================================
echo   Build ^& Push Docker Image
echo   Project: %PROJECT_NAME%
echo ==============================================
echo.

REM Prompt for image tag
set /p RAW_TAG="Enter image tag (press Enter for 'latest'): "
if "!RAW_TAG!"=="" (
    set IMAGE_TAG=latest
) else (
    set IMAGE_TAG=!RAW_TAG!
)
echo Using tag: !IMAGE_TAG!
echo.

REM Registry selection
echo Select container registry:
echo   1. AWS ECR
echo   2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "
echo.

if "!REGISTRY_CHOICE!"=="1" goto ECR_SETUP
if "!REGISTRY_CHOICE!"=="2" goto DOCKERHUB_SETUP
echo Invalid choice. Exiting.
exit /b 1

:ECR_SETUP
set /p AWS_REGION="Enter AWS region (e.g. us-east-1): "
set /p AWS_ACCOUNT_ID="Enter AWS account ID: "
set /p ECR_REPO_INPUT="Enter ECR repository name (default: !IMAGE_NAME!): "
if "!ECR_REPO_INPUT!"=="" (
    set ECR_REPO=!IMAGE_NAME!
) else (
    set ECR_REPO=!ECR_REPO_INPUT!
)

set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

echo Authenticating with AWS ECR...
aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
if !ERRORLEVEL! neq 0 (
    echo ECR login failed.
    exit /b 1
)

echo Ensuring ECR repository exists...
aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository...
    aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ECR repository.
        exit /b 1
    )
)
goto BUILD

:DOCKERHUB_SETUP
set /p DOCKER_USERNAME="Enter Docker Hub username: "
set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
set /p DOCKER_REPO_INPUT="Enter Docker Hub repository name (default: !IMAGE_NAME!): "
if "!DOCKER_REPO_INPUT!"=="" (
    set DOCKER_REPO=!IMAGE_NAME!
) else (
    set DOCKER_REPO=!DOCKER_REPO_INPUT!
)

set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!DOCKER_REPO!:!IMAGE_TAG!

echo Authenticating with Docker Hub...
echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed.
    exit /b 1
)
goto BUILD

:BUILD
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed.
    exit /b 1
)

echo.
echo Pushing Docker image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed.
    exit /b 1
)

echo.
echo ==============================================
echo   Image pushed successfully!
echo   !FULL_IMAGE_NAME!
echo ==============================================

endlocal
