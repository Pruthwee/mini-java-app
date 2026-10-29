#!/bin/bash
# Deploys the pushed mini-java-app image to AWS ECS Fargate with the AWS CLI.
# Run from the repository root: ./scripts/deploy-image.sh
set -e
set -o pipefail

NAME="mini-java-app"
SERVICE_NAME="${NAME}-service"
TASK_FAMILY="${NAME}-task"
LOG_GROUP="/ecs/${NAME}"
export AWS_PAGER=""

echo "=== Deploy ${NAME} to AWS ECS Fargate ==="

if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR - the AWS CLI is not installed or not in PATH"
  exit 1
fi
if [ ! -f ecs/task-definition.json ] || [ ! -f ecs/service-definition.json ]; then
  echo "ERROR - run this script from the repository root, ecs/*.json not found"
  exit 1
fi

read -r -p "AWS region (for example us-east-1): " AWS_REGION
read -r -p "ECS cluster name: " CLUSTER_NAME
read -r -p "VPC ID: " VPC_ID
read -r -p "Subnet IDs, comma-separated, at least two: " SUBNET_IDS
read -r -p "Security group ID (must allow the application port 8080 and the health port 8081): " SECURITY_GROUP
read -r -p "Image URI (for example 123456789.dkr.ecr.us-east-1.amazonaws.com/${NAME}:latest): " IMAGE_URI
read -r -p "EFS file system ID (for the /app/data volume): " EFS_FILE_SYSTEM_ID
read -r -p "EFS access point ID (POSIX user 10001:10001, path /app/data): " EFS_ACCESS_POINT_ID

for V in AWS_REGION CLUSTER_NAME VPC_ID SUBNET_IDS SECURITY_GROUP IMAGE_URI EFS_FILE_SYSTEM_ID EFS_ACCESS_POINT_ID; do
  if [ -z "${!V}" ]; then
    echo "ERROR - $V is required"
    exit 1
  fi
done

SUBNET_IDS=$(echo "$SUBNET_IDS" | tr -d ' ')
SUBNET_1=$(echo "$SUBNET_IDS" | cut -d, -f1)
SUBNET_2=$(echo "$SUBNET_IDS" | cut -d, -f2)
if [ -z "$SUBNET_1" ] || [ -z "$SUBNET_2" ]; then
  echo "ERROR - at least two subnet IDs are required"
  exit 1
fi
SUBNETS_SPACED=$(echo "$SUBNET_IDS" | tr ',' ' ')

export AWS_DEFAULT_REGION="$AWS_REGION"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "AWS account: ${ACCOUNT_ID}"

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

# --- Cluster ---
echo "Step 1 - checking the ECS cluster ${CLUSTER_NAME}..."
CLUSTER_STATUS=$(aws ecs describe-clusters --clusters "$CLUSTER_NAME" --query 'clusters[0].status' --output text 2>/dev/null || true)
if [ "$CLUSTER_STATUS" != "ACTIVE" ]; then
  echo "Creating the cluster ${CLUSTER_NAME}..."
  aws ecs create-cluster --cluster-name "$CLUSTER_NAME" >/dev/null
else
  echo "Cluster exists"
fi

# --- Log group ---
echo "Step 2 - checking the log group ${LOG_GROUP}..."
EXISTING_LOG_GROUP=$(aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --query "logGroups[?logGroupName=='${LOG_GROUP}'].logGroupName | [0]" --output text)
if [ -z "$EXISTING_LOG_GROUP" ] || [ "$EXISTING_LOG_GROUP" = "None" ]; then
  aws logs create-log-group --log-group-name "$LOG_GROUP"
  echo "Log group created"
else
  echo "Log group exists"
fi

# --- Secrets Manager: the application's port settings, injected through the task definition secrets ---
store_secret() {
  # $1 = variable name, $2 = value; prints the secret ARN
  local SECRET_NAME="${NAME}/$1"
  local FOUND
  FOUND=$(aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --query ARN --output text 2>/dev/null || true)
  if [ -z "$FOUND" ] || [ "$FOUND" = "None" ]; then
    aws secretsmanager create-secret --name "$SECRET_NAME" --secret-string "$2" --query ARN --output text
  else
    aws secretsmanager put-secret-value --secret-id "$SECRET_NAME" --secret-string "$2" >/dev/null
    echo "$FOUND"
  fi
}

echo "Step 3 - storing the settings in Secrets Manager as ${NAME}/<VARIABLE>..."
read -r -p "APP_PORT, the application port [8080]: " APP_PORT_VALUE
APP_PORT_VALUE=${APP_PORT_VALUE:-8080}
read -r -p "REDIS_PORT, the cache port [6379]: " REDIS_PORT_VALUE
REDIS_PORT_VALUE=${REDIS_PORT_VALUE:-6379}
APP_PORT_SECRET_ARN=$(store_secret APP_PORT "$APP_PORT_VALUE")
REDIS_PORT_SECRET_ARN=$(store_secret REDIS_PORT "$REDIS_PORT_VALUE")
echo "Secrets stored"

# --- Load balancer ---
TARGET_GROUP_ARN=""
LB_DNS=""
read -r -p "Do you need a load balancer for this service? (y/n): " NEED_LB
if [ "$NEED_LB" = "y" ] || [ "$NEED_LB" = "Y" ]; then
  echo "Step 4 - creating the Application Load Balancer and target group..."
  TARGET_GROUP_ARN=$(aws elbv2 describe-target-groups --names "${NAME}-tg" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
  if [ -z "$TARGET_GROUP_ARN" ] || [ "$TARGET_GROUP_ARN" = "None" ]; then
    TARGET_GROUP_ARN=$(aws elbv2 create-target-group --name "${NAME}-tg" --protocol HTTP --port 8081 --vpc-id "$VPC_ID" \
      --target-type ip --health-check-path /health --health-check-port 8081 \
      --query 'TargetGroups[0].TargetGroupArn' --output text)
  fi
  LB_ARN=$(aws elbv2 describe-load-balancers --names "${NAME}-alb" --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || true)
  if [ -z "$LB_ARN" ] || [ "$LB_ARN" = "None" ]; then
    # shellcheck disable=SC2086
    LB_ARN=$(aws elbv2 create-load-balancer --name "${NAME}-alb" --type application --scheme internet-facing \
      --subnets $SUBNETS_SPACED --security-groups "$SECURITY_GROUP" \
      --query 'LoadBalancers[0].LoadBalancerArn' --output text)
    aws elbv2 create-listener --load-balancer-arn "$LB_ARN" --protocol HTTP --port 80 \
      --default-actions "Type=forward,TargetGroupArn=${TARGET_GROUP_ARN}" >/dev/null
  fi
  LB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns "$LB_ARN" --query 'LoadBalancers[0].DNSName' --output text)
  echo "Load balancer DNS name: ${LB_DNS}"
else
  echo "Step 4 - no load balancer"
fi

# --- Render the deployment files into a temporary folder ---
echo "Step 5 - preparing the task and service definitions..."
render() {
  sed -e "s|{{IMAGE_URI}}|${IMAGE_URI}|g" \
      -e "s|{{AWS_REGION}}|${AWS_REGION}|g" \
      -e "s|{{ACCOUNT_ID}}|${ACCOUNT_ID}|g" \
      -e "s|{{SUBNET_1}}|${SUBNET_1}|g" \
      -e "s|{{SUBNET_2}}|${SUBNET_2}|g" \
      -e "s|{{SECURITY_GROUP}}|${SECURITY_GROUP}|g" \
      -e "s|{{CLUSTER_NAME}}|${CLUSTER_NAME}|g" \
      -e "s|{{EFS_FILE_SYSTEM_ID}}|${EFS_FILE_SYSTEM_ID}|g" \
      -e "s|{{EFS_ACCESS_POINT_ID}}|${EFS_ACCESS_POINT_ID}|g" \
      -e "s|{{APP_PORT_SECRET_ARN}}|${APP_PORT_SECRET_ARN}|g" \
      -e "s|{{REDIS_PORT_SECRET_ARN}}|${REDIS_PORT_SECRET_ARN}|g" \
      -e "s|{{TARGET_GROUP_ARN}}|${TARGET_GROUP_ARN}|g" \
      "$1" > "$2"
}
render ecs/task-definition.json "$WORK_DIR/task-definition.json"
render ecs/service-definition.json "$WORK_DIR/service-definition.json"

if [ -z "$TARGET_GROUP_ARN" ]; then
  # no load balancer: take the loadBalancers section out
  sed -i.bak '/"loadBalancers"/,/^  \],$/d' "$WORK_DIR/service-definition.json"
else
  awk '{ print } /"desiredCount"/ { print "  \"healthCheckGracePeriodSeconds\": 300," }' "$WORK_DIR/service-definition.json" > "$WORK_DIR/service-definition.tmp"
  mv "$WORK_DIR/service-definition.tmp" "$WORK_DIR/service-definition.json"
fi
rm -f "$WORK_DIR/service-definition.json.bak"

# --- Task definition ---
echo "Step 6 - registering the task definition..."
TASK_DEFINITION_ARN=$(aws ecs register-task-definition --cli-input-json "file://${WORK_DIR}/task-definition.json" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "Registered ${TASK_DEFINITION_ARN}"

# --- Service ---
echo "Step 7 - creating or updating the service ${SERVICE_NAME}..."
sed -i.bak "s|\"taskDefinition\": \"${TASK_FAMILY}\"|\"taskDefinition\": \"${TASK_DEFINITION_ARN}\"|" "$WORK_DIR/service-definition.json"
rm -f "$WORK_DIR/service-definition.json.bak"

EXISTING_SERVICE=$(aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" \
  --query "services[?status=='ACTIVE'].serviceName | [0]" --output text)
if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" = "None" ]; then
  aws ecs create-service --cli-input-json "file://${WORK_DIR}/service-definition.json" >/dev/null
  echo "Service created"
else
  aws ecs update-service --cluster "$CLUSTER_NAME" --service "$SERVICE_NAME" \
    --task-definition "$TASK_DEFINITION_ARN" --force-new-deployment >/dev/null
  echo "Service updated"
fi

echo "Step 8 - waiting for the service to become stable, this can take a few minutes..."
aws ecs wait services-stable --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME"

echo "=== Deployment result ==="
aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" \
  --query 'services[0].{status:status,desired:desiredCount,running:runningCount,pending:pendingCount}' --output table
RUNNING_COUNT=$(aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" --query 'services[0].runningCount' --output text)
echo "Running tasks: ${RUNNING_COUNT}"
if [ -n "$LB_DNS" ]; then
  echo "Load balancer DNS name: ${LB_DNS}  (health check: http://${LB_DNS}/health)"
fi
echo "Log group: ${LOG_GROUP}"

cat <<'EOF'

Troubleshooting
- Task stops with "CannotPullContainerError": check the image URI and that the execution role can pull from ECR.
- Task stops with "ResourceInitializationError" about secrets: the execution role needs secretsmanager:GetSecretValue on the mini-java-app/ secrets.
- Task hangs or fails at start with an EFS error: allow NFS (TCP 2049) from the task security group to the EFS mount targets, and use platform version 1.4.0 or later.
- Target group shows unhealthy targets: the security group must allow port 8081 from the load balancer, and GET /health must answer 200.
- "Permission denied" writing /app/data: the EFS access point must use POSIX user 10001:10001.
- Tasks killed with exit code 137: out of memory, raise the task memory in ecs/task-definition.json.
- View the logs: aws logs tail /ecs/mini-java-app --follow
EOF
