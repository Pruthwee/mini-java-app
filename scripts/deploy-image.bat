@echo off
setlocal enabledelayedexpansion

:: =============================================================
:: deploy-image.bat  -  Deploy mini-java-app to AWS EKS
:: =============================================================

set APP_NAME=mini-java-app
set NAMESPACE=mini-java-app
set K8S_DIR=kubernetes

echo ==============================================
echo   Deploy %APP_NAME% to AWS EKS
echo ==============================================

:: ── Collect AWS / EKS details ──────────────────────────────────
set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI: "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

:: ── Collect application environment variables ──────────────────
echo.
echo --- Application Environment Variables (press Enter to skip) ---

set /p DB_HOST="Enter DB_HOST: "
set /p DB_PORT="Enter DB_PORT [3306]: "
if "!DB_PORT!"=="" set DB_PORT=3306
set /p DB_NAME="Enter DB_NAME [mini_app_db]: "
if "!DB_NAME!"=="" set DB_NAME=mini_app_db
set /p DB_USERNAME="Enter DB_USERNAME: "
set /p DB_PASSWORD="Enter DB_PASSWORD: "
set /p REDIS_HOST="Enter REDIS_HOST: "
set /p REDIS_PORT="Enter REDIS_PORT [6379]: "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379
set /p EXTERNAL_API_BASE_URL="Enter EXTERNAL_API_BASE_URL: "
set /p EXTERNAL_API_TIMEOUT="Enter EXTERNAL_API_TIMEOUT [30000]: "
if "!EXTERNAL_API_TIMEOUT!"=="" set EXTERNAL_API_TIMEOUT=30000
set /p EXTERNAL_API_KEY="Enter EXTERNAL_API_KEY: "
set /p PAYMENT_SERVICE_URL="Enter PAYMENT_SERVICE_URL: "
set /p PAYMENT_SERVICE_USERNAME="Enter PAYMENT_SERVICE_USERNAME: "
set /p PAYMENT_SERVICE_PASSWORD="Enter PAYMENT_SERVICE_PASSWORD: "
set /p APP_CONFIG_FILE_PATH="Enter APP_CONFIG_FILE_PATH [/mnt/efs/app/config/app.properties]: "
if "!APP_CONFIG_FILE_PATH!"=="" set APP_CONFIG_FILE_PATH=/mnt/efs/app/config/app.properties
set /p APP_LOG_DIR="Enter APP_LOG_DIR [/var/log/mini-app]: "
if "!APP_LOG_DIR!"=="" set APP_LOG_DIR=/var/log/mini-app
set /p APP_LOG_FILE_PATH="Enter APP_LOG_FILE_PATH [/mnt/efs/logs/mini-app.log]: "
if "!APP_LOG_FILE_PATH!"=="" set APP_LOG_FILE_PATH=/mnt/efs/logs/mini-app.log
set /p SECURITY_JWT_SECRET="Enter SECURITY_JWT_SECRET: "
set /p SECURITY_ADMIN_USERNAME="Enter SECURITY_ADMIN_USERNAME [admin]: "
if "!SECURITY_ADMIN_USERNAME!"=="" set SECURITY_ADMIN_USERNAME=admin
set /p SECURITY_ADMIN_PASSWORD="Enter SECURITY_ADMIN_PASSWORD: "
set /p SECURITY_ENCRYPTION_KEY="Enter SECURITY_ENCRYPTION_KEY: "
set /p MONITORING_ENDPOINT="Enter MONITORING_ENDPOINT: "
set /p MONITORING_USERNAME="Enter MONITORING_USERNAME: "
set /p MONITORING_PASSWORD="Enter MONITORING_PASSWORD: "
set /p MESSAGING_RABBITMQ_HOST="Enter MESSAGING_RABBITMQ_HOST: "
set /p MESSAGING_RABBITMQ_PORT="Enter MESSAGING_RABBITMQ_PORT [5672]: "
if "!MESSAGING_RABBITMQ_PORT!"=="" set MESSAGING_RABBITMQ_PORT=5672
set /p MESSAGING_RABBITMQ_USERNAME="Enter MESSAGING_RABBITMQ_USERNAME: "
set /p MESSAGING_RABBITMQ_PASSWORD="Enter MESSAGING_RABBITMQ_PASSWORD: "

:: ── Configure kubectl ──────────────────────────────────────────
echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! ...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to cluster.
    exit /b 1
)

:: ── Patch manifests with PowerShell ───────────────────────────
echo.
echo Updating Kubernetes manifests...

set DEPLOY_YAML=!K8S_DIR!\deployment.yaml

powershell -NoProfile -Command ^
  "(Get-Content '!DEPLOY_YAML!') ^
   -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
   -replace '{{DB_HOST}}','!DB_HOST!' ^
   -replace '{{DB_PORT}}','!DB_PORT!' ^
   -replace '{{DB_NAME}}','!DB_NAME!' ^
   -replace '{{DB_USERNAME}}','!DB_USERNAME!' ^
   -replace '{{DB_PASSWORD}}','!DB_PASSWORD!' ^
   -replace '{{REDIS_HOST}}','!REDIS_HOST!' ^
   -replace '{{REDIS_PORT}}','!REDIS_PORT!' ^
   -replace '{{EXTERNAL_API_BASE_URL}}','!EXTERNAL_API_BASE_URL!' ^
   -replace '{{EXTERNAL_API_TIMEOUT}}','!EXTERNAL_API_TIMEOUT!' ^
   -replace '{{EXTERNAL_API_KEY}}','!EXTERNAL_API_KEY!' ^
   -replace '{{PAYMENT_SERVICE_URL}}','!PAYMENT_SERVICE_URL!' ^
   -replace '{{PAYMENT_SERVICE_USERNAME}}','!PAYMENT_SERVICE_USERNAME!' ^
   -replace '{{PAYMENT_SERVICE_PASSWORD}}','!PAYMENT_SERVICE_PASSWORD!' ^
   -replace '{{APP_CONFIG_FILE_PATH}}','!APP_CONFIG_FILE_PATH!' ^
   -replace '{{APP_LOG_DIR}}','!APP_LOG_DIR!' ^
   -replace '{{APP_LOG_FILE_PATH}}','!APP_LOG_FILE_PATH!' ^
   -replace '{{SECURITY_JWT_SECRET}}','!SECURITY_JWT_SECRET!' ^
   -replace '{{SECURITY_ADMIN_USERNAME}}','!SECURITY_ADMIN_USERNAME!' ^
   -replace '{{SECURITY_ADMIN_PASSWORD}}','!SECURITY_ADMIN_PASSWORD!' ^
   -replace '{{SECURITY_ENCRYPTION_KEY}}','!SECURITY_ENCRYPTION_KEY!' ^
   -replace '{{MONITORING_ENDPOINT}}','!MONITORING_ENDPOINT!' ^
   -replace '{{MONITORING_USERNAME}}','!MONITORING_USERNAME!' ^
   -replace '{{MONITORING_PASSWORD}}','!MONITORING_PASSWORD!' ^
   -replace '{{MESSAGING_RABBITMQ_HOST}}','!MESSAGING_RABBITMQ_HOST!' ^
   -replace '{{MESSAGING_RABBITMQ_PORT}}','!MESSAGING_RABBITMQ_PORT!' ^
   -replace '{{MESSAGING_RABBITMQ_USERNAME}}','!MESSAGING_RABBITMQ_USERNAME!' ^
   -replace '{{MESSAGING_RABBITMQ_PASSWORD}}','!MESSAGING_RABBITMQ_PASSWORD!' ^
   | Set-Content '!DEPLOY_YAML!'"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

:: ── Apply manifests ────────────────────────────────────────────
echo.
echo Applying Kubernetes manifests...
kubectl apply -f !K8S_DIR!\namespace.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

kubectl apply -f !K8S_DIR!\deployment.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

kubectl apply -f !K8S_DIR!\service.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

kubectl apply -f !K8S_DIR!\ingress.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

:: ── Wait for rollout ───────────────────────────────────────────
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Rollback command: kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

:: ── Verify ────────────────────────────────────────────────────
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ==============================================
echo   Deployment complete!
echo   Check ingress for application URL.
echo   Health check: /actuator/health
echo ==============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
