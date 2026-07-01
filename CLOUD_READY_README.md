# Mini Java Application - Cloud-Ready Version

## Overview
This application has been transformed to be fully cloud-ready for AWS deployment. All cloud compatibility blockers have been resolved.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies (cr-java-0061, cr-java-0063)
**Problem**: Hardcoded file paths and java.io.File usage for persistent storage
**Solution**: 
- Replaced all file operations with Amazon S3 using AWS SDK for Java v2
- Configuration files now stored in S3 bucket
- Log files written to S3 instead of local file system
- All file paths externalized to environment variables

### 2. Database Credentials (cr-java-0069, cr-java-0113)
**Problem**: Hardcoded database credentials in source code
**Solution**:
- Integrated AWS Secrets Manager for secure credential storage
- Credentials retrieved at runtime with automatic rotation support
- Fallback to environment variables for local development
- All secrets externalized from source code

### 3. Connection Pooling (cr-java-0073)
**Problem**: Direct JDBC connections without pooling
**Solution**:
- Implemented HikariCP connection pool
- Configured with cloud-optimized settings
- Connection lifecycle managed by pool
- Compatible with Amazon RDS Proxy

### 4. Hardcoded Ports (cr-java-0077)
**Problem**: Hardcoded port numbers preventing dynamic assignment
**Solution**:
- All ports externalized to environment variables
- Server port configurable via SERVER_PORT
- Database port configurable via DB_PORT
- Redis port configurable via REDIS_PORT

### 5. Connection Timeouts (cr-java-0097)
**Problem**: Missing timeout configurations
**Solution**:
- Added connection timeouts to HikariCP configuration
- Query timeouts configurable via environment variables
- Leak detection enabled for cloud environments

### 6. Classpath Properties (cr-java-0070)
**Problem**: Configuration bundled in classpath
**Solution**:
- Configuration externalized to AWS Systems Manager Parameter Store
- Runtime configuration changes without redeployment
- Environment-specific configuration support

## Environment Variables

### Required Environment Variables

#### AWS Configuration
- `AWS_REGION` - AWS region (default: us-east-1)
- `S3_BUCKET_NAME` - S3 bucket for configuration and logs (default: mini-app-config-bucket)

#### Database Configuration
- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 3306)
- `DB_NAME` - Database name (default: mini_app_db)
- `DB_SECRET_NAME` - AWS Secrets Manager secret name (default: mini-app/database/credentials)

#### Connection Pool Configuration
- `DB_POOL_SIZE` - Maximum pool size (default: 10)
- `DB_POOL_MIN_IDLE` - Minimum idle connections (default: 2)
- `DB_CONNECTION_TIMEOUT` - Connection timeout in ms (default: 30000)
- `DB_IDLE_TIMEOUT` - Idle timeout in ms (default: 600000)
- `DB_MAX_LIFETIME` - Max connection lifetime in ms (default: 1800000)
- `DB_QUERY_TIMEOUT` - Query timeout in seconds (default: 30)

#### Server Configuration
- `SERVER_PORT` - Application server port (default: 8080)
- `SERVER_HOST` - Server bind address (default: 0.0.0.0)

#### Cache Configuration
- `REDIS_HOST` - Redis host (default: 127.0.0.1)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (optional)

#### External Services
- `EXTERNAL_API_URL` - External API endpoint
- `PAYMENT_SERVICE_URL` - Payment service endpoint

### Optional Environment Variables
- `CONFIG_S3_KEY` - S3 key for configuration file (default: config/app.properties)
- `LOG_S3_KEY_PREFIX` - S3 prefix for log files (default: logs/)
- `ENVIRONMENT` - Environment name (default: production)
- `DEBUG_ENABLED` - Enable debug mode (default: false)
- `LOGGING_LEVEL` - Logging level (default: INFO)

## AWS Secrets Manager Setup

### Database Credentials Secret
Create a secret in AWS Secrets Manager with the following JSON structure:

```json
{
  "username": "your-db-username",
  "password": "your-db-password"
}
```

Secret name: `mini-app/database/credentials` (or value of `DB_SECRET_NAME`)

### Other Secrets
- JWT Secret: `mini-app/jwt/secret`
- Admin Credentials: `mini-app/admin/credentials`
- Encryption Key: `mini-app/encryption/key`

## AWS S3 Setup

### S3 Bucket Structure
```
mini-app-config-bucket/
├── config/
│   └── app.properties
├── logs/
│   └── app-{timestamp}.log
├── temp/
└── uploads/
```

### Required S3 Permissions
- `s3:GetObject` - Read configuration files
- `s3:PutObject` - Write log files
- `s3:ListBucket` - List bucket contents

## AWS IAM Permissions Required

### Secrets Manager
```json
{
  "Effect": "Allow",
  "Action": [
    "secretsmanager:GetSecretValue",
    "secretsmanager:DescribeSecret"
  ],
  "Resource": "arn:aws:secretsmanager:*:*:secret:mini-app/*"
}
```

### S3
```json
{
  "Effect": "Allow",
  "Action": [
    "s3:GetObject",
    "s3:PutObject",
    "s3:ListBucket"
  ],
  "Resource": [
    "arn:aws:s3:::mini-app-config-bucket",
    "arn:aws:s3:::mini-app-config-bucket/*"
  ]
}
```

### Systems Manager Parameter Store
```json
{
  "Effect": "Allow",
  "Action": [
    "ssm:GetParameter",
    "ssm:GetParameters"
  ],
  "Resource": "arn:aws:ssm:*:*:parameter/mini-app/*"
}
```

## Dependencies Added

### AWS SDK for Java v2
- `software.amazon.awssdk:s3` - S3 client
- `software.amazon.awssdk:secretsmanager` - Secrets Manager client
- `software.amazon.awssdk:ssm` - Systems Manager client

### Connection Pooling
- `com.zaxxer:HikariCP` - High-performance JDBC connection pool

### JSON Processing
- `com.google.code.gson:gson` - JSON parsing for AWS Secrets

## Deployment Options

### AWS ECS (Elastic Container Service)
- Environment variables configured in task definition
- IAM role attached to task for AWS service access
- S3 bucket and Secrets Manager accessible via VPC endpoints

### AWS EKS (Elastic Kubernetes Service)
- Environment variables configured in Kubernetes deployment
- IAM roles for service accounts (IRSA) for AWS access
- ConfigMaps and Secrets for configuration

### AWS Elastic Beanstalk
- Environment variables configured in Beanstalk environment
- IAM instance profile for AWS service access
- Auto-scaling and load balancing included

### AWS Lambda (with modifications)
- Requires additional changes for serverless deployment
- Environment variables configured in Lambda function
- IAM execution role for AWS service access

## Local Development

For local development without AWS services:

1. Set environment variables in your IDE or shell
2. Use default values for AWS resources
3. Optionally run LocalStack for AWS service emulation

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=mini_app_db
export DB_USERNAME=root
export DB_PASSWORD=password
export SERVER_PORT=8080
export AWS_REGION=us-east-1
```

## Build and Run

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar target/mini-java-app-1.0.0.jar
```

## Cloud-Native Compliance

This application now follows the 12-factor app methodology:
1. ✅ Codebase - Single codebase tracked in version control
2. ✅ Dependencies - Explicitly declared in pom.xml
3. ✅ Config - Externalized to environment variables
4. ✅ Backing services - Treated as attached resources (S3, Secrets Manager, RDS)
5. ✅ Build, release, run - Strictly separated
6. ✅ Processes - Stateless (no local file system dependencies)
7. ✅ Port binding - Configurable via environment variables
8. ✅ Concurrency - Scalable via process model
9. ✅ Disposability - Fast startup and graceful shutdown
10. ✅ Dev/prod parity - Same configuration mechanism across environments
11. ✅ Logs - Treated as event streams (S3 storage)
12. ✅ Admin processes - Run as one-off processes

## Security Best Practices

- ✅ No hardcoded credentials in source code
- ✅ Secrets managed by AWS Secrets Manager
- ✅ Automatic credential rotation support
- ✅ Encrypted secrets at rest and in transit
- ✅ IAM-based access control
- ✅ Audit logging via AWS CloudTrail

## Monitoring and Observability

- Application logs written to S3 for centralized logging
- CloudWatch integration for metrics and alarms
- Health check endpoints for load balancer integration
- Distributed tracing ready (add AWS X-Ray SDK if needed)

## Next Steps

1. Create S3 bucket and configure bucket policy
2. Create secrets in AWS Secrets Manager
3. Configure IAM roles with required permissions
4. Set up RDS database instance
5. Deploy to AWS ECS, EKS, or Elastic Beanstalk
6. Configure CloudWatch alarms and dashboards
7. Set up AWS RDS Proxy for connection pooling optimization
