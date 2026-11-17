@echo off
setlocal enabledelayedexpansion

set "PROJECT_NAME=minijava_bach_con_1439"
for /f "delims=" %%i in ('powershell -command "$env:PROJECT_NAME.ToLower() -replace '[^a-z0-9]+', '-'"') do set "IMAGE_NAME=%%i"
for /f "delims=" %%i in ('powershell -command "$env:IMAGE_NAME.Trim('-')"') do set "IMAGE_NAME=%%i"

echo === Deploy to AWS EKS: !IMAGE_NAME! ===
echo.

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter full image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/minijava-bach-con-1439:latest): "

echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in region !AWS_REGION!
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo Failed to configure kubectl for EKS cluster
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Failed to connect to cluster
    exit /b 1
)

echo.
echo === Collecting environment variable values ===
echo.

set /p APP_PORT="Enter value for APP_PORT (or press Enter to skip): "
set /p HEALTH_PORT="Enter value for HEALTH_PORT (or press Enter to skip): "
set /p CONFIG_PATH="Enter value for CONFIG_PATH (or press Enter to skip): "
set /p LOG_PATH="Enter value for LOG_PATH (or press Enter to skip): "
set /p LOG_DIR="Enter value for LOG_DIR (or press Enter to skip): "
set /p DB_HOST="Enter value for DB_HOST (or press Enter to skip): "
set /p DB_PORT="Enter value for DB_PORT (or press Enter to skip): "
set /p DB_NAME="Enter value for DB_NAME (or press Enter to skip): "
set /p REDIS_HOST="Enter value for REDIS_HOST (or press Enter to skip): "
set /p REDIS_PORT="Enter value for REDIS_PORT (or press Enter to skip): "

echo.
echo === Collecting credential values (will not be echoed) ===
echo.

set /p DB_USERNAME="Enter value for DB_USERNAME: "
set /p DB_PASSWORD="Enter value for DB_PASSWORD: "
set /p REDIS_PASSWORD="Enter value for REDIS_PASSWORD: "
set /p EXTERNAL_API_KEY="Enter value for EXTERNAL_API_KEY: "
set /p PAYMENT_SERVICE_USERNAME="Enter value for PAYMENT_SERVICE_USERNAME: "
set /p PAYMENT_SERVICE_PASSWORD="Enter value for PAYMENT_SERVICE_PASSWORD: "
set /p SECURITY_JWT_SECRET="Enter value for SECURITY_JWT_SECRET: "
set /p SECURITY_ADMIN_USERNAME="Enter value for SECURITY_ADMIN_USERNAME: "
set /p SECURITY_ADMIN_PASSWORD="Enter value for SECURITY_ADMIN_PASSWORD: "
set /p SECURITY_ENCRYPTION_KEY="Enter value for SECURITY_ENCRYPTION_KEY: "
set /p MONITORING_USERNAME="Enter value for MONITORING_USERNAME: "
set /p MONITORING_PASSWORD="Enter value for MONITORING_PASSWORD: "
set /p MESSAGING_RABBITMQ_USERNAME="Enter value for MESSAGING_RABBITMQ_USERNAME: "
set /p MESSAGING_RABBITMQ_PASSWORD="Enter value for MESSAGING_RABBITMQ_PASSWORD: "
echo.

echo.
echo === Applying Kubernetes manifests ===
echo.

echo Applying namespace...
kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply namespace
    exit /b 1
)

echo Creating Kubernetes Secret...
kubectl create secret generic !IMAGE_NAME!-secrets -n !IMAGE_NAME! ^
  --from-literal=db-username="!DB_USERNAME!" ^
  --from-literal=db-password="!DB_PASSWORD!" ^
  --from-literal=redis-password="!REDIS_PASSWORD!" ^
  --from-literal=external-api-key="!EXTERNAL_API_KEY!" ^
  --from-literal=payment-service-username="!PAYMENT_SERVICE_USERNAME!" ^
  --from-literal=payment-service-password="!PAYMENT_SERVICE_PASSWORD!" ^
  --from-literal=security-jwt-secret="!SECURITY_JWT_SECRET!" ^
  --from-literal=security-admin-username="!SECURITY_ADMIN_USERNAME!" ^
  --from-literal=security-admin-password="!SECURITY_ADMIN_PASSWORD!" ^
  --from-literal=security-encryption-key="!SECURITY_ENCRYPTION_KEY!" ^
  --from-literal=monitoring-username="!MONITORING_USERNAME!" ^
  --from-literal=monitoring-password="!MONITORING_PASSWORD!" ^
  --from-literal=messaging-rabbitmq-username="!MESSAGING_RABBITMQ_USERNAME!" ^
  --from-literal=messaging-rabbitmq-password="!MESSAGING_RABBITMQ_PASSWORD!" ^
  --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (
    echo Failed to create Secret
    exit /b 1
)

echo Applying deployment...
copy kubernetes\deployment.yaml kubernetes\deployment.yaml.bak >nul
powershell -command "(gc kubernetes\deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp"
powershell -command "(gc kubernetes\deployment.yaml.tmp) -replace '{{CONFIG_PATH}}', '!CONFIG_PATH!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp2"
powershell -command "(gc kubernetes\deployment.yaml.tmp2) -replace '{{LOG_PATH}}', '!LOG_PATH!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp"
powershell -command "(gc kubernetes\deployment.yaml.tmp) -replace '{{LOG_DIR}}', '!LOG_DIR!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp2"
powershell -command "(gc kubernetes\deployment.yaml.tmp2) -replace '{{DB_HOST}}', '!DB_HOST!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp"
powershell -command "(gc kubernetes\deployment.yaml.tmp) -replace '{{DB_PORT}}', '!DB_PORT!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp2"
powershell -command "(gc kubernetes\deployment.yaml.tmp2) -replace '{{DB_NAME}}', '!DB_NAME!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp"
powershell -command "(gc kubernetes\deployment.yaml.tmp) -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp2"
powershell -command "(gc kubernetes\deployment.yaml.tmp2) -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Out-File -encoding UTF8 kubernetes\deployment.yaml.tmp"
move /y kubernetes\deployment.yaml.tmp kubernetes\deployment.yaml >nul
del kubernetes\deployment.yaml.tmp2
del kubernetes\deployment.yaml.bak

kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply deployment
    exit /b 1
)

echo Applying service...
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply service
    exit /b 1
)

echo Applying ingress...
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo Failed to apply ingress
    exit /b 1
)

echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/!IMAGE_NAME! -n !IMAGE_NAME! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo Deployment rollout failed
    echo To rollback: kubectl rollout undo deployment/!IMAGE_NAME! -n !IMAGE_NAME!
    exit /b 1
)

echo.
echo === Deployment complete ===
kubectl get pods,svc,ingress -n !IMAGE_NAME!
echo.
echo Application URL: https://!IMAGE_NAME!.example.com
echo.
echo To rollback: kubectl rollout undo deployment/!IMAGE_NAME! -n !IMAGE_NAME!
