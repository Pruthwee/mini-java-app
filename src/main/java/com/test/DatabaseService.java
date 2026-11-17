package com.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers
 */
public class DatabaseService {

    // cr-java-0077: Ports and hosts externalized to environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");
    // cr-java-0069: Username externalized to environment variable
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "root");
    // cr-java-0069, cr-java-0113: Secret name for Secrets Manager (no default - never a hardcoded credential)
    private static final String DB_SECRET_NAME = System.getenv("DB_SECRET_NAME");

    private static final String REDIS_HOST = "127.0.0.1";
    // cr-java-0077: Redis port externalized to environment variable
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    // cr-java-0097: Named timeout constants in milliseconds
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    private Connection connection;
    private HikariDataSource dataSource;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // cr-java-0069, cr-java-0113: Retrieve password from AWS Secrets Manager
            SecretsManagerClient secretsClient = SecretsManagerClient.create();
            GetSecretValueRequest secretRequest = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();
            GetSecretValueResponse secretResponse = secretsClient.getSecretValue(secretRequest);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretJson = mapper.readTree(secretResponse.secretString());
            String dbPassword = secretJson.get("password").asText();

            // cr-java-0073: Use HikariCP connection pool instead of raw JDBC
            Class.forName("com.mysql.cj.jdbc.Driver");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME);
            config.setUsername(DB_USER);
            config.setPassword(dbPassword);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            // cr-java-0073: Start without a connection so app starts even if DB is unreachable
            config.setInitializationFailTimeout(-1);
            // cr-java-0097: Connection and socket timeouts
            config.setConnectionTimeout(CONNECT_TIMEOUT_MS);
            config.addDataSourceProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
            config.addDataSourceProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));

            dataSource = new HikariDataSource(config);
            connection = dataSource.getConnection();

            System.out.println("Connected to database: " + config.getJdbcUrl());
            System.out.println("Using username: " + DB_USER);

            // cr-java-0077: Cache connection (port now from environment variable)
            connectToCache();

            // External service URLs (no blockers, left as-is)
            initializeExternalServices();

        } catch (SdkException e) {
            System.err.println("Failed to retrieve database credentials: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Failed to parse database credentials: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // cr-java-0077: Redis port now from environment variable
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // External service URLs (no blockers, left as-is)
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
            System.exit(1);
        }
    }
}
