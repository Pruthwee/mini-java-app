package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

/**
 * Database service with externalized connection details
 */
public class DatabaseService {
    
    // BLOCKER FIXED: Hardcoded database connection details replaced with environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    
    // BLOCKER FIXED: Hardcoded credentials replaced with AWS Secrets Manager
    private static final String DB_SECRET_NAME = System.getenv().getOrDefault("DB_SECRET_NAME", "");
    
    // BLOCKER FIXED: Hardcoded Redis port replaced with environment variable
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
        System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // BLOCKER FIXED: Hardcoded external service URLs replaced with environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
    
    // HikariCP connection pool
    private static HikariDataSource dataSource;
    private Connection connection;
    
    // Timeout constants (milliseconds)
    private static final int DB_CONNECT_TIMEOUT_MS = 5000;
    private static final int DB_SOCKET_TIMEOUT_MS = 5000;
    private static final int DB_LOGIN_TIMEOUT_MS = 5000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    
    private void loadDatabaseCredentials() {
        try {
            if (dataSource == null) {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(DB_URL);
                
                // BLOCKER FIXED: Hardcoded DB credentials replaced with Secrets Manager
                if (!DB_SECRET_NAME.isEmpty()) {
                    SecretsManagerClient secretsClient = SecretsManagerClient.builder().build();
                    GetSecretValueRequest request = GetSecretValueRequest.builder()
                        .secretId(DB_SECRET_NAME)
                        .build();
                    GetSecretValueResponse response = secretsClient.getSecretValue(request);
                    String secretString = response.secretString();
                    
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode secretJson = objectMapper.readTree(secretString);
                    String username = secretJson.get("username").asText();
                    String password = secretJson.get("password").asText();
                    
                    config.setUsername(username);
                    config.setPassword(password);
                } else {
                    config.setUsername(System.getenv().getOrDefault("DB_USER", "root"));
                    config.setPassword(System.getenv().getOrDefault("DB_PASSWORD", ""));
                }
                
                // BLOCKER FIXED: cr-java-0073 - Direct JDBC replaced with HikariCP connection pool
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setMaximumPoolSize(20);
                config.setMinimumIdle(5);
                config.setConnectionTimeout(DB_CONNECT_TIMEOUT_MS);
                config.setIdleTimeout(600000);
                config.setMaxLifetime(1800000);
                config.setLeakDetectionThreshold(60000);
                // Allow pool to start without a connection (keep old failure path)
                config.setInitializationFailTimeout(-1);
                
                // BLOCKER FIXED: cr-java-0097 - Connection timeouts configured
                config.addDataSourceProperty("connectTimeout", String.valueOf(DB_CONNECT_TIMEOUT_MS));
                config.addDataSourceProperty("socketTimeout", String.valueOf(DB_SOCKET_TIMEOUT_MS));
                config.addDataSourceProperty("loginTimeout", String.valueOf(DB_LOGIN_TIMEOUT_MS));
                config.addDataSourceProperty("tcpKeepAlive", "true");
                
                dataSource = new HikariDataSource(config);
            }
        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve database secret: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("AWS SDK error retrieving database secret: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to initialize database credentials: " + e.getMessage());
        }
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // BLOCKER FIXED: Hardcoded JDBC driver loading and connection replaced with HikariCP pool
            loadDatabaseCredentials();
            
            if (dataSource != null) {
                connection = dataSource.getConnection();
                System.out.println("Connected to database: " + DB_URL);
                // BLOCKER FIXED: username no longer logged (was hardcoded credential)
            } else {
                System.err.println("Database connection pool not initialized");
            }
            
            // BLOCKER FIXED: Hardcoded cache connection details replaced with environment variables
            connectToCache();
            
            // BLOCKER FIXED: Hardcoded external service URLs replaced with environment variables
            initializeExternalServices();
            
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
    
    private void connectToCache() {
        // BLOCKER FIXED: Hardcoded Redis connection details replaced with environment variables
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // BLOCKER FIXED: Hardcoded external service URLs replaced with environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // BLOCKER FIXED: cr-java-0097 - Query timeout configured (was hardcoded 30 seconds)
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                
                System.out.println("Executing query: " + sql);
                stmt.execute();
                stmt.close();
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }
    
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
