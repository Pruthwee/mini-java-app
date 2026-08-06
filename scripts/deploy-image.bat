@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem deploy-image.bat – Deploy mini-java-app to AWS EKS (Windows)
rem ============================================================

set APP_NAME=mini-java-app
set NAMESPACE=mini-java-app
set K8S_DIR=kubernetes
set TEMP_DIR=%TEMP%\mini-java-app-k8s-deploy

echo ============================================
echo   mini-java-app - EKS Deployment
echo ============================================
echo.

rem ── Collect inputs ───────────────────────────────────────────
set /p AWS_REGION="Enter AWS region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS region is required.
    exit /b 1
)

set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS cluster name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo --- Optional: Application Environment Variables ---
echo Press Enter to skip any variable.
echo.

set /p DB_HOST="Enter DB_HOST (database hostname): "
set /p DB_PORT="Enter DB_PORT (default: 3306): "
set /p DB_NAME="Enter DB_NAME (database name): "
set /p DB_USERNAME="Enter DB_USERNAME (database user): "
set /p DB_PASSWORD="Enter DB_PASSWORD (database password): "
set /p DB_URL="Enter DB_URL (full JDBC URL, overrides DB_HOST/PORT/NAME if set): "
set /p REDIS_HOST="Enter REDIS_HOST (Redis hostname): "
set /p REDIS_PORT="Enter REDIS_PORT (default: 6379): "

if "!DB_HOST!"==""     set DB_HOST=localhost
if "!DB_PORT!"==""     set DB_PORT=3306
if "!DB_NAME!"==""     set DB_NAME=mini_app_db
if "!DB_USERNAME!"=="" set DB_USERNAME=root
if "!REDIS_HOST!"==""  set REDIS_HOST=redis.internal.svc.cluster.local
if "!REDIS_PORT!"==""  set REDIS_PORT=6379

echo.

rem ── Configure kubectl ────────────────────────────────────────
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in !AWS_REGION!...
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

echo.

rem ── Copy manifests to temp dir ───────────────────────────────
if exist "!TEMP_DIR!" rmdir /s /q "!TEMP_DIR!"
xcopy /e /i /q "!K8S_DIR!" "!TEMP_DIR!" >nul
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to copy Kubernetes manifests.
    exit /b 1
)

rem ── Update manifests using PowerShell ────────────────────────
echo Updating Kubernetes manifests...

set DEPLOY_FILE=!TEMP_DIR!\deployment.yaml

powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{IMAGE_URI\}\}', '!IMAGE_URI!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{DB_HOST\}\}', '!DB_HOST!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{DB_PORT\}\}', '!DB_PORT!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{DB_NAME\}\}', '!DB_NAME!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{DB_USERNAME\}\}', '!DB_USERNAME!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{DB_PASSWORD\}\}', '!DB_PASSWORD!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{DB_URL\}\}', '!DB_URL!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{REDIS_HOST\}\}', '!REDIS_HOST!' | Set-Content '!DEPLOY_FILE!'"
powershell -Command "(Get-Content '!DEPLOY_FILE!') -replace '\{\{REDIS_PORT\}\}', '!REDIS_PORT!' | Set-Content '!DEPLOY_FILE!'"

rem ── Apply manifests ──────────────────────────────────────────
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service...
kubectl apply -f "!TEMP_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

rem ── Wait for rollout ─────────────────────────────────────────
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Rollback command: kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

rem ── Verify ───────────────────────────────────────────────────
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ============================================
echo   Deployment Complete!
echo   Check ingress for the application URL.
echo   Health Check path: /actuator/health
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

rem ── Cleanup ──────────────────────────────────────────────────
if exist "!TEMP_DIR!" rmdir /s /q "!TEMP_DIR!"

endlocal
