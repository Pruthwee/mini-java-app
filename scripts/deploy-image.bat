@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat - Deploy mini-java-app to AWS EKS
REM Usage: scripts\deploy-image.bat   (run from repository root)
REM =============================================================================

set "APP_NAME=mini-java-app"
set "NAMESPACE=mini-java-app"

echo ============================================
echo   mini-java-app - AWS EKS Deployment
echo ============================================
echo.

REM ---------------------------------------------------------------------------
REM Collect deployment parameters
REM ---------------------------------------------------------------------------
set /p "AWS_REGION=Enter AWS region [us-east-1]: "
if "!AWS_REGION!"=="" set "AWS_REGION=us-east-1"

set /p "CLUSTER_NAME=Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS cluster name is required.
    exit /b 1
)

set /p "IMAGE_URI=Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM Optional environment variable overrides
REM ---------------------------------------------------------------------------
echo.
echo --- Optional environment variable overrides (press Enter to skip) ---

set /p "DB_URL=Enter DB_URL [jdbc:mysql://db-host:3306/mini_app_db]: "
if "!DB_URL!"=="" set "DB_URL=jdbc:mysql://db-host:3306/mini_app_db"

set /p "REDIS_HOST=Enter REDIS_HOST [redis-service]: "
if "!REDIS_HOST!"=="" set "REDIS_HOST=redis-service"

set /p "REDIS_PORT=Enter REDIS_PORT [6379]: "
if "!REDIS_PORT!"=="" set "REDIS_PORT=6379"

set /p "EXTERNAL_API_URL=Enter EXTERNAL_API_URL: "
set /p "PAYMENT_SERVICE_URL=Enter PAYMENT_SERVICE_URL: "

set /p "APP_ENV=Enter APP_ENV [production]: "
if "!APP_ENV!"=="" set "APP_ENV=production"

set /p "APP_LOG_LEVEL=Enter APP_LOG_LEVEL [INFO]: "
if "!APP_LOG_LEVEL!"=="" set "APP_LOG_LEVEL=INFO"

set /p "APP_CONTEXT_PATH=Enter APP_CONTEXT_PATH [/mini-app]: "
if "!APP_CONTEXT_PATH!"=="" set "APP_CONTEXT_PATH=/mini-app"

set /p "MONITORING_ENDPOINT=Enter MONITORING_ENDPOINT: "

REM ---------------------------------------------------------------------------
REM Configure kubectl for EKS
REM ---------------------------------------------------------------------------
echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in !AWS_REGION! ...
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

REM ---------------------------------------------------------------------------
REM Substitute placeholders using PowerShell (Windows sed equivalent)
REM ---------------------------------------------------------------------------
echo.
echo Updating Kubernetes manifests with deployment values...

copy /Y kubernetes\deployment.yaml %TEMP%\deployment.yaml >nul
copy /Y kubernetes\service.yaml    %TEMP%\service.yaml    >nul
copy /Y kubernetes\ingress.yaml    %TEMP%\ingress.yaml    >nul
copy /Y kubernetes\namespace.yaml  %TEMP%\namespace.yaml  >nul

powershell -NoProfile -Command "(Get-Content '%TEMP%\deployment.yaml') -replace '\{\{IMAGE_URI\}\}','!IMAGE_URI!' -replace '\{\{APP_ENV\}\}','!APP_ENV!' -replace '\{\{APP_LOG_LEVEL\}\}','!APP_LOG_LEVEL!' -replace '\{\{APP_CONTEXT_PATH\}\}','!APP_CONTEXT_PATH!' -replace '\{\{DB_URL\}\}','!DB_URL!' -replace '\{\{REDIS_HOST\}\}','!REDIS_HOST!' -replace '\{\{REDIS_PORT\}\}','!REDIS_PORT!' -replace '\{\{EXTERNAL_API_URL\}\}','!EXTERNAL_API_URL!' -replace '\{\{PAYMENT_SERVICE_URL\}\}','!PAYMENT_SERVICE_URL!' -replace '\{\{MONITORING_ENDPOINT\}\}','!MONITORING_ENDPOINT!' | Set-Content '%TEMP%\deployment.yaml'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM Apply manifests in order
REM ---------------------------------------------------------------------------
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f %TEMP%\namespace.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment...
kubectl apply -f %TEMP%\deployment.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service...
kubectl apply -f %TEMP%\service.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress...
kubectl apply -f %TEMP%\ingress.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

REM ---------------------------------------------------------------------------
REM Wait for rollout
REM ---------------------------------------------------------------------------
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Rollback command: kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

REM ---------------------------------------------------------------------------
REM Verify resources
REM ---------------------------------------------------------------------------
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ============================================
echo   Deployment complete!
echo   Check ingress for application URL:
echo   kubectl get ingress -n !NAMESPACE!
echo   Health check: /actuator/health
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
