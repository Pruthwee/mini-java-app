package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service. Connection details come from the environment (DB_HOST, DB_PORT, DB_NAME),
 * the credentials from AWS Secrets Manager (secret named by DB_SECRET_NAME, JSON with
 * "username" and "password"), and connections come from a HikariCP pool.
 */
public class DatabaseService {

    // Database location: environment, with the old values as defaults
    private static final String DB_HOST = envOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = envOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = envOrDefault("DB_NAME", "mini_app_db");
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // Name of the Secrets Manager secret holding the database "username" and "password"
    private static final String DB_SECRET_NAME_VAR = "DB_SECRET_NAME";

    // Cache server details: environment, with the old values as defaults
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = envIntOrDefault("REDIS_PORT", 6379);

    // Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    // Timeouts, in milliseconds (MySQL Connector/J connectTimeout and socketTimeout are milliseconds)
    private static final long POOL_CONNECTION_TIMEOUT_MS = 5000L;
    private static final long POOL_VALIDATION_TIMEOUT_MS = 3000L;
    private static final long POOL_IDLE_TIMEOUT_MS = 600000L;
    private static final long POOL_MAX_LIFETIME_MS = 1800000L;
    private static final long POOL_INITIALIZATION_FAIL_TIMEOUT_MS = -1L;
    private static final int POOL_MAX_SIZE = 20;
    private static final int POOL_MIN_IDLE = 5;
    private static final String MYSQL_CONNECT_TIMEOUT_MS = "5000";
    private static final String MYSQL_SOCKET_TIMEOUT_MS = "30000";

    // AWS SDK client timeouts
    private static final Duration SDK_CONNECTION_TIMEOUT = Duration.ofMillis(5000L);
    private static final Duration SDK_SOCKET_TIMEOUT = Duration.ofMillis(30000L);
    private static final Duration SDK_API_CALL_TIMEOUT = Duration.ofMillis(60000L);
    private static final Duration SDK_API_CALL_ATTEMPT_TIMEOUT = Duration.ofMillis(20000L);

    private HikariDataSource dataSource;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Pooled connection; credentials are read from Secrets Manager on first use
            HikariDataSource pool = getDataSource();
            try (Connection ignored = pool.getConnection()) {
                System.out.println("Connected to database: " + DB_URL);
                System.out.println("Using credentials from AWS Secrets Manager");
            }

            // Cache connection
            connectToCache();

            // External service URLs
            initializeExternalServices();

        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException | SdkException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private synchronized HikariDataSource getDataSource() throws SQLException {
        if (dataSource == null) {
            JsonNode secret = readDatabaseSecret();
            JsonNode username = secret.get("username");
            JsonNode password = secret.get("password");
            if (username == null || password == null) {
                throw new SQLException("Database secret must contain username and password");
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(username.asText());
            config.setPassword(password.asText());
            config.setMaximumPoolSize(POOL_MAX_SIZE);
            config.setMinimumIdle(POOL_MIN_IDLE);
            config.setConnectionTimeout(POOL_CONNECTION_TIMEOUT_MS);
            config.setValidationTimeout(POOL_VALIDATION_TIMEOUT_MS);
            config.setIdleTimeout(POOL_IDLE_TIMEOUT_MS);
            config.setMaxLifetime(POOL_MAX_LIFETIME_MS);
            // The pool starts without a connection; the first use fails where the old connection failed
            config.setInitializationFailTimeout(POOL_INITIALIZATION_FAIL_TIMEOUT_MS);
            config.addDataSourceProperty("connectTimeout", MYSQL_CONNECT_TIMEOUT_MS);
            config.addDataSourceProperty("socketTimeout", MYSQL_SOCKET_TIMEOUT_MS);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    private JsonNode readDatabaseSecret() throws SQLException {
        String secretName = System.getenv(DB_SECRET_NAME_VAR);
        if (secretName == null || secretName.isEmpty()) {
            throw new SQLException(DB_SECRET_NAME_VAR + " is not set");
        }
        ApacheHttpClient.Builder httpClient = ApacheHttpClient.builder()
                .connectionTimeout(SDK_CONNECTION_TIMEOUT)
                .socketTimeout(SDK_SOCKET_TIMEOUT);
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(SDK_API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(SDK_API_CALL_ATTEMPT_TIMEOUT)
                .build();
        // Region and credentials come from the SDK's default chain
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .httpClientBuilder(httpClient)
                .overrideConfiguration(overrideConfig)
                .build()) {
            String secretString = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretName).build()).secretString();
            if (secretString == null) {
                throw new SQLException("Database secret has no string value");
            }
            return new ObjectMapper().readTree(secretString);
        } catch (IOException e) {
            throw new SQLException("Database secret is not valid JSON");
        }
    }

    private void connectToCache() {
        // Redis connection details
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // External service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // Query timeout, in seconds
                    stmt.setQueryTimeout(30);

                    System.out.println("Executing query: " + sql);
                    stmt.execute();
                }
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public synchronized void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection closed");
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    private static int envIntOrDefault(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
