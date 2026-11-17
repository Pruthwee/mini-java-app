@echo off & setlocal enabledelayedexpansion
rem Builds the application image and pushes it to AWS ECR or Docker Hub.
rem Run from the repository root, as scripts\build-push.bat

set "IMAGE_NAME=minijava-s55-con-1433"

echo === Build and push - !IMAGE_NAME! ===

where docker >nul 2>&1
if !ERRORLEVEL! neq 0 (
  echo Error - docker is not installed or not on the PATH
  exit /b 1
)

echo Select the registry
echo   1. AWS ECR
echo   2. Docker Hub
set /p "REGISTRY_CHOICE=Enter 1 or 2: "

if "!REGISTRY_CHOICE!"=="1" (
  where aws >nul 2>&1
  if !ERRORLEVEL! neq 0 (
    echo Error - the AWS CLI is not installed or not on the PATH
    exit /b 1
  )
  set /p "AWS_REGION=Enter the AWS region (for example us-east-1): "
  set /p "AWS_ACCOUNT_ID=Enter the AWS account ID: "
  set /p "ECR_REPO=Enter the ECR repository name (press Enter for !IMAGE_NAME!): "
  if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"
  if "!AWS_REGION!"=="" (
    echo Error - the AWS region is required
    exit /b 1
  )
  if "!AWS_ACCOUNT_ID!"=="" (
    echo Error - the AWS account ID is required
    exit /b 1
  )
  set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
  set "REPO_PATH=!ECR_REPO!"
) else if "!REGISTRY_CHOICE!"=="2" (
  set /p "DOCKER_USERNAME=Enter the Docker Hub username: "
  set /p "DOCKER_PASSWORD=Enter the Docker Hub password or access token: "
  if "!DOCKER_USERNAME!"=="" (
    echo Error - the Docker Hub username is required
    exit /b 1
  )
  if "!DOCKER_PASSWORD!"=="" (
    echo Error - the Docker Hub password is required
    exit /b 1
  )
  set "REGISTRY_URL=docker.io"
  set "REPO_PATH=!DOCKER_USERNAME!/!IMAGE_NAME!"
) else (
  echo Error - enter 1 or 2
  exit /b 1
)

set /p "IMAGE_TAG=Enter IMAGE_TAG (press Enter for latest): "
set "IMAGE_TAG_RAW=!IMAGE_TAG!"
set "TAG_FILE=%TEMP%\minijava_tag_%RANDOM%.txt"
powershell -NoProfile -Command "$t = $env:IMAGE_TAG_RAW.ToLower() -replace '[^a-z0-9._-]+','-'; $t = $t.Trim('-'); Set-Content -Path $env:TAG_FILE -Value $t -NoNewline"
set "IMAGE_TAG="
if exist "!TAG_FILE!" (
  set /p "IMAGE_TAG="<"!TAG_FILE!"
  del "!TAG_FILE!"
)
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

set "FULL_IMAGE_NAME=!REGISTRY_URL!/!REPO_PATH!"
set "FULL_IMAGE_NAME=!FULL_IMAGE_NAME!:!IMAGE_TAG!"
echo Image to build - !FULL_IMAGE_NAME!

echo Building the image...
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
  echo Docker build failed
  exit /b 1
)
echo Build finished.

if "!REGISTRY_CHOICE!"=="1" (
  echo Logging in to AWS ECR...
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
  echo Logging in to Docker Hub...
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed
    exit /b 1
  )
)

echo Pushing !FULL_IMAGE_NAME! ...
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
  echo Docker push failed
  exit /b 1
)

echo Done. Image pushed - !FULL_IMAGE_NAME!
echo Use this image URI when deploy-image.bat asks for it.
endlocal
