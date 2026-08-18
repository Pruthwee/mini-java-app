package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

/**
 * Mini Java Application – cloud-ready with hard-coded port numbers replaced
 * by environment-variable / AWS SSM Parameter Store resolution (cr-java-0077).
 *
 * <p>cr-java-0070 FIX (line 46): Application configuration is no longer loaded
 * from a classpath-bundled properties file (application.properties). Instead,
 * all runtime configuration is externalised to AWS Systems Manager Parameter Store,
 * enabling environment-specific configuration changes without redeployment and
 * following cloud-native 12-factor app principles.
 *
 * <p>Configuration is loaded at startup from the SSM Parameter Store path prefix
 * defined by the {@code SSM_CONFIG_PATH} environment variable
 * (default: {@code /mini-java-app/config}). All parameters under that path are
 * fetched recursively and made available to the application at runtime.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code SERVER_PORT}    – HTTP server port, injected via ECS task definition,
 *                                 EKS pod spec, or Elastic Beanstalk environment
 *                                 configuration; also resolvable from AWS SSM Parameter
 *                                 Store parameter {@code /mini-java-app/server/port}
 *                                 (default: 8080).</li>
 *   <li>{@code AWS_REGION}     – AWS region used for SSM calls (default: "us-east-1").</li>
 *   <li>{@code S3_BUCKET}      – S3 bucket for config and log objects.</li>
 *   <li>{@code CONFIG_S3_KEY}  – S3 key for the application properties object.</li>
 *   <li>{@code LOG_S3_KEY}     – S3 key for the log initialisation object.</li>
 *   <li>{@code SSM_CONFIG_PATH}– SSM Parameter Store path prefix for application
 *                                 configuration (default: /mini-java-app/config).
 *                                 cr-java-0070 FIX: replaces classpath application.properties.</li>
 * </ul>
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // cr-java-0077 FIX (line 15): SERVER_PORT is no longer a hard-coded literal.
    // The port is resolved at runtime from the SERVER_PORT environment variable
    // (set by ECS/EKS task definition or Elastic Beanstalk env config) or from
    // AWS SSM Parameter Store (/mini-java-app/server/port).
    // -----------------------------------------------------------------------

    /** AWS region used for SSM Parameter Store lookups. */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    /**
     * HTTP server port – resolved from environment variable {@code SERVER_PORT}
     * or AWS SSM Parameter Store parameter {@code /mini-java-app/server/port}.
     *
     * <p>cr-java-0077 FIX (line 15): replaces
     * {@code private static final int SERVER_PORT = 8080}.
     */
    private static final int SERVER_PORT = resolveServerPort();

    // S3 bucket and object keys sourced from environment variables
    // (replaces hard-coded absolute file paths /opt/app/config/app.properties
    //  and /var/log/mini-app.log)
    private static final String S3_BUCKET =
            System.getenv().getOrDefault("S3_BUCKET", "my-app-bucket");
    private static final String CONFIG_S3_KEY =
            System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");
    private static final String LOG_S3_KEY =
            System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");

    /**
     * AWS SSM Parameter Store path prefix for application configuration.
     *
     * <p>cr-java-0070 FIX (line 46): replaces the classpath-bundled
     * {@code application.properties} file. All runtime configuration parameters
     * are stored under this SSM path and loaded at application startup, enabling
     * environment-specific changes without redeployment.
     */
    private static final String SSM_CONFIG_PATH =
            System.getenv().getOrDefault("SSM_CONFIG_PATH", "/mini-java-app/config");

    private final S3Client s3Client;

    /**
     * Application configuration loaded from AWS SSM Parameter Store.
     *
     * <p>cr-java-0070 FIX (line 46): replaces {@code Properties} loaded from
     * the classpath {@code application.properties} file. Configuration is
     * fetched from SSM Parameter Store at startup via
     * {@link #loadConfigurationFromSsm()}, making it mutable at runtime
     * without requiring a new application build or deployment.
     */
    private Map<String, String> appConfig;

    public MiniApp() {
        this.s3Client = S3Client.builder()
                .region(Region.of(AWS_REGION))
                .build();
        this.appConfig = new HashMap<>();
    }

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    // -----------------------------------------------------------------------
    // Port-resolution helper
    // -----------------------------------------------------------------------

    /**
     * Resolves the HTTP server port using the following priority order:
     * <ol>
     *   <li>Environment variable {@code SERVER_PORT} (set by ECS task definition,
     *       EKS pod spec, or Elastic Beanstalk environment configuration).</li>
     *   <li>AWS SSM Parameter Store parameter {@code /mini-java-app/server/port}.</li>
     *   <li>Default port {@code 8080} (used only in local development).</li>
     * </ol>
     *
     * <p>cr-java-0077 FIX (line 15): eliminates the hard-coded literal 8080.
     *
     * @return the resolved server port number
     */
    private static int resolveServerPort() {
        // 1. Environment variable (highest priority – injected by ECS/EKS/EB)
        String envValue = System.getenv("SERVER_PORT");
        if (envValue != null && !envValue.trim().isEmpty()) {
            try {
                return Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                System.err.println("Warning: SERVER_PORT env var is not a valid integer ("
                        + envValue + "); trying SSM Parameter Store.");
            }
        }

        // 2. AWS SSM Parameter Store
        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(AWS_REGION))
                .build()) {

            GetParameterRequest request = GetParameterRequest.builder()
                    .name("/mini-java-app/server/port")
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            String ssmValue = response.parameter().value();
            if (ssmValue != null && !ssmValue.trim().isEmpty()) {
                return Integer.parseInt(ssmValue.trim());
            }
        } catch (Exception e) {
            System.err.println("Warning: could not retrieve server port from SSM "
                    + "(/mini-java-app/server/port): " + e.getMessage()
                    + ". Falling back to default port 8080.");
        }

        // 3. Default fallback (local development only)
        return 8080;
    }

    // -----------------------------------------------------------------------
    // cr-java-0070 FIX (line 46): SSM Parameter Store configuration loader
    // -----------------------------------------------------------------------

    /**
     * Loads all application configuration parameters from AWS Systems Manager
     * Parameter Store under the path prefix defined by {@link #SSM_CONFIG_PATH}.
     *
     * <p>cr-java-0070 FIX (line 46): This method replaces the previous pattern
     * of loading a classpath-bundled {@code application.properties} file, which
     * made configuration immutable at runtime and prevented environment-specific
     * changes without redeployment. By externalising configuration to SSM
     * Parameter Store:
     * <ul>
     *   <li>Configuration can be updated at runtime without rebuilding or
     *       redeploying the application.</li>
     *   <li>Different environments (dev, staging, prod) use different SSM paths,
     *       eliminating environment-specific build artifacts.</li>
     *   <li>Sensitive configuration values can be stored as SecureString
     *       parameters with KMS encryption and IAM-controlled access.</li>
     *   <li>All configuration changes are audited via AWS CloudTrail.</li>
     * </ul>
     *
     * <p>Parameters are fetched recursively from the SSM path prefix
     * {@code SSM_CONFIG_PATH} (default: {@code /mini-java-app/config}).
     * For example, the SSM parameter {@code /mini-java-app/config/server.port}
     * is stored in the config map with key {@code server.port}.
     *
     * @return a {@link Map} of configuration key-value pairs loaded from SSM
     */
    private Map<String, String> loadConfigurationFromSsm() {
        Map<String, String> config = new HashMap<>();

        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(AWS_REGION))
                .build()) {

            String nextToken = null;
            do {
                GetParametersByPathRequest.Builder requestBuilder =
                        GetParametersByPathRequest.builder()
                                .path(SSM_CONFIG_PATH)
                                .recursive(true)
                                .withDecryption(true)
                                .maxResults(10);

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                GetParametersByPathResponse response =
                        ssmClient.getParametersByPath(requestBuilder.build());

                for (Parameter param : response.parameters()) {
                    // Strip the SSM path prefix to get the config key.
                    // e.g. /mini-java-app/config/server.port -> server.port
                    String key = param.name().replaceFirst(
                            "^" + SSM_CONFIG_PATH.replaceAll("([.^$|*+?()\\[\\]{}\\\\])", "\\\\$1") + "/?", "");
                    config.put(key, param.value());
                }

                nextToken = response.nextToken();

            } while (nextToken != null);

            System.out.println("Configuration loaded from AWS SSM Parameter Store path: "
                    + SSM_CONFIG_PATH + " (" + config.size() + " parameters)");

        } catch (Exception e) {
            System.err.println("Warning: could not load configuration from SSM Parameter Store "
                    + "(path: " + SSM_CONFIG_PATH + "): " + e.getMessage()
                    + ". Application will use environment variables and built-in defaults.");
        }

        return config;
    }

    private void initializeApplication() {
        // cr-java-0070 FIX (line 46): Load configuration from AWS SSM Parameter Store
        // instead of from a classpath-bundled application.properties file.
        // This externalises configuration, enabling runtime changes without redeployment
        // and supporting environment-specific configuration across dev/staging/prod.
        this.appConfig = loadConfigurationFromSsm();

        // Writing log initialisation record to Amazon S3 (replaces hard-coded absolute path write)
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void initializeLogging() {
        try {
            // cr-java-0063 fix (lines 60, 62, 65): replaced new File("/var/log"),
            // new File(LOG_FILE_PATH), and logFile.createNewFile() with an Amazon S3
            // PutObject call using AWS SDK v2.
            String logInitContent = "Logging initialised for mini-app\n";
            byte[] logBytes = logInitContent.getBytes(StandardCharsets.UTF_8);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(logBytes));
            System.out.println("Logging initialised in S3: s3://"
                    + S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialise logging in S3: " + e.getMessage());
        }
    }

    /**
     * Retrieves a configuration value by key from the SSM-loaded configuration map.
     *
     * <p>cr-java-0070 FIX: Provides access to configuration values that were
     * previously read from the classpath {@code application.properties} file.
     * Values are now sourced from AWS SSM Parameter Store at runtime.
     *
     * @param key          the configuration key (e.g., "server.port")
     * @param defaultValue the fallback value if the key is not present in SSM config
     * @return the configuration value, or {@code defaultValue} if not found
     */
    public String getConfig(String key, String defaultValue) {
        return appConfig.getOrDefault(key, defaultValue);
    }

    private void startServer() {
        try {
            // cr-java-0077 FIX (line 79): SERVER_PORT is now resolved from environment
            // variable / AWS SSM Parameter Store instead of being hard-coded as 8080.
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
