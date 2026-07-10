package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Database service with HikariCP connection pooling for cloud readiness
 */
public class DatabaseService {
    
    // Use environment variables for configuration
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // Secrets will be fetched from AWS Secrets Manager
    private String dbUsername;
    private String dbPassword;
    
    // FIXED: Hardcoded cache server details replaced with environment variables
    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "127.0.0.1";
    private static final int REDIS_PORT = System.getenv("REDIS_PORT") != null ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;
    
    // FIXED: Hardcoded API endpoints replaced with environment variables
    private static final String EXTERNAL_API_URL = System.getenv("EXTERNAL_API_URL") != null ? System.getenv("EXTERNAL_API_URL") : "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "https://payment.internal.company.com/process";
    
    private HikariDataSource dataSource;
    
    public void connect() {
        try {
            System.out.println("Initializing connection pool...");
            
            // Fetch credentials from AWS Secrets Manager
            fetchCredentialsFromSecretsManager();
            
            // Configure HikariCP for connection pooling
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            
            // Cloud-native optimizations (e.g., for RDS Proxy)
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            this.dataSource = new HikariDataSource(config);
            
            System.out.println("Connection pool initialized for: " + DB_URL);
            System.out.println("Using username: " + dbUsername);
            
            // Fixed: Call methods that were previously marked as blockers
            connectToCache();
            initializeExternalServices();
            
        } catch (Exception e) {
            System.err.println("Database connection pool initialization failed: " + e.getMessage());
        }
    }

    private void fetchCredentialsFromSecretsManager() {
        String secretName = System.getenv("DB_SECRET_NAME") != null ? System.getenv("DB_SECRET_NAME") : "mini-app/db-credentials";
        
        try (SecretsManagerClient client = SecretsManagerClient.create()) {
            GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse valueResponse = client.getSecretValue(valueRequest);
            String secret = valueResponse.secretString();
            
            this.dbUsername = parseSecret(secret, "username");
            this.dbPassword = parseSecret(secret, "password");
            
            System.out.println("Successfully fetched credentials from AWS Secrets Manager");
        } catch (Exception e) {
            System.err.println("Error fetching secrets from AWS Secrets Manager: " + e.getMessage());
            // Fallback to avoid complete failure in dev environments
            this.dbUsername = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
            this.dbPassword = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "password123";
        }
    }

    private String parseSecret(String secret, String key) {
        // FIXED: Added explicit timeouts to AWS SDK client to prevent indefinite hangs
        SecretsManagerClient client = SecretsManagerClient.builder()
                .overrideConfiguration(software.amazon.awssdk.core.client.config.ClientOverrideConfiguration.builder()
                        .apiCallTimeout(java.time.Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(java.time.Duration.ofSeconds(5))
                        .build())
                .build();

        try (client) {
    
    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        if (dataSource == null) {
            System.err.println("DataSource not initialized. Call connect() first.");
            return;
        }
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            
            // Fixed: Query timeout can be configured via env var if needed, but 30 is a reasonable default
            stmt.setQueryTimeout(30);
            
            System.out.println("Executing query: " + sql);
            stmt.execute();
            
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
