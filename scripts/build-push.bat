@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem build-push.bat – Build and push mini-java-app Docker image
rem ============================================================

set PROJECT_NAME=mini-java-app
set DOCKERFILE_PATH=Dockerfile
set IMAGE_NAME=mini-java-app

echo ============================================
echo   mini-java-app - Docker Build ^& Push
echo ============================================
echo.

rem ── Registry selection ──────────────────────────────────────
echo Select container registry:
echo   1. AWS ECR
echo   2. Docker Hub
echo.
set /p REGISTRY_CHOICE="Enter choice [1-2]: "

rem ── Tag ─────────────────────────────────────────────────────
set /p RAW_TAG="Enter image tag (default: latest): "
if "!RAW_TAG!"=="" set RAW_TAG=latest
set IMAGE_TAG=!RAW_TAG!

echo.

rem ── Registry-specific logic ──────────────────────────────────
if "!REGISTRY_CHOICE!"=="1" (
    rem ── AWS ECR ──
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

    echo.
    echo Logging in to AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR login failed.
        exit /b 1
    )

    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo ERROR: Failed to create ECR repository.
            exit /b 1
        )
    )

) else if "!REGISTRY_CHOICE!"=="2" (
    rem ── Docker Hub ──
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    set /p DOCKER_REPO_INPUT="Enter Docker Hub repository (default: !DOCKER_USERNAME!/!IMAGE_NAME!): "
    if "!DOCKER_REPO_INPUT!"=="" (
        set DOCKER_REPO=!DOCKER_USERNAME!/!IMAGE_NAME!
    ) else (
        set DOCKER_REPO=!DOCKER_REPO_INPUT!
    )

    set FULL_IMAGE_NAME=!DOCKER_REPO!:!IMAGE_TAG!

    echo.
    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed.
        exit /b 1
    )

) else (
    echo ERROR: Invalid registry choice. Exiting.
    exit /b 1
)

rem ── Build ────────────────────────────────────────────────────
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

echo.
echo ============================================
echo   Build ^& Push Complete!
echo   Image: !FULL_IMAGE_NAME!
echo ============================================

endlocal
