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
 * Database service using AWS Secrets Manager for credentials,
 * AWS SSM Parameter Store for port/host configuration,
 * and HikariCP for connection pooling with proper timeouts.
 *
 * Fixes applied:
 *  - blocker-8,9,10,16 : Hard-coded DB credentials replaced with AWS Secrets Manager
 *  - blocker-11,12,13  : Hard-coded ports replaced with AWS SSM Parameter Store / env vars
 *  - blocker-17,18     : Direct JDBC replaced with HikariCP connection pool
 *  - blocker-19        : Connection timeouts configured on HikariCP data source
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration resolved at runtime from AWS Secrets Manager / SSM / env
    // -----------------------------------------------------------------------

    /** AWS region – override via AWS_REGION environment variable */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    /**
     * Name of the AWS Secrets Manager secret that stores DB credentials.
     * Expected JSON shape: {"username":"...","password":"...","host":"...","port":"...","dbname":"..."}
     * Override via DB_SECRET_NAME environment variable.
     */
    private static final String DB_SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db-credentials");

    /**
     * SSM Parameter Store path for the DB port (used as fallback when the
     * secret does not contain a port field).
     * Override via DB_PORT_PARAM environment variable.
     */
    private static final String DB_PORT_PARAM =
            System.getenv().getOrDefault("DB_PORT_PARAM", "/mini-app/db/port");

    /**
     * SSM Parameter Store path for the Redis port.
     * Override via REDIS_PORT_PARAM environment variable.
     */
    private static final String REDIS_PORT_PARAM =
            System.getenv().getOrDefault("REDIS_PORT_PARAM", "/mini-app/cache/redis-port");

    // External service URLs – injected via environment variables (12-factor)
    private static final String EXTERNAL_API_URL =
            System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com/v1");
    private static final String PAYMENT_SERVICE_URL =
            System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    // Redis host – injected via environment variable
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");

    // -----------------------------------------------------------------------
    // HikariCP data source (replaces raw DriverManager / direct JDBC)
    // -----------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final SecretsManagerClient secretsManagerClient;
    private final SsmClient ssmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseService() {
        Region region = Region.of(AWS_REGION);

        // blocker-8,9,10,16 – Secrets Manager client
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(region)
                .build();

        // blocker-11,12,13 – SSM client for port parameters
        this.ssmClient = SsmClient.builder()
                .region(region)
                .build();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void connect() {
        try {
            System.out.println("Resolving database credentials from AWS Secrets Manager...");

            // blocker-8,9,10,16 – retrieve credentials from Secrets Manager
            DbCredentials creds = resolveDbCredentials();

            // blocker-11,12 – resolve port from secret or SSM Parameter Store
            String dbPort = (creds.port != null && !creds.port.isEmpty())
                    ? creds.port
                    : resolveParameterStoreValue(DB_PORT_PARAM, "3306");

            String dbUrl = "jdbc:mysql://" + creds.host + ":" + dbPort + "/" + creds.dbName;

            // blocker-17,18,19 – HikariCP with explicit timeouts
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(dbUrl);
            hikariConfig.setUsername(creds.username);
            hikariConfig.setPassword(creds.password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // blocker-19 – connection and timeout settings
            hikariConfig.setConnectionTimeout(30_000);          // 30 s – max wait for a connection from pool
            hikariConfig.setIdleTimeout(600_000);               // 10 min – idle connection eviction
            hikariConfig.setMaxLifetime(1_800_000);             // 30 min – max connection lifetime
            hikariConfig.setKeepaliveTime(60_000);              // 60 s – keepalive ping
            hikariConfig.setInitializationFailTimeout(10_000);  // 10 s – fail fast on startup
            hikariConfig.setMaximumPoolSize(20);
            hikariConfig.setMinimumIdle(5);
            hikariConfig.setPoolName("MiniAppHikariPool");

            // Underlying JDBC socket / network timeouts (passed to MySQL driver)
            hikariConfig.addDataSourceProperty("connectTimeout", "10000");   // 10 s
            hikariConfig.addDataSourceProperty("socketTimeout", "30000");    // 30 s

            dataSource = new HikariDataSource(hikariConfig);

            System.out.println("HikariCP connection pool initialised for: " + dbUrl);
            System.out.println("Using username resolved from AWS Secrets Manager.");

            connectToCache();
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Database initialisation failed: " + e.getMessage());
        }
    }

    public void executeQuery(String sql) {
        if (dataSource == null) {
            System.err.println("DataSource not initialised – call connect() first.");
            return;
        }
        // blocker-17,18 – obtain connection from HikariCP pool
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
            System.out.println("HikariCP connection pool closed.");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Retrieves DB credentials from AWS Secrets Manager.
     * blocker-8 (DB_URL/host), blocker-9 (username), blocker-10 (password), blocker-16 (secret)
     */
    private DbCredentials resolveDbCredentials() {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(DB_SECRET_NAME)
                .build();

        GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
        String secretJson = response.secretString();

        try {
            JsonNode node = objectMapper.readTree(secretJson);
            DbCredentials creds = new DbCredentials();
            creds.host     = node.path("host").asText("localhost");
            creds.port     = node.path("port").asText("");
            creds.dbName   = node.path("dbname").asText("mini_app_db");
            creds.username = node.path("username").asText();
            creds.password = node.path("password").asText();
            return creds;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DB credentials from Secrets Manager: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a parameter value from AWS SSM Parameter Store.
     * blocker-11,12,13 – port numbers externalised to Parameter Store
     */
    private String resolveParameterStoreValue(String paramName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(paramName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            System.err.println("Could not resolve SSM parameter '" + paramName
                    + "', using default '" + defaultValue + "': " + e.getMessage());
            return defaultValue;
        }
    }

    private void connectToCache() {
        // blocker-13 – Redis port resolved from SSM Parameter Store / env var
        String redisPort = resolveParameterStoreValue(REDIS_PORT_PARAM, "6379");
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + redisPort);
        // Actual Redis client initialisation would go here
    }

    private void initializeExternalServices() {
        System.out.println("Initialising external API: " + EXTERNAL_API_URL);
        System.out.println("Initialising payment service: " + PAYMENT_SERVICE_URL);
    }

    // -----------------------------------------------------------------------
    // Inner value object
    // -----------------------------------------------------------------------

    private static class DbCredentials {
        String host;
        String port;
        String dbName;
        String username;
        String password;
    }
}
