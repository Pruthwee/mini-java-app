@echo off & setlocal enabledelayedexpansion
rem Builds the container image of minijava-br55-con-1612 and pushes it to AWS ECR or Docker Hub.
rem Run from the repository root: scripts\build-push.bat

set "IMAGE_NAME=minijava-br55-con-1612"

where docker >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo ERROR docker is not installed
    exit /b 1
)

echo === Build and push for !IMAGE_NAME! ===
echo Select the registry
echo   1. AWS ECR
echo   2. Docker Hub
set "REGISTRY_CHOICE="
set /p REGISTRY_CHOICE="Enter 1 or 2: "

if "!REGISTRY_CHOICE!"=="1" (
    where aws >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo ERROR the AWS CLI is not installed
        exit /b 1
    )
    set "AWS_REGION="
    set "AWS_ACCOUNT_ID="
    set "ECR_REPO="
    set /p AWS_REGION="Enter the AWS region, for example us-east-1: "
    set /p AWS_ACCOUNT_ID="Enter the AWS account ID: "
    set /p ECR_REPO="Enter the ECR repository name or press Enter for !IMAGE_NAME!: "
    if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"
    set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
) else if "!REGISTRY_CHOICE!"=="2" (
    set "DOCKER_USERNAME="
    set "DOCKER_PASSWORD="
    set "DOCKER_REPO="
    set /p DOCKER_USERNAME="Enter the Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter the Docker Hub password or access token: "
    set /p DOCKER_REPO="Enter the Docker Hub repository name or press Enter for !IMAGE_NAME!: "
    if "!DOCKER_REPO!"=="" set "DOCKER_REPO=!IMAGE_NAME!"
) else (
    echo ERROR choose 1 or 2
    exit /b 1
)

set "IMAGE_TAG="
set /p IMAGE_TAG="Enter IMAGE_TAG or press Enter for latest: "
if not "!IMAGE_TAG!"=="" (
    powershell -NoProfile -Command "('!IMAGE_TAG!').ToLower() -replace '[^a-z0-9._-]+','-' | ForEach-Object { $_.Trim('-') }" > "%TEMP%\minijava_imgtag.txt"
    set "IMAGE_TAG="
    set /p IMAGE_TAG=<"%TEMP%\minijava_imgtag.txt"
    del "%TEMP%\minijava_imgtag.txt" >nul 2>&1
)
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

if "!REGISTRY_CHOICE!"=="1" (
    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"
) else (
    set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!DOCKER_REPO!:!IMAGE_TAG!"
)

echo Building image !FULL_IMAGE_NAME!
docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
)

if "!REGISTRY_CHOICE!"=="1" (
    echo Logging in to AWS ECR
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo ECR repository creation failed
            exit /b 1
        )
    )
) else (
    echo Logging in to Docker Hub
    echo !DOCKER_PASSWORD!| docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker login failed
        exit /b 1
    )
)

echo Pushing !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
)

echo Push finished for !FULL_IMAGE_NAME!
echo Use this image URI when running scripts\deploy-image.bat
endlocal
