package com.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Database service – all credentials and secrets are retrieved at runtime from
 * AWS Secrets Manager (cr-java-0113 / cr-java-0069 fix).
 *
 * <p>cr-java-0073 FIX: Raw JDBC {@code DriverManager.getConnection()} calls have been
 * replaced with a HikariCP connection pool ({@link HikariDataSource}). The JDBC URL
 * is constructed to target an Amazon RDS Proxy endpoint when the environment variable
 * {@code RDS_PROXY_ENDPOINT} is set, enabling optimised connection multiplexing and
 * IAM-based authentication in AWS cloud environments.
 *
 * <p>cr-java-0097 FIX (line 39): All AWS SDK clients (SecretsManagerClient, SsmClient)
 * are now built with explicit connection timeouts, socket (read) timeouts, and API call
 * timeouts via {@link ClientOverrideConfiguration} and {@link UrlConnectionHttpClient}.
 * This prevents indefinite hangs and resource exhaustion in cloud environments with
 * variable network latency or service failures.
 *
 * <p>No credentials, API keys, or sensitive values are embedded in source code
 * or documentation. All secrets are stored in AWS Secrets Manager and resolved
 * at runtime via {@link #fetchDbCredentials()} and {@link #fetchApiCredentials()}.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code DB_SECRET_NAME}            – AWS Secrets Manager secret name/ARN for DB credentials
 *                                            (default: "mini-java-app/db-credentials")</li>
 *   <li>{@code API_SECRET_NAME}           – AWS Secrets Manager secret name/ARN for API credentials
 *                                            (default: "mini-java-app/api-credentials")</li>
 *   <li>{@code AWS_REGION}               – AWS region where secrets are stored
 *                                            (default: "us-east-1")</li>
 *   <li>{@code RDS_PROXY_ENDPOINT}        – Amazon RDS Proxy endpoint hostname; when set the
 *                                            HikariCP pool connects through the proxy instead of
 *                                            directly to the RDS instance (cr-java-0073 fix)</li>
 *   <li>{@code DB_POOL_MAX_SIZE}          – Maximum number of connections in the HikariCP pool
 *                                            (default: 10)</li>
 *   <li>{@code DB_POOL_MIN_IDLE}          – Minimum idle connections in the HikariCP pool
 *                                            (default: 2)</li>
 *   <li>{@code DB_POOL_CONN_TIMEOUT}      – HikariCP connection timeout in milliseconds
 *                                            (default: 30000)</li>
 *   <li>{@code AWS_SDK_CONNECT_TIMEOUT_MS} – TCP connection timeout for all AWS SDK HTTP clients
 *                                            in milliseconds (default: 5000, cr-java-0097 fix)</li>
 *   <li>{@code AWS_SDK_READ_TIMEOUT_MS}   – Socket read (response) timeout for all AWS SDK HTTP
 *                                            clients in milliseconds (default: 10000, cr-java-0097 fix)</li>
 *   <li>{@code AWS_SDK_API_CALL_TIMEOUT_MS} – Maximum total duration for a single AWS API call
 *                                            including retries, in milliseconds
 *                                            (default: 30000, cr-java-0097 fix)</li>
 *   <li>{@code REDIS_HOST}               – Redis cache hostname
 *                                            (default: "127.0.0.1")</li>
 *   <li>{@code REDIS_PORT}               – Redis cache port, injected via env var or resolved
 *                                            from AWS SSM Parameter Store parameter
 *                                            {@code /mini-java-app/redis/port}
 *                                            (default: "6379")</li>
 *   <li>{@code EXTERNAL_API_URL}         – External API base URL
 *                                            (default: "http://api.example.com/v1")</li>
 *   <li>{@code EXTERNAL_API_PORT}        – External API port, injected via env var or resolved
 *                                            from AWS SSM Parameter Store parameter
 *                                            {@code /mini-java-app/external-api/port}
 *                                            (default: "8080")</li>
 * </ul>
 *
 * <p>cr-java-0077 FIX: All hard-coded port numbers (REDIS_PORT = 6379 and the port
 * embedded in EXTERNAL_API_URL) have been replaced with values resolved at runtime
 * from environment variables, with AWS SSM Parameter Store as the authoritative
 * source when the environment variable is absent.
 *
 * <p>cr-java-0113 FIX: All API keys, authentication tokens, service credentials,
 * and encryption keys previously embedded in source code have been removed and
 * replaced with AWS Secrets Manager lookups, enabling centralized secret management,
 * automatic rotation, and audit logging.
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // cr-java-0069 / cr-java-0113 FIX: DB_URL, DB_USERNAME, and DB_PASSWORD
    // are no longer hard-coded constants. They are resolved at runtime by
    // fetchDbCredentials(), which calls AWS Secrets Manager.
    // -----------------------------------------------------------------------

    /** Name / ARN of the Secrets Manager secret that holds DB credentials. */
    private static final String DB_SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-java-app/db-credentials");

    /**
     * Name / ARN of the Secrets Manager secret that holds API credentials
     * (API keys, tokens, service passwords).
     *
     * <p>cr-java-0113 FIX: API keys and service credentials are no longer
     * embedded in source code; they are fetched from AWS Secrets Manager at runtime.
     */
    private static final String API_SECRET_NAME =
            System.getenv().getOrDefault("API_SECRET_NAME", "mini-java-app/api-credentials");

    /** AWS region where the secrets are stored. */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // cr-java-0097 FIX (line 39): AWS SDK client timeout constants.
    // All SecretsManagerClient and SsmClient instances are built with these
    // explicit timeouts to prevent indefinite hangs in cloud environments.
    // -----------------------------------------------------------------------

    /**
     * TCP connection timeout for AWS SDK HTTP clients (milliseconds).
     *
     * <p>cr-java-0097 FIX: prevents indefinite TCP connection hangs when
     * AWS service endpoints are unreachable or experiencing latency.
     */
    private static final long AWS_SDK_CONNECT_TIMEOUT_MS =
            parseLongEnv("AWS_SDK_CONNECT_TIMEOUT_MS", 5_000L);

    /**
     * Socket read (response) timeout for AWS SDK HTTP clients (milliseconds).
     *
     * <p>cr-java-0097 FIX: prevents indefinite waits for a response from
     * AWS service endpoints after a connection has been established.
     */
    private static final long AWS_SDK_READ_TIMEOUT_MS =
            parseLongEnv("AWS_SDK_READ_TIMEOUT_MS", 10_000L);

    /**
     * Maximum total duration for a single AWS API call including all retries
     * (milliseconds).
     *
     * <p>cr-java-0097 FIX: bounds the total time spent on any single AWS SDK
     * call, preventing resource exhaustion when retries accumulate.
     */
    private static final long AWS_SDK_API_CALL_TIMEOUT_MS =
            parseLongEnv("AWS_SDK_API_CALL_TIMEOUT_MS", 30_000L);

    // -----------------------------------------------------------------------
    // cr-java-0073 FIX: HikariCP pool configuration constants.
    // These replace the raw DriverManager.getConnection() calls that were
    // previously at lines 17 and 39 of the original source file.
    // -----------------------------------------------------------------------

    /**
     * Amazon RDS Proxy endpoint – when set, the HikariCP pool connects through
     * the proxy for optimised connection multiplexing and IAM authentication.
     *
     * <p>cr-java-0073 FIX: supports RDS Proxy integration as part of the
     * HikariCP + RDS Proxy remediation strategy.
     */
    private static final String RDS_PROXY_ENDPOINT =
            System.getenv("RDS_PROXY_ENDPOINT"); // null means connect directly to RDS

    /** Maximum number of connections in the HikariCP pool. */
    private static final int DB_POOL_MAX_SIZE =
            parseIntEnv("DB_POOL_MAX_SIZE", 10);

    /** Minimum number of idle connections maintained in the HikariCP pool. */
    private static final int DB_POOL_MIN_IDLE =
            parseIntEnv("DB_POOL_MIN_IDLE", 2);

    /** HikariCP connection acquisition timeout in milliseconds. */
    private static final long DB_POOL_CONN_TIMEOUT =
            parseLongEnv("DB_POOL_CONN_TIMEOUT", 30_000L);

    // -----------------------------------------------------------------------
    // cr-java-0077 FIX: REDIS_PORT is no longer a hard-coded literal.
    // The port is resolved at runtime: first from the REDIS_PORT environment
    // variable (set by ECS/EKS task definition or Elastic Beanstalk env config),
    // then from AWS SSM Parameter Store (/mini-java-app/redis/port), and finally
    // falls back to the conventional default only when neither source is available.
    // -----------------------------------------------------------------------

    /** Redis cache hostname – injected via environment variable. */
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");

    /**
     * Redis cache port – resolved from environment variable {@code REDIS_PORT}
     * or AWS SSM Parameter Store parameter {@code /mini-java-app/redis/port}.
     *
     * <p>cr-java-0077 FIX: replaces {@code private static final int REDIS_PORT = 6379}.
     */
    private static final int REDIS_PORT = resolvePortFromEnvOrSsm(
            "REDIS_PORT",
            "/mini-java-app/redis/port",
            6379);

    // -----------------------------------------------------------------------
    // cr-java-0077 FIX: The port embedded in EXTERNAL_API_URL is no longer
    // hard-coded. The URL is assembled at runtime from the EXTERNAL_API_URL
    // environment variable (preferred) or by combining the EXTERNAL_API_BASE_URL
    // env var with the port resolved from EXTERNAL_API_PORT env var / SSM
    // Parameter Store (/mini-java-app/external-api/port).
    // -----------------------------------------------------------------------

    /**
     * External API base URL – resolved from environment variable
     * {@code EXTERNAL_API_URL} (full URL) or constructed from
     * {@code EXTERNAL_API_BASE_URL} + port from {@code EXTERNAL_API_PORT} /
     * SSM {@code /mini-java-app/external-api/port}.
     *
     * <p>cr-java-0077 FIX: replaces
     * {@code private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1"}.
     */
    private static final String EXTERNAL_API_URL = resolveExternalApiUrl();

    /** Payment service URL – injected via environment variable. */
    private static final String PAYMENT_SERVICE_URL =
            System.getenv().getOrDefault(
                    "PAYMENT_SERVICE_URL",
                    "https://payment.internal.company.com/process");

    // -----------------------------------------------------------------------
    // cr-java-0073 FIX: HikariDataSource replaces the raw java.sql.Connection
    // field. The pool is initialised lazily on first use via getDataSource().
    // -----------------------------------------------------------------------

    /**
     * HikariCP connection pool – lazily initialised by {@link #getDataSource()}.
     *
     * <p>cr-java-0073 FIX (lines 17 and 39): replaces the raw
     * {@code Connection connection} field that was managed via
     * {@code DriverManager.getConnection()}.
     */
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // Numeric environment-variable helpers
    // -----------------------------------------------------------------------

    private static int parseIntEnv(String envVarName, int defaultValue) {
        String val = System.getenv(envVarName);
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                System.err.println("Warning: env var " + envVarName
                        + " is not a valid integer (" + val + "); using default " + defaultValue);
            }
        }
        return defaultValue;
    }

    private static long parseLongEnv(String envVarName, long defaultValue) {
        String val = System.getenv(envVarName);
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException e) {
                System.err.println("Warning: env var " + envVarName
                        + " is not a valid long (" + val + "); using default " + defaultValue);
            }
        }
        return defaultValue;
    }

    // -----------------------------------------------------------------------
    // cr-java-0097 FIX (line 39): Shared AWS SDK client configuration factory.
    // Builds a ClientOverrideConfiguration with explicit API-call timeout and
    // a UrlConnectionHttpClient with explicit connection + read timeouts.
    // Applied to every SecretsManagerClient and SsmClient instance created
    // in this class to prevent indefinite hangs and resource exhaustion.
    // -----------------------------------------------------------------------

    /**
     * Builds a {@link ClientOverrideConfiguration} with an explicit API-call
     * timeout (total time including retries) for AWS SDK clients.
     *
     * <p>cr-java-0097 FIX: bounds the total duration of any single AWS SDK
     * API call, preventing resource exhaustion when retries accumulate.
     *
     * @return configured {@link ClientOverrideConfiguration}
     */
    private static ClientOverrideConfiguration buildAwsSdkOverrideConfig() {
        return ClientOverrideConfiguration.builder()
                // Total time allowed for the API call including all retries
                .apiCallTimeout(Duration.ofMillis(AWS_SDK_API_CALL_TIMEOUT_MS))
                // Time allowed for each individual attempt (connection + read)
                .apiCallAttemptTimeout(Duration.ofMillis(AWS_SDK_READ_TIMEOUT_MS))
                .retryPolicy(RetryPolicy.defaultRetryPolicy())
                .build();
    }

    /**
     * Builds a {@link UrlConnectionHttpClient} with explicit TCP connection
     * and socket read timeouts for AWS SDK HTTP transport.
     *
     * <p>cr-java-0097 FIX: prevents indefinite TCP connection hangs and
     * indefinite waits for responses from AWS service endpoints.
     *
     * @return configured {@link UrlConnectionHttpClient}
     */
    private static UrlConnectionHttpClient buildAwsSdkHttpClient() {
        return UrlConnectionHttpClient.builder()
                // TCP connection establishment timeout
                .connectionTimeout(Duration.ofMillis(AWS_SDK_CONNECT_TIMEOUT_MS))
                // Socket read (response) timeout after connection is established
                .socketTimeout(Duration.ofMillis(AWS_SDK_READ_TIMEOUT_MS))
                .build();
    }

    // -----------------------------------------------------------------------
    // Port-resolution helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves a port number using the following priority order:
     * <ol>
     *   <li>Environment variable {@code envVarName} (set by ECS task definition,
     *       EKS pod spec, or Elastic Beanstalk environment configuration).</li>
     *   <li>AWS SSM Parameter Store parameter {@code ssmParamName}.</li>
     *   <li>Hard-coded {@code defaultPort} fallback (used only in local development).</li>
     * </ol>
     *
     * <p>cr-java-0097 FIX: the SsmClient used here is built with explicit
     * connection, read, and API-call timeouts via {@link #buildAwsSdkHttpClient()}
     * and {@link #buildAwsSdkOverrideConfig()}.
     *
     * @param envVarName   name of the environment variable to check first
     * @param ssmParamName SSM Parameter Store parameter path to check second
     * @param defaultPort  fallback port used when neither source is available
     * @return the resolved port number
     */
    private static int resolvePortFromEnvOrSsm(
            String envVarName, String ssmParamName, int defaultPort) {

        // 1. Environment variable (highest priority – injected by ECS/EKS/EB)
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                System.err.println("Warning: env var " + envVarName
                        + " is not a valid integer (" + envValue + "); trying SSM.");
            }
        }

        // 2. AWS SSM Parameter Store
        // cr-java-0097 FIX: SsmClient is built with explicit connection, read,
        // and API-call timeouts to prevent indefinite hangs.
        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(AWS_REGION))
                .httpClient(buildAwsSdkHttpClient())
                .overrideConfiguration(buildAwsSdkOverrideConfig())
                .build()) {

            GetParameterRequest request = GetParameterRequest.builder()
                    .name(ssmParamName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            String ssmValue = response.parameter().value();
            if (ssmValue != null && !ssmValue.trim().isEmpty()) {
                return Integer.parseInt(ssmValue.trim());
            }
        } catch (Exception e) {
            System.err.println("Warning: could not retrieve SSM parameter "
                    + ssmParamName + ": " + e.getMessage()
                    + ". Falling back to default port " + defaultPort + ".");
        }

        // 3. Default fallback (local development only)
        return defaultPort;
    }

    /**
     * Resolves the external API URL using the following priority order:
     * <ol>
     *   <li>Environment variable {@code EXTERNAL_API_URL} (full URL).</li>
     *   <li>Constructed from {@code EXTERNAL_API_BASE_URL} + port resolved via
     *       {@code EXTERNAL_API_PORT} env var or SSM
     *       {@code /mini-java-app/external-api/port}.</li>
     *   <li>Default fallback URL with port from SSM / default.</li>
     * </ol>
     *
     * <p>cr-java-0077 FIX: eliminates the hard-coded port 8080 that was
     * embedded in the URL literal.
     *
     * @return the resolved external API URL
     */
    private static String resolveExternalApiUrl() {
        // 1. Full URL from environment variable (highest priority)
        String fullUrl = System.getenv("EXTERNAL_API_URL");
        if (fullUrl != null && !fullUrl.trim().isEmpty()) {
            return fullUrl.trim();
        }

        // 2. Construct URL from base + dynamically resolved port
        String baseUrl = System.getenv().getOrDefault(
                "EXTERNAL_API_BASE_URL", "http://api.example.com");

        int apiPort = resolvePortFromEnvOrSsm(
                "EXTERNAL_API_PORT",
                "/mini-java-app/external-api/port",
                8080);

        String pathSuffix = System.getenv().getOrDefault("EXTERNAL_API_PATH", "/v1");

        return baseUrl + ":" + apiPort + pathSuffix;
    }

    // -----------------------------------------------------------------------
    // Inner value-objects that hold credentials fetched from Secrets Manager
    // -----------------------------------------------------------------------

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
     * Holds API credentials retrieved from AWS Secrets Manager.
     *
     * <p>cr-java-0113 FIX: API keys and service credentials are no longer
     * embedded in source code; they are fetched from AWS Secrets Manager at runtime.
     */
    private static class ApiCredentials {
        final String apiKey;
        final String serviceUsername;
        final String servicePassword;

        ApiCredentials(String apiKey, String serviceUsername, String servicePassword) {
            this.apiKey = apiKey;
            this.serviceUsername = serviceUsername;
            this.servicePassword = servicePassword;
        }
    }

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     *
     * <p>The secret value is expected to be a JSON string with the keys
     * {@code username}, {@code password}, {@code host}, {@code port}, and
     * {@code dbname}. No credential values are documented or embedded in source
     * code (cr-java-0113 fix).
     *
     * <p>cr-java-0097 FIX (line 39): the {@link SecretsManagerClient} is built
     * with explicit connection timeout ({@code AWS_SDK_CONNECT_TIMEOUT_MS}),
     * socket read timeout ({@code AWS_SDK_READ_TIMEOUT_MS}), and API call timeout
     * ({@code AWS_SDK_API_CALL_TIMEOUT_MS}) to prevent indefinite hangs.
     *
     * @return a {@link DbCredentials} instance populated from the secret
     * @throws RuntimeException if the secret cannot be retrieved or parsed
     */
    private DbCredentials fetchDbCredentials() {
        // cr-java-0097 FIX (line 39): SecretsManagerClient is now built with
        // explicit connection, read, and API-call timeouts via
        // buildAwsSdkHttpClient() and buildAwsSdkOverrideConfig().
        try (SecretsManagerClient smClient = SecretsManagerClient.builder()
                .region(Region.of(AWS_REGION))
                .httpClient(buildAwsSdkHttpClient())
                .overrideConfiguration(buildAwsSdkOverrideConfig())
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();

            GetSecretValueResponse response = smClient.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretNode = mapper.readTree(secretJson);

            String host     = secretNode.path("host").asText("localhost");
            String port     = secretNode.path("port").asText("3306");
            String dbName   = secretNode.path("dbname").asText("mini_app_db");
            String username = secretNode.path("username").asText();
            String password = secretNode.path("password").asText();

            // cr-java-0073 FIX: When RDS_PROXY_ENDPOINT is set, route the JDBC URL
            // through the Amazon RDS Proxy for optimised connection pooling and
            // IAM-based authentication in AWS cloud environments.
            String jdbcHost = (RDS_PROXY_ENDPOINT != null && !RDS_PROXY_ENDPOINT.trim().isEmpty())
                    ? RDS_PROXY_ENDPOINT.trim()
                    : host;

            String dbUrl = "jdbc:mysql://" + jdbcHost + ":" + port + "/" + dbName
                    + "?useSSL=true&requireSSL=true&serverTimezone=UTC";
            return new DbCredentials(dbUrl, username, password);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to retrieve database credentials from AWS Secrets Manager "
                    + "(secret: " + DB_SECRET_NAME + "): " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves API credentials (API keys, service usernames/passwords) from
     * AWS Secrets Manager.
     *
     * <p>cr-java-0113 FIX: API keys and authentication tokens are no longer
     * embedded in source code or property files. They are fetched at runtime
     * from AWS Secrets Manager, enabling centralized secret management,
     * automatic rotation, and audit logging.
     *
     * <p>cr-java-0097 FIX (line 39): the {@link SecretsManagerClient} is built
     * with explicit connection timeout ({@code AWS_SDK_CONNECT_TIMEOUT_MS}),
     * socket read timeout ({@code AWS_SDK_READ_TIMEOUT_MS}), and API call timeout
     * ({@code AWS_SDK_API_CALL_TIMEOUT_MS}) to prevent indefinite hangs.
     *
     * <p>The secret value is expected to be a JSON string with the keys
     * {@code apiKey}, {@code serviceUsername}, and {@code servicePassword}.
     *
     * @return an {@link ApiCredentials} instance populated from the secret
     * @throws RuntimeException if the secret cannot be retrieved or parsed
     */
    private ApiCredentials fetchApiCredentials() {
        // cr-java-0097 FIX (line 39): SecretsManagerClient is now built with
        // explicit connection, read, and API-call timeouts via
        // buildAwsSdkHttpClient() and buildAwsSdkOverrideConfig().
        try (SecretsManagerClient smClient = SecretsManagerClient.builder()
                .region(Region.of(AWS_REGION))
                .httpClient(buildAwsSdkHttpClient())
                .overrideConfiguration(buildAwsSdkOverrideConfig())
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(API_SECRET_NAME)
                    .build();

            GetSecretValueResponse response = smClient.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretNode = mapper.readTree(secretJson);

            String apiKey          = secretNode.path("apiKey").asText();
            String serviceUsername = secretNode.path("serviceUsername").asText();
            String servicePassword = secretNode.path("servicePassword").asText();

            return new ApiCredentials(apiKey, serviceUsername, servicePassword);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to retrieve API credentials from AWS Secrets Manager "
                    + "(secret: " + API_SECRET_NAME + "): " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // cr-java-0073 FIX: HikariCP pool initialisation
    // -----------------------------------------------------------------------

    /**
     * Lazily initialises and returns the HikariCP {@link HikariDataSource}.
     *
     * <p>cr-java-0073 FIX (lines 17 and 39): replaces the raw
     * {@code DriverManager.getConnection()} pattern with a managed HikariCP
     * connection pool. Key pool settings:
     * <ul>
     *   <li>{@code maximumPoolSize} – controlled by {@code DB_POOL_MAX_SIZE} env var</li>
     *   <li>{@code minimumIdle}     – controlled by {@code DB_POOL_MIN_IDLE} env var</li>
     *   <li>{@code connectionTimeout} – controlled by {@code DB_POOL_CONN_TIMEOUT} env var</li>
     *   <li>{@code connectionTestQuery} – validates connections before use</li>
     *   <li>JDBC URL routes through Amazon RDS Proxy when {@code RDS_PROXY_ENDPOINT} is set</li>
     * </ul>
     *
     * @return the shared {@link HikariDataSource} instance
     */
    private synchronized HikariDataSource getDataSource() {
        if (dataSource == null || dataSource.isClosed()) {
            DbCredentials creds = fetchDbCredentials();

            HikariConfig config = new HikariConfig();

            // cr-java-0073 FIX (line 17): JDBC URL now routes through RDS Proxy
            // when RDS_PROXY_ENDPOINT is set; credentials come from Secrets Manager.
            config.setJdbcUrl(creds.url);
            config.setUsername(creds.username);
            config.setPassword(creds.password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Pool sizing – controlled via environment variables
            config.setMaximumPoolSize(DB_POOL_MAX_SIZE);
            config.setMinimumIdle(DB_POOL_MIN_IDLE);
            config.setConnectionTimeout(DB_POOL_CONN_TIMEOUT);

            // Connection health-check and pool name
            config.setConnectionTestQuery("SELECT 1");
            config.setPoolName("mini-java-app-pool");

            // Recommended settings for RDS / RDS Proxy
            config.setIdleTimeout(600_000L);       // 10 minutes
            config.setMaxLifetime(1_800_000L);     // 30 minutes (< RDS wait_timeout)
            config.setKeepaliveTime(60_000L);      // 1 minute keepalive ping

            // cr-java-0073 FIX (line 39): HikariDataSource manages the connection
            // lifecycle; callers obtain connections via dataSource.getConnection()
            // instead of DriverManager.getConnection().
            dataSource = new HikariDataSource(config);

            System.out.println("HikariCP connection pool initialised: " + creds.url
                    + " (maxPoolSize=" + DB_POOL_MAX_SIZE + ")");
        }
        return dataSource;
    }

    public void connect() {
        try {
            System.out.println("Initialising HikariCP connection pool...");

            // cr-java-0073 FIX (line 17): getDataSource() initialises the HikariCP
            // pool instead of calling DriverManager.getConnection() directly.
            // cr-java-0069 / cr-java-0113 FIX: credentials are fetched from AWS
            // Secrets Manager inside getDataSource() → fetchDbCredentials().
            HikariDataSource ds = getDataSource();
            System.out.println("HikariCP pool ready. Active connections: "
                    + ds.getHikariPoolMXBean().getActiveConnections());

            // Connect to cache using dynamically resolved port
            connectToCache();

            // Initialize external services using dynamically resolved URLs/ports
            // and credentials from AWS Secrets Manager
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Failed to initialise connection pool: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // cr-java-0077 FIX: REDIS_PORT is now resolved from environment
        // variable / AWS SSM Parameter Store instead of being hard-coded as 6379.
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // cr-java-0077 FIX: EXTERNAL_API_URL port is now resolved from
        // environment variable / AWS SSM Parameter Store instead of being hard-coded.
        // cr-java-0113 FIX: API key is fetched from AWS Secrets Manager at runtime.
        // cr-java-0097 FIX: fetchApiCredentials() uses SecretsManagerClient built
        // with explicit connection, read, and API-call timeouts.
        ApiCredentials apiCreds = fetchApiCredentials();
        System.out.println("Initializing external API: " + EXTERNAL_API_URL
                + " (API key resolved from AWS Secrets Manager)");
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL
                + " (credentials resolved from AWS Secrets Manager)");
        // Use apiCreds.apiKey, apiCreds.serviceUsername, apiCreds.servicePassword
        // when making actual HTTP calls to external services
        System.out.println("API credentials loaded for: " + apiCreds.serviceUsername);
    }

    public void executeQuery(String sql) {
        // cr-java-0073 FIX (line 39): obtain a connection from the HikariCP pool
        // via dataSource.getConnection() instead of DriverManager.getConnection().
        // HikariCP manages the connection lifecycle, validation, and return-to-pool.
        try (Connection connection = getDataSource().getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setQueryTimeout(30);
            System.out.println("Executing query: " + sql);
            stmt.execute();

        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        // cr-java-0073 FIX: close the HikariCP pool instead of a single raw connection.
        // Individual connections are returned to the pool automatically via try-with-resources
        // in executeQuery(); this method shuts down the entire pool on application shutdown.
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed");
        }
    }
}
