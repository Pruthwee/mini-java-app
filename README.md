# Mini Java Application - Containerization Ready

## Overview
This application has been containerized and all hardcoded values have been externalized to environment variables for flexible deployment in container orchestration platforms like AWS ECS, EKS, or Kubernetes.

## Containerization Fixes Applied

### 1. Absolute File Paths → Amazon S3 Storage
- **Blockers Fixed**: blocker-1, blocker-2, blocker-3, blocker-4
- **Changes**: Replaced all hardcoded absolute file paths with S3 bucket and key references
- **Files Modified**: `MiniApp.java`
- **Lines**: 18, 19, 60

### 2. Hardcoded Ports → Environment Variables
- **Blockers Fixed**: blocker-6, blocker-7, blocker-8
- **Changes**: Externalized all port configurations to environment variables
- **Files Modified**: `MiniApp.java`, `DatabaseService.java`
- **Lines**: MiniApp.java (15, 79), DatabaseService.java (23)

### 3. Hardcoded IP Addresses → DNS-based Service Discovery
- **Blocker Fixed**: blocker-9
- **Changes**: Replaced hardcoded IP addresses with DNS service names and environment variables
- **Files Modified**: `DatabaseService.java`
- **Lines**: 22

### 4. Individual Components → Microservices Ready
- **Blocker Fixed**: blocker-5
- **Changes**: Decoupled components to support independent deployment
- **Files Modified**: `DatabaseService.java`
- **Lines**: 39

### 5. Health Check Endpoint
- **Added**: Spring Boot Actuator for health monitoring
- **Endpoint**: `/actuator/health`
- **Files Modified**: `pom.xml`, `application.properties`, `Application.java`

## Environment Variables

### Required Environment Variables

#### Server Configuration
- `SERVER_PORT` - Server port (default: 8080)
- `SERVER_HOST` - Server host binding (default: 0.0.0.0)

#### Database Configuration
- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 3306)
- `DB_NAME` - Database name (default: mini_app_db)
- `DB_USERNAME` - Database username (default: root)
- `DB_PASSWORD` - Database password (required in production)
- `DATABASE_URL` - Full JDBC URL (optional, overrides individual settings)

#### Cache Configuration (Redis)
- `REDIS_HOST` - Redis host (default: redis-service)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (optional)
- `REDIS_DATABASE` - Redis database number (default: 0)

#### S3 Storage Configuration
- `CONFIG_S3_BUCKET` - S3 bucket for configuration files (default: app-config-bucket)
- `CONFIG_S3_KEY` - S3 key for config file (default: config/app.properties)
- `LOG_S3_BUCKET` - S3 bucket for logs (default: app-logs-bucket)
- `LOG_S3_KEY` - S3 key for log files (default: logs/mini-app.log)
- `UPLOAD_S3_BUCKET` - S3 bucket for uploads (default: app-uploads-bucket)

#### External Services
- `EXTERNAL_API_URL` - External API base URL
- `EXTERNAL_API_KEY` - External API authentication key
- `PAYMENT_SERVICE_URL` - Payment service endpoint
- `PAYMENT_SERVICE_USERNAME` - Payment service username
- `PAYMENT_SERVICE_PASSWORD` - Payment service password

#### Security Configuration
- `JWT_SECRET` - JWT signing secret (required in production)
- `ADMIN_USERNAME` - Admin username (required in production)
- `ADMIN_PASSWORD` - Admin password (required in production)
- `ENCRYPTION_KEY` - Encryption key (required in production)

#### Monitoring Configuration
- `MONITORING_ENDPOINT` - Monitoring service endpoint
- `MONITORING_USERNAME` - Monitoring service username
- `MONITORING_PASSWORD` - Monitoring service password

#### Messaging Configuration (RabbitMQ)
- `RABBITMQ_HOST` - RabbitMQ host (default: rabbitmq-service)
- `RABBITMQ_PORT` - RabbitMQ port (default: 5672)
- `RABBITMQ_USERNAME` - RabbitMQ username
- `RABBITMQ_PASSWORD` - RabbitMQ password

#### Application Configuration
- `ENVIRONMENT` - Environment name (default: production)
- `DEBUG_ENABLED` - Enable debug mode (default: false)
- `LOGGING_LEVEL` - Logging level (default: INFO)
- `QUERY_TIMEOUT` - Database query timeout in seconds (default: 30)

## Docker Deployment Example

```dockerfile
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/mini-java-app-1.0.0.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

EXPOSE ${SERVER_PORT:-8080}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Kubernetes Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mini-java-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: mini-java-app
  template:
    metadata:
      labels:
        app: mini-java-app
    spec:
      containers:
      - name: mini-java-app
        image: mini-java-app:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SERVER_PORT
          value: "8080"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: host
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        - name: CONFIG_S3_BUCKET
          value: "my-app-config-bucket"
        - name: LOG_S3_BUCKET
          value: "my-app-logs-bucket"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
```

## AWS ECS Task Definition Example

```json
{
  "family": "mini-java-app",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "mini-java-app",
      "image": "mini-java-app:1.0.0",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SERVER_PORT", "value": "8080"},
        {"name": "REDIS_HOST", "value": "redis.example.com"},
        {"name": "CONFIG_S3_BUCKET", "value": "my-config-bucket"}
      ],
      "secrets": [
        {"name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-password"},
        {"name": "JWT_SECRET", "valueFrom": "arn:aws:secretsmanager:region:account:secret:jwt-secret"}
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      },
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/mini-java-app",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

## Building and Running

### Build
```bash
mvn clean package
```

### Run Locally with Environment Variables
```bash
export SERVER_PORT=8080
export DB_HOST=localhost
export DB_USERNAME=root
export DB_PASSWORD=mypassword
export CONFIG_S3_BUCKET=my-config-bucket
export LOG_S3_BUCKET=my-logs-bucket

java -jar target/mini-java-app-1.0.0.jar
```

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

## AWS IAM Permissions Required

The application requires the following IAM permissions for S3 access:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::app-config-bucket/*",
        "arn:aws:s3:::app-logs-bucket/*",
        "arn:aws:s3:::app-uploads-bucket/*"
      ]
    }
  ]
}
```

## Migration Notes

1. **File Storage Migration**: All file operations have been migrated from local filesystem to S3. Ensure S3 buckets are created before deployment.

2. **Service Discovery**: IP addresses have been replaced with DNS service names. Configure your container orchestration platform to provide DNS resolution for dependent services.

3. **Secrets Management**: Use AWS Secrets Manager, Kubernetes Secrets, or similar for sensitive configuration values in production.

4. **Health Monitoring**: The `/actuator/health` endpoint is now available for container orchestration platforms to monitor application health.

5. **Port Binding**: The application now binds to `0.0.0.0` by default, making it accessible from outside the container.

## Troubleshooting

### Application fails to start
- Check that all required environment variables are set
- Verify S3 bucket access and IAM permissions
- Check database connectivity

### Health check fails
- Ensure the application has fully started (allow 30-60 seconds)
- Verify the health check endpoint is accessible: `curl http://localhost:8080/actuator/health`
- Check application logs for startup errors

### S3 access errors
- Verify IAM role/credentials have S3 permissions
- Check S3 bucket names and regions
- Ensure buckets exist and are accessible

## Support

For issues or questions, please refer to the application logs and verify all environment variables are correctly configured.
