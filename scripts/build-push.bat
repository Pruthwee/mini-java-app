@echo off & setlocal enabledelayedexpansion
rem Builds the mini-java-app image and pushes it to AWS ECR or Docker Hub.
rem Run from the repository root: scripts\build-push.bat

set PROJECT_NAME=mini-java-app
set WORK_DIR=%TEMP%\mini-java-app-build
if not exist "!WORK_DIR!" mkdir "!WORK_DIR!"

rem Sanitized image name: lower case, runs of other characters become one hyphen, no hyphen at either end
powershell -NoProfile -Command "$n = $env:PROJECT_NAME.ToLower(); $n = [regex]::Replace($n,'[^a-z0-9]+','-'); $n.Trim('-')" > "!WORK_DIR!\name.txt"
set IMAGE_NAME=
set /p IMAGE_NAME=<"!WORK_DIR!\name.txt"

echo === Build and push !IMAGE_NAME! ===

where docker >nul 2>&1
if !ERRORLEVEL! neq 0 (
  echo ERROR - docker is not installed or not in PATH
  exit /b 1
)

echo Choose the registry
echo   1. AWS ECR - target platform
echo   2. Docker Hub
set REGISTRY_CHOICE=
set /p REGISTRY_CHOICE="Enter 1 or 2: "

if "!REGISTRY_CHOICE!"=="1" (
  where aws >nul 2>&1
  if !ERRORLEVEL! neq 0 (
    echo ERROR - the AWS CLI is not installed or not in PATH
    exit /b 1
  )
  set AWS_REGION=
  set ECR_REPO=
  set /p AWS_REGION="AWS region, for example us-east-1: "
  if "!AWS_REGION!"=="" (
    echo ERROR - the AWS region is required
    exit /b 1
  )
  set /p ECR_REPO="ECR repository name, empty for !IMAGE_NAME!: "
  if "!ECR_REPO!"=="" set ECR_REPO=!IMAGE_NAME!
  echo Looking up the AWS account...
  set ACCOUNT_ID=
  aws sts get-caller-identity --query Account --output text > "!WORK_DIR!\account.txt"
  if !ERRORLEVEL! neq 0 (
    echo ERROR - could not read the AWS account
    exit /b 1
  )
  set /p ACCOUNT_ID=<"!WORK_DIR!\account.txt"
  set REGISTRY_URL=!ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
  set REPO_PATH=!ECR_REPO!
) else if "!REGISTRY_CHOICE!"=="2" (
  set DOCKER_USERNAME=
  set DOCKER_PASSWORD=
  set /p DOCKER_USERNAME="Docker Hub username: "
  set /p DOCKER_PASSWORD="Docker Hub password or access token: "
  if "!DOCKER_USERNAME!"=="" (
    echo ERROR - the Docker Hub username is required
    exit /b 1
  )
  set REGISTRY_URL=docker.io
  set REPO_PATH=!DOCKER_USERNAME!/!IMAGE_NAME!
) else (
  echo ERROR - choose 1 or 2
  exit /b 1
)

set IMAGE_TAG=
set /p IMAGE_TAG="IMAGE_TAG, empty for latest: "
set SAFE_TAG=
powershell -NoProfile -Command "$t = [regex]::Replace($env:IMAGE_TAG,'[^A-Za-z0-9._-]+','-'); $t = $t.Trim('-','.'); if ($t.Length -eq 0) { $t = 'latest' }; $t" > "!WORK_DIR!\tag.txt"
set /p SAFE_TAG=<"!WORK_DIR!\tag.txt"
if "!SAFE_TAG!"=="" set SAFE_TAG=latest
set IMAGE_TAG=!SAFE_TAG!

set FULL_IMAGE_NAME=!REGISTRY_URL!/!REPO_PATH!
echo Image to build is !FULL_IMAGE_NAME! with the tag !IMAGE_TAG!

echo Step 1 - building the image...
docker build -f Dockerfile -t !FULL_IMAGE_NAME!:!IMAGE_TAG! .
if !ERRORLEVEL! neq 0 (
  echo Docker build failed
  exit /b 1
)

echo Step 2 - logging in to the registry...
if "!REGISTRY_CHOICE!"=="1" (
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
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed
    exit /b 1
  )
)

echo Step 3 - pushing the image...
docker push !FULL_IMAGE_NAME!:!IMAGE_TAG!
if !ERRORLEVEL! neq 0 (
  echo Docker push failed
  exit /b 1
)

echo === Done. Pushed !FULL_IMAGE_NAME! with the tag !IMAGE_TAG! ===
echo Deploy it with scripts\deploy-image.bat using this image URI
rmdir /s /q "!WORK_DIR!" >nul 2>&1
endlocal
exit /b 0
