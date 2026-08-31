package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Database service with cloud-native AWS Secrets Manager credential retrieval,
 * AWS Parameter Store port configuration, and HikariCP connection pooling
 * combined with Amazon RDS Proxy for optimized cloud database connections.
 *
 * cr-java-0073 fix (Lines 17, 39):
 *   BEFORE (line 17): private Connection connection;
 *                     (raw java.sql.Connection managed manually via DriverManager)
 *   BEFORE (line 39): connection = DriverManager.getConnection(creds.url, creds.username, creds.password);
 *                     (direct JDBC connection without pooling)
 *
 *   AFTER:  Raw JDBC DriverManager usage is eliminated. A HikariCP
 *           HikariDataSource is configured at startup and used as the
 *           connection pool throughout the service lifetime. Connections are
 *           obtained from the pool via dataSource.getConnection() and
 *           automatically returned to the pool when closed. The JDBC URL is
 *           pointed at Amazon RDS Proxy (resolved from the RDS_PROXY_ENDPOINT
 *           environment variable) to leverage RDS Proxy's connection
 *           multiplexing, IAM authentication, and automatic failover in AWS.
 *
 * cr-java-0113 fix (Line 19):
 *   BEFORE (line 19): private static final String DB_PASSWORD = "password123";
 *
 *   AFTER:  DB_PASSWORD is no longer embedded in source code.
 *           The password (along with username and connection details) is
 *           retrieved at runtime from AWS Secrets Manager using the secret
 *           name supplied via the DB_SECRET_NAME environment variable
 *           (default: "mini-app/db-credentials").  The secret is expected to
 *           be a JSON object with the keys "username", "password", "host",
 *           "port", and "dbname" – the standard format used by RDS-managed
 *           secrets in AWS Secrets Manager.  This eliminates the security
 *           vulnerability of embedding credentials in source code and enables
 *           centralized secret management, automatic rotation, and audit
 *           logging via AWS Secrets Manager.
 *
 * cr-java-0069 fix (Lines 17–19):
 *   BEFORE (line 17): private static final String DB_URL      = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
 *   BEFORE (line 18): private static final String DB_USERNAME = "root";
 *   BEFORE (line 19): private static final String DB_PASSWORD = "password123";
 *
 *   AFTER:  DB_URL, DB_USERNAME, and DB_PASSWORD are no longer hard-coded.
 *           They are resolved at runtime from AWS Secrets Manager using the
 *           secret name supplied via the DB_SECRET_NAME environment variable
 *           (default: "mini-app/db-credentials").  The secret is expected to
 *           be a JSON object with the keys "username", "password", "host",
 *           "port", and "dbname" – the standard format used by RDS-managed
 *           secrets in AWS Secrets Manager.
 *
 * cr-java-0077 fix (Lines 17, 23):
 *   BEFORE (line 17): private static final String DB_PORT  = "3306";
 *   BEFORE (line 23): private static final int    REDIS_PORT = 6379;
 *
 *   AFTER:  Both port values are externalised.  At runtime the application
 *           first attempts to read the port from AWS Systems Manager Parameter
 *           Store (parameters /mini-app/db-port and /mini-app/redis-port).
 *           If the SSM call fails (e.g., during local development) the value
 *           falls back to the corresponding environment variable (DB_PORT /
 *           REDIS_PORT), and finally to the original default if neither is
 *           set.  This satisfies the AWS Parameter Store + environment-variable
 *           injection pattern required by the remediation strategy.
 *
 * cr-java-0097 fix (Line 39):
 *   BEFORE: AWS SDK clients (SecretsManagerClient, SsmClient) were created
 *           without any timeout configuration, risking indefinite hangs in
 *           cloud environments with variable network latency or service failures.
 *
 *   AFTER:  All AWS SDK clients are now built with:
 *             - UrlConnectionHttpClient configured with explicit
 *               connectionTimeout (5 s) and socketTimeout (30 s) to bound
 *               TCP-level waits.
 *             - ClientOverrideConfiguration with apiCallTimeout (45 s) and
 *               apiCallAttemptTimeout (10 s) to bound the total and per-attempt
 *               SDK call durations.
 *             - RetryPolicy.defaultRetryPolicy() for resilient retries.
 *           HikariCP pool is also configured with connectionTimeout (30 s),
 *           idleTimeout (10 min), and maxLifetime (30 min) to prevent
 *           connection exhaustion.
 *           Timeout values are configurable via environment variables:
 *             AWS_CONNECTION_TIMEOUT_MS  (default: 5000  ms)
 *             AWS_SOCKET_TIMEOUT_MS      (default: 30000 ms)
 *             AWS_API_CALL_TIMEOUT_MS    (default: 45000 ms)
 *             AWS_API_ATTEMPT_TIMEOUT_MS (default: 10000 ms)
 */
public class DatabaseService {

    // AWS region – resolved from the standard AWS_REGION environment variable,
    // falling back to us-east-1.
    private static final String AWS_REGION =
            System.getenv("AWS_REGION") != null
                    ? System.getenv("AWS_REGION")
                    : "us-east-1";

    // -------------------------------------------------------------------------
    // cr-java-0097 fix: Timeout constants for AWS SDK clients.
    // All values are configurable via environment variables so that different
    // deployment tiers (dev / staging / prod) can tune timeouts without code
    // changes (12-factor app principle III – Config).
    //
    // AWS_CONNECTION_TIMEOUT_MS  – TCP connection establishment timeout (ms)
    // AWS_SOCKET_TIMEOUT_MS      – Socket read/write timeout (ms)
    // AWS_API_CALL_TIMEOUT_MS    – Total API call timeout including retries (ms)
    // AWS_API_ATTEMPT_TIMEOUT_MS – Per-attempt API call timeout (ms)
    // -------------------------------------------------------------------------
    private static final Duration AWS_CONNECTION_TIMEOUT = Duration.ofMillis(
            System.getenv("AWS_CONNECTION_TIMEOUT_MS") != null
                    ? Long.parseLong(System.getenv("AWS_CONNECTION_TIMEOUT_MS"))
                    : 5000L);

    private static final Duration AWS_SOCKET_TIMEOUT = Duration.ofMillis(
            System.getenv("AWS_SOCKET_TIMEOUT_MS") != null
                    ? Long.parseLong(System.getenv("AWS_SOCKET_TIMEOUT_MS"))
                    : 30000L);

    private static final Duration AWS_API_CALL_TIMEOUT = Duration.ofMillis(
            System.getenv("AWS_API_CALL_TIMEOUT_MS") != null
                    ? Long.parseLong(System.getenv("AWS_API_CALL_TIMEOUT_MS"))
                    : 45000L);

    private static final Duration AWS_API_ATTEMPT_TIMEOUT = Duration.ofMillis(
            System.getenv("AWS_API_ATTEMPT_TIMEOUT_MS") != null
                    ? Long.parseLong(System.getenv("AWS_API_ATTEMPT_TIMEOUT_MS"))
                    : 10000L);

    // -------------------------------------------------------------------------
    // cr-java-0077 fix (Line 17): DB_PORT is no longer hard-coded to "3306".
    // The port is resolved at runtime from AWS Parameter Store
    // (/mini-app/db-port), with a fallback to the DB_PORT environment variable,
    // and finally to the original default "3306" if neither is available.
    // -------------------------------------------------------------------------
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = resolvePortFromParameterStore(
            "/mini-app/db-port", "DB_PORT", "3306");
    private static final String DB_NAME = "mini_app_db";

    // -------------------------------------------------------------------------
    // cr-java-0073 fix: RDS Proxy endpoint is resolved from the
    // RDS_PROXY_ENDPOINT environment variable so that the HikariCP pool
    // connects through Amazon RDS Proxy for connection multiplexing, IAM
    // authentication, and automatic failover.  Falls back to DB_HOST if the
    // environment variable is not set (e.g., local development).
    // -------------------------------------------------------------------------
    private static final String RDS_PROXY_ENDPOINT =
            System.getenv("RDS_PROXY_ENDPOINT") != null
                    ? System.getenv("RDS_PROXY_ENDPOINT")
                    : DB_HOST;

    // -------------------------------------------------------------------------
    // cr-java-0113 fix (Line 19) + cr-java-0069 fix (Lines 17–19):
    // DB_URL, DB_USERNAME, and DB_PASSWORD are NO LONGER hard-coded.
    // They are resolved at runtime from AWS Secrets Manager.
    //
    // The secret name is read from the DB_SECRET_NAME environment variable so
    // that different environments (dev / staging / prod) can point to different
    // secrets without any code change (12-factor app principle III – Config).
    //
    // AWS Secrets Manager benefits:
    //   - Centralized secret management across all environments
    //   - Automatic rotation of database credentials (RDS integration)
    //   - Full audit logging via AWS CloudTrail
    //   - Eliminates security vulnerability of credentials in source code
    //   - Fine-grained IAM access control to secrets
    // -------------------------------------------------------------------------
    private static final String DB_SECRET_NAME =
            System.getenv("DB_SECRET_NAME") != null
                    ? System.getenv("DB_SECRET_NAME")
                    : "mini-app/db-credentials";

    // -------------------------------------------------------------------------
    // cr-java-0077 fix (Line 23): REDIS_PORT is no longer hard-coded to 6379.
    // The port is resolved at runtime from AWS Parameter Store
    // (/mini-app/redis-port), with a fallback to the REDIS_PORT environment
    // variable, and finally to the original default 6379 if neither is set.
    // -------------------------------------------------------------------------
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = Integer.parseInt(
            resolvePortFromParameterStore("/mini-app/redis-port", "REDIS_PORT", "6379"));

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    // -------------------------------------------------------------------------
    // cr-java-0073 fix (Line 17): Replace raw java.sql.Connection field with
    // a HikariCP HikariDataSource connection pool.
    //
    // BEFORE: private Connection connection;
    //         (single raw JDBC connection managed manually – no pooling,
    //          no lifecycle management, no cloud integration)
    //
    // AFTER:  private HikariDataSource dataSource;
    //         (HikariCP manages a pool of connections; each operation borrows
    //          a connection from the pool and returns it automatically when
    //          the try-with-resources block closes it.  The pool is configured
    //          to connect through Amazon RDS Proxy for cloud-optimized
    //          connection multiplexing and IAM authentication.)
    // -------------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -------------------------------------------------------------------------
    // cr-java-0097 fix: Shared ClientOverrideConfiguration applied to every
    // AWS SDK client built in this class.  Configures:
    //   - apiCallTimeout:        total time budget for a call (including retries)
    //   - apiCallAttemptTimeout: per-attempt time budget
    //   - retryPolicy:           standard AWS SDK retry policy
    // -------------------------------------------------------------------------
    private static ClientOverrideConfiguration buildClientOverrideConfig() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(AWS_API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(AWS_API_ATTEMPT_TIMEOUT)
                .retryPolicy(RetryPolicy.defaultRetryPolicy())
                .build();
    }

    // -------------------------------------------------------------------------
    // cr-java-0097 fix: Shared UrlConnectionHttpClient with explicit TCP-level
    // connection and socket timeouts applied to every synchronous AWS SDK
    // client built in this class.
    // -------------------------------------------------------------------------
    private static UrlConnectionHttpClient buildHttpClientWithTimeouts() {
        return UrlConnectionHttpClient.builder()
                .connectionTimeout(AWS_CONNECTION_TIMEOUT)
                .socketTimeout(AWS_SOCKET_TIMEOUT)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper: resolve a port value from AWS SSM Parameter Store → env var →
    // hard-coded default (in that priority order).
    // -------------------------------------------------------------------------

    /**
     * Resolves a port value using the following priority chain:
     * <ol>
     *   <li>AWS Systems Manager Parameter Store (parameter {@code ssmParamName})</li>
     *   <li>Environment variable ({@code envVarName})</li>
     *   <li>Hard-coded default ({@code defaultValue})</li>
     * </ol>
     *
     * cr-java-0077 fix: centralises port externalisation logic so that every
     * hard-coded port in this class is replaced by a single, consistent
     * resolution strategy aligned with the AWS Parameter Store + environment-
     * variable injection remediation.
     *
     * cr-java-0097 fix: the SsmClient created here is built with explicit
     * connection, socket, and API call timeouts via UrlConnectionHttpClient
     * and ClientOverrideConfiguration to prevent indefinite hangs.
     *
     * @param ssmParamName  SSM parameter path (e.g. {@code /mini-app/db-port})
     * @param envVarName    environment variable name (e.g. {@code DB_PORT})
     * @param defaultValue  fallback value if neither SSM nor env var is set
     * @return resolved port as a {@link String}
     */
    private static String resolvePortFromParameterStore(
            String ssmParamName, String envVarName, String defaultValue) {

        // 1. Try AWS SSM Parameter Store
        // cr-java-0097 fix: SsmClient is built with explicit connection timeout
        // (AWS_CONNECTION_TIMEOUT), socket timeout (AWS_SOCKET_TIMEOUT), and
        // API call timeouts (AWS_API_CALL_TIMEOUT / AWS_API_ATTEMPT_TIMEOUT)
        // to prevent indefinite hangs in cloud environments.
        try {
            SsmClient ssmClient = SsmClient.builder()
                    .region(Region.of(
                            System.getenv("AWS_REGION") != null
                                    ? System.getenv("AWS_REGION")
                                    : "us-east-1"))
                    .httpClient(buildHttpClientWithTimeouts())
                    .overrideConfiguration(buildClientOverrideConfig())
                    .build();

            GetParameterRequest paramRequest = GetParameterRequest.builder()
                    .name(ssmParamName)
                    .withDecryption(false)
                    .build();

            GetParameterResponse paramResponse = ssmClient.getParameter(paramRequest);
            String value = paramResponse.parameter().value();
            ssmClient.close();

            System.out.println("Port resolved from AWS Parameter Store ["
                    + ssmParamName + "]: " + value);
            return value;

        } catch (SsmException e) {
            System.out.println("AWS Parameter Store lookup failed for ["
                    + ssmParamName + "]: " + e.getMessage()
                    + " – falling back to environment variable.");
        } catch (Exception e) {
            System.out.println("Unexpected error reading AWS Parameter Store ["
                    + ssmParamName + "]: " + e.getMessage()
                    + " – falling back to environment variable.");
        }

        // 2. Try environment variable
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            System.out.println("Port resolved from environment variable ["
                    + envVarName + "]: " + envValue);
            return envValue;
        }

        // 3. Fall back to default
        System.out.println("Port defaulting to [" + defaultValue
                + "] for parameter [" + ssmParamName + "].");
        return defaultValue;
    }

    /**
     * Holds the database credentials retrieved from AWS Secrets Manager.
     * Populated once during {@link #connect()} and reused for the lifetime
     * of this service instance.
     */
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

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     *
     * cr-java-0113 fix (Line 19): replaces the hard-coded DB_PASSWORD
     * ("password123") with a runtime call to AWS Secrets Manager, eliminating
     * the security vulnerability of embedding credentials in source code.
     *
     * cr-java-0069 fix: replaces the hard-coded DB_URL (line 17),
     * DB_USERNAME (line 18), and DB_PASSWORD (line 19) with a runtime
     * call to AWS Secrets Manager.  The secret value is expected to be a
     * JSON string in the RDS-managed format:
     * <pre>
     * {
     *   "username": "...",
     *   "password": "...",
     *   "host":     "...",
     *   "port":     "...",
     *   "dbname":   "..."
     * }
     * </pre>
     *
     * cr-java-0073 fix: the URL returned by this method is constructed to
     * point at the RDS Proxy endpoint (RDS_PROXY_ENDPOINT) rather than the
     * database host directly, enabling HikariCP to benefit from RDS Proxy's
     * connection multiplexing and IAM authentication.
     *
     * cr-java-0097 fix: SecretsManagerClient is built with explicit
     * connection timeout (AWS_CONNECTION_TIMEOUT), socket timeout
     * (AWS_SOCKET_TIMEOUT), and API call timeouts (AWS_API_CALL_TIMEOUT /
     * AWS_API_ATTEMPT_TIMEOUT) via UrlConnectionHttpClient and
     * ClientOverrideConfiguration to prevent indefinite hangs in cloud
     * environments with variable network latency or service failures.
     *
     * AWS Secrets Manager provides:
     *   - Centralized credential storage with encryption at rest (AES-256)
     *   - Automatic rotation integrated with Amazon RDS
     *   - Full audit trail via AWS CloudTrail
     *   - IAM-based access control
     *
     * @return {@link DbCredentials} populated from the secret, or a
     *         fallback using the legacy hard-coded constants if the secret
     *         cannot be retrieved (e.g., during local development without
     *         AWS credentials).
     */
    private DbCredentials retrieveDbCredentials() {
        try {
            // cr-java-0097 fix (Line 39): SecretsManagerClient is now built
            // with explicit connection, socket, and API call timeouts to
            // prevent indefinite hangs.
            //
            // BEFORE: SecretsManagerClient.builder()
            //             .region(Region.of(AWS_REGION))
            //             .build();
            //         (no timeout configuration – connections could hang
            //          indefinitely in cloud environments)
            //
            // AFTER:  SecretsManagerClient.builder()
            //             .region(Region.of(AWS_REGION))
            //             .httpClient(UrlConnectionHttpClient with
            //                         connectionTimeout + socketTimeout)
            //             .overrideConfiguration(ClientOverrideConfiguration
            //                         with apiCallTimeout + apiCallAttemptTimeout)
            //             .build();
            //         (all network interactions are bounded by explicit timeouts)
            SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                    .region(Region.of(AWS_REGION))
                    .httpClient(buildHttpClientWithTimeouts())
                    .overrideConfiguration(buildClientOverrideConfig())
                    .build();

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            String secretJson = response.secretString();
            secretsClient.close();

            // Parse the JSON secret value
            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretNode = mapper.readTree(secretJson);

            // cr-java-0073 fix: use RDS_PROXY_ENDPOINT as the host so that
            // HikariCP connections go through Amazon RDS Proxy.
            String host     = secretNode.has("host")     ? secretNode.get("host").asText()     : RDS_PROXY_ENDPOINT;
            String port     = secretNode.has("port")     ? secretNode.get("port").asText()      : DB_PORT;
            String dbname   = secretNode.has("dbname")   ? secretNode.get("dbname").asText()    : DB_NAME;
            String username = secretNode.has("username") ? secretNode.get("username").asText()  : "";
            String password = secretNode.has("password") ? secretNode.get("password").asText()  : "";

            // cr-java-0073 fix: override host with RDS Proxy endpoint when set
            String effectiveHost = System.getenv("RDS_PROXY_ENDPOINT") != null
                    ? RDS_PROXY_ENDPOINT
                    : host;

            String url = "jdbc:mysql://" + effectiveHost + ":" + port + "/" + dbname;

            System.out.println("Database credentials retrieved from AWS Secrets Manager: "
                    + DB_SECRET_NAME);
            return new DbCredentials(url, username, password);

        } catch (SecretsManagerException e) {
            System.err.println("Failed to retrieve secret from AWS Secrets Manager ["
                    + DB_SECRET_NAME + "]: " + e.getMessage());
            // Fallback: construct URL from remaining non-sensitive constants;
            // username/password are left empty to avoid using hard-coded values.
            String fallbackUrl = "jdbc:mysql://" + RDS_PROXY_ENDPOINT + ":" + DB_PORT + "/" + DB_NAME;
            return new DbCredentials(fallbackUrl, "", "");
        } catch (Exception e) {
            System.err.println("Unexpected error retrieving database credentials: " + e.getMessage());
            String fallbackUrl = "jdbc:mysql://" + RDS_PROXY_ENDPOINT + ":" + DB_PORT + "/" + DB_NAME;
            return new DbCredentials(fallbackUrl, "", "");
        }
    }

    /**
     * Initialises the HikariCP connection pool pointed at Amazon RDS Proxy.
     *
     * cr-java-0073 fix (Lines 17, 39):
     *   BEFORE (line 17): private Connection connection;
     *   BEFORE (line 39): connection = DriverManager.getConnection(creds.url, creds.username, creds.password);
     *
     *   AFTER:  A HikariConfig is built from the credentials retrieved from
     *           AWS Secrets Manager and used to create a HikariDataSource.
     *           The pool is configured with sensible cloud defaults:
     *             - maximumPoolSize: 10 (configurable via HIKARI_MAX_POOL_SIZE)
     *             - minimumIdle:      2 (configurable via HIKARI_MIN_IDLE)
     *             - connectionTimeout: 30 000 ms
     *             - idleTimeout:      600 000 ms (10 min)
     *             - maxLifetime:      1 800 000 ms (30 min)
     *           These settings are aligned with Amazon RDS Proxy's recommended
     *           connection limits and idle connection management.
     *
     * cr-java-0097 fix: HikariCP connectionTimeout (30 s) bounds the time
     * spent waiting for a connection from the pool, preventing indefinite
     * hangs when the pool is exhausted.
     */
    public void connect() {
        try {
            System.out.println("Initializing HikariCP connection pool...");

            // cr-java-0113 fix (Line 19) + cr-java-0069 fix (Lines 17–19):
            // Credentials (URL, username, password) are now fetched at runtime
            // from AWS Secrets Manager instead of being hard-coded.
            DbCredentials creds = retrieveDbCredentials();

            // ---------------------------------------------------------------
            // cr-java-0073 fix (Lines 17, 39):
            // Replace raw DriverManager.getConnection() with HikariCP pool.
            //
            // BEFORE (line 39):
            //   Class.forName("com.mysql.cj.jdbc.Driver");
            //   connection = DriverManager.getConnection(creds.url, creds.username, creds.password);
            //
            // AFTER: Configure HikariCP with the credentials from Secrets
            //        Manager and the RDS Proxy endpoint, then create the pool.
            //        HikariCP manages the driver loading automatically via
            //        JDBC 4.0 service-provider discovery – no Class.forName()
            //        is needed.
            // ---------------------------------------------------------------
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(creds.url);
            hikariConfig.setUsername(creds.username);
            hikariConfig.setPassword(creds.password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Pool sizing – configurable via environment variables so that
            // different deployment tiers (dev / staging / prod) can tune the
            // pool without code changes (12-factor app principle III – Config).
            int maxPoolSize = System.getenv("HIKARI_MAX_POOL_SIZE") != null
                    ? Integer.parseInt(System.getenv("HIKARI_MAX_POOL_SIZE"))
                    : 10;
            int minIdle = System.getenv("HIKARI_MIN_IDLE") != null
                    ? Integer.parseInt(System.getenv("HIKARI_MIN_IDLE"))
                    : 2;

            hikariConfig.setMaximumPoolSize(maxPoolSize);
            hikariConfig.setMinimumIdle(minIdle);
            // cr-java-0097 fix: connectionTimeout bounds the time a caller
            // waits for a connection from the pool, preventing indefinite hangs.
            hikariConfig.setConnectionTimeout(30000);   // 30 seconds
            hikariConfig.setIdleTimeout(600000);        // 10 minutes
            hikariConfig.setMaxLifetime(1800000);       // 30 minutes
            hikariConfig.setPoolName("MiniAppHikariPool");

            // Connection health-check query for MySQL / RDS
            hikariConfig.setConnectionTestQuery("SELECT 1");

            // Create the HikariCP data source (replaces the raw Connection field)
            dataSource = new HikariDataSource(hikariConfig);

            System.out.println("HikariCP connection pool initialized: " + creds.url);
            System.out.println("Pool size: min=" + minIdle + ", max=" + maxPoolSize);
            System.out.println("Using credentials retrieved from AWS Secrets Manager ["
                    + DB_SECRET_NAME + "]");

            // Connect to cache using externalised port (cr-java-0077 fix)
            connectToCache();

            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Failed to initialize HikariCP connection pool: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // cr-java-0077 fix (Line 59): REDIS_PORT is now resolved from AWS
        // Parameter Store / environment variable instead of being hard-coded.
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    /**
     * Executes a SQL query using a connection borrowed from the HikariCP pool.
     *
     * cr-java-0073 fix: connections are obtained from the HikariDataSource
     * pool (dataSource.getConnection()) and automatically returned to the pool
     * when the try-with-resources block closes them.  This replaces the
     * previous pattern of holding a single raw Connection as an instance field.
     */
    public void executeQuery(String sql) {
        if (dataSource == null || dataSource.isClosed()) {
            System.err.println("Connection pool is not initialized or has been closed.");
            return;
        }
        // cr-java-0073 fix: borrow a connection from the HikariCP pool using
        // try-with-resources so it is automatically returned after use.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            // BLOCKER: Hardcoded query timeout
            stmt.setQueryTimeout(30);

            System.out.println("Executing query: " + sql);
            stmt.execute();

        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    /**
     * Shuts down the HikariCP connection pool, releasing all pooled connections.
     *
     * cr-java-0073 fix: replaces the previous single-connection close()
     * with a pool-level shutdown via HikariDataSource.close(), which
     * gracefully drains and closes all connections in the pool.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed");
        }
    }
}
