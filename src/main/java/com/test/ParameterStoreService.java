package com.test;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Service for retrieving configuration from AWS Systems Manager Parameter Store
 * Provides externalized configuration management with runtime updates
 */
public class ParameterStoreService {
    
    private final SsmClient ssmClient;
    private final String awsRegion;
    
    // Cache for parameters to reduce API calls
    private final Map<String, String> parameterCache;
    
    public ParameterStoreService() {
        this.awsRegion = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        
        // Cloud-ready: Configure timeouts to prevent indefinite hangs
        ClientOverrideConfiguration clientConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(30))           // Total time for API call
                .apiCallAttemptTimeout(Duration.ofSeconds(10))    // Time per attempt
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(3)
                        .build())
                .build();
        
        this.ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(clientConfig)
                .build();
        
        this.parameterCache = new HashMap<>();
        
        System.out.println("AWS Systems Manager Parameter Store client initialized for region: " + awsRegion);
    }
    
    /**
     * Retrieve a single parameter from Parameter Store
     * 
     * @param parameterName The name of the parameter (e.g., /mini-app/server/port)
     * @param withDecryption Whether to decrypt SecureString parameters
     * @return The parameter value
     */
    public String getParameter(String parameterName, boolean withDecryption) {
        // Check cache first
        if (parameterCache.containsKey(parameterName)) {
            System.out.println("Retrieved parameter from cache: " + parameterName);
            return parameterCache.get(parameterName);
        }
        
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(withDecryption)
                    .build();
            
            GetParameterResponse response = ssmClient.getParameter(request);
            String value = response.parameter().value();
            
            // Cache the parameter
            parameterCache.put(parameterName, value);
            
            System.out.println("Successfully retrieved parameter from Parameter Store: " + parameterName);
            return value;
            
        } catch (SsmException e) {
            System.err.println("Failed to retrieve parameter from Parameter Store: " + e.awsErrorDetails().errorMessage());
            System.err.println("Parameter name: " + parameterName);
            throw new RuntimeException("Failed to retrieve parameter: " + parameterName, e);
        }
    }
    
    /**
     * Retrieve all parameters under a specific path
     * 
     * @param path The parameter path (e.g., /mini-app/)
     * @param withDecryption Whether to decrypt SecureString parameters
     * @return Map of parameter names to values
     */
    public Map<String, String> getParametersByPath(String path, boolean withDecryption) {
        Map<String, String> parameters = new HashMap<>();
        
        try {
            GetParametersByPathRequest request = GetParametersByPathRequest.builder()
                    .path(path)
                    .recursive(true)
                    .withDecryption(withDecryption)
                    .build();
            
            GetParametersByPathResponse response = ssmClient.getParametersByPath(request);
            
            for (Parameter parameter : response.parameters()) {
                String name = parameter.name();
                String value = parameter.value();
                
                // Store with full path and also with simplified key
                parameters.put(name, value);
                parameterCache.put(name, value);
                
                // Extract the key after the path for easier access
                String key = name.substring(path.length());
                if (key.startsWith("/")) {
                    key = key.substring(1);
                }
                parameters.put(key, value);
            }
            
            System.out.println("Successfully retrieved " + parameters.size() + " parameters from path: " + path);
            return parameters;
            
        } catch (SsmException e) {
            System.err.println("Failed to retrieve parameters from path: " + e.awsErrorDetails().errorMessage());
            System.err.println("Path: " + path);
            throw new RuntimeException("Failed to retrieve parameters from path: " + path, e);
        }
    }
    
    /**
     * Load configuration as Properties object from Parameter Store
     * 
     * @param parameterPath The base path for parameters (e.g., /mini-app/config/)
     * @return Properties object with all parameters
     */
    public Properties loadConfigurationAsProperties(String parameterPath) {
        Properties props = new Properties();
        
        try {
            Map<String, String> parameters = getParametersByPath(parameterPath, true);
            
            // Convert map to properties
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                // Remove the path prefix if present
                if (key.startsWith(parameterPath)) {
                    key = key.substring(parameterPath.length());
                    if (key.startsWith("/")) {
                        key = key.substring(1);
                    }
                }
                // Convert path separators to dots for property keys
                key = key.replace("/", ".");
                props.setProperty(key, entry.getValue());
            }
            
            System.out.println("Loaded " + props.size() + " configuration properties from Parameter Store");
            return props;
            
        } catch (Exception e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
            // Return empty properties instead of failing
            return props;
        }
    }
    
    /**
     * Get a parameter with a default value if not found
     * 
     * @param parameterName The parameter name
     * @param defaultValue The default value to return if parameter not found
     * @param withDecryption Whether to decrypt SecureString parameters
     * @return The parameter value or default value
     */
    public String getParameterOrDefault(String parameterName, String defaultValue, boolean withDecryption) {
        try {
            return getParameter(parameterName, withDecryption);
        } catch (Exception e) {
            System.out.println("Parameter not found, using default value: " + parameterName);
            return defaultValue;
        }
    }
    
    /**
     * Clear the parameter cache (useful for testing or forcing refresh)
     */
    public void clearCache() {
        parameterCache.clear();
        System.out.println("Parameter cache cleared");
    }
    
    /**
     * Close the SSM client
     */
    public void close() {
        if (ssmClient != null) {
            ssmClient.close();
            System.out.println("AWS Systems Manager Parameter Store client closed");
        }
    }
}
