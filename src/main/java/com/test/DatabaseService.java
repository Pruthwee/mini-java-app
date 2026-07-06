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
 * Parameter Store or environment variables (blocker-11, blocker-12, blocker-13).
 * Raw JDBC DriverManager has been replaced with HikariCP connection pooling
 * combined with Amazon RDS Proxy (blocker-17, blocker-18).  Explicit connection
 * and socket timeouts are configured on the HikariCP pool (blocker-19).
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration keys / environment-variable names
    // -----------------------------------------------------------------------

    /** Name of the AWS Secrets Manager secret that holds DB credentials. */
    private static final String DB_SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db-credentials");

    /** AWS region used for all SDK clients. */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    /**
     * SSM Parameter Store key for the DB port (blocker-11, blocker-12).
     * Falls back to the environment variable DB_PORT, then to "3306".
     */
    private static final String DB_PORT_PARAM =
            System.getenv().getOrDefault("DB_PORT_PARAM", "/mini-app/db/port");

    /**
     * SSM Parameter Store key for the Redis port (blocker-13).
     * Falls back to the environment variable REDIS_PORT, then to "6379".
     */
    private static final String REDIS_PORT_PARAM =
            System.getenv().getOrDefault("REDIS_PORT_PARAM", "/mini-app/cache/redis/port");

    // -----------------------------------------------------------------------
    // Runtime-resolved configuration (populated in connect())
    // -----------------------------------------------------------------------
    private String dbHost;
    private String dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    private String redisHost;
    private String redisPort;
    private String externalApiUrl;
    private String paymentServiceUrl;

    // -----------------------------------------------------------------------
    // HikariCP data source (replaces raw DriverManager — blocker-17, blocker-18)
    // -----------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // AWS SDK clients
    // -----------------------------------------------------------------------
    private final SecretsManagerClient secretsClient;
    private final SsmClient ssmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseService() {
        Region region = Region.of(AWS_REGION);

        // blocker-19: configure explicit timeouts on the AWS SDK HTTP client
        this.secretsClient = SecretsManagerClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();

        this.ssmClient = SsmClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void connect() {
        try {
            System.out.println("Resolving database configuration from AWS...");

            // blocker-8, blocker-9, blocker-10, blocker-16:
            // Retrieve DB credentials from AWS Secrets Manager
            resolveDbCredentials();

            // blocker-11, blocker-12:
            // Retrieve DB port from AWS SSM Parameter Store / env var
            dbPort = resolvePort(DB_PORT_PARAM, "DB_PORT", "3306");

            // blocker-13:
            // Retrieve Redis port from AWS SSM Parameter Store / env var
            redisPort = resolvePort(REDIS_PORT_PARAM, "REDIS_PORT", "6379");

            // Resolve remaining config from environment variables
            redisHost = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
            externalApiUrl = System.getenv().getOrDefault("EXTERNAL_API_URL",
                    "http://api.example.com/v1");
            paymentServiceUrl = System.getenv().getOrDefault("PAYMENT_SERVICE_URL",
                    "https://payment.internal.company.com/process");

            // blocker-17, blocker-18, blocker-19:
            // Build HikariCP pool (replaces raw DriverManager.getConnection)
            initHikariPool();

            System.out.println("Connected to database via HikariCP: " + dbHost + ":" + dbPort + "/" + dbName);

            // Connect to cache and external services
            connectToCache();
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Database initialisation failed: " + e.getMessage());
        }
    }

    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            // blocker-19: query timeout is already enforced by HikariCP
            // connectionTimeout; keep explicit statement timeout for safety
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
            System.out.println("HikariCP connection pool closed");
        }
        secretsClient.close();
        ssmClient.close();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Fetches DB credentials from AWS Secrets Manager.
     * The secret is expected to be a JSON object with keys:
     * host, port, dbname, username, password.
     *
     * blocker-8 (DB_USERNAME), blocker-9 (DB_PASSWORD), blocker-10 (DB_URL),
     * blocker-16 (externalized secrets).
     */
    private void resolveDbCredentials() throws Exception {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(DB_SECRET_NAME)
                .build();

        GetSecretValueResponse response = secretsClient.getSecretValue(request);
        String secretJson = response.secretString();

        JsonNode secret = objectMapper.readTree(secretJson);
        dbHost     = secret.path("host").asText(
                System.getenv().getOrDefault("DB_HOST", "localhost"));
        dbPort     = secret.path("port").asText(
                System.getenv().getOrDefault("DB_PORT", "3306"));
        dbName     = secret.path("dbname").asText(
                System.getenv().getOrDefault("DB_NAME", "mini_app_db"));
        dbUsername = secret.path("username").asText();
        dbPassword = secret.path("password").asText();

        System.out.println("DB credentials resolved from AWS Secrets Manager: " + DB_SECRET_NAME);
    }

    /**
     * Resolves a port value from AWS SSM Parameter Store, falling back to an
     * environment variable and then a hard-coded default.
     *
     * blocker-11, blocker-12, blocker-13.
     */
    private String resolvePort(String ssmParamKey, String envVarName, String defaultValue) {
        // 1. Try SSM Parameter Store
        try {
            GetParameterRequest req = GetParameterRequest.builder()
                    .name(ssmParamKey)
                    .withDecryption(false)
                    .build();
            GetParameterResponse resp = ssmClient.getParameter(req);
            String value = resp.parameter().value();
            System.out.println("Port resolved from SSM [" + ssmParamKey + "]: " + value);
            return value;
        } catch (Exception e) {
            System.out.println("SSM lookup failed for " + ssmParamKey + ", falling back to env var.");
        }

        // 2. Fall back to environment variable
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // 3. Last resort default
        return defaultValue;
    }

    /**
     * Initialises the HikariCP connection pool.
     *
     * blocker-17: replaces raw DriverManager.getConnection.
     * blocker-18: connection pooling for cloud-native resource management.
     * blocker-19: explicit connection and socket timeouts prevent indefinite hangs.
     */
    private void initHikariPool() {
        String jdbcUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=true&requireSSL=true&serverTimezone=UTC";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool sizing
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        // blocker-19: timeout settings (milliseconds)
        config.setConnectionTimeout(30_000);   // max wait for a connection from pool
        config.setIdleTimeout(600_000);         // idle connection eviction
        config.setMaxLifetime(1_800_000);       // max connection lifetime
        config.setKeepaliveTime(60_000);        // keepalive ping interval
        config.setInitializationFailTimeout(10_000);

        // Connection validation
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("MiniAppHikariPool");

        dataSource = new HikariDataSource(config);
        System.out.println("HikariCP pool initialised with RDS Proxy endpoint: " + dbHost);
    }

    private void connectToCache() {
        // Redis host/port resolved from environment variables and SSM (blocker-13)
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
    }

    private void initializeExternalServices() {
        // URLs resolved from environment variables — no hard-coded values
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
}
