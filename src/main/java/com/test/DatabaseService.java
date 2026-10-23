package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with cloud-ready configuration (AWS)
 * Uses HikariCP connection pool with RDS Proxy and AWS Secrets Manager for credentials
 */
public class DatabaseService {

    // Externalized port configuration via environment variables
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "mini_app_db");

    // Secret name from environment variable - no default for credentials
    private static final String DB_SECRET_NAME = System.getenv().getOrDefault("DB_SECRET_NAME", "");

    // Externalized cache server configuration via environment variables
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // Externalized API endpoints via environment variables
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    // Named timeout constants (milliseconds)
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int VALIDATION_TIMEOUT_MS = 3000;
    private static final int IDLE_TIMEOUT_MS = 600000;
    private static final int MAX_LIFETIME_MS = 1800000;
    private static final int LEAK_DETECTION_THRESHOLD_MS = 60000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private DataSource dataSource;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Load credentials from AWS Secrets Manager
            DatabaseCredentials creds = getDatabaseCredentials();

            // Build HikariCP DataSource with RDS Proxy endpoint
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + creds.getHost() + ":" + creds.getPort() + "/" + creds.getDatabase());
            config.setUsername(creds.getUsername());
            config.setPassword(creds.getPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection pool optimization for cloud environments
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setValidationTimeout(VALIDATION_TIMEOUT_MS);
            config.setIdleTimeout(IDLE_TIMEOUT_MS);
            config.setMaxLifetime(MAX_LIFETIME_MS);
            config.setLeakDetectionThreshold(LEAK_DETECTION_THRESHOLD_MS);
            config.setInitializationFailTimeout(-1); // Start without a connection

            // MySQL Connector/J timeout properties (milliseconds)
            config.addDataSourceProperty("connectTimeout", String.valueOf(CONNECTION_TIMEOUT_MS));
            config.addDataSourceProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
            config.addDataSourceProperty("loginTimeout", "10");
            config.addDataSourceProperty("useSSL", "true");
            config.addDataSourceProperty("requireSSL", "true");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);

            System.out.println("Connected to database: " + creds.getHost() + ":" + creds.getPort() + "/" + creds.getDatabase());
            System.out.println("Using username: " + creds.getUsername());

            // Connect to cache
            connectToCache();

            // Initialize external services
            initializeExternalServices();

        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve database credentials from Secrets Manager: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("AWS SDK error retrieving database credentials: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private DatabaseCredentials getDatabaseCredentials() {
        if (DB_SECRET_NAME.isEmpty()) {
            System.err.println("DB_SECRET_NAME environment variable is not set");
            return null;
        }

        SecretsManagerClient secretsClient = SecretsManagerClient.builder().build();
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(DB_SECRET_NAME)
                .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            String secretString = response.secretString();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode secretJson = objectMapper.readTree(secretString);

            String host = secretJson.has("host") ? secretJson.get("host").asText() : DB_HOST;
            int port = secretJson.has("port") ? secretJson.get("port").asInt() : Integer.parseInt(DB_PORT);
            String database = secretJson.has("database") ? secretJson.get("database").asText() : DB_NAME;
            String username = secretJson.get("username").asText();
            String password = secretJson.get("password").asText();

            return new DatabaseCredentials(host, port, database, username, password);
        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve secret: " + e.getMessage());
            return null;
        } catch (SdkException e) {
            System.err.println("AWS SDK error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Failed to parse secret: " + e.getMessage());
            return null;
        } finally {
            secretsClient.close();
        }
    }

    private void connectToCache() {
        // Externalized Redis connection details via environment variables
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // Externalized API endpoints via environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // Query timeout in seconds
                    stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

                    System.out.println("Executing query: " + sql);
                    stmt.execute();
                }
            } else {
                System.err.println("Database connection not available");
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

    /**
     * Helper class for database credentials retrieved from AWS Secrets Manager
     */
    private static class DatabaseCredentials {
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;

        public DatabaseCredentials(String host, int port, String database, String username, String password) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }
}
