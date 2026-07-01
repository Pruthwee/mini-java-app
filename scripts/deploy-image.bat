@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=mini-java-app

echo --- AWS EKS Deployment Script ---
set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
set /p IMAGE_URI="Enter Full Docker Image URI: "

echo Enter environment variables (press Enter to skip):
set /p DATABASE_URL="DATABASE_URL: "
set /p DATABASE_USERNAME="DATABASE_USERNAME: "
set /p DATABASE_PASSWORD="DATABASE_PASSWORD: "
set /p CACHE_REDIS_HOST="CACHE_REDIS_HOST: "
set /p EXTERNAL_API_BASE_URL="EXTERNAL_API_BASE_URL: "
set /p MESSAGING_RABBITMQ_HOST="MESSAGING_RABBITMQ_HOST: "

echo Updating manifests...
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '%IMAGE_URI%' -replace '{{DATABASE_URL}}', '%DATABASE_URL%' -replace '{{DATABASE_USERNAME}}', '%DATABASE_USERNAME%' -replace '{{DATABASE_PASSWORD}}', '%DATABASE_PASSWORD%' -replace '{{CACHE_REDIS_HOST}}', '%CACHE_REDIS_HOST%' -replace '{{EXTERNAL_API_BASE_URL}}', '%EXTERNAL_API_BASE_URL%' -replace '{{MESSAGING_RABBITMQ_HOST}}', '%MESSAGING_RABBITMQ_HOST%' | Set-Content kubernetes/deployment.yaml"

echo Configuring kubectl...
aws eks update-kubeconfig --region %AWS_REGION% --name %CLUSTER_NAME%

echo Verifying cluster connectivity...
kubectl cluster-info
if %ERRORLEVEL% neq 0 (echo Cluster connectivity failed & exit /b 1)

echo Applying manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for rollout...
kubectl rollout status deployment/%PROJECT_NAME% -n %PROJECT_NAME%

echo Verifying resources...
kubectl get pods,svc,ingress -n %PROJECT_NAME%

echo Deployment complete. Application URL: http://mini-java-app.example.com
echo To rollback: kubectl rollout undo deployment/%PROJECT_NAME% -n %PROJECT_NAME%
