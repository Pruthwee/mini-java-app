package com.test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    // BLOCKER: Hardcoded database connection details
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // Timeouts in milliseconds (MySQL Connector/J connectTimeout and socketTimeout are milliseconds)
    private static final int DB_CONNECT_TIMEOUT_MS = 5000;
    private static final int DB_SOCKET_TIMEOUT_MS = 30000;
    private static final long POOL_CONNECTION_TIMEOUT_MS = 5000;
    private static final long POOL_VALIDATION_TIMEOUT_MS = 3000;
    private static final long POOL_IDLE_TIMEOUT_MS = 600000;
    private static final long POOL_MAX_LIFETIME_MS = 1800000;
    private static final int POOL_MAX_SIZE = 20;
    private static final int POOL_MIN_IDLE = 1;

    // AWS SDK client timeouts in milliseconds
    private static final long AWS_CONNECT_TIMEOUT_MS = 5000;
    private static final long AWS_SOCKET_TIMEOUT_MS = 30000;
    private static final long AWS_API_CALL_TIMEOUT_MS = 60000;
    private static final long AWS_API_CALL_ATTEMPT_TIMEOUT_MS = 20000;
    
    // BLOCKER: Hardcoded cache server details
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";
    
    private Connection connection;
    private HikariDataSource dataSource;
    private SecretsManagerClient secretsClient;
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Pooled connection; credentials come from AWS Secrets Manager (DB_SECRET_NAME)
            if (dataSource == null) {
                dataSource = createDataSource();
            }
            connection = dataSource.getConnection();
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using credentials from AWS Secrets Manager");
            
            // BLOCKER: Hardcoded cache connection
            connectToCache();
            
            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();
            
        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private HikariDataSource createDataSource() throws SQLException {
        String secretName = System.getenv("DB_SECRET_NAME");
        if (secretName == null || secretName.isEmpty()) {
            throw new SQLException("DB_SECRET_NAME is not set");
        }
        if (secretsClient == null) {
            secretsClient = SecretsManagerClient.builder()
                    .httpClient(ApacheHttpClient.builder()
                            .connectionTimeout(Duration.ofMillis(AWS_CONNECT_TIMEOUT_MS))
                            .socketTimeout(Duration.ofMillis(AWS_SOCKET_TIMEOUT_MS))
                            .build())
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallTimeout(Duration.ofMillis(AWS_API_CALL_TIMEOUT_MS))
                            .apiCallAttemptTimeout(Duration.ofMillis(AWS_API_CALL_ATTEMPT_TIMEOUT_MS))
                            .build())
                    .build();
        }
        String secretString = secretsClient.getSecretValue(
                GetSecretValueRequest.builder().secretId(secretName).build()).secretString();

        String username;
        String password;
        try {
            JsonNode secret = new ObjectMapper().readTree(secretString);
            username = secret.path("username").asText(null);
            password = secret.path("password").asText(null);
        } catch (IOException | RuntimeException e) {
            throw new SQLException("Database secret could not be read");
        }
        if (username == null || password == null) {
            throw new SQLException("Database secret has no username or password");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(POOL_MAX_SIZE);
        config.setMinimumIdle(POOL_MIN_IDLE);
        config.setConnectionTimeout(POOL_CONNECTION_TIMEOUT_MS);
        config.setValidationTimeout(POOL_VALIDATION_TIMEOUT_MS);
        config.setIdleTimeout(POOL_IDLE_TIMEOUT_MS);
        config.setMaxLifetime(POOL_MAX_LIFETIME_MS);
        // Start without a connection; the first getConnection fails where the old connection failed
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("connectTimeout", String.valueOf(DB_CONNECT_TIMEOUT_MS));
        config.addDataSourceProperty("socketTimeout", String.valueOf(DB_SOCKET_TIMEOUT_MS));
        return new HikariDataSource(config);
    }
    
    private void connectToCache() {
        // BLOCKER: Hardcoded Redis connection details
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }
    
    private void initializeExternalServices() {
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
    
    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
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
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}