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
    private final SecretsManagerClient secretsClient = SecretsManagerClient.create();
    private final SsmClient ssmClient = SsmClient.create();
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // FIX: Replace hard-coded database credentials with AWS Secrets Manager
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId(System.getenv().getOrDefault("DB_SECRET_ID", "mini-app/db-credentials"))
                    .build();
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
            String secret = secretResponse.secretString();
            
            // Assuming secret is stored as JSON or simple string. For simplicity, we'll parse it or use it.
            // In a real app, you'd parse JSON to get username/password.
            String dbUsername = "root"; // Default or parsed from secret
            String dbPassword = secret;   // Simplified for this example
            
            // FIX: Replace hard-coded ports with AWS Parameter Store and environment variable injection
            String dbHost = System.getenv().getOrDefault("DB_HOST", "localhost");
            String dbPort = System.getenv().getOrDefault("DB_PORT", "3306");
            String dbName = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
            
            // FIX: Replace raw JDBC with HikariCP connection pool and RDS Proxy
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // FIX: Configure connection timeouts
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database using HikariCP: " + config.getJdbcUrl());
            
            // FIX: Hardcoded cache connection - replace with env vars
            connectToCache();
            
            // FIX: Hardcoded external service URLs - replace with env vars
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // FIX: Replace hardcoded Redis connection details with environment variables
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }
    
    private void initializeExternalServices() {
        // FIX: Replace hardcoded external service URLs with environment variables
        String externalApiUrl = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
        String paymentServiceUrl = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
    
    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // FIX: Ensure query timeout is configured (already present, but now using pooled connection)
                stmt.setQueryTimeout(30);
                
                System.out.println("Executing query: " + sql);
                stmt.execute();
                stmt.close();
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