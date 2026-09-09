package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Database service — credentials retrieved from AWS Secrets Manager at runtime,
 * connections managed by HikariCP connection pool (optionally fronted by Amazon RDS Proxy).
 *
 * FIX (cr-java-0069): Hard-coded DB_URL, DB_USERNAME, and DB_PASSWORD have been removed.
 * The database URL, username, and password are now fetched from AWS Secrets Manager using
 * the secret name configured via the environment variable DB_SECRET_NAME (default:
 * "mini-java-app/db-credentials"). The secret is expected to be a JSON object with the
 * keys "url", "username", and "password".
 *
 * FIX (cr-java-0073): Raw JDBC DriverManager / Class.forName usage (original lines 17 and 39)
 * has been replaced with HikariCP connection pooling combined with Amazon RDS Proxy support.
 * HikariCP manages the connection lifecycle, pool sizing, health-checks, and automatic
 * reconnection — eliminating manual connection management and enabling efficient resource
 * utilisation in cloud environments.  When the JDBC URL points to an RDS Proxy endpoint
 * (e.g. jdbc:mysql://<proxy-endpoint>:3306/db) HikariCP transparently benefits from
 * RDS Proxy's connection multiplexing and IAM authentication support.
 *
 * FIX (cr-java-0077): Hard-coded port numbers have been replaced with environment
 * variable injection following AWS Parameter Store / ECS/EKS environment variable patterns:
 *   - DB_PORT    (default: 3306)  — injected via environment variable DB_PORT
 *   - REDIS_PORT (default: 6379)  — injected via environment variable REDIS_PORT
 *
 * FIX (cr-java-0097): Explicit connection, socket, and API call timeouts have been added
 * to the AWS Secrets Manager SDK client and to the HikariCP connection pool to prevent
 * indefinite hangs and resource exhaustion in cloud environments with variable network
 * latency or transient service failures.
 *
 *   SecretsManagerClient timeouts (all configurable via environment variables):
 *     - SM_API_CALL_TIMEOUT_MS      (default: 10000 ms) — total time allowed for one API call
 *     - SM_API_CALL_ATTEMPT_TIMEOUT_MS (default: 5000 ms) — time allowed per attempt/retry
 *
 *   HikariCP pool timeout settings (all configurable via environment variables):
 *     - HIKARI_CONNECTION_TIMEOUT   (default: 30000 ms) — max wait for a connection from pool
 *     - HIKARI_IDLE_TIMEOUT         (default: 600000 ms) — max time a connection may sit idle
 *     - HIKARI_MAX_LIFETIME         (default: 1800000 ms) — max lifetime of a pooled connection
 *     - HIKARI_KEEPALIVE_TIME       (default: 60000 ms)  — interval for keepalive pings
 *     - HIKARI_VALIDATION_TIMEOUT   (default: 5000 ms)   — max time to validate a connection
 *     - HIKARI_MAXIMUM_POOL_SIZE    (default: 10)
 *     - HIKARI_MINIMUM_IDLE         (default: 2)
 *
 * Example secret value stored in AWS Secrets Manager:
 *   {
 *     "url":      "jdbc:mysql://<rds-proxy-endpoint>.rds.amazonaws.com:3306/mini_app_db",
 *     "username": "appuser",
 *     "password": "s3cr3t!"
 *   }
 *
 * Required IAM permission for the running workload:
 *   secretsmanager:GetSecretValue on the secret ARN.
 */
public class DatabaseService {

    // -------------------------------------------------------------------------
    // FIX (cr-java-0069) — Lines 17-19: DB_URL, DB_USERNAME, DB_PASSWORD
    // Hard-coded credential constants have been replaced by a runtime lookup
    // against AWS Secrets Manager.  The secret name is externalised via an
    // environment variable so it can differ per deployment environment without
    // any code change.
    // -------------------------------------------------------------------------

    /** Environment variable that holds the Secrets Manager secret name/ARN. */
    private static final String SECRET_NAME_ENV = "DB_SECRET_NAME";

    /** Default secret name used when the environment variable is not set. */
    private static final String DEFAULT_SECRET_NAME = "mini-java-app/db-credentials";

    /** AWS region resolved from the environment variable AWS_REGION (standard SDK convention). */
    private static final String AWS_REGION_ENV = "AWS_REGION";

    // -------------------------------------------------------------------------
    // FIX (cr-java-0077) — Lines 17, 23: Hard-coded DB_PORT and REDIS_PORT
    // Port numbers are now resolved from environment variables at runtime,
    // enabling dynamic port assignment by container orchestration platforms
    // (ECS, EKS, Elastic Beanstalk) and AWS Parameter Store injection.
    // -------------------------------------------------------------------------

    /** DB port resolved from environment variable DB_PORT (default: 3306). */
    private static final int DB_PORT = Integer.parseInt(
            System.getenv().getOrDefault("DB_PORT", "3306"));

    /** Redis host resolved from environment variable REDIS_HOST (default: 127.0.0.1). */
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");

    /** Redis port resolved from environment variable REDIS_PORT (default: 6379). */
    private static final int REDIS_PORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    // -------------------------------------------------------------------------
    // FIX (cr-java-0097) — Line 39: Missing connection timeouts
    // Timeout values for the AWS Secrets Manager SDK client are externalised via
    // environment variables so they can be tuned per environment without code changes.
    //
    //   SM_API_CALL_TIMEOUT_MS         — total wall-clock budget for one complete API call
    //                                    (including all retries). Default: 10 000 ms.
    //   SM_API_CALL_ATTEMPT_TIMEOUT_MS — per-attempt timeout (a single HTTP round-trip).
    //                                    Default: 5 000 ms.
    //
    // These prevent the SecretsManagerClient from hanging indefinitely when the
    // Secrets Manager endpoint is unreachable or slow, which would otherwise block
    // application startup and exhaust thread pools in cloud environments.
    // -------------------------------------------------------------------------

    /** Total API call timeout for Secrets Manager (ms). Env var: SM_API_CALL_TIMEOUT_MS. */
    private static final long SM_API_CALL_TIMEOUT_MS = Long.parseLong(
            System.getenv().getOrDefault("SM_API_CALL_TIMEOUT_MS", "10000"));

    /** Per-attempt timeout for Secrets Manager (ms). Env var: SM_API_CALL_ATTEMPT_TIMEOUT_MS. */
    private static final long SM_API_CALL_ATTEMPT_TIMEOUT_MS = Long.parseLong(
            System.getenv().getOrDefault("SM_API_CALL_ATTEMPT_TIMEOUT_MS", "5000"));

    // -------------------------------------------------------------------------
    // FIX (cr-java-0073) — Lines 17 and 39: Replace raw JDBC DriverManager with
    // HikariCP connection pool + Amazon RDS Proxy.
    //
    // A single HikariDataSource instance is created once during connect() and
    // reused for all subsequent getConnection() calls.  This replaces:
    //   Line 17: Class.forName("com.mysql.cj.jdbc.Driver")   — manual driver loading
    //   Line 39: DriverManager.getConnection(url, user, pwd) — unmanaged raw connection
    //
    // HikariCP automatically:
    //   • Manages a pool of reusable connections (no manual open/close per query)
    //   • Validates connections before handing them out (connectionTestQuery / isValid)
    //   • Reconnects transparently after transient network failures
    //   • Integrates with Amazon RDS Proxy when the JDBC URL targets a Proxy endpoint
    // -------------------------------------------------------------------------

    /** HikariCP data source — initialised once in connect(), closed in disconnect(). */
    private HikariDataSource dataSource;

    // -------------------------------------------------------------------------
    // Inner value-object that holds the credentials retrieved from Secrets Manager
    // -------------------------------------------------------------------------
    private static class DbCredentials {
        final String url;
        final String username;
        final String password;

        DbCredentials(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }
    }

    // -------------------------------------------------------------------------
    // AWS Secrets Manager helper
    // -------------------------------------------------------------------------

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     *
     * FIX (cr-java-0097) — Line 39: The SecretsManagerClient is now built with
     * explicit {@code apiCallTimeout} and {@code apiCallAttemptTimeout} values via
     * {@link ClientOverrideConfiguration}.  This prevents the client from hanging
     * indefinitely when the Secrets Manager endpoint is unreachable or slow, which
     * would otherwise block application startup and exhaust thread pools.
     *
     * The secret name / ARN is read from the environment variable
     * {@value #SECRET_NAME_ENV}. If that variable is not set the default name
     * {@value #DEFAULT_SECRET_NAME} is used.
     *
     * @return {@link DbCredentials} populated from the secret JSON.
     * @throws RuntimeException if the secret cannot be retrieved or parsed.
     */
    private DbCredentials fetchCredentialsFromSecretsManager() {
        String secretName = System.getenv(SECRET_NAME_ENV);
        if (secretName == null || secretName.isEmpty()) {
            secretName = DEFAULT_SECRET_NAME;
        }

        String regionStr = System.getenv(AWS_REGION_ENV);
        Region region = (regionStr != null && !regionStr.isEmpty())
                ? Region.of(regionStr)
                : Region.US_EAST_1;

        // FIX (cr-java-0097) — Line 39: Build ClientOverrideConfiguration with explicit
        // timeouts to prevent indefinite hangs on the Secrets Manager API call.
        //
        //   apiCallTimeout        — total wall-clock budget for the entire call (all retries).
        //                           Sourced from env var SM_API_CALL_TIMEOUT_MS (default 10 s).
        //   apiCallAttemptTimeout — per-attempt (single HTTP round-trip) timeout.
        //                           Sourced from env var SM_API_CALL_ATTEMPT_TIMEOUT_MS (default 5 s).
        //
        // Without these timeouts, a slow or unreachable Secrets Manager endpoint would
        // cause the calling thread to block indefinitely, exhausting the application's
        // thread pool and degrading overall cloud service performance.
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(SM_API_CALL_TIMEOUT_MS))
                .apiCallAttemptTimeout(Duration.ofMillis(SM_API_CALL_ATTEMPT_TIMEOUT_MS))
                .retryPolicy(RetryPolicy.defaultRetryPolicy())
                .build();

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .overrideConfiguration(overrideConfig)
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(secretJson);

            String url      = root.path("url").asText();
            String username = root.path("username").asText();
            String password = root.path("password").asText();

            return new DbCredentials(url, username, password);

        } catch (SecretsManagerException e) {
            throw new RuntimeException(
                    "Failed to retrieve database credentials from AWS Secrets Manager "
                    + "(secret: " + secretName + "): " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse database credentials from AWS Secrets Manager secret: "
                    + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // HikariCP pool factory
    // -------------------------------------------------------------------------

    /**
     * Builds and returns a configured {@link HikariDataSource}.
     *
     * FIX (cr-java-0073) — Lines 17 and 39:
     * Replaces {@code Class.forName("com.mysql.cj.jdbc.Driver")} (line 17) and
     * {@code DriverManager.getConnection(url, user, pwd)} (line 39) with a
     * HikariCP-managed connection pool.  Pool sizing and timeout parameters are
     * externalised via environment variables so they can be tuned per environment
     * without code changes.
     *
     * FIX (cr-java-0097) — Line 39:
     * All HikariCP timeout parameters are explicitly set and externalised via
     * environment variables to prevent indefinite connection hangs:
     *   - connectionTimeout  — max time to wait for a connection from the pool
     *   - idleTimeout        — max time a connection may sit idle in the pool
     *   - maxLifetime        — max lifetime of a pooled connection
     *   - keepaliveTime      — interval for keepalive pings to the database
     *   - validationTimeout  — max time to validate a connection before use
     *
     * When the JDBC URL targets an Amazon RDS Proxy endpoint the pool automatically
     * benefits from RDS Proxy's connection multiplexing, IAM authentication, and
     * automatic failover capabilities.
     *
     * @param creds database credentials obtained from AWS Secrets Manager.
     * @return a fully configured and started {@link HikariDataSource}.
     */
    private HikariDataSource buildDataSource(DbCredentials creds) {
        HikariConfig config = new HikariConfig();

        // JDBC URL — may point directly to RDS or to an RDS Proxy endpoint
        config.setJdbcUrl(creds.url);
        config.setUsername(creds.username);
        config.setPassword(creds.password);

        // HikariCP automatically loads the correct JDBC driver from the classpath
        // (mysql-connector-java is on the classpath), so Class.forName() is not needed.
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Pool sizing — externalised via environment variables for cloud deployability
        config.setMaximumPoolSize(Integer.parseInt(
                System.getenv().getOrDefault("HIKARI_MAXIMUM_POOL_SIZE", "10")));
        config.setMinimumIdle(Integer.parseInt(
                System.getenv().getOrDefault("HIKARI_MINIMUM_IDLE", "2")));

        // -------------------------------------------------------------------------
        // FIX (cr-java-0097) — Line 39: Explicit HikariCP timeout configuration
        //
        // All timeout values are externalised via environment variables so they can
        // be tuned per environment (dev / staging / prod) without code changes.
        // These settings prevent connections from hanging indefinitely in cloud
        // environments with variable network latency or transient database failures.
        //
        //   connectionTimeout  — max ms to wait for a connection from the pool before
        //                        throwing SQLException. Default: 30 000 ms (30 s).
        //   idleTimeout        — max ms a connection may sit idle before being retired.
        //                        Default: 600 000 ms (10 min). Must be < maxLifetime.
        //   maxLifetime        — max ms a connection may exist in the pool (regardless
        //                        of activity). Default: 1 800 000 ms (30 min).
        //   keepaliveTime      — interval (ms) at which HikariCP sends a keepalive ping
        //                        to prevent the database or network from closing idle
        //                        connections. Default: 60 000 ms (1 min).
        //   validationTimeout  — max ms to validate a connection before handing it out.
        //                        Default: 5 000 ms (5 s). Must be < connectionTimeout.
        // -------------------------------------------------------------------------
        config.setConnectionTimeout(Long.parseLong(
                System.getenv().getOrDefault("HIKARI_CONNECTION_TIMEOUT", "30000")));
        config.setIdleTimeout(Long.parseLong(
                System.getenv().getOrDefault("HIKARI_IDLE_TIMEOUT", "600000")));
        config.setMaxLifetime(Long.parseLong(
                System.getenv().getOrDefault("HIKARI_MAX_LIFETIME", "1800000")));
        config.setKeepaliveTime(Long.parseLong(
                System.getenv().getOrDefault("HIKARI_KEEPALIVE_TIME", "60000")));
        config.setValidationTimeout(Long.parseLong(
                System.getenv().getOrDefault("HIKARI_VALIDATION_TIMEOUT", "5000")));

        // Connection health-check query for MySQL
        config.setConnectionTestQuery("SELECT 1");

        // Pool name for JMX / logging identification
        config.setPoolName("MiniAppHikariPool");

        return new HikariDataSource(config);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void connect() {
        System.out.println("Initialising HikariCP connection pool...");

        // FIX (cr-java-0069): Credentials are now fetched from AWS Secrets Manager
        // instead of being hard-coded in source.  This enables automatic credential
        // rotation via Secrets Manager without any code change or redeployment.
        DbCredentials creds = fetchCredentialsFromSecretsManager();

        // FIX (cr-java-0073) — Line 17: Class.forName("com.mysql.cj.jdbc.Driver") removed.
        // FIX (cr-java-0073) — Line 39: DriverManager.getConnection(...) replaced by
        // HikariDataSource.  The pool is created once here and reused for all queries.
        dataSource = buildDataSource(creds);

        System.out.println("HikariCP connection pool initialised. JDBC URL: " + creds.url);
        System.out.println("Using username: " + creds.username);

        // Cache connection using environment-variable-driven port (cr-java-0077)
        connectToCache();

        // BLOCKER: Hardcoded external service URLs
        initializeExternalServices();
    }

    private void connectToCache() {
        // FIX (cr-java-0077) — Line 59: REDIS_PORT is now resolved from the
        // environment variable REDIS_PORT instead of being hard-coded as 6379.
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        // FIX (cr-java-0073): Obtain a connection from the HikariCP pool instead of
        // holding a single long-lived raw Connection field.  The try-with-resources
        // block ensures the connection is returned to the pool after each query.
        if (dataSource == null || dataSource.isClosed()) {
            System.err.println("DataSource is not initialised or has been closed.");
            return;
        }
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
        // FIX (cr-java-0073): Close the HikariCP pool (releases all pooled connections)
        // instead of closing a single raw Connection.
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed.");
        }
    }
}
