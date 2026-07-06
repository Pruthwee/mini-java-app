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
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    private HikariDataSource dataSource;
    
    // FIX: Replace hardcoded values with environment variables/AWS services
    private String getDbHost() {
        return System.getenv().getOrDefault("DB_HOST", "localhost");
    }
    
    private String getDbPort() {
        // FIX: Replace hardcoded port with AWS Parameter Store/Env Var
        return System.getenv().getOrDefault("DB_PORT", "3306");
    }
    
    private String getDbName() {
        return System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    }
    
    private String getDbUsername() {
        // FIX: Replace hardcoded credentials with AWS Secrets Manager
        return getSecret("db_username");
    }
    
    private String getDbPassword() {
        // FIX: Replace hardcoded credentials with AWS Secrets Manager
        return getSecret("db_password");
    }
    
    private String getSecret(String secretName) {
        try {
            SecretsManagerClient secretsClient = SecretsManagerClient.create();
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
            return valueResponse.secretString();
        } catch (Exception e) {
            System.err.println("Error retrieving secret " + secretName + ": " + e.getMessage());
            return "fallback_value";
        }
    }
    
    // FIX: Replace hardcoded cache server details
    private String getRedisHost() {
        return System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    }
    
    private int getRedisPort() {
        return Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    }
    
    // FIX: Replace hardcoded API endpoints
    private String getExternalApiUrl() {
        return System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    }
    
    private String getPaymentServiceUrl() {
        return System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // FIX: Replace raw JDBC with HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName());
            config.setUsername(getDbUsername());
            config.setPassword(getDbPassword());
            
            // FIX: Configure connection timeouts
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database using HikariCP: " + config.getJdbcUrl());
            
            // FIX: Replace hardcoded cache connection
            connectToCache();
            
            // FIX: Replace hardcoded external service URLs
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + getRedisHost() + ":" + getRedisPort());
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + getExternalApiUrl());
        System.out.println("Initializing payment service: " + getPaymentServiceUrl());
    }
    
    public void executeQuery(String sql) {
        try {
            // FIX: Use connection from pool
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement stmt = connection.prepareStatement(sql);
                    // FIX: Set explicit query timeout
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
        try {
            if (dataSource != null) {
                dataSource.close();
                System.out.println("Database connection pool closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}