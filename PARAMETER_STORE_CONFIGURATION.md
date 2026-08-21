# AWS Systems Manager Parameter Store Configuration

## Overview

This application has been migrated from classpath-bundled properties files to AWS Systems Manager Parameter Store for externalized configuration management. This enables runtime configuration changes without requiring application redeployment.

## Benefits

1. **Runtime Configuration Updates**: Change configuration without redeploying the application
2. **Environment-Specific Configuration**: Different parameters for dev, staging, and production
3. **Secure Storage**: SecureString parameters are encrypted using AWS KMS
4. **Version Control**: Parameter Store maintains version history of all changes
5. **Access Control**: Fine-grained IAM permissions for parameter access
6. **Audit Trail**: CloudTrail logs all parameter access and modifications

## Configuration Structure

### Parameter Store Path

The application loads configuration from the following path:
```
/mini-app/config/
```

This path can be customized using the `PARAMETER_STORE_PATH` environment variable.

### Parameter Naming Convention

Parameters follow a hierarchical structure using forward slashes:

```
/mini-app/config/server/port
/mini-app/config/server/host
/mini-app/config/database/url
/mini-app/config/database/pool/max-connections
/mini-app/config/cache/redis/host
/mini-app/config/cache/redis/port
```

## Setting Up Parameters

### Using AWS CLI

Create parameters using the AWS CLI:

```bash
# String parameter
aws ssm put-parameter \
    --name "/mini-app/config/server/port" \
    --value "8080" \
    --type "String" \
    --description "Server port number"

# SecureString parameter (encrypted)
aws ssm put-parameter \
    --name "/mini-app/config/database/password" \
    --value "your-secure-password" \
    --type "SecureString" \
    --description "Database password"
```

### Using AWS Console

1. Navigate to AWS Systems Manager → Parameter Store
2. Click "Create parameter"
3. Enter parameter name (e.g., `/mini-app/config/server/port`)
4. Select parameter type (String or SecureString)
5. Enter parameter value
6. Click "Create parameter"

### Bulk Import

Create a JSON file with all parameters:

```json
{
  "/mini-app/config/server/port": "8080",
  "/mini-app/config/server/host": "0.0.0.0",
  "/mini-app/config/database/url": "jdbc:mysql://localhost:3306/mini_app_db",
  "/mini-app/config/database/pool/max-connections": "20"
}
```

Import using a script:

```bash
#!/bin/bash
while IFS= read -r line; do
    name=$(echo "$line" | jq -r '.name')
    value=$(echo "$line" | jq -r '.value')
    aws ssm put-parameter --name "$name" --value "$value" --type "String" --overwrite
done < parameters.json
```

## Required IAM Permissions

The application requires the following IAM permissions:

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
        "arn:aws:ssm:*:*:parameter/mini-app/config/*"
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
          "kms:ViaService": "ssm.*.amazonaws.com"
        }
      }
    }
  ]
}
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PARAMETER_STORE_PATH` | Base path for configuration parameters | `/mini-app/config/` |
| `AWS_REGION` | AWS region for Parameter Store | `us-east-1` |
| `SERVER_PORT` | Server port (can also be in Parameter Store) | `8080` |

## Migration from Properties Files

### Before (Classpath Properties)

```java
// Load from classpath
InputStream input = getClass().getClassLoader()
    .getResourceAsStream("application.properties");
Properties props = new Properties();
props.load(input);
```

### After (Parameter Store)

```java
// Load from Parameter Store
ParameterStoreService parameterStore = new ParameterStoreService();
Properties props = parameterStore.loadConfigurationAsProperties("/mini-app/config/");
```

## Local Development

For local development without AWS access, you can:

1. Use environment variables as fallbacks
2. Set up LocalStack for local AWS service emulation
3. Use AWS CLI with `--endpoint-url` pointing to LocalStack

Example LocalStack setup:

```bash
# Start LocalStack
docker run -d -p 4566:4566 localstack/localstack

# Create parameters in LocalStack
aws ssm put-parameter \
    --endpoint-url http://localhost:4566 \
    --name "/mini-app/config/server/port" \
    --value "8080" \
    --type "String"
```

## Monitoring and Troubleshooting

### Check Parameter Access

View CloudTrail logs for parameter access:

```bash
aws cloudtrail lookup-events \
    --lookup-attributes AttributeKey=ResourceName,AttributeValue=/mini-app/config/server/port
```

### Application Logs

The application logs parameter loading:

```
Parameter Store service initialized for path: /mini-app/config/
Successfully retrieved 15 parameters from path: /mini-app/config/
Loaded 15 configuration properties from Parameter Store
Configuration loaded from AWS Systems Manager Parameter Store: /mini-app/config/
```

### Common Issues

1. **Permission Denied**: Ensure IAM role has `ssm:GetParametersByPath` permission
2. **Parameter Not Found**: Verify parameter path and name are correct
3. **Decryption Failed**: Ensure IAM role has `kms:Decrypt` permission for SecureString parameters
4. **Region Mismatch**: Verify `AWS_REGION` environment variable matches parameter location

## Best Practices

1. **Use SecureString for Sensitive Data**: Passwords, API keys, tokens
2. **Organize with Hierarchical Paths**: Group related parameters
3. **Use Parameter Policies**: Set expiration dates for temporary credentials
4. **Enable Parameter Store Advanced Tier**: For more than 10,000 parameters
5. **Implement Caching**: Reduce API calls (already implemented in ParameterStoreService)
6. **Use Parameter Store Versioning**: Track configuration changes over time
7. **Set Up CloudWatch Alarms**: Monitor parameter access patterns

## Cost Optimization

- Standard parameters: Free (up to 10,000 parameters)
- Advanced parameters: $0.05 per parameter per month
- API calls: First 10,000 calls per month are free, then $0.05 per 10,000 calls
- Use caching to minimize API calls (implemented in ParameterStoreService)

## Security Considerations

1. **Encryption at Rest**: Use SecureString parameters with KMS encryption
2. **Encryption in Transit**: All API calls use HTTPS
3. **Access Control**: Use IAM policies to restrict parameter access
4. **Audit Logging**: Enable CloudTrail for all parameter operations
5. **Least Privilege**: Grant only necessary permissions to application IAM role
6. **Parameter Policies**: Use expiration policies for temporary credentials

## References

- [AWS Systems Manager Parameter Store Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html)
- [AWS SDK for Java v2 - SSM](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/ssm/package-summary.html)
- [Parameter Store Best Practices](https://docs.aws.amazon.com/systems-manager/latest/userguide/parameter-store-best-practices.html)
