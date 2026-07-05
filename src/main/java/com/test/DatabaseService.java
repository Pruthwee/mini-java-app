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
    
    // FIX: Replace hardcoded database connection details with environment variables/Secrets Manager
    private static final String DB_SECRET_NAME = System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app-db-secret");
    private static final String DB_URL_PARAM = "/app/db/url";
    
    // FIX: Replace hardcoded cache server details with environment variables
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "redis-cache.internal");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // FIX: Replace hardcoded API endpoints with environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    
    private HikariDataSource dataSource;
    private final SecretsManagerClient secretsClient = SecretsManagerClient.create();
    private final SsmClient ssmClient = SsmClient.create();
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // FIX: Use AWS Secrets Manager for credentials
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
            String secretString = secretResponse.secretString();
            
            // Assuming secretString is in a format that can be parsed (e.g., JSON), 
            // but for simplicity in this example we'll assume it contains the password.
            String dbPassword = secretString; 
            String dbUsername = System.getenv().getOrDefault("DB_USERNAME", "admin");

            // FIX: Use AWS SSM Parameter Store for DB URL/Port
            GetParameterRequest paramRequest = GetParameterRequest.builder()
                    .name(DB_URL_PARAM)
                    .build();
            GetParameterResponse paramResponse = ssmClient.getParameter(paramRequest);
            String dbUrl = paramResponse.parameter().value();
            
            // FIX: Replace raw JDBC with HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // FIX: Configure connection timeouts
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database using HikariCP: " + dbUrl);
            
            // FIX: Hardcoded cache connection
            connectToCache();
            
            // FIX: Hardcoded external service URLs
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // FIX: Use environment variables for Redis connection details
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // FIX: Use environment variables for external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try {
            if (dataSource != null) {
                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement stmt = connection.prepareStatement(sql);
                    // FIX: Hardcoded query timeout
                    stmt.setQueryTimeout(Integer.parseInt(System.getenv().getOrDefault("QUERY_TIMEOUT", "30")));
                    
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
        } catch (Exception e) {
            System.err.println("Failed to close database connection pool: " + e.getMessage());
        }
    }
}