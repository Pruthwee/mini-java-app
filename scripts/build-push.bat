@echo off
setlocal enabledelayedexpansion

set "PROJECT_NAME=minijava_bach_con_1439"
set "IMAGE_NAME=%PROJECT_NAME%"
rem Sanitize: lowercase and replace non-alphanumeric with hyphen
for /f "delims=" %%i in ('powershell -command "$env:PROJECT_NAME.ToLower() -replace '[^a-z0-9]+', '-'"') do set "IMAGE_NAME=%%i"
rem Remove leading/trailing hyphens
for /f "delims=" %%i in ('powershell -command "$env:IMAGE_NAME.Trim('-')"') do set "IMAGE_NAME=%%i"

echo === Build and Push Script for %IMAGE_NAME% ===
echo.

echo Select target platform:
echo 1. AWS ECR
echo 2. Docker Hub
echo.
set /p PLATFORM_CHOICE="Enter choice (1 or 2): "

if "%PLATFORM_CHOICE%"=="1" (
    set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
    set /p REGISTRY_URL="Enter ECR registry URL (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com): "
    set /p ECR_REPO="Enter ECR repository name: "

    echo Logging in to AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )

    echo Checking if ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )

    set "REPO=!ECR_REPO!"
)

if "%PLATFORM_CHOICE%"=="2" (
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password: "

    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed
        exit /b 1
    )

    set "REGISTRY_URL=docker.io"
    set "REPO=!DOCKER_USERNAME!/!IMAGE_NAME!"
)

if not "%PLATFORM_CHOICE%"=="1" if not "%PLATFORM_CHOICE%"=="2" (
    echo Invalid choice. Exiting.
    exit /b 1
)

set /p IMAGE_TAG="Enter image tag (or press Enter for 'latest'): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

rem Sanitize image tag
for /f "delims=" %%i in ('powershell -command "$env:IMAGE_TAG -replace '[^a-z0-9-.]+', '-'"') do set "IMAGE_TAG=%%i"
for /f "delims=" %%i in ('powershell -command "$env:IMAGE_TAG.Trim('-')"') do set "IMAGE_TAG=%%i"
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

set "FULL_IMAGE_NAME=!REGISTRY_URL!/!REPO!:!IMAGE_TAG!"

echo.
echo Building Docker image: !FULL_IMAGE_NAME!
echo.

docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
)

echo.
echo Pushing Docker image: !FULL_IMAGE_NAME!
echo.

docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
)

echo.
echo === Build and push complete ===
echo Image: !FULL_IMAGE_NAME!
echo.
echo To deploy, run: scripts\deploy-image.bat
