@echo off & setlocal enabledelayedexpansion
rem Deploys the pushed mini-java-app image to AWS ECS Fargate with the AWS CLI.
rem Run from the repository root: scripts\deploy-image.bat

set NAME=mini-java-app
set SERVICE_NAME=!NAME!-service
set TASK_FAMILY=!NAME!-task
set LOG_GROUP=/ecs/!NAME!
set AWS_PAGER=
set WORK_DIR=%TEMP%\!NAME!-deploy
if not exist "!WORK_DIR!" mkdir "!WORK_DIR!"

echo === Deploy !NAME! to AWS ECS Fargate ===

where aws >nul 2>&1
if !ERRORLEVEL! neq 0 (
  echo ERROR - the AWS CLI is not installed or not in PATH
  exit /b 1
)
if not exist "ecs\task-definition.json" (
  echo ERROR - run this script from the repository root, ecs files not found
  exit /b 1
)

set AWS_REGION=
set CLUSTER_NAME=
set VPC_ID=
set SUBNET_IDS=
set SECURITY_GROUP=
set IMAGE_URI=
set EFS_FILE_SYSTEM_ID=
set EFS_ACCESS_POINT_ID=
set /p AWS_REGION="AWS region, for example us-east-1: "
set /p CLUSTER_NAME="ECS cluster name: "
set /p VPC_ID="VPC ID: "
set /p SUBNET_IDS="Subnet IDs, comma-separated, at least two: "
set /p SECURITY_GROUP="Security group ID, it must allow the application port 8080 and the health port 8081: "
set /p IMAGE_URI="Image URI, for example 123456789.dkr.ecr.us-east-1.amazonaws.com/mini-java-app:latest: "
set /p EFS_FILE_SYSTEM_ID="EFS file system ID for the /app/data volume: "
set /p EFS_ACCESS_POINT_ID="EFS access point ID, POSIX user 10001 and path /app/data: "

for %%V in (AWS_REGION CLUSTER_NAME VPC_ID SUBNET_IDS SECURITY_GROUP IMAGE_URI EFS_FILE_SYSTEM_ID EFS_ACCESS_POINT_ID) do (
  if "!%%V!"=="" (
    echo ERROR - %%V is required
    exit /b 1
  )
)

set AWS_DEFAULT_REGION=!AWS_REGION!

rem Split the subnet list without for /f, with PowerShell
set SUBNETS_SPACED=
powershell -NoProfile -Command "($env:SUBNET_IDS -replace '\s','' -replace ',',' ')" > "!WORK_DIR!\subnets.txt"
set /p SUBNETS_SPACED=<"!WORK_DIR!\subnets.txt"
set SUBNET_COUNT=0
set SUBNET_1=
set SUBNET_2=
for %%S in (!SUBNETS_SPACED!) do (
  set /a SUBNET_COUNT+=1
  if "!SUBNET_COUNT!"=="1" set SUBNET_1=%%S
  if "!SUBNET_COUNT!"=="2" set SUBNET_2=%%S
)
if "!SUBNET_2!"=="" (
  echo ERROR - at least two subnet IDs are required
  exit /b 1
)

set ACCOUNT_ID=
aws sts get-caller-identity --query Account --output text > "!WORK_DIR!\account.txt"
if !ERRORLEVEL! neq 0 (
  echo ERROR - could not read the AWS account
  exit /b 1
)
set /p ACCOUNT_ID=<"!WORK_DIR!\account.txt"
echo AWS account is !ACCOUNT_ID!

rem --- Cluster ---
echo Step 1 - checking the ECS cluster !CLUSTER_NAME!...
set CLUSTER_STATUS=
aws ecs describe-clusters --clusters !CLUSTER_NAME! --query "clusters[0].status" --output text > "!WORK_DIR!\cluster.txt" 2>nul
set /p CLUSTER_STATUS=<"!WORK_DIR!\cluster.txt"
if not "!CLUSTER_STATUS!"=="ACTIVE" (
  echo Creating the cluster !CLUSTER_NAME!...
  aws ecs create-cluster --cluster-name !CLUSTER_NAME! >nul
  if !ERRORLEVEL! neq 0 (
    echo Cluster creation failed
    exit /b 1
  )
)

rem --- Log group ---
echo Step 2 - checking the log group !LOG_GROUP!...
set EXISTING_LOG_GROUP=
aws logs describe-log-groups --log-group-name-prefix !LOG_GROUP! --query "logGroups[?logGroupName=='!LOG_GROUP!'].logGroupName | [0]" --output text > "!WORK_DIR!\loggroup.txt"
set /p EXISTING_LOG_GROUP=<"!WORK_DIR!\loggroup.txt"
if "!EXISTING_LOG_GROUP!"=="" set EXISTING_LOG_GROUP=None
if "!EXISTING_LOG_GROUP!"=="None" (
  aws logs create-log-group --log-group-name !LOG_GROUP!
  if !ERRORLEVEL! neq 0 (
    echo Log group creation failed
    exit /b 1
  )
  echo Log group created
)

rem --- Secrets Manager: the application's port settings ---
echo Step 3 - storing the settings in Secrets Manager as !NAME!/VARIABLE...
set APP_PORT_VALUE=
set REDIS_PORT_VALUE=
set /p APP_PORT_VALUE="APP_PORT, the application port, empty for 8080: "
if "!APP_PORT_VALUE!"=="" set APP_PORT_VALUE=8080
set /p REDIS_PORT_VALUE="REDIS_PORT, the cache port, empty for 6379: "
if "!REDIS_PORT_VALUE!"=="" set REDIS_PORT_VALUE=6379
call :store_secret APP_PORT !APP_PORT_VALUE! APP_PORT_SECRET_ARN
if !ERRORLEVEL! neq 0 exit /b 1
call :store_secret REDIS_PORT !REDIS_PORT_VALUE! REDIS_PORT_SECRET_ARN
if !ERRORLEVEL! neq 0 exit /b 1
echo Secrets stored

rem --- Load balancer ---
set TARGET_GROUP_ARN=
set LB_DNS=
set NEED_LB=
set /p NEED_LB="Do you need a load balancer for this service? (y/n): "
if /i "!NEED_LB!"=="y" (
  echo Step 4 - creating the Application Load Balancer and target group...
  set TG_ARN=
  aws elbv2 describe-target-groups --names !NAME!-tg --query "TargetGroups[0].TargetGroupArn" --output text > "!WORK_DIR!\tg.txt" 2>nul
  set /p TG_ARN=<"!WORK_DIR!\tg.txt"
  if "!TG_ARN!"=="" set TG_ARN=None
  if "!TG_ARN!"=="None" (
    aws elbv2 create-target-group --name !NAME!-tg --protocol HTTP --port 8081 --vpc-id !VPC_ID! --target-type ip --health-check-path /health --health-check-port 8081 --query "TargetGroups[0].TargetGroupArn" --output text > "!WORK_DIR!\tg.txt"
    if !ERRORLEVEL! neq 0 (
      echo Target group creation failed
      exit /b 1
    )
    set /p TG_ARN=<"!WORK_DIR!\tg.txt"
  )
  set TARGET_GROUP_ARN=!TG_ARN!
  set LB_ARN=
  aws elbv2 describe-load-balancers --names !NAME!-alb --query "LoadBalancers[0].LoadBalancerArn" --output text > "!WORK_DIR!\lb.txt" 2>nul
  set /p LB_ARN=<"!WORK_DIR!\lb.txt"
  if "!LB_ARN!"=="" set LB_ARN=None
  if "!LB_ARN!"=="None" (
    aws elbv2 create-load-balancer --name !NAME!-alb --type application --scheme internet-facing --subnets !SUBNETS_SPACED! --security-groups !SECURITY_GROUP! --query "LoadBalancers[0].LoadBalancerArn" --output text > "!WORK_DIR!\lb.txt"
    if !ERRORLEVEL! neq 0 (
      echo Load balancer creation failed
      exit /b 1
    )
    set /p LB_ARN=<"!WORK_DIR!\lb.txt"
    aws elbv2 create-listener --load-balancer-arn !LB_ARN! --protocol HTTP --port 80 --default-actions Type=forward,TargetGroupArn=!TARGET_GROUP_ARN! >nul
    if !ERRORLEVEL! neq 0 (
      echo Listener creation failed
      exit /b 1
    )
  )
  aws elbv2 describe-load-balancers --load-balancer-arns !LB_ARN! --query "LoadBalancers[0].DNSName" --output text > "!WORK_DIR!\lbdns.txt"
  set /p LB_DNS=<"!WORK_DIR!\lbdns.txt"
  echo Load balancer DNS name is !LB_DNS!
) else (
  echo Step 4 - no load balancer
)

rem --- Render the deployment files ---
echo Step 5 - preparing the task and service definitions...
set R_IN=ecs\task-definition.json
set R_OUT=!WORK_DIR!\task-definition.json
call :render
if !ERRORLEVEL! neq 0 exit /b 1
set R_IN=ecs\service-definition.json
set R_OUT=!WORK_DIR!\service-definition.json
call :render
if !ERRORLEVEL! neq 0 exit /b 1

if "!TARGET_GROUP_ARN!"=="" (
  powershell -NoProfile -Command "$t = [IO.File]::ReadAllText($env:R_OUT); $t = [regex]::Replace($t,'(?s)\s*.loadBalancers.: \[.*?\r?\n  \],',''); [IO.File]::WriteAllText($env:R_OUT,$t)"
) else (
  powershell -NoProfile -Command "$t = [IO.File]::ReadAllText($env:R_OUT); $t = [regex]::Replace($t,'(.desiredCount.: 2,)','$1'+[char]10+'  '+[char]34+'healthCheckGracePeriodSeconds'+[char]34+': 300,'); [IO.File]::WriteAllText($env:R_OUT,$t)"
)
if !ERRORLEVEL! neq 0 (
  echo Could not prepare the service definition
  exit /b 1
)

rem --- Task definition ---
echo Step 6 - registering the task definition...
set TASK_DEFINITION_ARN=
aws ecs register-task-definition --cli-input-json file://!WORK_DIR:\=/!/task-definition.json --query "taskDefinition.taskDefinitionArn" --output text > "!WORK_DIR!\taskarn.txt"
if !ERRORLEVEL! neq 0 (
  echo Task definition registration failed
  exit /b 1
)
set /p TASK_DEFINITION_ARN=<"!WORK_DIR!\taskarn.txt"
echo Registered !TASK_DEFINITION_ARN!

rem --- Service ---
echo Step 7 - creating or updating the service !SERVICE_NAME!...
powershell -NoProfile -Command "$t = [IO.File]::ReadAllText($env:R_OUT); $t = [regex]::Replace($t,'(.taskDefinition.: .)mini-java-app-task(.)','${1}'+$env:TASK_DEFINITION_ARN+'${2}'); [IO.File]::WriteAllText($env:R_OUT,$t)"
if !ERRORLEVEL! neq 0 (
  echo Could not prepare the service definition
  exit /b 1
)
set EXISTING_SERVICE=
aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --query "services[?status=='ACTIVE'].serviceName | [0]" --output text > "!WORK_DIR!\service.txt"
set /p EXISTING_SERVICE=<"!WORK_DIR!\service.txt"
if "!EXISTING_SERVICE!"=="" set EXISTING_SERVICE=None
if "!EXISTING_SERVICE!"=="None" (
  aws ecs create-service --cli-input-json file://!WORK_DIR:\=/!/service-definition.json >nul
  if !ERRORLEVEL! neq 0 (
    echo Service creation failed
    exit /b 1
  )
  echo Service created
) else (
  aws ecs update-service --cluster !CLUSTER_NAME! --service !SERVICE_NAME! --task-definition !TASK_DEFINITION_ARN! --force-new-deployment >nul
  if !ERRORLEVEL! neq 0 (
    echo Service update failed
    exit /b 1
  )
  echo Service updated
)

echo Step 8 - waiting for the service to become stable, this can take a few minutes...
aws ecs wait services-stable --cluster !CLUSTER_NAME! --services !SERVICE_NAME!
if !ERRORLEVEL! neq 0 (
  echo The service did not become stable
  exit /b 1
)

echo === Deployment result ===
aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --query "services[0].{status:status,desired:desiredCount,running:runningCount,pending:pendingCount}" --output table
set RUNNING_COUNT=
aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --query "services[0].runningCount" --output text > "!WORK_DIR!\running.txt"
set /p RUNNING_COUNT=<"!WORK_DIR!\running.txt"
echo Running tasks !RUNNING_COUNT!
if not "!LB_DNS!"=="" echo Load balancer DNS name is !LB_DNS! and the health check path is /health
echo Log group is !LOG_GROUP!
echo.
echo Troubleshooting
echo - CannotPullContainerError means check the image URI and that the execution role can pull from ECR
echo - ResourceInitializationError about secrets means the execution role needs secretsmanager GetSecretValue on the mini-java-app secrets
echo - An EFS error at start means allow NFS on TCP 2049 from the task security group and use platform version 1.4.0 or later
echo - Unhealthy targets mean the security group must allow port 8081 from the load balancer and GET /health must answer 200
echo - Permission denied on /app/data means the EFS access point must use POSIX user 10001
echo - Exit code 137 means out of memory so raise the task memory in ecs\task-definition.json
echo - View the logs with aws logs tail /ecs/mini-java-app --follow

rmdir /s /q "!WORK_DIR!" >nul 2>&1
endlocal
exit /b 0

rem ---------------------------------------------------------------
rem Renders R_IN into R_OUT, replacing the placeholders
:render
powershell -NoProfile -Command "$t = [IO.File]::ReadAllText($env:R_IN); $t = $t.Replace('{{IMAGE_URI}}',$env:IMAGE_URI).Replace('{{AWS_REGION}}',$env:AWS_REGION).Replace('{{ACCOUNT_ID}}',$env:ACCOUNT_ID).Replace('{{SUBNET_1}}',$env:SUBNET_1).Replace('{{SUBNET_2}}',$env:SUBNET_2).Replace('{{SECURITY_GROUP}}',$env:SECURITY_GROUP).Replace('{{CLUSTER_NAME}}',$env:CLUSTER_NAME).Replace('{{EFS_FILE_SYSTEM_ID}}',$env:EFS_FILE_SYSTEM_ID).Replace('{{EFS_ACCESS_POINT_ID}}',$env:EFS_ACCESS_POINT_ID).Replace('{{APP_PORT_SECRET_ARN}}',$env:APP_PORT_SECRET_ARN).Replace('{{REDIS_PORT_SECRET_ARN}}',$env:REDIS_PORT_SECRET_ARN).Replace('{{TARGET_GROUP_ARN}}',$env:TARGET_GROUP_ARN); [IO.File]::WriteAllText($env:R_OUT,$t)"
if !ERRORLEVEL! neq 0 (
  echo Could not render the deployment files
  exit /b 1
)
exit /b 0

rem Stores a secret named mini-java-app/VARIABLE and returns its ARN in the variable named by the third argument
:store_secret
set SECRET_NAME=!NAME!/%1
set FOUND_ARN=
aws secretsmanager describe-secret --secret-id !SECRET_NAME! --query ARN --output text > "!WORK_DIR!\secret.txt" 2>nul
set /p FOUND_ARN=<"!WORK_DIR!\secret.txt"
if "!FOUND_ARN!"=="" set FOUND_ARN=None
if "!FOUND_ARN!"=="None" (
  aws secretsmanager create-secret --name !SECRET_NAME! --secret-string "%2" --query ARN --output text > "!WORK_DIR!\secret.txt"
  if !ERRORLEVEL! neq 0 (
    echo Secret creation failed for %1
    exit /b 1
  )
  set /p FOUND_ARN=<"!WORK_DIR!\secret.txt"
) else (
  aws secretsmanager put-secret-value --secret-id !SECRET_NAME! --secret-string "%2" >nul
  if !ERRORLEVEL! neq 0 (
    echo Secret update failed for %1
    exit /b 1
  )
)
set %3=!FOUND_ARN!
exit /b 0
