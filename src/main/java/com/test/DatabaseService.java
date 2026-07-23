package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Database service with cloud-native credential management via AWS Secrets Manager
 * and HikariCP connection pooling backed by Amazon RDS Proxy.
 *
 * Rule cr-java-0073 fix (lines 17, 39):
 *   BEFORE: private Connection connection;
 *           connection = DriverManager.getConnection(DB_URL, dbUsername, dbPassword);
 *           Raw JDBC DriverManager was used to obtain a single, unmanaged connection,
 *           preventing efficient resource utilisation, proper connection lifecycle
 *           management, and integration with cloud database connection pooling.
 *   AFTER:  HikariCP connection pool (HikariDataSource) is used to manage a pool of
 *           connections to Amazon RDS Proxy. RDS Proxy multiplexes application
 *           connections to the underlying RDS/Aurora instance, improving scalability
 *           and resilience. HikariCP provides fast connection acquisition, health
 *           checking, and automatic eviction of stale connections — all required for
 *           cloud-native database access patterns.
 *
 * Rule cr-java-0113 fix (line 19):
 *   BEFORE: private static final String DB_PASSWORD = "password123";
 *           Hard-coded DB_USERNAME = "root" and DB_PASSWORD = "password123"
 *           were embedded directly in source code, creating security vulnerabilities,
 *           preventing credential rotation, and violating cloud security compliance.
 *   AFTER:  Credentials are retrieved at runtime from AWS Secrets Manager using
 *           the secret name resolved from the DB_SECRET_NAME environment variable
 *           (default: "mini-app/db-credentials"). The secret is expected to be a
 *           JSON object with "username" and "password" fields, enabling automatic
 *           rotation without redeployment and full audit logging via AWS CloudTrail.
 *
 * Rule cr-java-0077 fix (lines 17, 23):
 *   BEFORE: Hard-coded DB_PORT = "3306" and REDIS_PORT = 6379 were embedded
 *           directly in source code, preventing dynamic port assignment required
 *           by container orchestration platforms and cloud service discovery.
 *   AFTER:  Both ports are resolved at runtime from environment variables
 *           (DB_PORT and REDIS_PORT respectively), with safe fallback defaults.
 *           Values can be injected via ECS task definitions, EKS ConfigMaps,
 *           or Elastic Beanstalk environment properties — or sourced from
 *           AWS Systems Manager Parameter Store at deployment time.
 *
 * Rule cr-java-0097 fix (line 39):
 *   BEFORE: SecretsManagerClient was built without any timeout configuration,
 *           allowing connections to hang indefinitely in cloud environments with
 *           variable network latency or transient service failures, exhausting
 *           thread pools and degrading application performance.
 *   AFTER:  SecretsManagerClient is configured with explicit timeouts via
 *           ClientOverrideConfiguration (apiCallTimeout, apiCallAttemptTimeout)
 *           and UrlConnectionHttpClient (connectionTimeout, socketTimeout).
 *           HikariCP connectionTimeout is also explicitly set to prevent indefinite
 *           waits when acquiring connections from the pool.
 */
public class DatabaseService {

    // cr-java-0077 fix: DB_HOST resolved from environment variable.
    // For RDS Proxy, set DB_HOST to the RDS Proxy endpoint (e.g.,
    // my-proxy.proxy-xxxx.us-east-1.rds.amazonaws.com).
    private static final String DB_HOST =
            System.getenv().getOrDefault("DB_HOST", "localhost");

    // cr-java-0077 fix (line 17):
    // BEFORE: private static final String DB_PORT = "3306";
    // AFTER:  Port is resolved from the DB_PORT environment variable at runtime,
    //         enabling dynamic port assignment in ECS/EKS/Elastic Beanstalk.
    //         The value can be injected from AWS Systems Manager Parameter Store.
    private static final String DB_PORT =
            System.getenv().getOrDefault("DB_PORT", "3306");

    private static final String DB_NAME =
            System.getenv().getOrDefault("DB_NAME", "mini_app_db");

    // cr-java-0073 fix: JDBC URL now points to the Amazon RDS Proxy endpoint.
    // RDS Proxy is configured via the DB_HOST environment variable.
    // The URL includes sslMode=REQUIRED to enforce TLS for RDS Proxy connections.
    private static final String DB_URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
            + "?sslMode=REQUIRED&serverTimezone=UTC";

    // cr-java-0113 fix (line 19):
    // BEFORE: private static final String DB_USERNAME = "root";
    //         private static final String DB_PASSWORD = "password123";
    // AFTER:  DB_USERNAME and DB_PASSWORD are NO LONGER hard-coded in source code.
    //         They are fetched at runtime from AWS Secrets Manager, enabling:
    //           - Centralized secret management across all environments
    //           - Automatic credential rotation without redeployment
    //           - Full audit logging of secret access via AWS CloudTrail
    //           - Elimination of credentials from source code and version control
    // The secret name is resolved from the DB_SECRET_NAME environment variable.
    private static final String DB_SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db-credentials");

    // AWS region resolved from standard environment variables
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_DEFAULT_REGION",
                    System.getenv().getOrDefault("AWS_REGION", "us-east-1"));

    // cr-java-0077 fix (line 23):
    // BEFORE: private static final String REDIS_HOST = "127.0.0.1";
    //         private static final int REDIS_PORT = 6379;
    // AFTER:  Both REDIS_HOST and REDIS_PORT are resolved from environment variables
    //         at runtime, enabling dynamic service discovery in cloud environments.
    //         Values can be injected from AWS Systems Manager Parameter Store via
    //         ECS task definitions, EKS ConfigMaps, or Elastic Beanstalk env properties.
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT =
            Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    // HikariCP connection pool size resolved from environment variables,
    // allowing tuning per environment without code changes.
    private static final int HIKARI_MAX_POOL_SIZE =
            Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MAX_SIZE", "10"));
    private static final int HIKARI_MIN_IDLE =
            Integer.parseInt(System.getenv().getOrDefault("DB_POOL_MIN_IDLE", "2"));

    // cr-java-0097 fix: HikariCP connection timeout — maximum time (ms) to wait
    // for a connection from the pool before throwing an exception. Prevents threads
    // from blocking indefinitely when the pool is exhausted or the database is
    // unreachable. Resolved from environment variable for per-environment tuning.
    private static final long HIKARI_CONNECTION_TIMEOUT_MS =
            Long.parseLong(System.getenv().getOrDefault("DB_POOL_CONNECTION_TIMEOUT_MS", "30000"));
    private static final long HIKARI_IDLE_TIMEOUT_MS =
            Long.parseLong(System.getenv().getOrDefault("DB_POOL_IDLE_TIMEOUT_MS", "600000"));
    private static final long HIKARI_MAX_LIFETIME_MS =
            Long.parseLong(System.getenv().getOrDefault("DB_POOL_MAX_LIFETIME_MS", "1800000"));

    // cr-java-0097 fix: AWS SDK client timeout values resolved from environment
    // variables. These control how long the SecretsManagerClient waits for a
    // connection to be established, for data to be received, and for the entire
    // API call to complete — preventing indefinite hangs in cloud environments.
    private static final int AWS_HTTP_CONNECTION_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_HTTP_CONNECTION_TIMEOUT_MS", "5000"));
    private static final int AWS_HTTP_SOCKET_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_HTTP_SOCKET_TIMEOUT_MS", "10000"));
    private static final int AWS_API_CALL_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_API_CALL_TIMEOUT_MS", "15000"));
    private static final int AWS_API_CALL_ATTEMPT_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_API_CALL_ATTEMPT_TIMEOUT_MS", "10000"));

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    // cr-java-0073 fix (line 17):
    // BEFORE: private Connection connection;
    //         A single raw JDBC Connection was stored as an instance field, requiring
    //         manual lifecycle management and preventing connection reuse across threads.
    // AFTER:  HikariDataSource manages a pool of connections to Amazon RDS Proxy.
    //         Connections are borrowed from the pool per operation and returned
    //         automatically, enabling efficient resource utilisation and cloud scalability.
    private HikariDataSource dataSource;

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     *
     * cr-java-0113 fix: This method replaces the previously hard-coded
     * DB_USERNAME ("root") and DB_PASSWORD ("password123") constants.
     * The secret stored in Secrets Manager must be a JSON string of the form:
     * <pre>
     * {
     *   "username": "db_user",
     *   "password": "db_pass"
     * }
     * </pre>
     *
     * The secret name is configured via the DB_SECRET_NAME environment variable
     * (default: "mini-app/db-credentials"), allowing different secrets per
     * environment (dev/staging/prod) without code changes.
     *
     * cr-java-0097 fix (line 39):
     *   BEFORE: SecretsManagerClient.builder().region(...).build()
     *           No timeout configuration was applied to the AWS SDK client, allowing
     *           connections to hang indefinitely when Secrets Manager was unreachable
     *           or experiencing elevated latency — exhausting thread pools and
     *           degrading application performance in cloud environments.
     *   AFTER:  The SecretsManagerClient is built with:
     *           1. UrlConnectionHttpClient configured with explicit connectionTimeout
     *              (time to establish TCP connection) and socketTimeout (time to wait
     *              for data after connection is established).
     *           2. ClientOverrideConfiguration with apiCallTimeout (total time budget
     *              for the entire API call including retries) and apiCallAttemptTimeout
     *              (time budget for a single attempt before retry).
     *           All timeout values are resolved from environment variables, enabling
     *           per-environment tuning without code changes (12-factor app principle III).
     *
     * @return a two-element array where [0] is the username and [1] is the password.
     * @throws RuntimeException if the secret cannot be retrieved or parsed.
     */
    private String[] fetchDbCredentialsFromSecretsManager() {
        // cr-java-0097 fix (line 39):
        // BEFORE: SecretsManagerClient.builder()
        //             .region(Region.of(AWS_REGION))
        //             .build();
        //         No HTTP client timeouts or API call timeouts were configured,
        //         allowing the client to block indefinitely on network issues.
        //
        // AFTER:  UrlConnectionHttpClient is configured with:
        //           - connectionTimeout: max time to establish the TCP connection to
        //             the Secrets Manager endpoint (default: 5 000 ms). Prevents
        //             indefinite blocking when the endpoint is unreachable.
        //           - socketTimeout: max time to wait for data after the connection
        //             is established (default: 10 000 ms). Prevents indefinite blocking
        //             when the endpoint accepts the connection but stops sending data.
        //
        //         ClientOverrideConfiguration is configured with:
        //           - apiCallTimeout: total time budget for the entire API call,
        //             including all retry attempts (default: 15 000 ms). Ensures the
        //             call fails fast if Secrets Manager is degraded.
        //           - apiCallAttemptTimeout: time budget for a single attempt before
        //             the SDK retries (default: 10 000 ms). Works with the SDK retry
        //             policy to bound each individual attempt.
        //
        //         Together these settings prevent connection hangs, resource exhaustion,
        //         and cascading failures in cloud environments with variable latency.
        SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(Region.of(AWS_REGION))
                .httpClient(
                        UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofMillis(AWS_HTTP_CONNECTION_TIMEOUT_MS))
                                .socketTimeout(Duration.ofMillis(AWS_HTTP_SOCKET_TIMEOUT_MS))
                                .build()
                )
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.ofMillis(AWS_API_CALL_TIMEOUT_MS))
                                .apiCallAttemptTimeout(Duration.ofMillis(AWS_API_CALL_ATTEMPT_TIMEOUT_MS))
                                .build()
                )
                .build();

        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            String secretJson = response.secretString();

            // Parse the JSON secret to extract username and password
            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretNode = mapper.readTree(secretJson);

            String username = secretNode.get("username").asText();
            String password = secretNode.get("password").asText();

            System.out.println("Database credentials retrieved from AWS Secrets Manager: "
                    + DB_SECRET_NAME);
            return new String[]{username, password};

        } catch (SecretsManagerException e) {
            throw new RuntimeException(
                    "Failed to retrieve database credentials from AWS Secrets Manager ["
                            + DB_SECRET_NAME + "]: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse database credentials from AWS Secrets Manager ["
                            + DB_SECRET_NAME + "]: " + e.getMessage(), e);
        } finally {
            secretsClient.close();
        }
    }

    /**
     * Initialises the HikariCP connection pool targeting Amazon RDS Proxy.
     *
     * cr-java-0073 fix (lines 17, 39):
     *   BEFORE: connection = DriverManager.getConnection(DB_URL, dbUsername, dbPassword);
     *           A single raw JDBC connection was obtained via DriverManager, with no
     *           pooling, health checking, or lifecycle management.
     *   AFTER:  HikariDataSource is configured with pool sizing, timeouts, and the
     *           RDS Proxy JDBC URL. RDS Proxy handles connection multiplexing and
     *           failover at the infrastructure level; HikariCP handles efficient
     *           connection acquisition and health checking at the application level.
     *           Together they provide the connection pooling pattern required for
     *           cloud-native database access on AWS.
     *
     * Pool configuration parameters are resolved from environment variables to allow
     * per-environment tuning without code changes (12-factor app principle III).
     */
    public void connect() {
        try {
            System.out.println("Initialising HikariCP connection pool targeting Amazon RDS Proxy...");

            // cr-java-0113 fix: Credentials fetched from AWS Secrets Manager at runtime
            String[] credentials = fetchDbCredentialsFromSecretsManager();
            String dbUsername = credentials[0];
            String dbPassword = credentials[1];

            // cr-java-0073 fix (lines 17, 39):
            // BEFORE: connection = DriverManager.getConnection(DB_URL, dbUsername, dbPassword);
            // AFTER:  HikariCP pool configured to connect via Amazon RDS Proxy.
            //
            // Key HikariCP settings for RDS Proxy:
            //   - maximumPoolSize: controls max concurrent connections to RDS Proxy
            //   - minimumIdle: keeps warm connections ready for burst traffic
            //   - connectionTimeout: max wait time to acquire a connection from the pool
            //     (cr-java-0097 fix: explicitly set to prevent indefinite blocking)
            //   - idleTimeout: how long idle connections are kept before eviction
            //   - maxLifetime: max connection lifetime to support RDS Proxy IAM rotation
            //   - connectionTestQuery: validates connections before use (MySQL-specific)
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(DB_URL);
            hikariConfig.setUsername(dbUsername);
            hikariConfig.setPassword(dbPassword);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Pool sizing — tuned for RDS Proxy which multiplexes connections
            hikariConfig.setMaximumPoolSize(HIKARI_MAX_POOL_SIZE);
            hikariConfig.setMinimumIdle(HIKARI_MIN_IDLE);

            // cr-java-0097 fix: Explicit timeout configuration for HikariCP pool.
            // connectionTimeout: max time (ms) a thread will wait to acquire a
            //   connection from the pool. Prevents indefinite blocking when the pool
            //   is exhausted or the database is unreachable (default: 30 000 ms).
            // idleTimeout: max time (ms) a connection may sit idle in the pool before
            //   being evicted (default: 600 000 ms / 10 min).
            // maxLifetime: max lifetime (ms) of a connection in the pool, ensuring
            //   connections are recycled before RDS Proxy forcibly closes them
            //   (default: 1 800 000 ms / 30 min).
            hikariConfig.setConnectionTimeout(HIKARI_CONNECTION_TIMEOUT_MS);
            hikariConfig.setIdleTimeout(HIKARI_IDLE_TIMEOUT_MS);
            hikariConfig.setMaxLifetime(HIKARI_MAX_LIFETIME_MS);

            // Connection validation — ensures stale connections are detected early
            hikariConfig.setConnectionTestQuery("SELECT 1");

            // Pool name for monitoring and logging
            hikariConfig.setPoolName("MiniApp-RDSProxy-Pool");

            // Initialise the HikariCP data source (replaces raw DriverManager call)
            dataSource = new HikariDataSource(hikariConfig);

            System.out.println("HikariCP connection pool initialised: " + DB_URL);
            System.out.println("Pool: " + hikariConfig.getPoolName()
                    + " | maxPoolSize=" + HIKARI_MAX_POOL_SIZE
                    + " | minIdle=" + HIKARI_MIN_IDLE
                    + " | connectionTimeout=" + HIKARI_CONNECTION_TIMEOUT_MS + "ms");
            System.out.println("Using username: " + dbUsername);

            // Cache connection using environment-resolved REDIS_HOST and REDIS_PORT
            connectToCache();

            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Failed to initialise HikariCP connection pool: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // cr-java-0077 fix (line 59):
        // REDIS_PORT is now resolved from the REDIS_PORT environment variable,
        // so the value printed here reflects the runtime-injected port.
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        // cr-java-0073 fix: Borrow a connection from the HikariCP pool per operation
        // instead of reusing a single long-lived raw JDBC connection.
        // The try-with-resources block ensures the connection is returned to the pool
        // automatically after use, preventing connection leaks.
        if (dataSource == null || dataSource.isClosed()) {
            System.err.println("Data source is not initialised or has been closed.");
            return;
        }
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

    public void disconnect() {
        // cr-java-0073 fix: Close the HikariCP data source (shuts down the entire pool)
        // instead of closing a single raw JDBC connection.
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed");
        }
    }
}
