package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {

    // Externalized host, port, database via environment variables with old values as defaults (cr-java-0077)
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");

    // Externalized Redis host and port via environment variables with old values as defaults (cr-java-0077)
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // Externalized API endpoints via environment variables with old values as defaults
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    // Externalized secret name for database credentials (cr-java-0069, cr-java-0113)
    private static final String DB_SECRET_NAME = System.getenv("DB_SECRET_NAME");

    // Named millisecond constants for timeouts (cr-java-0097)
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int LOGIN_TIMEOUT_MS = 10000;
    private static final int HIKARI_CONNECTION_TIMEOUT_MS = 5000;
    private static final int HIKARI_VALIDATION_TIMEOUT_MS = 3000;
    private static final int HIKARI_IDLE_TIMEOUT_MS = 600000;
    private static final int HIKARI_MAX_LIFETIME_MS = 1800000;

    private Connection connection;
    private HikariDataSource dataSource;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Load database credentials from AWS Secrets Manager (cr-java-0069, cr-java-0113)
            String dbUsername = null;
            String dbPassword = null;
            if (DB_SECRET_NAME != null) {
                SecretsManagerClient secretsClient = SecretsManagerClient.builder().build();
                GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                        .secretId(DB_SECRET_NAME)
                        .build();
                GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode secretJson = objectMapper.readTree(secretResponse.secretString());
                dbUsername = secretJson.get("username").asText();
                dbPassword = secretJson.get("password").asText();
            }

            // Build HikariCP DataSource with externalized credentials and timeouts (cr-java-0073, cr-java-0097)
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME);
            if (dbUsername != null) {
                config.setUsername(dbUsername);
            }
            if (dbPassword != null) {
                config.setPassword(dbPassword);
            }
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection pool timeouts in milliseconds (cr-java-0097)
            config.setConnectionTimeout(HIKARI_CONNECTION_TIMEOUT_MS);
            config.setValidationTimeout(HIKARI_VALIDATION_TIMEOUT_MS);
            config.setIdleTimeout(HIKARI_IDLE_TIMEOUT_MS);
            config.setMaxLifetime(HIKARI_MAX_LIFETIME_MS);
            config.setInitializationFailTimeout(-1); // Start without a connection (cr-java-0073)

            // MySQL Connector/J connection properties (cr-java-0097)
            config.addDataSourceProperty("connectTimeout", String.valueOf(CONNECTION_TIMEOUT_MS));
            config.addDataSourceProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
            config.addDataSourceProperty("loginTimeout", String.valueOf(LOGIN_TIMEOUT_MS));

            dataSource = new HikariDataSource(config);

            // Get connection from pool (cr-java-0073)
            connection = dataSource.getConnection();

            System.out.println("Connected to database: " + config.getJdbcUrl());
            System.out.println("Using username: " + dbUsername);

            // Externalized cache connection (cr-java-0077)
            connectToCache();

            // Externalized external service URLs
            initializeExternalServices();

        } catch (SdkException e) {
            System.err.println("Failed to retrieve database credentials from Secrets Manager: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // Externalized Redis connection details (cr-java-0077)
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // Externalized external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // Query timeout retained from original code
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
        } finally {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}
