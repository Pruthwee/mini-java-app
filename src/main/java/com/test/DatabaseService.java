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
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * Database service with externalized credentials and connection pooling
 */
public class DatabaseService {

    // cr-java-0077: ports and hosts from environment variables with old defaults
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // cr-java-0077: external API URLs from environment variables with old defaults
    private static final String EXTERNAL_API_URL = System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");
    private static final String PAYMENT_SERVICE_URL = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    // cr-java-0069 / cr-java-0113: DB_SECRET_NAME names the Secrets Manager secret
    // whose JSON holds host, port, database, username, password
    private static final String DB_SECRET_NAME = System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db-credentials");

    // cr-java-0097: named millisecond timeout constants
    private static final int CONNECT_TIMEOUT_MS = 5000;   // 5 seconds
    private static final int SOCKET_TIMEOUT_MS = 30000;   // 30 seconds
    private static final int QUERY_TIMEOUT_SECONDS = 30;  // 30 seconds

    private SecretsManagerClient secretsClient;
    private HikariDataSource dataSource;
    private ObjectMapper objectMapper;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // cr-java-0069 / cr-java-0113: credentials from Secrets Manager
            DatabaseCredentials creds = getDatabaseCredentials();

            // cr-java-0073: HikariCP connection pool replaces raw JDBC DriverManager
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + creds.getHost() + ":" + creds.getPort() + "/" + creds.getDatabase());
            config.setUsername(creds.getUsername());
            config.setPassword(creds.getPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // cr-java-0097: connection pool timeouts
            config.setConnectionTimeout(CONNECT_TIMEOUT_MS);
            config.setValidationTimeout(CONNECT_TIMEOUT_MS);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
            config.setInitializationFailTimeout(-1); // Start without a connection

            // cr-java-0097: JDBC-level timeouts (MySQL Connector/J uses milliseconds)
            config.addDataSourceProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
            config.addDataSourceProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
            config.addDataSourceProperty("loginTimeout", "10");

            dataSource = new HikariDataSource(config);

            System.out.println("Connected to database: " + creds.getHost() + ":" + creds.getPort() + "/" + creds.getDatabase());
            System.out.println("Using username: " + creds.getUsername());

            connectToCache();
            initializeExternalServices();

        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve database credentials: " + e.getMessage());
        } catch (SdkException e) {
            System.err.println("Failed to retrieve database credentials: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private DatabaseCredentials getDatabaseCredentials() throws Exception {
        // cr-java-0069 / cr-java-0113: retrieve credentials from AWS Secrets Manager
        if (secretsClient == null) {
            secretsClient = SecretsManagerClient.create();
        }
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }

        GetSecretValueRequest request = GetSecretValueRequest.builder()
            .secretId(DB_SECRET_NAME)
            .build();

        GetSecretValueResponse response = secretsClient.getSecretValue(request);
        String secretString = response.secretString();

        JsonNode secretJson = objectMapper.readTree(secretString);
        return new DatabaseCredentials(
            secretJson.get("host").asText(),
            secretJson.get("port").asInt(),
            secretJson.get("database").asText(),
            secretJson.get("username").asText(),
            secretJson.get("password").asText()
        );
    }

    private void connectToCache() {
        // cr-java-0077: Redis port from environment variable
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // cr-java-0077: External service URLs from environment variables
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                // cr-java-0073: use connection from HikariCP pool
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // cr-java-0097: query timeout
                    stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

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
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                System.out.println("Database connection closed");
            }
        } catch (Exception e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }

    static class DatabaseCredentials {
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
