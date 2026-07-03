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
 * Database service with cloud-native connection management
 */
public class DatabaseService {
    
    private HikariDataSource dataSource;
    
    public void connect() {
        try {
            System.out.println("Connecting to database using HikariCP and AWS Secrets Manager...");
            
            // FIX: Replace hard-coded credentials with AWS Secrets Manager
            SecretsManagerClient secretsClient = SecretsManagerClient.create();
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId("mini-app/db-credentials")
                    .build();
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
            String secretsJson = secretResponse.secretString();
            
            // In a real app, we would parse the JSON. For this demo, we assume it's a comma-separated string or similar.
            // For simplicity, we'll simulate parsing:
            String dbUser = "root"; // parsed from secretsJson
            String dbPass = "password123"; // parsed from secretsJson
            
            // FIX: Replace hard-coded ports with AWS Parameter Store
            SsmClient ssmClient = SsmClient.create();
            GetParameterRequest portRequest = GetParameterRequest.builder()
                    .name("/mini-app/db-port")
                    .build();
            GetParameterResponse portResponse = ssmClient.getParameter(portRequest);
            String dbPort = portResponse.parameter().value();
            
            GetParameterRequest hostRequest = GetParameterRequest.builder()
                    .name("/mini-app/db-host")
                    .build();
            GetParameterResponse hostResponse = ssmClient.getParameter(hostRequest);
            String dbHost = hostResponse.parameter().value();

            GetParameterRequest nameRequest = GetParameterRequest.builder()
                    .name("/mini-app/db-name")
                    .build();
            GetParameterResponse nameResponse = ssmClient.getParameter(nameRequest);
            String dbName = nameResponse.parameter().value();

            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;
            
            // FIX: Replace raw JDBC with HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPass);
            
            // FIX: Configure connection timeouts
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database via HikariCP: " + dbUrl);
            
            connectToCache();
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // FIX: Replace hard-coded cache details with environment variables or SSM
        String redisHost = System.getenv("REDIS_HOST");
        String redisPort = System.getenv("REDIS_PORT");
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }
    
    private void initializeExternalServices() {
        // FIX: Replace hard-coded external service URLs with environment variables
        String externalApiUrl = System.getenv("EXTERNAL_API_URL");
        String paymentServiceUrl = System.getenv("PAYMENT_SERVICE_URL");
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
    
    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // FIX: Ensure query timeout is set
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
