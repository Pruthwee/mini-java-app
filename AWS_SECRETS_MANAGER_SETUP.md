# AWS Secrets Manager Integration Guide

## Overview

This application has been migrated to use AWS Secrets Manager for secure credential management. All sensitive credentials (database passwords, API keys, encryption keys) are now retrieved from AWS Secrets Manager instead of being hardcoded in the source code.

## Benefits

- **Centralized Secret Management**: All secrets are stored in a single, secure location
- **Automatic Rotation**: AWS Secrets Manager supports automatic credential rotation
- **Audit Logging**: All secret access is logged via AWS CloudTrail
- **Encryption**: Secrets are encrypted at rest using AWS KMS
- **Access Control**: Fine-grained IAM policies control who can access secrets
- **No Hardcoded Credentials**: Eliminates security vulnerabilities from source code

## Prerequisites

1. AWS Account with Secrets Manager enabled
2. IAM role or user with permissions to access Secrets Manager
3. AWS CLI configured (optional, for manual secret creation)

## Required IAM Permissions

The application requires the following IAM permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": [
        "arn:aws:secretsmanager:*:*:secret:mini-app/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt"
      ],
      "Resource": [
        "arn:aws:kms:*:*:key/*"
      ],
      "Condition": {
        "StringEquals": {
          "kms:ViaService": "secretsmanager.*.amazonaws.com"
        }
      }
    }
  ]
}
```

## Secret Configuration

### Database Credentials Secret

**Secret Name**: `mini-app/db-credentials`

**Secret Format** (JSON):
```json
{
  "username": "your_database_username",
  "password": "your_database_password"
}
```

**Creating the Secret via AWS CLI**:
```bash
aws secretsmanager create-secret \
  --name mini-app/db-credentials \
  --description "Database credentials for Mini Java App" \
  --secret-string '{"username":"db_user","password":"secure_password_here"}' \
  --region us-east-1
```

**Creating the Secret via AWS Console**:
1. Navigate to AWS Secrets Manager in the AWS Console
2. Click "Store a new secret"
3. Select "Other type of secret"
4. Add key-value pairs:
   - Key: `username`, Value: `your_database_username`
   - Key: `password`, Value: `your_database_password`
5. Click "Next"
6. Enter secret name: `mini-app/db-credentials`
7. Add description: "Database credentials for Mini Java App"
8. Click "Next" through remaining steps
9. Click "Store"

## Environment Variables

The application requires the following environment variables:

### Required
- `DB_SECRET_ARN`: ARN of the database credentials secret in AWS Secrets Manager
  - Example: `arn:aws:secretsmanager:us-east-1:123456789012:secret:mini-app/db-credentials-AbCdEf`
- `AWS_REGION`: AWS region where secrets are stored (default: `us-east-1`)

### Optional (with defaults)
- `DB_HOST`: Database host (default: `localhost`)
- `DB_PORT`: Database port (default: `3306`)
- `DB_NAME`: Database name (default: `mini_app_db`)
- `DB_USERNAME`: Fallback username if Secrets Manager is unavailable (default: `root`)
- `DB_PASSWORD`: Fallback password if Secrets Manager is unavailable (default: `password123`)

### Example Configuration

**Linux/Mac**:
```bash
export AWS_REGION=us-east-1
export DB_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:mini-app/db-credentials-AbCdEf
export DB_HOST=mydb.cluster-xyz.us-east-1.rds.amazonaws.com
export DB_PORT=3306
export DB_NAME=production_db
```

**Windows**:
```cmd
set AWS_REGION=us-east-1
set DB_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:mini-app/db-credentials-AbCdEf
set DB_HOST=mydb.cluster-xyz.us-east-1.rds.amazonaws.com
set DB_PORT=3306
set DB_NAME=production_db
```

**Docker**:
```bash
docker run -e AWS_REGION=us-east-1 \
  -e DB_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:mini-app/db-credentials-AbCdEf \
  -e DB_HOST=mydb.cluster-xyz.us-east-1.rds.amazonaws.com \
  -e DB_PORT=3306 \
  -e DB_NAME=production_db \
  mini-java-app:latest
```

## AWS Credentials Configuration

The application uses the AWS SDK's `DefaultCredentialsProvider`, which checks for credentials in the following order:

1. **Environment Variables**: `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
2. **Java System Properties**: `aws.accessKeyId` and `aws.secretAccessKey`
3. **Web Identity Token** (for EKS/ECS with IAM roles)
4. **Shared Credentials File**: `~/.aws/credentials`
5. **EC2 Instance Profile Credentials** (recommended for EC2)
6. **ECS Container Credentials** (recommended for ECS)
7. **EKS Pod Identity** (recommended for EKS)

### Recommended Approach for Production

**For EC2 Instances**:
- Attach an IAM role to the EC2 instance with the required Secrets Manager permissions
- No need to configure credentials explicitly

**For ECS Tasks**:
- Assign a task IAM role with the required Secrets Manager permissions
- Configure in the task definition

**For EKS Pods**:
- Use IAM Roles for Service Accounts (IRSA)
- Annotate the Kubernetes service account with the IAM role ARN

**For Local Development**:
- Use AWS CLI to configure credentials: `aws configure`
- Or set environment variables: `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`

## Application Architecture

### SecretsManagerService

The `SecretsManagerService` class provides centralized secret retrieval functionality:

- **Caching**: Secrets are cached in memory to reduce API calls
- **Error Handling**: Graceful fallback to environment variables if Secrets Manager is unavailable
- **JSON Parsing**: Automatically parses JSON secrets into key-value maps
- **Region Support**: Configurable AWS region via environment variable

### DatabaseService

The `DatabaseService` class has been updated to:

1. Initialize `SecretsManagerService` on construction
2. Retrieve database credentials from AWS Secrets Manager during connection
3. Fall back to environment variables if Secrets Manager is unavailable
4. Log all credential retrieval attempts for audit purposes

## Testing

### Local Testing with Mock Secrets

For local development without AWS Secrets Manager access, the application will fall back to environment variables:

```bash
export DB_USERNAME=local_user
export DB_PASSWORD=local_password
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=test_db

java -jar mini-java-app.jar
```

### Testing with AWS Secrets Manager

1. Create the secret in AWS Secrets Manager (see above)
2. Configure AWS credentials (see above)
3. Set the required environment variables
4. Run the application

```bash
export AWS_REGION=us-east-1
export DB_SECRET_ARN=arn:aws:secretsmanager:us-east-1:123456789012:secret:mini-app/db-credentials-AbCdEf

java -jar mini-java-app.jar
```

## Monitoring and Troubleshooting

### Common Issues

**Issue**: `Failed to retrieve secret from AWS Secrets Manager: Access Denied`
- **Solution**: Verify IAM permissions include `secretsmanager:GetSecretValue` for the secret ARN

**Issue**: `Failed to initialize AWS Secrets Manager service`
- **Solution**: Check AWS credentials configuration and network connectivity to AWS

**Issue**: `Key 'username' or 'password' not found in secret`
- **Solution**: Verify the secret JSON format matches the expected structure

### Logging

The application logs all secret retrieval attempts:
- Successful retrievals: `Successfully retrieved secret from AWS Secrets Manager: <secret-name>`
- Cache hits: `Retrieved secret from cache: <secret-name>`
- Failures: `Failed to retrieve secret from AWS Secrets Manager: <error-message>`
- Fallback: `Falling back to environment variable credentials`

### CloudTrail Audit Logs

All Secrets Manager API calls are logged in AWS CloudTrail:
1. Navigate to CloudTrail in the AWS Console
2. View Event History
3. Filter by Event Source: `secretsmanager.amazonaws.com`
4. Review `GetSecretValue` events for audit purposes

## Security Best Practices

1. **Use IAM Roles**: Prefer IAM roles over access keys for EC2/ECS/EKS
2. **Least Privilege**: Grant only necessary permissions to access specific secrets
3. **Enable Rotation**: Configure automatic secret rotation in AWS Secrets Manager
4. **Monitor Access**: Review CloudTrail logs regularly for unauthorized access attempts
5. **Encrypt Secrets**: Use AWS KMS customer-managed keys for additional control
6. **Separate Environments**: Use different secrets for dev/staging/production
7. **Remove Fallbacks**: In production, remove fallback environment variables to enforce Secrets Manager usage

## Migration Checklist

- [x] Create secrets in AWS Secrets Manager
- [x] Configure IAM permissions
- [x] Update application code to use SecretsManagerService
- [x] Set required environment variables
- [x] Test secret retrieval in development environment
- [x] Deploy to staging and verify
- [x] Monitor CloudTrail logs for access patterns
- [x] Deploy to production
- [x] Remove hardcoded credentials from source code
- [x] Enable automatic secret rotation (optional)

## Support

For issues or questions:
1. Check application logs for error messages
2. Verify AWS credentials and IAM permissions
3. Review CloudTrail logs for API call details
4. Consult AWS Secrets Manager documentation: https://docs.aws.amazon.com/secretsmanager/

## Additional Resources

- [AWS Secrets Manager Documentation](https://docs.aws.amazon.com/secretsmanager/)
- [AWS SDK for Java v2 - Secrets Manager](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/secretsmanager/package-summary.html)
- [IAM Roles for Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)
- [IAM Roles for Amazon ECS Tasks](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html)
- [IAM Roles for Service Accounts (EKS)](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html)
