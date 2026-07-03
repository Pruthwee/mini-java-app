package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Database service with connection pooling for cloud readiness
 */
public class DatabaseService {
    
    // Use environment variables for configuration
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "127.0.0.1";
    private static final int REDIS_PORT = Integer.parseInt(System.getenv("REDIS_PORT") != null ? System.getenv("REDIS_PORT") : "6379");
    
    private static final String EXTERNAL_API_URL = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.internal.company.com/process";
    
    private HikariDataSource dataSource;
    private String dbUsername;
    private String dbPassword;
    
    public void connect() {
        try {
            System.out.println("Connecting to database using HikariCP...");
            
            // Fetch credentials from AWS Secrets Manager
            fetchCredentials();
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // Cloud-native pooling settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            config.setPoolName("CloudAppPool");
            
            // Optimization for MySQL/RDS
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            this.dataSource = new HikariDataSource(config);
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + dbUsername);
            
            // BLOCKER: Hardcoded cache connection
            connectToCache();
            
            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    private void fetchCredentials() {
        String secretName = System.getenv("DB_SECRET_NAME");
        if (secretName == null) {
            // Fallback for local development if secret name is not provided
            this.dbUsername = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : "root";
            this.dbPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "password123";
            System.out.println("DB_SECRET_NAME not set, using fallback credentials");
            return;
        }

        try (SecretsManagerClient client = software.amazon.awssdk.services.secretsmanager.SecretsManagerClient.builder()
                .overrideConfiguration(software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder()
                        .apiCallTimeout(java.time.Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(java.time.Duration.ofSeconds(5))
                        .build())
                .build()) {
            
            // Simple JSON parsing for the purpose of this transformation
            this.dbUsername = parseJsonValue(secret, "username");
            this.dbPassword = parseJsonValue(secret, "password");
            
        } catch (Exception e) {
            System.err.println("Error fetching secrets from AWS Secrets Manager: " + e.getMessage());
            // Fallback to avoid complete failure in dev
            this.dbUsername = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : "root";
            this.dbPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "password123";
        }
    }

    private String parseJsonValue(String json, String key) {
        // Very basic JSON value extractor for simple key-value pairs
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return null;
        start += searchKey.length();
        
        // Skip whitespace and opening quote
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
            start++;
        }
        
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        
        return json.substring(start, end);
    }
    
    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection != null) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // BLOCKER: Hardcoded query timeout
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
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection pool closed");
        }
    }
}
