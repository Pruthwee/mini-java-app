package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service — cloud-native version.
 *
 * Credentials are retrieved from AWS Secrets Manager (blocker-8, blocker-9,
 * blocker-10, blocker-16).  Port numbers are read from AWS Systems Manager
 * Parameter Store or environment variables (blocker-11, blocker-12,
 * blocker-13).  Raw JDBC DriverManager has been replaced with HikariCP
 * connection pooling (blocker-17, blocker-18).  Explicit connection and
 * socket timeouts are configured on the pool (blocker-19).
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration keys / environment-variable names
    // -----------------------------------------------------------------------

    /** AWS Secrets Manager secret name that holds DB credentials as JSON. */
    private static final String DB_SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db-credentials");

    /** AWS SSM Parameter Store path for the DB port (blocker-11, blocker-12). */
    private static final String DB_PORT_PARAM =
            System.getenv().getOrDefault("DB_PORT_PARAM", "/mini-app/db/port");

    /** AWS SSM Parameter Store path for the Redis port (blocker-13). */
    private static final String REDIS_PORT_PARAM =
            System.getenv().getOrDefault("REDIS_PORT_PARAM", "/mini-app/redis/port");

    /** AWS region — injected via environment variable at deploy time. */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // Runtime-resolved configuration (no hard-coded values)
    // -----------------------------------------------------------------------

    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUsername;
    private final String dbPassword;
    private final String redisHost;
    private final String redisPort;
    private final String externalApiUrl;
    private final String paymentServiceUrl;

    // HikariCP data source replaces raw DriverManager (blocker-17, blocker-18)
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // Constructor — resolve all configuration at startup
    // -----------------------------------------------------------------------

    public DatabaseService() {
        // Resolve DB credentials from AWS Secrets Manager (blocker-8, blocker-9,
        // blocker-10, blocker-16)
        DbCredentials creds = resolveDbCredentials();
        this.dbUsername = creds.username;
        this.dbPassword = creds.password;

        // Resolve host / name from environment variables (12-factor)
        this.dbHost = System.getenv().getOrDefault("DB_HOST", "localhost");
        this.dbName = System.getenv().getOrDefault("DB_NAME", "mini_app_db");

        // Resolve DB port from AWS SSM Parameter Store (blocker-11, blocker-12)
        this.dbPort = resolveParameter(DB_PORT_PARAM, "3306");

        // Resolve Redis connection details from environment / SSM (blocker-13)
        this.redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
        this.redisPort = resolveParameter(REDIS_PORT_PARAM, "6379");

        // External service URLs from environment variables (12-factor)
        this.externalApiUrl  = System.getenv().getOrDefault("EXTERNAL_API_URL",
                "http://api.example.com/v1");
        this.paymentServiceUrl = System.getenv().getOrDefault("PAYMENT_SERVICE_URL",
                "https://payment.internal.company.com/process");
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void connect() {
        System.out.println("Connecting to database via HikariCP...");

        // Build HikariCP pool — replaces raw DriverManager.getConnection()
        // (blocker-17, blocker-18) and adds explicit timeouts (blocker-19)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Connection pool sizing
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        // Explicit timeouts — prevents indefinite hangs in cloud environments
        // (blocker-19: Configure connection timeouts)
        config.setConnectionTimeout(Duration.ofSeconds(30).toMillis());   // max wait for pool connection
        config.setIdleTimeout(Duration.ofMinutes(10).toMillis());          // idle connection eviction
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());          // max connection lifetime
        config.setKeepaliveTime(Duration.ofMinutes(5).toMillis());         // keepalive ping interval
        config.setInitializationFailTimeout(Duration.ofSeconds(30).toMillis());

        // JDBC-level socket / connect timeouts passed via connection properties
        config.addDataSourceProperty("connectTimeout", "10000");   // 10 s TCP connect
        config.addDataSourceProperty("socketTimeout",  "30000");   // 30 s socket read

        config.setPoolName("MiniAppPool");

        dataSource = new HikariDataSource(config);
        System.out.println("HikariCP pool initialised — connected to: jdbc:mysql://"
                + dbHost + ":" + dbPort + "/" + dbName);

        connectToCache();
        initializeExternalServices();
    }

    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setQueryTimeout(30);
            System.out.println("Executing query: " + sql);
            stmt.execute();

        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP pool closed");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Retrieves DB credentials from AWS Secrets Manager.
     * The secret is expected to be a JSON object with "username" and "password"
     * keys (blocker-8, blocker-9, blocker-10, blocker-16).
     */
    private DbCredentials resolveDbCredentials() {
        try {
            SecretsManagerClient smClient = SecretsManagerClient.builder()
                    .region(Region.of(AWS_REGION))
                    .build();

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();

            GetSecretValueResponse response = smClient.getSecretValue(request);
            String secretJson = response.secretString();
            smClient.close();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(secretJson);
            String username = node.path("username").asText();
            String password = node.path("password").asText();
            return new DbCredentials(username, password);

        } catch (Exception e) {
            System.err.println("WARNING: Could not retrieve DB credentials from Secrets Manager ("
                    + e.getMessage() + "). Falling back to environment variables.");
            // Fallback: read from environment variables (still externalized, not hard-coded)
            String username = System.getenv().getOrDefault("DB_USERNAME", "");
            String password = System.getenv().getOrDefault("DB_PASSWORD", "");
            return new DbCredentials(username, password);
        }
    }

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
     * Falls back to the supplied default if the parameter cannot be fetched
     * (blocker-11, blocker-12, blocker-13).
     */
    private String resolveParameter(String paramName, String defaultValue) {
        try {
            SsmClient ssmClient = SsmClient.builder()
                    .region(Region.of(AWS_REGION))
                    .build();

            GetParameterRequest request = GetParameterRequest.builder()
                    .name(paramName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            ssmClient.close();
            return response.parameter().value();

        } catch (Exception e) {
            System.err.println("WARNING: Could not retrieve parameter '" + paramName
                    + "' from SSM (" + e.getMessage() + "). Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
        // Simulate cache connection — host/port are now externalized
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }

    // -----------------------------------------------------------------------
    // Inner helper class
    // -----------------------------------------------------------------------

    private static class DbCredentials {
        final String username;
        final String password;

        DbCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
