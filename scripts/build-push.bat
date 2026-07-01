@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=mini-java-app

echo --- Docker Build and Push Script ---
set /p IMAGE_TAG="Enter IMAGE_TAG (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

echo Select Registry:
echo 1) AWS ECR
echo 2) Docker Hub
set /p REGISTRY_CHOICE="Choice [1-2]: "

:: Sanitize image name using PowerShell
for /f "usebackq tokens=*" %%i in (`powershell -Command "'%PROJECT_NAME%'.ToLower().Replace(' ', '-').Trim('-')"`) do set IMAGE_NAME=%%i

if "!REGISTRY_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set ECR_REPO=!IMAGE_NAME!
    
    echo Logging into AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (echo ECR login failed & exit /b 1)
    
    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
) else (
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub Password: "
    
    echo Logging into Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (echo Docker Hub login failed & exit /b 1)
    
    set FULL_IMAGE_NAME=docker.io/!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!
)

echo Building Docker image: !FULL_IMAGE_NAME!...
docker build -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (echo Docker build failed & exit /b 1)

echo Pushing Docker image...
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (echo Docker push failed & exit /b 1)

echo Successfully built and pushed !FULL_IMAGE_NAME!
