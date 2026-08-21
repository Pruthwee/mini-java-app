package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import java.time.Duration;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for retrieving secrets from AWS Secrets Manager
 * Provides centralized secret management with automatic rotation support
 */
public class SecretsManagerService {
    
    private final SecretsManagerClient secretsClient;
    private final ObjectMapper objectMapper;
    private final String awsRegion;
    
    // Cache for secrets to reduce API calls
    private final Map<String, Map<String, String>> secretsCache;
    
    public SecretsManagerService() {
        this.awsRegion = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        
        // Cloud-ready: Configure timeouts to prevent indefinite hangs
        ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(30))           // Total time for API call
                .apiCallAttemptTimeout(Duration.ofSeconds(10))    // Time per attempt
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(3)
                        .build())
                .build();
        
        this.secretsClient = SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(clientConfig)
                .build();
        
        this.objectMapper = new ObjectMapper();
        this.secretsCache = new HashMap<>();
        
        System.out.println("AWS Secrets Manager client initialized for region: " + awsRegion);
    }
    
    /**
     * Retrieve a secret from AWS Secrets Manager
     * 
     * @param secretName The name or ARN of the secret
     * @return Map containing the secret key-value pairs
     */
    public Map<String, String> getSecret(String secretName) {
        // Check cache first
        if (secretsCache.containsKey(secretName)) {
            System.out.println("Retrieved secret from cache: " + secretName);
            return secretsCache.get(secretName);
        }
        
        try {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            
            GetSecretValueResponse getSecretValueResponse = secretsClient.getSecretValue(getSecretValueRequest);
            String secretString = getSecretValueResponse.secretString();
            
            // Parse JSON secret
            Map<String, String> secretMap = parseSecretJson(secretString);
            
            // Cache the secret
            secretsCache.put(secretName, secretMap);
            
            System.out.println("Successfully retrieved secret from AWS Secrets Manager: " + secretName);
            return secretMap;
            
        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve secret from AWS Secrets Manager: " + e.awsErrorDetails().errorMessage());
            System.err.println("Secret name: " + secretName);
            throw new RuntimeException("Failed to retrieve secret: " + secretName, e);
        }
    }
    
    /**
     * Get a specific value from a secret
     * 
     * @param secretName The name or ARN of the secret
     * @param key The key within the secret JSON
     * @return The secret value
     */
    public String getSecretValue(String secretName, String key) {
        Map<String, String> secret = getSecret(secretName);
        String value = secret.get(key);
        
        if (value == null) {
            throw new RuntimeException("Key '" + key + "' not found in secret: " + secretName);
        }
        
        return value;
    }
    
    /**
     * Parse JSON secret string into a map
     */
    private Map<String, String> parseSecretJson(String secretString) {
        try {
            Map<String, String> secretMap = new HashMap<>();
            JsonNode jsonNode = objectMapper.readTree(secretString);
            
            jsonNode.fields().forEachRemaining(entry -> {
                secretMap.put(entry.getKey(), entry.getValue().asText());
            });
            
            return secretMap;
        } catch (Exception e) {
            System.err.println("Failed to parse secret JSON: " + e.getMessage());
            throw new RuntimeException("Failed to parse secret JSON", e);
        }
    }
    
    /**
     * Clear the secrets cache (useful for testing or forcing refresh)
     */
    public void clearCache() {
        secretsCache.clear();
        System.out.println("Secrets cache cleared");
    }
    
    /**
     * Close the Secrets Manager client
     */
    public void close() {
        if (secretsClient != null) {
            secretsClient.close();
            System.out.println("AWS Secrets Manager client closed");
        }
    }
}
