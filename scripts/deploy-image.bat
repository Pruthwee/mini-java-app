@echo off & setlocal enabledelayedexpansion
rem Deploys the pushed image to AWS EKS with kubectl.
rem Run from the repository root, as scripts\deploy-image.bat

set "APP_NAME=minijava-s55-con-1433"
set "NAMESPACE=minijava-s55-con-1433"
set "SECRET_NAME=minijava-s55-con-1433-secrets"

echo === Deploying !APP_NAME! to AWS EKS ===

where aws >nul 2>&1
if !ERRORLEVEL! neq 0 (
  echo Error - the AWS CLI is not installed or not on the PATH
  exit /b 1
)
where kubectl >nul 2>&1
if !ERRORLEVEL! neq 0 (
  echo Error - kubectl is not installed or not on the PATH
  exit /b 1
)
if not exist "kubernetes\deployment.yaml" (
  echo Error - run this script from the repository root, kubernetes\deployment.yaml was not found
  exit /b 1
)

set /p "AWS_REGION=Enter the AWS region (for example us-east-1): "
set /p "CLUSTER_NAME=Enter the EKS cluster name: "
set /p "IMAGE_URI=Enter the image URI (registry/repository and tag, as printed by build-push.bat): "

if "!AWS_REGION!"=="" (
  echo Error - the AWS region is required
  exit /b 1
)
if "!CLUSTER_NAME!"=="" (
  echo Error - the cluster name is required
  exit /b 1
)
if "!IMAGE_URI!"=="" (
  echo Error - the image URI is required
  exit /b 1
)

set /p "REDIS_HOST=Enter value for REDIS_HOST (or press Enter to skip): "
set /p "REDIS_PORT=Enter value for REDIS_PORT (or press Enter to skip): "
if "!REDIS_HOST!"=="" set "REDIS_HOST=localhost"
if "!REDIS_PORT!"=="" set "REDIS_PORT=6379"

echo Credentials are not echoed and are stored in the Kubernetes Secret !SECRET_NAME!.
call :readsecret DB_PASSWORD
call :readsecret REDIS_PASSWORD
call :readsecret EXTERNAL_API_KEY
call :readsecret PAYMENT_SERVICE_PASSWORD
call :readsecret JWT_SECRET
call :readsecret ADMIN_PASSWORD
call :readsecret ENCRYPTION_KEY
call :readsecret MONITORING_PASSWORD
call :readsecret RABBITMQ_PASSWORD

echo Configuring kubectl for the cluster !CLUSTER_NAME! in !AWS_REGION!...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo Error - aws eks update-kubeconfig failed
  exit /b 1
)

kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo Error - the cluster is not reachable
  exit /b 1
)

echo Applying the namespace...
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (
  echo Error - namespace apply failed
  exit /b 1
)

echo Creating the Secret !SECRET_NAME!...
kubectl create secret generic !SECRET_NAME! -n !NAMESPACE! "--from-literal=DB_PASSWORD=!DB_PASSWORD!" "--from-literal=REDIS_PASSWORD=!REDIS_PASSWORD!" "--from-literal=EXTERNAL_API_KEY=!EXTERNAL_API_KEY!" "--from-literal=PAYMENT_SERVICE_PASSWORD=!PAYMENT_SERVICE_PASSWORD!" "--from-literal=JWT_SECRET=!JWT_SECRET!" "--from-literal=ADMIN_PASSWORD=!ADMIN_PASSWORD!" "--from-literal=ENCRYPTION_KEY=!ENCRYPTION_KEY!" "--from-literal=MONITORING_PASSWORD=!MONITORING_PASSWORD!" "--from-literal=RABBITMQ_PASSWORD=!RABBITMQ_PASSWORD!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (
  echo Error - secret creation failed
  exit /b 1
)

echo Replacing the placeholders in kubernetes\deployment.yaml...
copy /y "kubernetes\deployment.yaml" "kubernetes\deployment.yaml.orig" >nul
powershell -NoProfile -Command "$c = Get-Content -Raw 'kubernetes/deployment.yaml'; $c = $c.Replace('{{IMAGE_URI}}', $env:IMAGE_URI).Replace('{{NAMESPACE}}', $env:NAMESPACE).Replace('{{REDIS_HOST}}', $env:REDIS_HOST).Replace('{{REDIS_PORT}}', $env:REDIS_PORT); Set-Content -Path 'kubernetes/deployment.yaml' -Value $c -NoNewline"
if !ERRORLEVEL! neq 0 (
  echo Error - placeholder replacement failed
  move /y "kubernetes\deployment.yaml.orig" "kubernetes\deployment.yaml" >nul
  exit /b 1
)

echo Applying the deployment, the service and the ingress...
kubectl apply -f kubernetes\deployment.yaml
set "APPLY_RESULT=!ERRORLEVEL!"
move /y "kubernetes\deployment.yaml.orig" "kubernetes\deployment.yaml" >nul
if !APPLY_RESULT! neq 0 goto :rollback_help
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 goto :rollback_help
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 goto :rollback_help

echo Waiting for the rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 goto :rollback_help

kubectl get pods,svc,ingress -n !NAMESPACE!

set "INGRESS_HOST="
kubectl get ingress !APP_NAME!-ingress -n !NAMESPACE! -o jsonpath="{.status.loadBalancer.ingress[0].hostname}" > "%TEMP%\mj_ingress.txt" 2>nul
if exist "%TEMP%\mj_ingress.txt" (
  set /p "INGRESS_HOST="<"%TEMP%\mj_ingress.txt"
  del "%TEMP%\mj_ingress.txt"
)
if not "!INGRESS_HOST!"=="" (
  echo Application URL - http://!INGRESS_HOST!
) else (
  echo The load balancer address is not ready yet. Check later with kubectl get ingress -n !NAMESPACE!
)
echo The ingress host rule is !APP_NAME!.example.com, point your DNS name at the load balancer address.
echo Deployment finished.
endlocal
exit /b 0

:rollback_help
echo.
echo The deployment failed. To roll back
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
echo   kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE!
echo To inspect the problem
echo   kubectl describe pods -n !NAMESPACE!
echo   kubectl logs deployment/!APP_NAME! -n !NAMESPACE!
exit /b 1

:readsecret
set "SECRET_FILE=%TEMP%\mj_secret_%RANDOM%.txt"
powershell -NoProfile -Command "$s = Read-Host 'Enter value for %~1' -AsSecureString; $p = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)); Set-Content -Path $env:SECRET_FILE -Value $p -NoNewline"
set "%~1="
if exist "!SECRET_FILE!" (
  set /p "%~1="<"!SECRET_FILE!"
  del "!SECRET_FILE!"
)
exit /b 0
