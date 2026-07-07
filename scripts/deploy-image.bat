@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat  -  Deploy mini-java-app to Azure AKS (Windows)
REM Usage: scripts\deploy-image.bat
REM =============================================================================

set "APP_NAME=mini-java-app"
set "NAMESPACE=mini-java-app"
set "K8S_DIR=kubernetes"

echo ==============================================
echo   Deploy %APP_NAME% to Azure AKS
echo ==============================================

REM ---------- Validate required tools ----------
where az >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: 'az' (Azure CLI) is not installed or not in PATH.
    exit /b 1
)
where kubectl >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: 'kubectl' is not installed or not in PATH.
    exit /b 1
)

REM ---------- Prompt for Azure / AKS details ----------
set /p "RESOURCE_GROUP=Enter Azure Resource Group name: "
if "!RESOURCE_GROUP!"=="" (
    echo ERROR: Resource group cannot be empty.
    exit /b 1
)

set /p "CLUSTER_NAME=Enter AKS Cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: AKS cluster name cannot be empty.
    exit /b 1
)

set /p "IMAGE_URI=Enter full Docker image URI (e.g. myregistry.azurecr.io/mini-java-app:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI cannot be empty.
    exit /b 1
)

echo.
echo --- Application Environment Variables ---
echo Press Enter to skip any variable.
echo.

set /p "DATABASE_URL=DATABASE_URL (e.g. jdbc:mysql://host:3306/db): "
set /p "DATABASE_USERNAME=DATABASE_USERNAME: "
set /p "DATABASE_PASSWORD=DATABASE_PASSWORD: "
set /p "REDIS_HOST=REDIS_HOST: "
set /p "REDIS_PORT=REDIS_PORT [6379]: "
if "!REDIS_PORT!"=="" set "REDIS_PORT=6379"
set /p "REDIS_PASSWORD=REDIS_PASSWORD: "
set /p "EXTERNAL_API_BASE_URL=EXTERNAL_API_BASE_URL: "
set /p "EXTERNAL_API_KEY=EXTERNAL_API_KEY: "
set /p "PAYMENT_SERVICE_URL=PAYMENT_SERVICE_URL: "
set /p "PAYMENT_SERVICE_USERNAME=PAYMENT_SERVICE_USERNAME: "
set /p "PAYMENT_SERVICE_PASSWORD=PAYMENT_SERVICE_PASSWORD: "
set /p "SECURITY_JWT_SECRET=SECURITY_JWT_SECRET: "
set /p "MONITORING_ENDPOINT=MONITORING_ENDPOINT: "
set /p "MONITORING_USERNAME=MONITORING_USERNAME: "
set /p "MONITORING_PASSWORD=MONITORING_PASSWORD: "
set /p "MESSAGING_RABBITMQ_HOST=MESSAGING_RABBITMQ_HOST: "
set /p "MESSAGING_RABBITMQ_PORT=MESSAGING_RABBITMQ_PORT [5672]: "
if "!MESSAGING_RABBITMQ_PORT!"=="" set "MESSAGING_RABBITMQ_PORT=5672"
set /p "MESSAGING_RABBITMQ_USERNAME=MESSAGING_RABBITMQ_USERNAME: "
set /p "MESSAGING_RABBITMQ_PASSWORD=MESSAGING_RABBITMQ_PASSWORD: "

REM ---------- Configure kubectl for AKS ----------
echo.
echo Configuring kubectl for AKS cluster: !CLUSTER_NAME! ...
az aks get-credentials --resource-group !RESOURCE_GROUP! --name !CLUSTER_NAME! --overwrite-existing
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to get AKS credentials.
    exit /b 1
)

echo Verifying cluster connectivity ...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to AKS cluster.
    exit /b 1
)

REM ---------- Create temp working copies of manifests ----------
echo.
echo Updating Kubernetes manifests with provided values ...
set "DEPLOY_DIR=%TEMP%\mini-java-app-deploy"
if exist "!DEPLOY_DIR!" rmdir /s /q "!DEPLOY_DIR!"
mkdir "!DEPLOY_DIR!"
xcopy /s /q "%K8S_DIR%\*" "!DEPLOY_DIR!\" >nul

set "DEPLOYMENT_FILE=!DEPLOY_DIR!\deployment.yaml"

REM Use PowerShell to perform sed-like replacements
powershell -NoProfile -Command ^
  "(Get-Content '!DEPLOYMENT_FILE!') ^
   -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
   -replace '{{DATABASE_URL}}','!DATABASE_URL!' ^
   -replace '{{DATABASE_USERNAME}}','!DATABASE_USERNAME!' ^
   -replace '{{DATABASE_PASSWORD}}','!DATABASE_PASSWORD!' ^
   -replace '{{REDIS_HOST}}','!REDIS_HOST!' ^
   -replace '{{REDIS_PORT}}','!REDIS_PORT!' ^
   -replace '{{REDIS_PASSWORD}}','!REDIS_PASSWORD!' ^
   -replace '{{EXTERNAL_API_BASE_URL}}','!EXTERNAL_API_BASE_URL!' ^
   -replace '{{EXTERNAL_API_KEY}}','!EXTERNAL_API_KEY!' ^
   -replace '{{PAYMENT_SERVICE_URL}}','!PAYMENT_SERVICE_URL!' ^
   -replace '{{PAYMENT_SERVICE_USERNAME}}','!PAYMENT_SERVICE_USERNAME!' ^
   -replace '{{PAYMENT_SERVICE_PASSWORD}}','!PAYMENT_SERVICE_PASSWORD!' ^
   -replace '{{SECURITY_JWT_SECRET}}','!SECURITY_JWT_SECRET!' ^
   -replace '{{MONITORING_ENDPOINT}}','!MONITORING_ENDPOINT!' ^
   -replace '{{MONITORING_USERNAME}}','!MONITORING_USERNAME!' ^
   -replace '{{MONITORING_PASSWORD}}','!MONITORING_PASSWORD!' ^
   -replace '{{MESSAGING_RABBITMQ_HOST}}','!MESSAGING_RABBITMQ_HOST!' ^
   -replace '{{MESSAGING_RABBITMQ_PORT}}','!MESSAGING_RABBITMQ_PORT!' ^
   -replace '{{MESSAGING_RABBITMQ_USERNAME}}','!MESSAGING_RABBITMQ_USERNAME!' ^
   -replace '{{MESSAGING_RABBITMQ_PASSWORD}}','!MESSAGING_RABBITMQ_PASSWORD!' ^
   | Set-Content '!DEPLOYMENT_FILE!'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

REM ---------- Apply manifests in order ----------
echo.
echo Applying Kubernetes manifests ...

echo   [1/4] Applying namespace ...
kubectl apply -f "!DEPLOY_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment ...
kubectl apply -f "!DEPLOY_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service ...
kubectl apply -f "!DEPLOY_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress ...
kubectl apply -f "!DEPLOY_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

REM ---------- Wait for rollout ----------
echo.
echo Waiting for deployment rollout ...
kubectl rollout status deployment/%APP_NAME% -n %NAMESPACE% --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Rollback command: kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%
    exit /b 1
)

REM ---------- Verify resources ----------
echo.
echo Verifying deployed resources ...
kubectl get pods,svc,ingress -n %NAMESPACE%

echo.
echo ==============================================
echo   DEPLOYMENT COMPLETE
echo   Application : %APP_NAME%
echo   Namespace   : %NAMESPACE%
echo   Image       : !IMAGE_URI!
echo   Run the following to get the ingress IP:
echo     kubectl get ingress -n %NAMESPACE%
echo ==============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%

REM ---------- Cleanup temp dir ----------
rmdir /s /q "!DEPLOY_DIR!"

endlocal
