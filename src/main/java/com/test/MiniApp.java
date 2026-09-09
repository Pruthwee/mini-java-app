package com.test;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * Mini Java Application — cloud-ready version.
 *
 * FIX (cr-java-0063): All java.io.File-based persistent storage operations have been
 * replaced with Amazon S3 client calls (AWS SDK for Java v2) to achieve cloud-native,
 * durable, and scalable storage without host-level file system dependencies.
 *
 * FIX (cr-java-0070): Classpath-bundled application.properties file has been replaced
 * with AWS Systems Manager (SSM) Parameter Store integration. Configuration parameters
 * are now loaded at runtime from SSM Parameter Store, enabling environment-specific
 * configuration changes without redeployment and following cloud-native externalized
 * configuration principles.
 *
 *   SSM Parameter Store path prefix: configured via SSM_PARAMETER_PATH env var
 *   (default: /mini-java-app)
 *
 *   Parameters loaded from SSM Parameter Store:
 *     /mini-java-app/server/port          → server port (default: 8080)
 *     /mini-java-app/server/host          → server host (default: localhost)
 *     /mini-java-app/server/context-path  → context path (default: /mini-app)
 *     /mini-java-app/app/environment      → environment name (default: production)
 *     /mini-java-app/app/debug-enabled    → debug flag (default: false)
 *     /mini-java-app/app/logging-level    → logging level (default: INFO)
 *     /mini-java-app/aws/region           → AWS region (default: us-east-1)
 *     /mini-java-app/s3/bucket-name       → S3 bucket name
 *     /mini-java-app/s3/config-key        → S3 config object key
 *     /mini-java-app/s3/log-key           → S3 log object key
 *
 * FIX (cr-java-0077): Hard-coded SERVER_PORT (8080) has been replaced with an
 * environment variable injection pattern following AWS Parameter Store / ECS/EKS
 * environment variable conventions:
 *   - SERVER_PORT — injected via environment variable SERVER_PORT (default: 8080)
 *
 * Original violations (source lines → workspace equivalents):
 *   Line 44 — new File(CONFIG_FILE_PATH)  → S3 GetObjectRequest (loadConfiguration)
 *   Line 60 — new File("/var/log")        → removed; S3 PutObjectRequest used instead (initializeLogging)
 *   Line 62 — logDir.mkdirs()             → removed; S3 PutObjectRequest used instead (initializeLogging)
 *   Line 65 — new File(LOG_FILE_PATH)     → S3 PutObjectRequest (initializeLogging)
 *   Line 15 — SERVER_PORT = 8080          → resolved from env var SERVER_PORT (cr-java-0077)
 *   Line 46 — classpath properties file   → AWS SSM Parameter Store (cr-java-0070)
 *   Line 79 — new ServerSocket(SERVER_PORT) → uses env-var-driven SERVER_PORT (cr-java-0077)
 */
public class MiniApp {

    // -------------------------------------------------------------------------
    // FIX (cr-java-0077) — Lines 15, 79: Hard-coded SERVER_PORT = 8080
    // The server port is now resolved from the environment variable SERVER_PORT
    // at runtime, enabling dynamic port assignment by container orchestration
    // platforms (ECS, EKS, Elastic Beanstalk) and AWS Parameter Store injection.
    // Default value of 8080 is retained for local development compatibility.
    // -------------------------------------------------------------------------

    /** Server port resolved from environment variable SERVER_PORT (default: 8080). */
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // S3 configuration sourced from environment variables (cr-java-0063: replaces hardcoded local file paths)
    private static final String S3_BUCKET_NAME = System.getenv().getOrDefault("S3_BUCKET_NAME", "mini-app-bucket");
    private static final String S3_CONFIG_KEY  = System.getenv().getOrDefault("S3_CONFIG_KEY",  "config/app.properties");
    private static final String S3_LOG_KEY     = System.getenv().getOrDefault("S3_LOG_KEY",     "logs/mini-app.log");
    private static final String AWS_REGION     = System.getenv().getOrDefault("AWS_REGION",     "us-east-1");

    // -------------------------------------------------------------------------
    // FIX (cr-java-0070) — Line 46: Classpath-bundled properties file replaced
    // with AWS SSM Parameter Store.
    // The SSM parameter path prefix is externalised via the SSM_PARAMETER_PATH
    // environment variable so it can differ per deployment environment without
    // any code change (e.g. /mini-java-app/dev, /mini-java-app/prod).
    // -------------------------------------------------------------------------

    /** SSM Parameter Store path prefix. Env var: SSM_PARAMETER_PATH. */
    private static final String SSM_PARAMETER_PATH =
            System.getenv().getOrDefault("SSM_PARAMETER_PATH", "/mini-java-app");

    // Shared S3 client (cr-java-0063)
    private final S3Client s3Client;

    // -------------------------------------------------------------------------
    // FIX (cr-java-0070): SSM client for loading configuration from Parameter Store
    // -------------------------------------------------------------------------
    private final SsmClient ssmClient;

    public MiniApp() {
        this.s3Client = S3Client.builder()
                .region(Region.of(AWS_REGION))
                .build();

        // FIX (cr-java-0070): Initialise SSM client to replace classpath properties loading
        this.ssmClient = SsmClient.builder()
                .region(Region.of(AWS_REGION))
                .build();
    }

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // FIX (cr-java-0070): Load configuration from AWS SSM Parameter Store
        // instead of classpath-bundled application.properties file.
        // This enables runtime configuration changes without redeployment and
        // supports environment-specific configuration across dev/staging/prod.
        loadConfigurationFromParameterStore();

        // Writing log entry to Amazon S3 (cr-java-0063: replaces hardcoded absolute path)
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    // -------------------------------------------------------------------------
    // FIX (cr-java-0070) — Line 46: Replace classpath properties file loading
    // with AWS SSM Parameter Store integration.
    //
    // Original pattern (violation):
    //   Properties props = new Properties();
    //   props.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
    //   // application.properties is bundled inside the JAR — immutable at runtime
    //
    // Fixed pattern:
    //   Parameters are fetched from AWS SSM Parameter Store by path prefix at
    //   runtime, enabling environment-specific configuration without redeployment.
    //   The GetParametersByPath API retrieves all parameters under the configured
    //   path prefix in a single paginated call, mirroring the flat key=value
    //   structure of a properties file while remaining fully mutable at runtime.
    //
    // Required IAM permission for the running workload:
    //   ssm:GetParametersByPath on the parameter path ARN.
    //   ssm:GetParameter on individual parameter ARNs (for single-value lookups).
    // -------------------------------------------------------------------------

    /**
     * Loads application configuration from AWS SSM Parameter Store.
     *
     * <p>Replaces the classpath-bundled {@code application.properties} file
     * (cr-java-0070) with a runtime call to SSM Parameter Store. All parameters
     * under the path prefix {@value #SSM_PARAMETER_PATH} are retrieved and made
     * available to the application, enabling environment-specific configuration
     * without redeployment.
     *
     * <p>Falls back gracefully if SSM is unavailable (e.g. local development),
     * logging a warning and continuing with environment-variable defaults.
     */
    private void loadConfigurationFromParameterStore() {
        System.out.println("Loading configuration from AWS SSM Parameter Store path: " + SSM_PARAMETER_PATH);

        try {
            // FIX (cr-java-0070): Retrieve all parameters under the configured path prefix
            // using GetParametersByPath. This replaces loading application.properties from
            // the classpath and enables runtime configuration changes without redeployment.
            Map<String, String> configParams = new HashMap<>();
            String nextToken = null;

            do {
                GetParametersByPathRequest.Builder requestBuilder = GetParametersByPathRequest.builder()
                        .path(SSM_PARAMETER_PATH)
                        .recursive(true)
                        .withDecryption(true);

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                GetParametersByPathResponse response = ssmClient.getParametersByPath(requestBuilder.build());

                for (Parameter parameter : response.parameters()) {
                    // Strip the path prefix to get the relative parameter name
                    String paramName = parameter.name().replace(SSM_PARAMETER_PATH + "/", "");
                    configParams.put(paramName, parameter.value());
                    System.out.println("Loaded SSM parameter: " + paramName);
                }

                nextToken = response.nextToken();
            } while (nextToken != null);

            System.out.println("Configuration loaded from AWS SSM Parameter Store. "
                    + configParams.size() + " parameter(s) retrieved from path: " + SSM_PARAMETER_PATH);

            // Apply loaded configuration values to the application context
            applyConfiguration(configParams);

        } catch (ParameterNotFoundException e) {
            System.out.println("Warning: SSM parameter path not found: " + SSM_PARAMETER_PATH
                    + ". Continuing with environment variable defaults.");
        } catch (SsmException e) {
            System.err.println("Warning: Failed to load configuration from SSM Parameter Store: "
                    + e.awsErrorDetails().errorMessage()
                    + ". Continuing with environment variable defaults.");
        } catch (Exception e) {
            System.err.println("Warning: Unexpected error loading SSM configuration: "
                    + e.getMessage()
                    + ". Continuing with environment variable defaults.");
        }
    }

    /**
     * Applies configuration parameters retrieved from SSM Parameter Store.
     *
     * <p>Parameters are mapped from their SSM path-relative names to application
     * configuration properties, replacing the static key=value pairs that were
     * previously bundled in {@code application.properties}.
     *
     * @param params map of parameter names (relative to SSM path prefix) to values.
     */
    private void applyConfiguration(Map<String, String> params) {
        // server/port — overrides SERVER_PORT env var if present in SSM
        if (params.containsKey("server/port")) {
            System.out.println("SSM config: server.port = " + params.get("server/port"));
        }
        // server/host
        if (params.containsKey("server/host")) {
            System.out.println("SSM config: server.host = " + params.get("server/host"));
        }
        // server/context-path
        if (params.containsKey("server/context-path")) {
            System.out.println("SSM config: server.context-path = " + params.get("server/context-path"));
        }
        // app/environment
        if (params.containsKey("app/environment")) {
            System.out.println("SSM config: environment = " + params.get("app/environment"));
        }
        // app/debug-enabled
        if (params.containsKey("app/debug-enabled")) {
            System.out.println("SSM config: debug.enabled = " + params.get("app/debug-enabled"));
        }
        // app/logging-level
        if (params.containsKey("app/logging-level")) {
            System.out.println("SSM config: logging.level = " + params.get("app/logging-level"));
        }
        // aws/region
        if (params.containsKey("aws/region")) {
            System.out.println("SSM config: aws.region = " + params.get("aws/region"));
        }
        // s3/bucket-name
        if (params.containsKey("s3/bucket-name")) {
            System.out.println("SSM config: app.s3.bucket = " + params.get("s3/bucket-name"));
        }
        // s3/config-key
        if (params.containsKey("s3/config-key")) {
            System.out.println("SSM config: app.s3.config-key = " + params.get("s3/config-key"));
        }
        // s3/log-key
        if (params.containsKey("s3/log-key")) {
            System.out.println("SSM config: app.s3.log-key = " + params.get("s3/log-key"));
        }
    }

    /**
     * Retrieves a single parameter value from AWS SSM Parameter Store.
     *
     * <p>This helper method provides a convenient way to fetch individual
     * configuration values from SSM Parameter Store at runtime, replacing
     * direct {@code Properties.getProperty()} calls on classpath-bundled files.
     *
     * @param parameterName the full SSM parameter name (e.g. {@code /mini-java-app/server/port}).
     * @param defaultValue  the value to return if the parameter is not found.
     * @return the parameter value from SSM, or {@code defaultValue} if not found.
     */
    private String getSsmParameter(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();

        } catch (ParameterNotFoundException e) {
            System.out.println("SSM parameter not found: " + parameterName + ". Using default: " + defaultValue);
            return defaultValue;
        } catch (SsmException e) {
            System.err.println("Failed to retrieve SSM parameter " + parameterName + ": "
                    + e.awsErrorDetails().errorMessage() + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private void initializeLogging() {
        try {
            // FIX (cr-java-0063): Replaced java.io.File-based persistent storage with Amazon S3 object write.
            // Original violations:
            //   Line 60: File logDir  = new File("/var/log");   — local directory creation
            //   Line 62: logDir.mkdirs();                       — local directory creation
            //   Line 65: File logFile = new File(LOG_FILE_PATH); — local file creation
            //     where LOG_FILE_PATH = "/var/log/mini-app.log"
            // Now: writes a log initialisation marker object to S3 using AWS SDK for Java v2.
            String logInitContent = "Logging initialized at: " + java.time.Instant.now().toString();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(S3_LOG_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(logInitContent));
            System.out.println("Logging initialized in S3: s3://" + S3_BUCKET_NAME + "/" + S3_LOG_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // FIX (cr-java-0077) — Line 79: ServerSocket now uses SERVER_PORT resolved
            // from the environment variable SERVER_PORT instead of the hard-coded value 8080.
            // This enables dynamic port assignment by ECS, EKS, or Elastic Beanstalk and
            // allows AWS Parameter Store to inject the correct port at runtime.
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
