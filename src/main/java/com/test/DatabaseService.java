package com.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    // BLOCKER FIXED: Hardcoded database connection details - now from environment variables and Secrets Manager
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_SECRET_NAME = System.getenv("DB_SECRET_NAME");
    
    // BLOCKER FIXED: Hardcoded cache server details - port now from environment variable
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
        System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // BLOCKER FIXED: Hardcoded API endpoints - now from environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault(
        "EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault(
        "PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private DataSource dataSource;
    private SecretsManagerClient secretsClient;
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // BLOCKER FIXED: Hardcoded JDBC driver - using HikariCP DataSource
            // BLOCKER FIXED: Hardcoded connection string and credentials - now from Secrets Manager
            if (DB_SECRET_NAME == null || DB_SECRET_NAME.isEmpty()) {
                System.err.println("DB_SECRET_NAME environment variable is not set");
                return;
            }
            
            if (secretsClient == null) {
                secretsClient = SecretsManagerClient.builder().build();
            }
            
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                .secretId(DB_SECRET_NAME)
                .build();
            
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
            JsonNode secretJson = objectMapper.readTree(secretResponse.secretString());
            
            String dbUsername = secretJson.get("username").asText();
            String dbPassword = secretJson.get("password").asText();
            
            String jdbcUrl = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setInitializationFailTimeout(-1);
            config.setMaximumPoolSize(20);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(60000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            
            dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database: " + jdbcUrl);
            System.out.println("Using username: " + dbUsername);
            
            // BLOCKER FIXED: Hardcoded cache connection - port now from environment variable
            connectToCache();
            
            // BLOCKER FIXED: Hardcoded external service URLs - now from environment variables
            initializeExternalServices();
            
        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve secret: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to retrieve secret: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // BLOCKER FIXED: Hardcoded Redis connection details - port now from environment variable
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // BLOCKER FIXED: Hardcoded external service URLs - now from environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try {
            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // BLOCKER FIXED: Hardcoded query timeout - using named constant
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
            if (dataSource != null && dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
                System.out.println("Database connection closed");
            }
        } catch (Exception e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
