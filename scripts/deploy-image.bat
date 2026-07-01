@echo off
setlocal enabledelayedexpansion

echo ==========================================
echo AWS EKS Deployment Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=mini-java-app
set NAMESPACE=mini-java-app

echo Project: %PROJECT_NAME%
echo Namespace: %NAMESPACE%
echo.

REM Prompt for AWS region
set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
    echo Error: AWS region is required
    exit /b 1
)

REM Prompt for EKS cluster name
set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo Error: EKS cluster name is required
    exit /b 1
)

REM Prompt for Docker image URI
echo.
echo Enter the full Docker image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest)
set /p IMAGE_URI="Image URI: "
if "!IMAGE_URI!"=="" (
    echo Error: Image URI is required
    exit /b 1
)

echo.
echo === Collecting Environment Variables ===
echo Enter values for application environment variables (press Enter to skip optional ones)
echo.

REM Database configuration
set /p DATABASE_URL="DATABASE_URL (e.g., jdbc:mysql://db-host:3306/mini_app_db): "
set /p DB_USERNAME="DB_USERNAME: "
set /p DB_PASSWORD="DB_PASSWORD: "

REM Redis configuration
set /p REDIS_HOST="REDIS_HOST (e.g., redis.example.com): "
set /p REDIS_PASSWORD="REDIS_PASSWORD (optional): "

REM External API configuration
set /p EXTERNAL_API_URL="EXTERNAL_API_URL (optional): "
set /p EXTERNAL_API_KEY="EXTERNAL_API_KEY (optional): "

REM Payment service configuration
set /p PAYMENT_SERVICE_URL="PAYMENT_SERVICE_URL (optional): "
set /p PAYMENT_SERVICE_USERNAME="PAYMENT_SERVICE_USERNAME (optional): "
set /p PAYMENT_SERVICE_PASSWORD="PAYMENT_SERVICE_PASSWORD (optional): "

REM S3 configuration
set /p CONFIG_S3_BUCKET="CONFIG_S3_BUCKET (e.g., app-config-bucket): "
set /p LOG_S3_BUCKET="LOG_S3_BUCKET (e.g., app-logs-bucket): "
set /p UPLOAD_S3_BUCKET="UPLOAD_S3_BUCKET (e.g., app-uploads-bucket): "

REM Security configuration
set /p JWT_SECRET="JWT_SECRET: "
set /p ADMIN_USERNAME="ADMIN_USERNAME: "
set /p ADMIN_PASSWORD="ADMIN_PASSWORD: "
set /p ENCRYPTION_KEY="ENCRYPTION_KEY: "

REM Monitoring configuration
set /p MONITORING_ENDPOINT="MONITORING_ENDPOINT (optional): "
set /p MONITORING_USERNAME="MONITORING_USERNAME (optional): "
set /p MONITORING_PASSWORD="MONITORING_PASSWORD (optional): "

REM RabbitMQ configuration
set /p RABBITMQ_HOST="RABBITMQ_HOST (optional): "
set /p RABBITMQ_USERNAME="RABBITMQ_USERNAME (optional): "
set /p RABBITMQ_PASSWORD="RABBITMQ_PASSWORD (optional): "

echo.
echo === Configuring kubectl for EKS ===
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to configure kubectl for EKS cluster
    exit /b 1
)

echo kubectl configured successfully

echo.
echo === Verifying cluster connectivity ===
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo Error: Cannot connect to Kubernetes cluster
    exit /b 1
)

echo.
echo === Updating Kubernetes manifests ===

REM Create temporary directory for modified manifests
set TEMP_DIR=%TEMP%\k8s-deploy-%RANDOM%
mkdir !TEMP_DIR!
xcopy /E /I /Q kubernetes !TEMP_DIR! >nul

REM Update IMAGE_URI placeholder using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

REM Update environment variable placeholders
if not "!DATABASE_URL!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DATABASE_URL}}', '!DATABASE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!DB_USERNAME!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_USERNAME}}', '!DB_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!DB_PASSWORD!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!REDIS_HOST!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!REDIS_PASSWORD!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!EXTERNAL_API_URL!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{EXTERNAL_API_URL}}', '!EXTERNAL_API_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!EXTERNAL_API_KEY!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{EXTERNAL_API_KEY}}', '!EXTERNAL_API_KEY!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!PAYMENT_SERVICE_URL!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!PAYMENT_SERVICE_USERNAME!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{PAYMENT_SERVICE_USERNAME}}', '!PAYMENT_SERVICE_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!PAYMENT_SERVICE_PASSWORD!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{PAYMENT_SERVICE_PASSWORD}}', '!PAYMENT_SERVICE_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!CONFIG_S3_BUCKET!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{CONFIG_S3_BUCKET}}', '!CONFIG_S3_BUCKET!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!LOG_S3_BUCKET!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{LOG_S3_BUCKET}}', '!LOG_S3_BUCKET!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!UPLOAD_S3_BUCKET!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{UPLOAD_S3_BUCKET}}', '!UPLOAD_S3_BUCKET!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!JWT_SECRET!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{JWT_SECRET}}', '!JWT_SECRET!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!ADMIN_USERNAME!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{ADMIN_USERNAME}}', '!ADMIN_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!ADMIN_PASSWORD!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{ADMIN_PASSWORD}}', '!ADMIN_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!ENCRYPTION_KEY!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{ENCRYPTION_KEY}}', '!ENCRYPTION_KEY!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!MONITORING_ENDPOINT!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{MONITORING_ENDPOINT}}', '!MONITORING_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!MONITORING_USERNAME!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{MONITORING_USERNAME}}', '!MONITORING_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!MONITORING_PASSWORD!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{MONITORING_PASSWORD}}', '!MONITORING_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!RABBITMQ_HOST!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{RABBITMQ_HOST}}', '!RABBITMQ_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!RABBITMQ_USERNAME!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{RABBITMQ_USERNAME}}', '!RABBITMQ_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
if not "!RABBITMQ_PASSWORD!"=="" powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{RABBITMQ_PASSWORD}}', '!RABBITMQ_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated successfully

echo.
echo === Deploying to AWS EKS ===

REM Apply namespace
echo Creating namespace...
kubectl apply -f !TEMP_DIR!\namespace.yaml

REM Apply deployment
echo Deploying application...
kubectl apply -f !TEMP_DIR!\deployment.yaml

REM Apply service
echo Creating service...
kubectl apply -f !TEMP_DIR!\service.yaml

REM Apply ingress
echo Creating ingress...
kubectl apply -f !TEMP_DIR!\ingress.yaml

echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/%PROJECT_NAME% -n %NAMESPACE% --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo Warning: Deployment rollout did not complete within timeout
    echo Check deployment status with: kubectl get pods -n %NAMESPACE%
)

echo.
echo === Verifying deployment ===
kubectl get pods,svc,ingress -n %NAMESPACE%

echo.
echo === Getting Application URL ===
for /f "delims=" %%i in ('kubectl get ingress %PROJECT_NAME%-ingress -n %NAMESPACE% -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set INGRESS_HOST=%%i

echo.
echo ==========================================
echo Deployment Completed Successfully!
echo ==========================================
echo Namespace: %NAMESPACE%
echo Image: !IMAGE_URI!
echo.
if not "!INGRESS_HOST!"=="" (
    echo Application URL: http://!INGRESS_HOST!
) else (
    echo Ingress is being provisioned. Check status with:
    echo kubectl get ingress -n %NAMESPACE%
)
echo.
echo Useful commands:
echo   View pods: kubectl get pods -n %NAMESPACE%
echo   View logs: kubectl logs -f deployment/%PROJECT_NAME% -n %NAMESPACE%
echo   Describe deployment: kubectl describe deployment %PROJECT_NAME% -n %NAMESPACE%
echo   Scale deployment: kubectl scale deployment %PROJECT_NAME% --replicas=3 -n %NAMESPACE%
echo.
echo To rollback deployment:
echo   kubectl rollout undo deployment/%PROJECT_NAME% -n %NAMESPACE%
echo ==========================================

REM Cleanup temporary directory
rmdir /S /Q !TEMP_DIR!

endlocal
