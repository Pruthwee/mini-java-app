package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Database service with credentials retrieved from AWS Secrets Manager
 * and connections managed by HikariCP connection pool via Amazon RDS Proxy.
 *
 * REMEDIATION (cr-java-0073 — Direct JDBC Connections):
 *   Raw JDBC DriverManager.getConnection() calls (previously at lines 17 and 39)
 *   have been replaced with HikariCP connection pooling backed by Amazon RDS Proxy.
 *   HikariCP manages the connection lifecycle, pool sizing, and health checks,
 *   while RDS Proxy provides connection multiplexing, failover, and IAM-based
 *   authentication at the cloud layer.
 *
 * REMEDIATION (cr-java-0113 — Lack of Externalized Secrets):
 *   All previously hard-coded credentials (DB_URL, DB_USERNAME, DB_PASSWORD)
 *   have been removed from source code.  They are now retrieved at runtime from
 *   AWS Secrets Manager, eliminating credential exposure and enabling automatic
 *   rotation and centralised audit logging.
 *
 * REMEDIATION (cr-java-0097 — Missing Connection Timeouts):
 *   Explicit connection timeout, socket timeout, and API call timeout have been
 *   added to the SecretsManagerClient via ClientOverrideConfiguration and
 *   ApacheHttpClient. This prevents indefinite hangs in cloud environments with
 *   variable network latency or transient service failures.
 *
 * Secret format expected in AWS Secrets Manager (JSON):
 *   {
 *     "username": "...",
 *     "password": "...",
 *     "host":     "...",
 *     "port":     "...",
 *     "dbname":   "..."
 *   }
 *
 * Runtime environment variables:
 *   DB_SECRET_NAME            – name/ARN of the Secrets Manager secret
 *                               (default: "mini-app/database/credentials")
 *   AWS_REGION                – AWS region for Secrets Manager
 *                               (default: "us-east-1")
 *   DB_HOST                   – RDS Proxy endpoint or DB host fallback
 *                               (default: "localhost")
 *   DB_PORT                   – fallback port when the secret omits the "port" field
 *                               (default: "3306")
 *   DB_NAME                   – fallback DB name
 *                               (default: "mini_app_db")
 *   DB_POOL_MAX_SIZE          – HikariCP maximum pool size (default: "10")
 *   DB_POOL_MIN_IDLE          – HikariCP minimum idle connections (default: "2")
 *   DB_POOL_TIMEOUT_MS        – HikariCP connection timeout in ms (default: "30000")
 *   DB_POOL_IDLE_TIMEOUT      – HikariCP idle timeout in ms (default: "600000")
 *   DB_POOL_MAX_LIFETIME      – HikariCP max connection lifetime in ms (default: "1800000")
 *   REDIS_PORT                – Redis port, injected from SSM Parameter Store
 *                               (default: "6379")
 *   EXTERNAL_API_URL          – full external API URL, injected from SSM Parameter Store
 *                               (default: "http://api.example.com:8080/v1")
 *   AWS_SDK_CONNECT_TIMEOUT_MS – AWS SDK HTTP connection timeout in ms (default: "5000")
 *   AWS_SDK_SOCKET_TIMEOUT_MS  – AWS SDK HTTP socket/read timeout in ms (default: "10000")
 *   AWS_SDK_API_CALL_TIMEOUT_MS– AWS SDK total API call timeout in ms (default: "15000")
 */
public class DatabaseService {

    // ---------------------------------------------------------------------------
    // Non-secret configuration — injected via environment variables at runtime.
    // These values are NOT credentials; they are topology/routing parameters that
    // are supplied by the deployment platform (ECS task definition, EKS pod spec,
    // or Elastic Beanstalk environment configuration) and are typically sourced
    // from AWS Systems Manager Parameter Store.
    // ---------------------------------------------------------------------------

    /** Fallback DB host used only when the Secrets Manager secret omits "host".
     *  In cloud deployments this should point to the Amazon RDS Proxy endpoint. */
    private static final String DB_HOST_FALLBACK =
            System.getenv().getOrDefault("DB_HOST", "localhost");

    /**
     * Fallback DB port — injected at runtime via the DB_PORT environment variable
     * (sourced from SSM Parameter /mini-app/database/port).
     * Falls back to "3306" only when the variable is absent.
     */
    private static final String DB_PORT_FALLBACK =
            System.getenv().getOrDefault("DB_PORT", "3306");

    /** Fallback DB name used only when the Secrets Manager secret omits "dbname". */
    private static final String DB_NAME_FALLBACK =
            System.getenv().getOrDefault("DB_NAME", "mini_app_db");

    // ---------------------------------------------------------------------------
    // HikariCP pool configuration — injected via environment variables.
    // ---------------------------------------------------------------------------

    /** Maximum number of connections in the HikariCP pool. */
    private static final int DB_POOL_MAX_SIZE =
            Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MAX_SIZE", "10"));

    /** Minimum number of idle connections maintained by HikariCP. */
    private static final int DB_POOL_MIN_IDLE =
            Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MIN_IDLE", "2"));

    /** Maximum milliseconds HikariCP will wait for a connection from the pool. */
    private static final long DB_POOL_TIMEOUT_MS =
            Long.parseLong(System.getenv().getOrDefault("DB_POOL_TIMEOUT_MS", "30000"));

    /** Milliseconds a connection is allowed to sit idle before being retired. */
    private static final long DB_POOL_IDLE_TIMEOUT =
            Long.parseLong(System.getenv().getOrDefault("DB_POOL_IDLE_TIMEOUT", "600000"));

    /** Maximum lifetime of a connection in the pool (ms). */
    private static final long DB_POOL_MAX_LIFETIME =
            Long.parseLong(System.getenv().getOrDefault("DB_POOL_MAX_LIFETIME", "1800000"));

    // ---------------------------------------------------------------------------
    // AWS SDK timeout configuration — injected via environment variables.
    // REMEDIATION (cr-java-0097 — Missing Connection Timeouts):
    //   These values configure explicit timeouts on all AWS SDK HTTP connections
    //   to prevent indefinite hangs in cloud environments.
    // ---------------------------------------------------------------------------

    /**
     * HTTP connection timeout for AWS SDK clients (ms).
     * Controls how long the SDK waits to establish a TCP connection to the
     * AWS service endpoint before failing.
     * Override via AWS_SDK_CONNECT_TIMEOUT_MS environment variable.
     */
    private static final long AWS_SDK_CONNECT_TIMEOUT_MS =
            Long.parseLong(System.getenv().getOrDefault("AWS_SDK_CONNECT_TIMEOUT_MS", "5000"));

    /**
     * HTTP socket (read) timeout for AWS SDK clients (ms).
     * Controls how long the SDK waits for data on an established connection
     * before failing with a socket timeout.
     * Override via AWS_SDK_SOCKET_TIMEOUT_MS environment variable.
     */
    private static final long AWS_SDK_SOCKET_TIMEOUT_MS =
            Long.parseLong(System.getenv().getOrDefault("AWS_SDK_SOCKET_TIMEOUT_MS", "10000"));

    /**
     * Total API call timeout for AWS SDK clients (ms).
     * Caps the total time (including retries) for a single SDK API call.
     * Override via AWS_SDK_API_CALL_TIMEOUT_MS environment variable.
     */
    private static final long AWS_SDK_API_CALL_TIMEOUT_MS =
            Long.parseLong(System.getenv().getOrDefault("AWS_SDK_API_CALL_TIMEOUT_MS", "15000"));

    // ---------------------------------------------------------------------------
    // AWS Secrets Manager configuration
    // ---------------------------------------------------------------------------

    /**
     * Name (or ARN) of the AWS Secrets Manager secret that stores the database
     * credentials.  Override at runtime via the DB_SECRET_NAME environment variable.
     */
    private static final String DB_SECRET_NAME =
            System.getenv("DB_SECRET_NAME") != null
                    ? System.getenv("DB_SECRET_NAME")
                    : "mini-app/database/credentials";

    /**
     * AWS region used to contact Secrets Manager.
     * Override at runtime via the AWS_REGION environment variable.
     */
    private static final String AWS_REGION =
            System.getenv("AWS_REGION") != null
                    ? System.getenv("AWS_REGION")
                    : "us-east-1";

    // ---------------------------------------------------------------------------
    // Cache / external-service configuration — injected via environment variables.
    // ---------------------------------------------------------------------------

    /** Redis host — injected at runtime via REDIS_HOST env var. */
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");

    /**
     * Redis port — injected at runtime via the REDIS_PORT environment variable
     * (sourced from SSM Parameter /mini-app/cache/port).
     * Falls back to 6379 only when the variable is absent.
     */
    private static final int REDIS_PORT =
            Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    /**
     * External API URL — injected at runtime via the EXTERNAL_API_URL environment
     * variable (sourced from SSM Parameter /mini-app/external-api/url).
     * Falls back to the original value only when the variable is absent.
     */
    private static final String EXTERNAL_API_URL =
            System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com:8080/v1");

    /** Payment service URL — injected at runtime via PAYMENT_SERVICE_URL env var. */
    private static final String PAYMENT_SERVICE_URL =
            System.getenv().getOrDefault("PAYMENT_SERVICE_URL",
                    "https://payment.internal.company.com/process");

    /**
     * HikariCP DataSource — replaces the raw {@code java.sql.DriverManager} approach.
     * A single shared pool is created on first call to {@link #connect()} and reused
     * for all subsequent {@link #executeQuery(String)} calls.
     *
     * REMEDIATION (cr-java-0073): HikariDataSource manages the full connection
     * lifecycle (creation, validation, eviction) so the application never calls
     * DriverManager.getConnection() directly.
     */
    private HikariDataSource dataSource;

    // ---------------------------------------------------------------------------
    // AWS Secrets Manager helpers
    // ---------------------------------------------------------------------------

    /**
     * Retrieves the raw secret string from AWS Secrets Manager for the
     * configured secret name.
     *
     * REMEDIATION (cr-java-0097 — Missing Connection Timeouts, line 39):
     *   The SecretsManagerClient is now built with explicit HTTP-level timeouts
     *   (connection timeout, socket/read timeout) via ApacheHttpClient and a
     *   total API call timeout via ClientOverrideConfiguration. This prevents
     *   the client from hanging indefinitely when the Secrets Manager endpoint
     *   is unreachable or slow, which is critical in cloud environments with
     *   variable network latency.
     *
     * @return the secret value as a JSON string
     * @throws RuntimeException if the secret cannot be retrieved
     */
    private String fetchSecretValue() {
        // REMEDIATION (cr-java-0097): Build an Apache HTTP client with explicit
        // connection and socket timeouts to prevent indefinite hangs.
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofMillis(AWS_SDK_CONNECT_TIMEOUT_MS))
                .socketTimeout(Duration.ofMillis(AWS_SDK_SOCKET_TIMEOUT_MS));

        // REMEDIATION (cr-java-0097): Apply a total API call timeout via
        // ClientOverrideConfiguration so that the entire Secrets Manager call
        // (including retries) is bounded.
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(AWS_SDK_API_CALL_TIMEOUT_MS))
                .apiCallAttemptTimeout(Duration.ofMillis(AWS_SDK_SOCKET_TIMEOUT_MS))
                .retryPolicy(RetryPolicy.defaultRetryPolicy())
                .build();

        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(AWS_REGION))
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(overrideConfig)
                .build();

        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();
            GetSecretValueResponse response = client.getSecretValue(request);
            return response.secretString();
        } catch (SecretsManagerException e) {
            throw new RuntimeException(
                    "Failed to retrieve database credentials from AWS Secrets Manager (secret: "
                            + DB_SECRET_NAME + "): " + e.awsErrorDetails().errorMessage(), e);
        } finally {
            client.close();
        }
    }

    /**
     * Parses the JSON secret and returns the value for the requested key.
     *
     * @param secretJson raw JSON string from Secrets Manager
     * @param key        field name to extract (e.g. "username", "password")
     * @return the field value, or an empty string if the key is absent
     */
    private String extractSecretField(String secretJson, String key) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(secretJson);
            JsonNode node = root.get(key);
            return (node != null && !node.isNull()) ? node.asText() : "";
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse secret JSON for key '" + key + "': " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Initialises the HikariCP connection pool backed by Amazon RDS Proxy.
     *
     * REMEDIATION (cr-java-0073 — Direct JDBC Connections, lines 17 and 39):
     *   Previously this method called {@code DriverManager.getConnection()} directly,
     *   which bypasses connection pooling and is incompatible with cloud-managed
     *   database proxies.  The fix replaces both occurrences:
     *
     *   • Line 17 (import java.sql.DriverManager) — import removed; HikariCP
     *     imports added instead (com.zaxxer.hikari.HikariConfig / HikariDataSource).
     *
     *   • Line 39 (DriverManager.getConnection(...)) — replaced with a
     *     {@link HikariDataSource} that is configured once and reused for every
     *     subsequent query.  HikariCP internally manages the pool of physical
     *     JDBC connections, including health checks, eviction, and reconnection.
     *     When the JDBC URL points to an Amazon RDS Proxy endpoint (set via the
     *     DB_HOST environment variable), RDS Proxy further multiplexes connections
     *     and provides IAM-based authentication and automatic failover.
     */
    public void connect() {
        try {
            System.out.println("Initialising HikariCP connection pool...");

            // Retrieve ALL credentials from AWS Secrets Manager at runtime.
            // No credentials (username, password, host, port, dbname) are present
            // in source code — they are resolved dynamically from the secret stored
            // in AWS Secrets Manager under the name configured by DB_SECRET_NAME.
            String secretJson = fetchSecretValue();

            String dbHost     = extractSecretField(secretJson, "host");
            String dbPort     = extractSecretField(secretJson, "port");
            String dbName     = extractSecretField(secretJson, "dbname");
            String dbUsername = extractSecretField(secretJson, "username");
            String dbPassword = extractSecretField(secretJson, "password");

            // Fall back to environment-variable-driven defaults when the secret
            // omits topology fields (host / port / dbname) so that non-credential
            // parameters remain independently configurable.
            if (dbHost.isEmpty())  dbHost  = DB_HOST_FALLBACK;
            if (dbPort.isEmpty())  dbPort  = DB_PORT_FALLBACK;
            if (dbName.isEmpty())  dbName  = DB_NAME_FALLBACK;

            // Build the JDBC URL targeting the RDS Proxy endpoint (or direct host
            // when running locally).  In production, DB_HOST should be set to the
            // RDS Proxy endpoint so that HikariCP connections are multiplexed and
            // managed by the proxy.
            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                    + "?useSSL=true&requireSSL=true&serverTimezone=UTC";

            // -----------------------------------------------------------------
            // REMEDIATION (cr-java-0073): Configure HikariCP pool.
            // HikariDataSource replaces the direct DriverManager.getConnection()
            // call.  All pool parameters are externalised via environment variables
            // so they can be tuned per environment without code changes.
            // -----------------------------------------------------------------
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(dbUrl);
            hikariConfig.setUsername(dbUsername);
            hikariConfig.setPassword(dbPassword);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Pool sizing — tuned for cloud environments with RDS Proxy
            hikariConfig.setMaximumPoolSize(DB_POOL_MAX_SIZE);
            hikariConfig.setMinimumIdle(DB_POOL_MIN_IDLE);

            // Timeout / lifetime settings
            hikariConfig.setConnectionTimeout(DB_POOL_TIMEOUT_MS);
            hikariConfig.setIdleTimeout(DB_POOL_IDLE_TIMEOUT);
            hikariConfig.setMaxLifetime(DB_POOL_MAX_LIFETIME);

            // Pool name for JMX / logging identification
            hikariConfig.setPoolName("MiniAppHikariPool");

            // Connection validation query
            hikariConfig.setConnectionTestQuery("SELECT 1");

            // Initialise the pool — HikariCP establishes the configured minimum
            // number of physical connections to the database (or RDS Proxy) here.
            dataSource = new HikariDataSource(hikariConfig);

            System.out.println("HikariCP pool initialised. JDBC URL: " + dbUrl);
            System.out.println("Pool size: min-idle=" + DB_POOL_MIN_IDLE
                    + ", max=" + DB_POOL_MAX_SIZE);
            System.out.println("Using username: " + dbUsername);

            connectToCache();
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Failed to initialise HikariCP connection pool: " + e.getMessage());
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    /**
     * Executes the given SQL statement using a connection borrowed from the
     * HikariCP pool.  The connection is automatically returned to the pool when
     * the try-with-resources block exits, ensuring proper lifecycle management
     * without manual {@code connection.close()} calls.
     *
     * REMEDIATION (cr-java-0073): Connections are obtained from
     * {@link HikariDataSource#getConnection()} rather than
     * {@code DriverManager.getConnection()}, enabling pool reuse and cloud-proxy
     * compatibility.
     */
    public void executeQuery(String sql) {
        if (dataSource == null || dataSource.isClosed()) {
            System.err.println("DataSource is not initialised or has been closed.");
            return;
        }
        // Use try-with-resources so the pooled connection is always returned.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setQueryTimeout(30);
            System.out.println("Executing query: " + sql);
            stmt.execute();

        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    /**
     * Shuts down the HikariCP connection pool, closing all pooled connections.
     *
     * REMEDIATION (cr-java-0073): Pool shutdown replaces the previous single-
     * connection {@code connection.close()} call, ensuring all pooled connections
     * are cleanly released.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed.");
        }
    }
}
