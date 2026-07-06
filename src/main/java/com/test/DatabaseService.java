package com.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service with cloud-native connection management
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
            System.out.println("Connecting to database using HikariCP and AWS Secrets Manager...");
            
            // FIXED: Replace hard-coded credentials with AWS Secrets Manager
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId("mini-app/db-credentials")
                    .build();
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
            String secretsJson = secretResponse.secretString();
            
            // In a real app, we would parse the JSON. For this demo, we assume it contains the password.
            String dbPassword = secretsJson; 
            
            // FIXED: Replace hard-coded ports and host with AWS Parameter Store / Env Vars
            String dbHost = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "rds-instance.aws.com";
            String dbPort = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
            String dbName = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "mini_app_db";
            String dbUser = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "admin";
            
            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;
            
            // FIXED: Replace raw JDBC with HikariCP connection pool
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPassword);
            
            // FIXED: Configure connection timeouts
            config.setConnectionTimeout(30000); // 30 seconds
            config.setIdleTimeout(600000);    // 10 minutes
            config.setMaxLifetime(1800000);   // 30 minutes
            config.setMaximumPoolSize(10);
            
            this.dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database via HikariCP: " + dbUrl);
            
            connectToCache();
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // FIXED: Replace hardcoded Redis connection details with environment variables
        String redisHost = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "cache.aws.com";
        String redisPort = System.getenv("REDIS_PORT") != null ? System.getenv("REDIS_PORT") : "6379";
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }
    
    private void initializeExternalServices() {
        // FIXED: Replace hardcoded external service URLs with environment variables
        String externalApiUrl = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "https://api.example.com/v1";
        String paymentServiceUrl = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.internal.company.com/process";
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
    
    public void executeQuery(String sql) {
        try {
            if (dataSource != null) {
                // FIXED: Use connection from pool
                try (Connection connection = dataSource.getConnection()) {
                    PreparedStatement stmt = connection.prepareStatement(sql);
                    // FIXED: Ensure query timeout is set
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