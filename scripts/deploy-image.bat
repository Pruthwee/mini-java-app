@echo off
setlocal enabledelayedexpansion

:: =============================================================
:: deploy-image.bat — Deploy mini-java-app to AWS EKS (Windows)
:: =============================================================

set NAMESPACE=mini-java-app
set APP_NAME=mini-java-app
set K8S_DIR=kubernetes

echo =============================================
echo   mini-java-app -- Deploy to AWS EKS
echo =============================================
echo.

:: ── AWS / EKS configuration ──────────────────────────────────
set /p AWS_REGION_INPUT="Enter AWS Region [default: us-east-1]: "
if "!AWS_REGION_INPUT!"=="" set AWS_REGION_INPUT=us-east-1
set AWS_REGION=!AWS_REGION_INPUT!

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS cluster name is required.
    exit /b 1
)

:: ── Docker image URI ─────────────────────────────────────────
set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

:: ── Application environment variables ────────────────────────
echo.
echo --- Application Environment Variables ---
echo Press Enter to skip any variable.
echo.

set /p DB_HOST="Enter DB_HOST (database host) [or Enter to skip]: "
set /p DB_PORT_INPUT="Enter DB_PORT [default: 3306]: "
if "!DB_PORT_INPUT!"=="" set DB_PORT_INPUT=3306
set DB_PORT=!DB_PORT_INPUT!

set /p DB_NAME_INPUT="Enter DB_NAME [default: mini_app_db]: "
if "!DB_NAME_INPUT!"=="" set DB_NAME_INPUT=mini_app_db
set DB_NAME=!DB_NAME_INPUT!

set /p DB_USERNAME_INPUT="Enter DB_USERNAME [default: root]: "
if "!DB_USERNAME_INPUT!"=="" set DB_USERNAME_INPUT=root
set DB_USERNAME=!DB_USERNAME_INPUT!

set /p DB_PASSWORD="Enter DB_PASSWORD [or Enter to skip]: "

set /p REDIS_HOST_INPUT="Enter REDIS_HOST [default: redis.default.svc.cluster.local]: "
if "!REDIS_HOST_INPUT!"=="" set REDIS_HOST_INPUT=redis.default.svc.cluster.local
set REDIS_HOST=!REDIS_HOST_INPUT!

set /p REDIS_PORT_INPUT="Enter REDIS_PORT [default: 6379]: "
if "!REDIS_PORT_INPUT!"=="" set REDIS_PORT_INPUT=6379
set REDIS_PORT=!REDIS_PORT_INPUT!

set /p EXTERNAL_API_BASE_URL="Enter EXTERNAL_API_BASE_URL [or Enter to skip]: "
set /p EXTERNAL_API_KEY="Enter EXTERNAL_API_KEY [or Enter to skip]: "

set /p PAYMENT_SERVICE_URL="Enter PAYMENT_SERVICE_URL [or Enter to skip]: "
set /p PAYMENT_SERVICE_USERNAME="Enter PAYMENT_SERVICE_USERNAME [or Enter to skip]: "
set /p PAYMENT_SERVICE_PASSWORD="Enter PAYMENT_SERVICE_PASSWORD [or Enter to skip]: "

set /p SECURITY_JWT_SECRET="Enter SECURITY_JWT_SECRET [or Enter to skip]: "
set /p SECURITY_ADMIN_USERNAME_INPUT="Enter SECURITY_ADMIN_USERNAME [default: admin]: "
if "!SECURITY_ADMIN_USERNAME_INPUT!"=="" set SECURITY_ADMIN_USERNAME_INPUT=admin
set SECURITY_ADMIN_USERNAME=!SECURITY_ADMIN_USERNAME_INPUT!
set /p SECURITY_ADMIN_PASSWORD="Enter SECURITY_ADMIN_PASSWORD [or Enter to skip]: "
set /p SECURITY_ENCRYPTION_KEY="Enter SECURITY_ENCRYPTION_KEY [or Enter to skip]: "

set /p MONITORING_ENDPOINT="Enter MONITORING_ENDPOINT [or Enter to skip]: "
set /p MONITORING_USERNAME="Enter MONITORING_USERNAME [or Enter to skip]: "
set /p MONITORING_PASSWORD="Enter MONITORING_PASSWORD [or Enter to skip]: "

set /p MESSAGING_RABBITMQ_HOST="Enter MESSAGING_RABBITMQ_HOST [or Enter to skip]: "
set /p MESSAGING_RABBITMQ_PORT_INPUT="Enter MESSAGING_RABBITMQ_PORT [default: 5672]: "
if "!MESSAGING_RABBITMQ_PORT_INPUT!"=="" set MESSAGING_RABBITMQ_PORT_INPUT=5672
set MESSAGING_RABBITMQ_PORT=!MESSAGING_RABBITMQ_PORT_INPUT!
set /p MESSAGING_RABBITMQ_USERNAME="Enter MESSAGING_RABBITMQ_USERNAME [or Enter to skip]: "
set /p MESSAGING_RABBITMQ_PASSWORD="Enter MESSAGING_RABBITMQ_PASSWORD [or Enter to skip]: "

:: ── Configure kubectl ─────────────────────────────────────────
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
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

:: ── Patch manifests via PowerShell ───────────────────────────
echo.
echo Updating Kubernetes manifests with provided values...

set DEPLOY_FILE=!K8S_DIR!\deployment.yaml

powershell -NoProfile -Command ^
  "(Get-Content '!DEPLOY_FILE!') ^
   -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
   -replace '{{DB_HOST}}','!DB_HOST!' ^
   -replace '{{DB_PORT}}','!DB_PORT!' ^
   -replace '{{DB_NAME}}','!DB_NAME!' ^
   -replace '{{DB_USERNAME}}','!DB_USERNAME!' ^
   -replace '{{DB_PASSWORD}}','!DB_PASSWORD!' ^
   -replace '{{REDIS_HOST}}','!REDIS_HOST!' ^
   -replace '{{REDIS_PORT}}','!REDIS_PORT!' ^
   -replace '{{EXTERNAL_API_BASE_URL}}','!EXTERNAL_API_BASE_URL!' ^
   -replace '{{EXTERNAL_API_KEY}}','!EXTERNAL_API_KEY!' ^
   -replace '{{PAYMENT_SERVICE_URL}}','!PAYMENT_SERVICE_URL!' ^
   -replace '{{PAYMENT_SERVICE_USERNAME}}','!PAYMENT_SERVICE_USERNAME!' ^
   -replace '{{PAYMENT_SERVICE_PASSWORD}}','!PAYMENT_SERVICE_PASSWORD!' ^
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
   | Set-Content '!DEPLOY_FILE!'"

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

:: ── Apply manifests ───────────────────────────────────────────
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f !K8S_DIR!\namespace.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment...
kubectl apply -f !K8S_DIR!\deployment.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service...
kubectl apply -f !K8S_DIR!\service.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress...
kubectl apply -f !K8S_DIR!\ingress.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

:: ── Wait for rollout ──────────────────────────────────────────
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
echo =============================================
echo   Deployment complete!
echo   Check ingress for the application URL.
echo   Health check: /actuator/health
echo =============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
