@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem deploy-image.bat — Deploy mini-java-app to AWS EKS
rem ============================================================

set APP_NAME=mini-java-app
set NAMESPACE=mini-java-app
set SCRIPT_DIR=%~dp0
set MANIFESTS_DIR=%SCRIPT_DIR%..\kubernetes

echo ============================================
echo   Deploy to AWS EKS: %APP_NAME%
echo ============================================

rem Prompt for AWS region
set /p AWS_REGION="Enter AWS region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo AWS region is required. Exiting.
    exit /b 1
)

rem Prompt for EKS cluster name
set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo EKS cluster name is required. Exiting.
    exit /b 1
)

rem Prompt for Docker image URI
set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): "
if "!IMAGE_URI!"=="" (
    echo Docker image URI is required. Exiting.
    exit /b 1
)

rem Prompt for application-specific environment variables
echo.
echo --- Application Environment Variables ---

set /p DB_HOST="Enter DB_HOST (database host, default: localhost): "
if "!DB_HOST!"=="" set DB_HOST=localhost

set /p DB_PORT="Enter DB_PORT (database port, default: 3306): "
if "!DB_PORT!"=="" set DB_PORT=3306

set /p DB_NAME="Enter DB_NAME (database name, default: mini_app_db): "
if "!DB_NAME!"=="" set DB_NAME=mini_app_db

set /p DB_USERNAME="Enter DB_USERNAME (database username, default: root): "
if "!DB_USERNAME!"=="" set DB_USERNAME=root

set /p DB_PASSWORD="Enter DB_PASSWORD (database password): "
if "!DB_PASSWORD!"=="" set DB_PASSWORD=

set /p REDIS_HOST="Enter REDIS_HOST (Redis host): "
if "!REDIS_HOST!"=="" set REDIS_HOST=redis-service.default.svc.cluster.local

set /p REDIS_PORT="Enter REDIS_PORT (Redis port, default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p EXTERNAL_API_URL="Enter EXTERNAL_API_URL (external API base URL): "
if "!EXTERNAL_API_URL!"=="" set EXTERNAL_API_URL=http://api.example.com:8080/v1

set /p PAYMENT_SERVICE_URL="Enter PAYMENT_SERVICE_URL (payment service URL): "
if "!PAYMENT_SERVICE_URL!"=="" set PAYMENT_SERVICE_URL=https://payment.internal.company.com/process

rem Configure kubectl for EKS
echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in !AWS_REGION!...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo Failed to configure kubectl. Exiting.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Failed to connect to EKS cluster. Exiting.
    exit /b 1
)

rem Update deployment.yaml with actual values using PowerShell
echo.
echo Updating Kubernetes manifests with deployment values...

set DEPLOY_YAML=%MANIFESTS_DIR%\deployment.yaml

powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{DB_HOST}}', '!DB_HOST!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{DB_PORT}}', '!DB_PORT!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{DB_NAME}}', '!DB_NAME!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{DB_USERNAME}}', '!DB_USERNAME!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{EXTERNAL_API_URL}}', '!EXTERNAL_API_URL!' | Set-Content '%DEPLOY_YAML%'"
powershell -Command "(Get-Content '%DEPLOY_YAML%') -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Set-Content '%DEPLOY_YAML%'"

rem Apply Kubernetes manifests in order
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f "%MANIFESTS_DIR%\namespace.yaml"
if !ERRORLEVEL! neq 0 (
    echo Failed to apply namespace. Exiting.
    exit /b 1
)

echo   [2/4] Applying deployment...
kubectl apply -f "%MANIFESTS_DIR%\deployment.yaml"
if !ERRORLEVEL! neq 0 (
    echo Failed to apply deployment. Exiting.
    exit /b 1
)

echo   [3/4] Applying service...
kubectl apply -f "%MANIFESTS_DIR%\service.yaml"
if !ERRORLEVEL! neq 0 (
    echo Failed to apply service. Exiting.
    exit /b 1
)

echo   [4/4] Applying ingress...
kubectl apply -f "%MANIFESTS_DIR%\ingress.yaml"
if !ERRORLEVEL! neq 0 (
    echo Failed to apply ingress. Exiting.
    exit /b 1
)

rem Wait for rollout
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/%APP_NAME% -n %NAMESPACE% --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo Deployment rollout failed. Check pod logs.
    echo Rollback command: kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%
    exit /b 1
)

rem Verify resources
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n %NAMESPACE%

echo.
echo ============================================
echo   Deployment complete!
echo   Application: %APP_NAME%
echo   Namespace:   %NAMESPACE%
echo   Image:       !IMAGE_URI!
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%

endlocal
