@echo off & setlocal enabledelayedexpansion
rem Deploys the image of minijava-br55-con-1612 to AWS EKS with kubectl.
rem Run from the repository root: scripts\deploy-image.bat

set "APP_NAME=minijava-br55-con-1612"
set "NAMESPACE=minijava-br55-con-1612"

where aws >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo ERROR the AWS CLI is not installed
    exit /b 1
)
where kubectl >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo ERROR kubectl is not installed
    exit /b 1
)

echo === Deploy !APP_NAME! to AWS EKS ===
set "AWS_REGION="
set "CLUSTER_NAME="
set "IMAGE_URI="
set "REDIS_HOST="
set "REDIS_PORT="
set /p AWS_REGION="Enter the AWS region, for example us-east-1: "
set /p CLUSTER_NAME="Enter the EKS cluster name: "
set /p IMAGE_URI="Enter the image URI, registry, repository and tag: "

if "!AWS_REGION!"=="" (
    echo ERROR the region is required
    exit /b 1
)
if "!CLUSTER_NAME!"=="" (
    echo ERROR the cluster name is required
    exit /b 1
)
if "!IMAGE_URI!"=="" (
    echo ERROR the image URI is required
    exit /b 1
)

set /p REDIS_HOST="Enter value for REDIS_HOST (or press Enter to skip): "
set /p REDIS_PORT="Enter value for REDIS_PORT (or press Enter to skip): "

call :read_secret DB_PASSWORD
call :read_secret REDIS_PASSWORD
call :read_secret EXTERNAL_API_KEY
call :read_secret PAYMENT_SERVICE_PASSWORD
call :read_secret JWT_SECRET
call :read_secret ADMIN_PASSWORD
call :read_secret ENCRYPTION_KEY
call :read_secret MONITORING_PASSWORD
call :read_secret RABBITMQ_PASSWORD

echo Configuring kubectl for cluster !CLUSTER_NAME! in !AWS_REGION!
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo Could not configure kubectl for the cluster
    exit /b 1
)
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo kubectl cannot reach the cluster
    exit /b 1
)

echo Applying the namespace
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo namespace.yaml failed
    exit /b 1
)

echo Creating the Secret !APP_NAME!-secrets
kubectl create secret generic !APP_NAME!-secrets -n !NAMESPACE! "--from-literal=DB_PASSWORD=!DB_PASSWORD!" "--from-literal=REDIS_PASSWORD=!REDIS_PASSWORD!" "--from-literal=EXTERNAL_API_KEY=!EXTERNAL_API_KEY!" "--from-literal=PAYMENT_SERVICE_PASSWORD=!PAYMENT_SERVICE_PASSWORD!" "--from-literal=JWT_SECRET=!JWT_SECRET!" "--from-literal=ADMIN_PASSWORD=!ADMIN_PASSWORD!" "--from-literal=ENCRYPTION_KEY=!ENCRYPTION_KEY!" "--from-literal=MONITORING_PASSWORD=!MONITORING_PASSWORD!" "--from-literal=RABBITMQ_PASSWORD=!RABBITMQ_PASSWORD!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (
    echo The Secret could not be created
    exit /b 1
)

echo Filling in the placeholders
powershell -NoProfile -Command "(Get-Content -Raw kubernetes\deployment.yaml).Replace('{{IMAGE_URI}}','!IMAGE_URI!').Replace('{{NAMESPACE}}','!NAMESPACE!').Replace('{{REDIS_HOST}}','!REDIS_HOST!').Replace('{{REDIS_PORT}}','!REDIS_PORT!') | Set-Content -NoNewline '%TEMP%\minijava_deployment.yaml'"
if !ERRORLEVEL! neq 0 (
    echo Could not fill in the placeholders
    exit /b 1
)

echo Applying the deployment, the service and the ingress
kubectl apply -f "%TEMP%\minijava_deployment.yaml"
if !ERRORLEVEL! neq 0 (
    del "%TEMP%\minijava_deployment.yaml" >nul 2>&1
    echo deployment.yaml failed
    exit /b 1
)
del "%TEMP%\minijava_deployment.yaml" >nul 2>&1
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (
    echo service.yaml failed
    exit /b 1
)
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo ingress.yaml failed
    exit /b 1
)

echo Waiting for the rollout
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo The rollout failed. To go back to the previous version run
    echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    echo To look into the failure run
    echo   kubectl describe pods -n !NAMESPACE!
    echo   kubectl logs deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

kubectl get pods,svc,ingress -n !NAMESPACE!

set "INGRESS_HOST="
kubectl get ingress !APP_NAME!-ingress -n !NAMESPACE! -o "jsonpath={.status.loadBalancer.ingress[0].hostname}" > "%TEMP%\minijava_ingress.txt" 2>nul
set /p INGRESS_HOST=<"%TEMP%\minijava_ingress.txt"
del "%TEMP%\minijava_ingress.txt" >nul 2>&1
if not "!INGRESS_HOST!"=="" (
    echo The load balancer address is http://!INGRESS_HOST!
) else (
    echo The load balancer address is not ready yet. Check later with kubectl get ingress -n !NAMESPACE!
)
echo Deployment finished
endlocal
exit /b 0

:read_secret
set "SECRET_VALUE="
powershell -NoProfile -Command "$s = Read-Host 'Enter value for %~1' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))" > "%TEMP%\minijava_secret.txt"
set /p SECRET_VALUE=<"%TEMP%\minijava_secret.txt"
del "%TEMP%\minijava_secret.txt" >nul 2>&1
if "!SECRET_VALUE!"=="" goto read_secret
set "%~1=!SECRET_VALUE!"
exit /b 0
