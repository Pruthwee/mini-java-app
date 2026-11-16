package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {
    
    // Database location comes from the environment (DB_HOST, DB_PORT, DB_NAME); the old values are the defaults
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // Credentials come from AWS Secrets Manager; DB_SECRET_NAME names the secret (JSON with username and password)
    private static final String DB_SECRET_NAME_VARIABLE = "DB_SECRET_NAME";

    // Connection pool and timeouts (MySQL Connector/J timeouts are in milliseconds)
    private static final int POOL_MAX_CONNECTIONS = 20;
    private static final int POOL_MIN_IDLE = 2;
    private static final long POOL_CONNECTION_TIMEOUT_MS = 5000L;
    private static final long POOL_VALIDATION_TIMEOUT_MS = 3000L;
    private static final long POOL_IDLE_TIMEOUT_MS = 600000L;
    private static final long POOL_MAX_LIFETIME_MS = 1800000L;
    private static final String MYSQL_CONNECT_TIMEOUT_MS = "5000";
    private static final String MYSQL_SOCKET_TIMEOUT_MS = "30000";
    private static final Duration SECRETS_API_CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SECRETS_API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(10);
    
    // Cache server details come from the environment; the old values are the defaults
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = portFromEnvironment("REDIS_PORT", 6379);
    
    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";
    
    private Connection connection;
    private HikariDataSource dataSource;
    private SecretsManagerClient secretsClient;
    
    private static int portFromEnvironment(String name, int defaultPort) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(defaultPort)).trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }
    
    public void connect() {
        try {
            System.out.println("Connecting to database...");
            
            // BLOCKER: Hardcoded JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Credentials are read from Secrets Manager on first use; connections come from a HikariCP pool
            JsonNode secret = readDatabaseSecret();
            String dbUsername = secret.path("username").asText();
            String dbPassword = secret.path("password").asText();
            connection = getDataSource(dbUsername, dbPassword).getConnection();
            
            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + dbUsername);
            
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
    
    private JsonNode readDatabaseSecret() throws SQLException {
        String secretName = System.getenv(DB_SECRET_NAME_VARIABLE);
        if (secretName == null || secretName.trim().isEmpty()) {
            throw new SQLException(DB_SECRET_NAME_VARIABLE + " is not set");
        }
        if (secretsClient == null) {
            secretsClient = SecretsManagerClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(SECRETS_API_CALL_TIMEOUT)
                    .apiCallAttemptTimeout(SECRETS_API_CALL_ATTEMPT_TIMEOUT)
                    .build())
                .build();
        }
        GetSecretValueResponse response = secretsClient.getSecretValue(
            GetSecretValueRequest.builder().secretId(secretName).build());
        try {
            return new ObjectMapper().readTree(response.secretString());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new SQLException("Database secret is not valid JSON");
        }
    }
    
    private HikariDataSource getDataSource(String username, String password) {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(POOL_MAX_CONNECTIONS);
            config.setMinimumIdle(POOL_MIN_IDLE);
            config.setConnectionTimeout(POOL_CONNECTION_TIMEOUT_MS);
            config.setValidationTimeout(POOL_VALIDATION_TIMEOUT_MS);
            config.setIdleTimeout(POOL_IDLE_TIMEOUT_MS);
            config.setMaxLifetime(POOL_MAX_LIFETIME_MS);
            // Start without a connection; the first getConnection fails where the old connect failed
            config.setInitializationFailTimeout(-1);
            config.addDataSourceProperty("connectTimeout", MYSQL_CONNECT_TIMEOUT_MS);
            config.addDataSourceProperty("socketTimeout", MYSQL_SOCKET_TIMEOUT_MS);
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }
    
    private void connectToCache() {
        // Redis connection details come from REDIS_PORT (default 6379)
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
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        if (secretsClient != null) {
            secretsClient.close();
            secretsClient = null;
        }
    }
}
