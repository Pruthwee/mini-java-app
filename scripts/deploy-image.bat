@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat - Deploy mini-java-app to AWS EKS (Windows)
REM =============================================================================

set APP_NAME=mini-java-app
set NAMESPACE=mini-java-app

echo ==============================================
echo   Deploy %APP_NAME% to AWS EKS
echo ==============================================
echo.

REM Prompt for AWS region
set /p AWS_REGION="Enter AWS region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo AWS region is required. Exiting.
    exit /b 1
)

REM Prompt for EKS cluster name
set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo EKS cluster name is required. Exiting.
    exit /b 1
)

REM Prompt for Docker image URI
set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): "
if "!IMAGE_URI!"=="" (
    echo Docker image URI is required. Exiting.
    exit /b 1
)

echo.
echo --- Optional: Environment Variable Configuration ---
echo Press Enter to skip any variable and use the placeholder value.
echo.

set /p DB_HOST="Enter DB_HOST (database hostname): "
set /p DB_PORT="Enter DB_PORT (default: 3306): "
set /p DB_NAME="Enter DB_NAME (database name): "
set /p DB_USERNAME="Enter DB_USERNAME: "
set /p DB_PASSWORD="Enter DB_PASSWORD: "
set /p REDIS_HOST="Enter REDIS_HOST (default: redis.local): "
set /p REDIS_PORT="Enter REDIS_PORT (default: 6379): "
set /p REDIS_DATABASE="Enter REDIS_DATABASE (default: 0): "
set /p REDIS_PASSWORD="Enter REDIS_PASSWORD: "
set /p EXTERNAL_API_BASE_URL="Enter EXTERNAL_API_BASE_URL: "
set /p EXTERNAL_API_TIMEOUT="Enter EXTERNAL_API_TIMEOUT (default: 30000): "
set /p EXTERNAL_API_KEY="Enter EXTERNAL_API_KEY: "
set /p PAYMENT_SERVICE_URL="Enter PAYMENT_SERVICE_URL: "
set /p PAYMENT_SERVICE_USERNAME="Enter PAYMENT_SERVICE_USERNAME: "
set /p PAYMENT_SERVICE_PASSWORD="Enter PAYMENT_SERVICE_PASSWORD: "
set /p JWT_SECRET="Enter JWT_SECRET: "
set /p ADMIN_USERNAME="Enter ADMIN_USERNAME: "
set /p ADMIN_PASSWORD="Enter ADMIN_PASSWORD: "
set /p ENCRYPTION_KEY="Enter ENCRYPTION_KEY: "
set /p RABBITMQ_HOST="Enter RABBITMQ_HOST: "
set /p RABBITMQ_PORT="Enter RABBITMQ_PORT (default: 5672): "
set /p RABBITMQ_USERNAME="Enter RABBITMQ_USERNAME: "
set /p RABBITMQ_PASSWORD="Enter RABBITMQ_PASSWORD: "
set /p MONITORING_ENDPOINT="Enter MONITORING_ENDPOINT: "
set /p MONITORING_USERNAME="Enter MONITORING_USERNAME: "
set /p MONITORING_PASSWORD="Enter MONITORING_PASSWORD: "

REM Apply defaults for empty values
if "!DB_HOST!"==""                  set DB_HOST=localhost
if "!DB_PORT!"==""                  set DB_PORT=3306
if "!DB_NAME!"==""                  set DB_NAME=mini_app_db
if "!DB_USERNAME!"==""              set DB_USERNAME=root
if "!REDIS_HOST!"==""               set REDIS_HOST=redis.local
if "!REDIS_PORT!"==""               set REDIS_PORT=6379
if "!REDIS_DATABASE!"==""           set REDIS_DATABASE=0
if "!EXTERNAL_API_BASE_URL!"==""    set EXTERNAL_API_BASE_URL=http://api.example.com:8080/v1
if "!EXTERNAL_API_TIMEOUT!"==""     set EXTERNAL_API_TIMEOUT=30000
if "!PAYMENT_SERVICE_URL!"==""      set PAYMENT_SERVICE_URL=https://payment.internal.company.com/process
if "!RABBITMQ_HOST!"==""            set RABBITMQ_HOST=rabbitmq.internal.company.com
if "!RABBITMQ_PORT!"==""            set RABBITMQ_PORT=5672
if "!MONITORING_ENDPOINT!"==""      set MONITORING_ENDPOINT=http://monitoring.internal.company.com:9090/metrics

echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in !AWS_REGION! ...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo Failed to configure kubectl. Exiting.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Cannot connect to cluster. Exiting.
    exit /b 1
)

echo.
echo Updating Kubernetes manifests with provided values...

REM Use PowerShell to perform sed-like replacements on deployment.yaml
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_HOST}}', '!DB_HOST!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_PORT}}', '!DB_PORT!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_NAME}}', '!DB_NAME!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_USERNAME}}', '!DB_USERNAME!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_DATABASE}}', '!REDIS_DATABASE!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{EXTERNAL_API_BASE_URL}}', '!EXTERNAL_API_BASE_URL!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{EXTERNAL_API_TIMEOUT}}', '!EXTERNAL_API_TIMEOUT!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{EXTERNAL_API_KEY}}', '!EXTERNAL_API_KEY!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{PAYMENT_SERVICE_USERNAME}}', '!PAYMENT_SERVICE_USERNAME!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{PAYMENT_SERVICE_PASSWORD}}', '!PAYMENT_SERVICE_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{JWT_SECRET}}', '!JWT_SECRET!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{ADMIN_USERNAME}}', '!ADMIN_USERNAME!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{ADMIN_PASSWORD}}', '!ADMIN_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{ENCRYPTION_KEY}}', '!ENCRYPTION_KEY!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{RABBITMQ_HOST}}', '!RABBITMQ_HOST!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{RABBITMQ_PORT}}', '!RABBITMQ_PORT!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{RABBITMQ_USERNAME}}', '!RABBITMQ_USERNAME!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{RABBITMQ_PASSWORD}}', '!RABBITMQ_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{MONITORING_ENDPOINT}}', '!MONITORING_ENDPOINT!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{MONITORING_USERNAME}}', '!MONITORING_USERNAME!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{MONITORING_PASSWORD}}', '!MONITORING_PASSWORD!' | Set-Content kubernetes\deployment.yaml"

echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 ( echo Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment...
kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 ( echo Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service...
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 ( echo Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress...
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 ( echo Failed to apply ingress. & exit /b 1 )

echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/%APP_NAME% -n %NAMESPACE% --timeout=300s
if !ERRORLEVEL! neq 0 ( echo Deployment rollout failed. & exit /b 1 )

echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n %NAMESPACE%

echo.
echo ==============================================
echo   Deployment complete!
echo   Application URL: http://mini-java-app.example.com
echo   Health endpoint: http://mini-java-app.example.com/actuator/health
echo ==============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%

endlocal
