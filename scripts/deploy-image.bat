@echo off
setlocal enabledelayedexpansion

echo === Deploy mini-java-app to AWS EKS ===

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter the full image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest): "

echo.
echo === Configuring kubectl for EKS cluster ===
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (echo Failed to configure kubectl & exit /b 1)

echo.
echo === Verifying cluster connectivity ===
kubectl cluster-info
if !ERRORLEVEL! neq 0 (echo Failed to connect to cluster & exit /b 1)

echo.
echo === Collecting environment variable values ===
echo Enter values for non-credential environment variables.
echo Press Enter to skip (use default or leave unset).

set /p CONFIG_PATH="Enter value for CONFIG_PATH (or press Enter to skip): "
set /p LOG_PATH="Enter value for LOG_PATH (or press Enter to skip): "
set /p LOG_DIR="Enter value for LOG_DIR (or press Enter to skip): "
set /p DB_HOST="Enter value for DB_HOST (or press Enter to skip): "
set /p DB_PORT="Enter value for DB_PORT (or press Enter to skip): "
set /p DB_NAME="Enter value for DB_NAME (or press Enter to skip): "
set /p REDIS_HOST="Enter value for REDIS_HOST (or press Enter to skip): "
set /p REDIS_PORT="Enter value for REDIS_PORT (or press Enter to skip): "
set /p EXTERNAL_API_URL="Enter value for EXTERNAL_API_URL (or press Enter to skip): "
set /p PAYMENT_SERVICE_URL="Enter value for PAYMENT_SERVICE_URL (or press Enter to skip): "

echo.
echo === Collecting credential values (input hidden) ===
set /p DB_USERNAME_VAL="Enter value for DB_USERNAME: "
set /p DB_PASSWORD_VAL="Enter value for DB_PASSWORD: "
set /p REDIS_PASSWORD_VAL="Enter value for REDIS_PASSWORD: "
set /p EXTERNAL_API_KEY_VAL="Enter value for EXTERNAL_API_KEY: "
set /p PAYMENT_SERVICE_USERNAME_VAL="Enter value for PAYMENT_SERVICE_USERNAME: "
set /p PAYMENT_SERVICE_PASSWORD_VAL="Enter value for PAYMENT_SERVICE_PASSWORD: "

echo.
echo === Creating Kubernetes namespace ===
kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (echo Namespace creation failed & exit /b 1)

echo.
echo === Creating Kubernetes Secret for credentials ===
kubectl create secret generic mini-java-app-secrets -n mini-java-app --from-literal=DB_USERNAME="!DB_USERNAME_VAL!" --from-literal=DB_PASSWORD="!DB_PASSWORD_VAL!" --from-literal=REDIS_PASSWORD="!REDIS_PASSWORD_VAL!" --from-literal=EXTERNAL_API_KEY="!EXTERNAL_API_KEY_VAL!" --from-literal=PAYMENT_SERVICE_USERNAME="!PAYMENT_SERVICE_USERNAME_VAL!" --from-literal=PAYMENT_SERVICE_PASSWORD="!PAYMENT_SERVICE_PASSWORD_VAL!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (echo Secret creation failed & exit /b 1)

echo.
echo === Deploying application ===
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp"
powershell -Command "(gc kubernetes/deployment.yaml.tmp) -replace '{{CONFIG_PATH}}', '!CONFIG_PATH!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp2"
powershell -Command "(gc kubernetes/deployment.yaml.tmp2) -replace '{{LOG_PATH}}', '!LOG_PATH!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp"
powershell -Command "(gc kubernetes/deployment.yaml.tmp) -replace '{{LOG_DIR}}', '!LOG_DIR!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp2"
powershell -Command "(gc kubernetes/deployment.yaml.tmp2) -replace '{{DB_HOST}}', '!DB_HOST!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp"
powershell -Command "(gc kubernetes/deployment.yaml.tmp) -replace '{{DB_PORT}}', '!DB_PORT!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp2"
powershell -Command "(gc kubernetes/deployment.yaml.tmp2) -replace '{{DB_NAME}}', '!DB_NAME!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp"
powershell -Command "(gc kubernetes/deployment.yaml.tmp) -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp2"
powershell -Command "(gc kubernetes/deployment.yaml.tmp2) -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp"
powershell -Command "(gc kubernetes/deployment.yaml.tmp) -replace '{{EXTERNAL_API_URL}}', '!EXTERNAL_API_URL!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp2"
powershell -Command "(gc kubernetes/deployment.yaml.tmp2) -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Out-File -encoding ASCII kubernetes/deployment.yaml.tmp"
move /Y kubernetes/deployment.yaml.tmp kubernetes/deployment.yaml >nul
del kubernetes/deployment.yaml.tmp2 2>nul

kubectl apply -f kubernetes/deployment.yaml
if !ERRORLEVEL! neq 0 (echo Deployment failed & exit /b 1)

echo.
echo === Creating Service ===
kubectl apply -f kubernetes/service.yaml
if !ERRORLEVEL! neq 0 (echo Service creation failed & exit /b 1)

echo.
echo === Creating Ingress ===
kubectl apply -f kubernetes/ingress.yaml
if !ERRORLEVEL! neq 0 (echo Ingress creation failed & exit /b 1)

echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/mini-java-app -n mini-java-app
if !ERRORLEVEL! neq 0 (echo Rollout failed & exit /b 1)

echo.
echo === Deployment status ===
kubectl get pods,svc,ingress -n mini-java-app

echo.
echo === Application URL ===
kubectl get ingress mini-java-app-ingress -n mini-java-app -o jsonpath="{.status.loadBalancer.ingress[0].hostname}"

echo.
echo.
echo === Deployment complete ===
echo.
echo If the deployment fails, rollback with:
echo   kubectl rollout undo deployment/mini-java-app -n mini-java-app

endlocal
