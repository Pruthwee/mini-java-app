# Cloud Configuration Guide

## Environment Variables for AWS Deployment

This application has been updated to support cloud-native configuration using environment variables. All hard-coded ports have been replaced with environment variable injection compatible with AWS services (ECS, EKS, Elastic Beanstalk).

### Required Environment Variables

#### Server Configuration
- **SERVER_PORT**: Application server port (default: 8080)
  - Used in: `MiniApp.java`
  - Example: `export SERVER_PORT=8080`
  - AWS Parameter Store path: `/mini-app/server/port`

#### Database Configuration
- **DB_PORT**: MySQL database port (default: 3306)
  - Used in: `DatabaseService.java`
  - Example: `export DB_PORT=3306`
  - AWS Parameter Store path: `/mini-app/database/port`

- **DB_HOST**: Database host (default: localhost)
  - Example: `export DB_HOST=mydb.cluster-xxx.us-east-1.rds.amazonaws.com`
  - AWS Parameter Store path: `/mini-app/database/host`

- **DB_NAME**: Database name (default: mini_app_db)
  - Example: `export DB_NAME=mini_app_db`
  - AWS Parameter Store path: `/mini-app/database/name`

#### Cache Configuration
- **REDIS_PORT**: Redis cache port (default: 6379)
  - Used in: `DatabaseService.java`
  - Example: `export REDIS_PORT=6379`
  - AWS Parameter Store path: `/mini-app/cache/redis/port`

- **REDIS_HOST**: Redis host (default: 127.0.0.1)
  - Example: `export REDIS_HOST=my-redis.xxx.cache.amazonaws.com`
  - AWS Parameter Store path: `/mini-app/cache/redis/host`

#### AWS S3 Configuration
- **S3_BUCKET_NAME**: S3 bucket for configuration and logs (default: mini-app-config-bucket)
- **CONFIG_OBJECT_KEY**: S3 object key for configuration (default: config/app.properties)
- **LOG_OBJECT_KEY**: S3 object key for logs (default: logs/mini-app.log)
- **AWS_REGION**: AWS region (default: us-east-1)

## AWS Deployment Options

### Option 1: AWS ECS (Elastic Container Service)

Configure environment variables in your ECS Task Definition:

```json
{
  "containerDefinitions": [
    {
      "name": "mini-java-app",
      "environment": [
        {
          "name": "SERVER_PORT",
          "value": "8080"
        },
        {
          "name": "DB_PORT",
          "value": "3306"
        },
        {
          "name": "REDIS_PORT",
          "value": "6379"
        }
      ],
      "secrets": [
        {
          "name": "DB_HOST",
          "valueFrom": "arn:aws:ssm:us-east-1:123456789012:parameter/mini-app/database/host"
        },
        {
          "name": "REDIS_HOST",
          "valueFrom": "arn:aws:ssm:us-east-1:123456789012:parameter/mini-app/cache/redis/host"
        }
      ]
    }
  ]
}
```

### Option 2: AWS EKS (Elastic Kubernetes Service)

Create a ConfigMap for non-sensitive configuration:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: mini-app-config
data:
  SERVER_PORT: "8080"
  DB_PORT: "3306"
  REDIS_PORT: "6379"
  AWS_REGION: "us-east-1"
```

Reference AWS Parameter Store for sensitive values using External Secrets Operator or AWS Secrets Manager.

### Option 3: AWS Elastic Beanstalk

Configure environment properties in `.ebextensions/environment.config`:

```yaml
option_settings:
  aws:elasticbeanstalk:application:environment:
    SERVER_PORT: 8080
    DB_PORT: 3306
    REDIS_PORT: 6379
    DB_HOST: '`{"Fn::GetOptionSetting": {"Namespace": "aws:rds:dbinstance", "OptionName": "endpoint"}}`'
```

## AWS Systems Manager Parameter Store Setup

Create parameters in AWS Systems Manager Parameter Store:

```bash
# Server configuration
aws ssm put-parameter \
  --name "/mini-app/server/port" \
  --value "8080" \
  --type "String" \
  --description "Application server port"

# Database configuration
aws ssm put-parameter \
  --name "/mini-app/database/port" \
  --value "3306" \
  --type "String" \
  --description "MySQL database port"

aws ssm put-parameter \
  --name "/mini-app/database/host" \
  --value "mydb.cluster-xxx.us-east-1.rds.amazonaws.com" \
  --type "String" \
  --description "RDS database endpoint"

# Cache configuration
aws ssm put-parameter \
  --name "/mini-app/cache/redis/port" \
  --value "6379" \
  --type "String" \
  --description "Redis cache port"

aws ssm put-parameter \
  --name "/mini-app/cache/redis/host" \
  --value "my-redis.xxx.cache.amazonaws.com" \
  --type "String" \
  --description "ElastiCache Redis endpoint"
```

## IAM Permissions Required

Ensure your ECS task role or EC2 instance profile has the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": [
        "arn:aws:ssm:us-east-1:123456789012:parameter/mini-app/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::mini-app-config-bucket/*"
      ]
    }
  ]
}
```

## Local Development

For local development, create a `.env` file or export environment variables:

```bash
export SERVER_PORT=8080
export DB_PORT=3306
export DB_HOST=localhost
export DB_NAME=mini_app_db
export REDIS_PORT=6379
export REDIS_HOST=127.0.0.1
export S3_BUCKET_NAME=mini-app-config-bucket
export AWS_REGION=us-east-1
```

## Verification

After deployment, verify that environment variables are correctly injected:

1. Check application logs for startup messages showing the configured ports
2. Verify database connections use the correct port from environment variables
3. Confirm Redis cache connections use the configured port
4. Test server accessibility on the configured SERVER_PORT

## Migration from Hard-coded Values

The following hard-coded values have been replaced:

| Component | Old Value | New Configuration |
|-----------|-----------|-------------------|
| Server Port | 8080 (hard-coded) | SERVER_PORT environment variable |
| Database Port | "3306" (hard-coded string) | DB_PORT environment variable |
| Redis Port | 6379 (hard-coded integer) | REDIS_PORT environment variable |

All changes maintain backward compatibility with default values if environment variables are not set.
