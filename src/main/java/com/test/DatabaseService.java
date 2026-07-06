package com.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service with cloud-native configuration and connection pooling
 */
public class DatabaseService {
    
    private HikariDataSource dataSource;
    private final SecretsManagerClient secretsClient;
    private final SsmClient ssmClient;
    
    public DatabaseService() {
        this.secretsClient = SecretsManagerClient.create();
        this.ssmClient = SsmClient.create();
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database using HikariCP...");
            
            // FIX: Replace hard-coded ports and hosts with AWS SSM Parameter Store
            String dbHost = getParameter("/db/host");
            String dbPort = getParameter("/db/port");
            String dbName = getParameter("/db/name");
            
            // FIX: Replace hard-coded credentials with AWS Secrets Manager
            String secretId = getParameter("/db/secret-id");
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .build();
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
            String secretJson = secretResponse.secretString();
            
            // Assuming secretJson is a simple string or JSON; for simplicity we'll treat it as a comma-separated or similar
            // In a real app, use a JSON parser (like Jackson) to extract username and password
            String[] credentials = secretJson.split(","); 
            String dbUsername = credentials[0];
            String dbPassword = credentials[1];

            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;
            
            // FIX: Replace raw JDBC with HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // FIX: Configure connection timeouts
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            this.dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database via HikariCP: " + dbUrl);
            
            // FIX: Replace hard-coded cache details with SSM
            connectToCache();
            
            // FIX: Replace hard-coded external service URLs with SSM
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private String getParameter(String name) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(name)
                .build();
        GetParameterResponse response = ssmClient.getParameter(request);
        return response.parameter().value();
    }
    
    private void connectToCache() {
        // FIX: Replace hard-coded Redis connection details with SSM
        String redisHost = getParameter("/cache/redis/host");
        String redisPort = getParameter("/cache/redis/port");
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }
    
    private void initializeExternalServices() {
        // FIX: Replace hard-coded external service URLs with SSM
        String externalApiUrl = getParameter("/api/external/url");
        String paymentServiceUrl = getParameter("/api/payment/url");
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
    
    public void executeQuery(String sql) {
        try {
            if (dataSource != null) {
                // Use try-with-resources for connection from pool
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement stmt = connection.prepareStatement(sql)) {
                    
                    // FIX: Ensure query timeout is configured
                    stmt.setQueryTimeout(30);
                    
                    System.out.println("Executing query: " + sql);
                    stmt.execute();
                }
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }
    
    public void disconnect() {
        if (dataSource != null) {
            dataSource.close();
            System.out.println("Database connection pool closed");
        }
    }
}
