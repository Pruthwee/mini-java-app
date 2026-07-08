@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=mbasic

echo --- AWS EKS Deployment Script ---

set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo Cluster name is required
    exit /b 1
)

set /p IMAGE_URI="Enter Docker Image URI: "
if "!IMAGE_URI!"=="" (
    echo Image URI is required
    exit /b 1
)

echo Enter values for environment variables (press Enter to skip):
set /p DATABASE_URL="DATABASE_URL: "
set /p DATABASE_USERNAME="DATABASE_USERNAME: "
set /p DATABASE_PASSWORD="DATABASE_PASSWORD: "
set /p CACHE_REDIS_HOST="CACHE_REDIS_HOST: "
set /p CACHE_REDIS_PORT="CACHE_REDIS_PORT: "
set /p CACHE_REDIS_PASSWORD="CACHE_REDIS_PASSWORD: "
set /p EXTERNAL_API_BASE_URL="EXTERNAL_API_BASE_URL: "
set /p EXTERNAL_API_KEY="EXTERNAL_API_KEY: "
set /p PAYMENT_SERVICE_URL="PAYMENT_SERVICE_URL: "
set /p PAYMENT_SERVICE_USERNAME="PAYMENT_SERVICE_USERNAME: "
set /p PAYMENT_SERVICE_PASSWORD="PAYMENT_SERVICE_PASSWORD: "
set /p SECURITY_JWT_SECRET="SECURITY_JWT_SECRET: "
set /p SECURITY_ADMIN_USERNAME="SECURITY_ADMIN_USERNAME: "
set /p SECURITY_ADMIN_PASSWORD="SECURITY_ADMIN_PASSWORD: "
set /p MESSAGING_RABBITMQ_HOST="MESSAGING_RABBITMQ_HOST: "
set /p MESSAGING_RABBITMQ_USERNAME="MESSAGING_RABBITMQ_USERNAME: "
set /p MESSAGING_RABBITMQ_PASSWORD="MESSAGING_RABBITMQ_PASSWORD: "

:: Update manifests using PowerShell for sed-like replacement
echo Updating manifests...
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' -replace '{{DATABASE_URL}}', '!DATABASE_URL!' -replace '{{DATABASE_USERNAME}}', '!DATABASE_USERNAME!' -replace '{{DATABASE_PASSWORD}}', '!DATABASE_PASSWORD!' -replace '{{CACHE_REDIS_HOST}}', '!CACHE_REDIS_HOST!' -replace '{{CACHE_REDIS_PORT}}', '!CACHE_REDIS_PORT!' -replace '{{CACHE_REDIS_PASSWORD}}', '!CACHE_REDIS_PASSWORD!' -replace '{{EXTERNAL_API_BASE_URL}}', '!EXTERNAL_API_BASE_URL!' -replace '{{EXTERNAL_API_KEY}}', '!EXTERNAL_API_KEY!' -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' -replace '{{PAYMENT_SERVICE_USERNAME}}', '!PAYMENT_SERVICE_USERNAME!' -replace '{{PAYMENT_SERVICE_PASSWORD}}', '!PAYMENT_SERVICE_PASSWORD!' -replace '{{SECURITY_JWT_SECRET}}', '!SECURITY_JWT_SECRET!' -replace '{{SECURITY_ADMIN_USERNAME}}', '!SECURITY_ADMIN_USERNAME!' -replace '{{SECURITY_ADMIN_PASSWORD}}', '!SECURITY_ADMIN_PASSWORD!' -replace '{{MESSAGING_RABBITMQ_HOST}}', '!MESSAGING_RABBITMQ_HOST!' -replace '{{MESSAGING_RABBITMQ_USERNAME}}', '!MESSAGING_RABBITMQ_USERNAME!' -replace '{{MESSAGING_RABBITMQ_PASSWORD}}', '!MESSAGING_RABBITMQ_PASSWORD!' | Set-Content kubernetes/deployment.yaml"

echo Configuring kubectl for EKS cluster !CLUSTER_NAME!...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo Failed to update kubeconfig
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Cluster connectivity failed
    exit /b 1
)

echo Applying Kubernetes manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for deployment rollout...
kubectl rollout status deployment/%PROJECT_NAME% -n %PROJECT_NAME%

echo Verifying resources...
kubectl get pods,svc,ingress -n %PROJECT_NAME%

echo Deployment complete!
echo Application URL: http://mbasic.example.com
echo In case of failure, use: kubectl rollout undo deployment/%PROJECT_NAME% -n %PROJECT_NAME%
