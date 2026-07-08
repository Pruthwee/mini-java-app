@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=mbasic

echo --- Docker Build and Push Script ---

set /p IMAGE_TAG="Enter image tag [latest]: "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

echo Select registry:
echo 1) AWS ECR
echo 2) Docker Hub
set /p REGISTRY_CHOICE="Choice [1-2]: "

:: Sanitize project name
set "IMAGE_NAME=%PROJECT_NAME%"
for /f "tokens=*" %%i in ('powershell -Command "'%IMAGE_NAME%'.ToLower().Replace(' ', '-').Trim('-')"') do set IMAGE_NAME=%%i

if "!REGISTRY_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS Region [us-east-1]: "
    if "!AWS_REGION!"=="" set AWS_REGION=us-east-1
    set /p ECR_REPO="Enter ECR Repository Name [%IMAGE_NAME%]: "
    if "!ECR_REPO!"=="" set ECR_REPO=!IMAGE_NAME!
    
    echo Logging into AWS ECR...
    for /f "tokens=*" %%i in ('aws sts get-caller-identity --query Account --output text') do set AWS_ACCOUNT_ID=%%i
    set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
    
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )
    
    echo Checking if ECR repository !ECR_REPO! exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"
) else (
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub Password: "
    
    echo Logging into Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed
        exit /b 1
    )
    
    set "FULL_IMAGE_NAME=docker.io/!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"
)

echo Building Docker image: !FULL_IMAGE_NAME!...
docker build -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
)

echo Pushing Docker image to registry...
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
)

echo Successfully built and pushed !FULL_IMAGE_NAME!
