@echo off
setlocal enabledelayedexpansion

:: =============================================================
:: build-push.bat — Build and push mini-java-app Docker image
:: Supports: AWS ECR | Docker Hub
:: =============================================================

set PROJECT_NAME=mini-java-app
set DOCKERFILE_PATH=Dockerfile
set BUILD_CONTEXT=.

echo =============================================
echo   mini-java-app -- Docker Build ^& Push
echo =============================================
echo.

:: ── Registry selection ──────────────────────────────────────
echo Select container registry:
echo   1) AWS ECR
echo   2) Docker Hub
echo.
set /p REGISTRY_CHOICE="Enter choice [1-2]: "

:: ── Image tag ────────────────────────────────────────────────
set /p RAW_TAG="Enter image tag [default: latest]: "
if "!RAW_TAG!"=="" set RAW_TAG=latest

:: Lowercase and sanitise tag via PowerShell
for /f "delims=" %%T in ('powershell -NoProfile -Command "$t = '!RAW_TAG!'.ToLower() -replace '[^a-z0-9._-]','-'; $t = $t.Trim('-'); if ($t -eq '') { $t = 'latest' }; Write-Output $t"') do set IMAGE_TAG=%%T
echo Using tag: !IMAGE_TAG!
echo.

:: ── Registry-specific setup ──────────────────────────────────
if "!REGISTRY_CHOICE!"=="1" goto ecr_setup
if "!REGISTRY_CHOICE!"=="2" goto dockerhub_setup
echo ERROR: Invalid registry choice. Exiting.
exit /b 1

:ecr_setup
echo --- AWS ECR Configuration ---
set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
set /p AWS_REGION_INPUT="Enter AWS Region [default: us-east-1]: "
if "!AWS_REGION_INPUT!"=="" set AWS_REGION_INPUT=us-east-1
set AWS_REGION=!AWS_REGION_INPUT!

set /p ECR_REPO_INPUT="Enter ECR repository name [default: mini-java-app]: "
if "!ECR_REPO_INPUT!"=="" set ECR_REPO_INPUT=mini-java-app
set ECR_REPO=!ECR_REPO_INPUT!

set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

echo.
echo Logging in to AWS ECR...
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
goto build_image

:dockerhub_setup
echo --- Docker Hub Configuration ---
set /p DOCKER_USERNAME="Enter Docker Hub username: "
set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
set /p DOCKER_REPO_INPUT="Enter Docker Hub repository [default: !DOCKER_USERNAME!/mini-java-app]: "
if "!DOCKER_REPO_INPUT!"=="" set DOCKER_REPO_INPUT=!DOCKER_USERNAME!/mini-java-app
set DOCKER_REPO=!DOCKER_REPO_INPUT!

set FULL_IMAGE_NAME=!DOCKER_REPO!:!IMAGE_TAG!

echo.
echo Logging in to Docker Hub...
echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed.
    exit /b 1
)
goto build_image

:build_image
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! !BUILD_CONTEXT!
if !ERRORLEVEL! neq 0 (
    echo Docker build failed.
    exit /b 1
)
echo Build successful.

echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed.
    exit /b 1
)

echo.
echo =============================================
echo   Image pushed successfully!
echo   !FULL_IMAGE_NAME!
echo =============================================

endlocal
