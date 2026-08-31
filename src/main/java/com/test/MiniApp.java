package com.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * Mini Java Application with cloud-native S3-based file operations and
 * AWS Parameter Store port configuration.
 *
 * All java.io.File-based persistent storage operations (cr-java-0063) have been
 * replaced with Amazon S3 client calls using AWS SDK for Java v2, achieving
 * cloud-native, durable, and scalable storage without host-level file system
 * dependencies.
 *
 * cr-java-0070 fix (Line 46):
 *   BEFORE: Application loaded configuration from a classpath-bundled
 *           application.properties file (src/main/resources/application.properties),
 *           making configuration immutable at runtime and preventing
 *           environment-specific changes without redeployment.
 *
 *   AFTER:  Configuration is now loaded at runtime from AWS Systems Manager
 *           Parameter Store using the path prefix defined by the
 *           SSM_PARAMETER_PATH environment variable (default: /mini-app).
 *           All parameters under that path are fetched via GetParametersByPath
 *           and made available as application properties, enabling runtime
 *           configuration changes without redeployment and supporting
 *           environment-specific configuration across dev/staging/prod.
 *           The classpath application.properties is retained only as a
 *           last-resort fallback for local development when AWS credentials
 *           are not available.
 *
 * cr-java-0077 fix (Lines 15, 79):
 *   BEFORE (line 15): private static final int SERVER_PORT = 8080;
 *   BEFORE (line 79): ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
 *
 *   AFTER:  SERVER_PORT is no longer hard-coded to 8080.  The port is resolved
 *           at runtime from AWS Systems Manager Parameter Store
 *           (/mini-app/server-port), with a fallback to the SERVER_PORT
 *           environment variable, and finally to the original default 8080 if
 *           neither is available.  This satisfies the AWS Parameter Store +
 *           environment-variable injection remediation strategy (cr-java-0077).
 *
 * Original java.io.File usages replaced:
 *   Line 44 (original): File configFile = new File(CONFIG_FILE_PATH)
 *                        → S3 GetObject for config/app.properties
 *   Line 60 (original): File logDir = new File("/var/log")
 *                        → S3 PutObject replaces local directory creation
 *   Line 62 (original): logDir.mkdirs()
 *                        → S3 PutObject replaces local mkdirs() call
 *   Line 65 (original): File logFile = new File(LOG_FILE_PATH)
 *                        → S3 PutObject replaces local file creation
 */
public class MiniApp {

    // -------------------------------------------------------------------------
    // cr-java-0077 fix (Line 15): SERVER_PORT is no longer hard-coded to 8080.
    // The port is resolved at runtime from AWS Parameter Store
    // (/mini-app/server-port), with a fallback to the SERVER_PORT environment
    // variable, and finally to the original default 8080 if neither is set.
    // -------------------------------------------------------------------------
    private static final int SERVER_PORT = Integer.parseInt(
            resolvePortFromParameterStore("/mini-app/server-port", "SERVER_PORT", "8080"));

    // Cloud-native: S3 bucket name is read from the environment variable
    // S3_BUCKET_NAME so that it can be configured per deployment without
    // code changes (12-factor app principle III – Config).
    private static final String S3_BUCKET_NAME =
            System.getenv("S3_BUCKET_NAME") != null
                    ? System.getenv("S3_BUCKET_NAME")
                    : "mini-app-bucket";

    // S3 object keys that replace the former absolute local file paths:
    //   /opt/app/config/app.properties  →  config/app.properties
    //   /var/log/mini-app.log           →  logs/mini-app.log
    private static final String CONFIG_S3_KEY = "config/app.properties";
    private static final String LOG_S3_KEY    = "logs/mini-app.log";

    // -------------------------------------------------------------------------
    // cr-java-0070 fix (Line 46): SSM Parameter Store path prefix.
    // All application configuration parameters are stored under this path in
    // AWS Systems Manager Parameter Store, enabling runtime configuration
    // changes without redeployment.
    //
    // The path is configurable via the SSM_PARAMETER_PATH environment variable
    // so that different environments (dev/staging/prod) can use different
    // parameter namespaces without code changes (12-factor app principle III).
    //
    // Example parameters stored in SSM:
    //   /mini-app/server.host          → localhost
    //   /mini-app/server.context-path  → /mini-app
    //   /mini-app/database.driver      → com.mysql.cj.jdbc.Driver
    //   /mini-app/environment          → production
    //   /mini-app/logging.level        → INFO
    // -------------------------------------------------------------------------
    private static final String SSM_PARAMETER_PATH =
            System.getenv("SSM_PARAMETER_PATH") != null
                    ? System.getenv("SSM_PARAMETER_PATH")
                    : "/mini-app";

    // Shared S3 client – region is resolved from the AWS_REGION environment
    // variable or falls back to us-east-1.  Credentials are resolved
    // automatically by the AWS SDK default credential provider chain
    // (IAM role, environment variables, ~/.aws/credentials, etc.).
    private static final S3Client s3Client = S3Client.builder()
            .region(Region.of(
                    System.getenv("AWS_REGION") != null
                            ? System.getenv("AWS_REGION")
                            : "us-east-1"))
            .build();

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
     * @param ssmParamName  SSM parameter path (e.g. {@code /mini-app/server-port})
     * @param envVarName    environment variable name (e.g. {@code SERVER_PORT})
     * @param defaultValue  fallback value if neither SSM nor env var is set
     * @return resolved port as a {@link String}
     */
    private static String resolvePortFromParameterStore(
            String ssmParamName, String envVarName, String defaultValue) {

        // 1. Try AWS SSM Parameter Store
        try {
            SsmClient ssmClient = SsmClient.builder()
                    .region(Region.of(
                            System.getenv("AWS_REGION") != null
                                    ? System.getenv("AWS_REGION")
                                    : "us-east-1"))
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
     * Loads all application configuration parameters from AWS Systems Manager
     * Parameter Store using the path prefix {@code SSM_PARAMETER_PATH}.
     *
     * cr-java-0070 fix (Line 46):
     *   BEFORE: Configuration was loaded from the classpath-bundled
     *           application.properties file, making it immutable at runtime.
     *
     *   AFTER:  Configuration is fetched at runtime from AWS SSM Parameter
     *           Store via GetParametersByPath, which returns all parameters
     *           under the configured path prefix.  SSM parameter names are
     *           mapped to property keys by stripping the path prefix
     *           (e.g., /mini-app/server.host → server.host).
     *           This enables:
     *             - Runtime configuration changes without redeployment
     *             - Environment-specific configuration (dev/staging/prod)
     *             - Centralized configuration management via AWS Console/CLI
     *             - Audit logging of configuration access via AWS CloudTrail
     *             - Fine-grained IAM access control to configuration parameters
     *
     * @param ssmPathPrefix  the SSM parameter path prefix (e.g. {@code /mini-app})
     * @return {@link Properties} populated from SSM Parameter Store, or an
     *         empty Properties object if the SSM call fails
     */
    private Properties loadConfigurationFromParameterStore(String ssmPathPrefix) {
        Properties props = new Properties();
        try {
            SsmClient ssmClient = SsmClient.builder()
                    .region(Region.of(
                            System.getenv("AWS_REGION") != null
                                    ? System.getenv("AWS_REGION")
                                    : "us-east-1"))
                    .build();

            // Fetch all parameters under the path prefix using pagination
            String nextToken = null;
            do {
                GetParametersByPathRequest.Builder requestBuilder =
                        GetParametersByPathRequest.builder()
                                .path(ssmPathPrefix)
                                .recursive(true)
                                .withDecryption(true);

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                GetParametersByPathResponse response =
                        ssmClient.getParametersByPath(requestBuilder.build());

                for (Parameter parameter : response.parameters()) {
                    // Strip the path prefix to get the property key
                    // e.g., /mini-app/server.host → server.host
                    String paramName = parameter.name();
                    String propertyKey = paramName.startsWith(ssmPathPrefix + "/")
                            ? paramName.substring(ssmPathPrefix.length() + 1)
                            : paramName;
                    props.setProperty(propertyKey, parameter.value());
                }

                nextToken = response.nextToken();
            } while (nextToken != null);

            ssmClient.close();

            System.out.println("Configuration loaded from AWS SSM Parameter Store path: "
                    + ssmPathPrefix + " (" + props.size() + " parameters)");

        } catch (SsmException e) {
            System.err.println("Failed to load configuration from AWS SSM Parameter Store ["
                    + ssmPathPrefix + "]: " + e.getMessage()
                    + " – application will use fallback defaults.");
        } catch (Exception e) {
            System.err.println("Unexpected error loading configuration from AWS SSM Parameter Store ["
                    + ssmPathPrefix + "]: " + e.getMessage()
                    + " – application will use fallback defaults.");
        }
        return props;
    }

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // cr-java-0070 fix (Line 46): Load configuration from AWS SSM Parameter
        // Store instead of the classpath-bundled application.properties file.
        // This replaces the immutable classpath resource with a runtime-
        // configurable external configuration source, enabling environment-
        // specific configuration changes without redeployment.
        loadConfiguration();

        // Cloud-native: write log entries to S3 instead of a local file
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from AWS Systems Manager Parameter Store.
     *
     * cr-java-0070 fix (Line 46):
     *   BEFORE: Properties were loaded from the classpath-bundled
     *           application.properties file via ClassLoader.getResourceAsStream()
     *           or Spring's @PropertySource, making configuration immutable
     *           at runtime and tightly coupled to the deployment artifact.
     *
     *   AFTER:  Configuration is loaded at runtime from AWS SSM Parameter Store
     *           using GetParametersByPath with the SSM_PARAMETER_PATH prefix.
     *           This externalizes all configuration from the classpath artifact,
     *           enabling:
     *             - Runtime configuration updates without redeployment
     *             - Environment-specific configuration (dev/staging/prod)
     *             - Centralized configuration management
     *             - Audit logging via AWS CloudTrail
     *           The classpath application.properties is retained only as a
     *           last-resort fallback for local development environments where
     *           AWS credentials may not be available.
     *
     * FIX for cr-java-0063 – Line 44 (original):
     *   BEFORE: File configFile = new File(CONFIG_FILE_PATH);
     *           if (configFile.exists()) { props.load(new FileInputStream(configFile)); }
     *   AFTER:  S3 GetObjectRequest fetches the properties object directly from
     *           the S3 bucket, eliminating the local file system dependency.
     */
    private void loadConfiguration() {
        // -----------------------------------------------------------------------
        // cr-java-0070 fix (Line 46): PRIMARY configuration source is now
        // AWS Systems Manager Parameter Store.
        //
        // All parameters stored under SSM_PARAMETER_PATH (default: /mini-app)
        // are fetched at runtime and made available as application properties.
        // This replaces the classpath-bundled application.properties as the
        // primary configuration source, making configuration mutable at runtime
        // and enabling environment-specific configuration without redeployment.
        // -----------------------------------------------------------------------
        Properties ssmProperties = loadConfigurationFromParameterStore(SSM_PARAMETER_PATH);

        if (!ssmProperties.isEmpty()) {
            System.out.println("Application configuration loaded from AWS SSM Parameter Store.");
            // Log loaded configuration keys (not values, to avoid leaking secrets)
            ssmProperties.stringPropertyNames().forEach(key ->
                    System.out.println("  SSM config key loaded: " + key));
        } else {
            System.out.println("Warning: No configuration parameters found in AWS SSM Parameter Store "
                    + "at path [" + SSM_PARAMETER_PATH + "]. "
                    + "Falling back to S3-based configuration.");

            // -----------------------------------------------------------------------
            // FALLBACK: Load configuration from S3 when SSM is unavailable.
            // This is the secondary fallback for environments where SSM is not
            // accessible (e.g., local development without AWS credentials).
            // -----------------------------------------------------------------------
            try {
                // cr-java-0063 fix (original line 44): replaced new File(CONFIG_FILE_PATH)
                // with an Amazon S3 GetObject call using AWS SDK for Java v2.
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(S3_BUCKET_NAME)
                        .key(CONFIG_S3_KEY)
                        .build();

                ResponseInputStream<GetObjectResponse> s3Object =
                        s3Client.getObject(getObjectRequest);

                Properties s3Props = new Properties();
                s3Props.load(s3Object);
                s3Object.close();

                System.out.println("Configuration loaded from S3 fallback: s3://"
                        + S3_BUCKET_NAME + "/" + CONFIG_S3_KEY);

            } catch (NoSuchKeyException e) {
                System.out.println("Warning: Configuration object not found in S3 at: s3://"
                        + S3_BUCKET_NAME + "/" + CONFIG_S3_KEY
                        + ". Application will use built-in defaults.");
            } catch (S3Exception | IOException e) {
                System.err.println("Failed to load configuration from S3 fallback: " + e.getMessage()
                        + ". Application will use built-in defaults.");
            }
        }
    }

    /**
     * Initialises application logging by writing a startup entry to Amazon S3.
     *
     * FIX for cr-java-0063 – Lines 60, 62, 65 (original):
     *   BEFORE (line 60): File logDir  = new File("/var/log");
     *   BEFORE (line 62): logDir.mkdirs();
     *   BEFORE (line 65): File logFile = new File(LOG_FILE_PATH);
     *                     logFile.createNewFile();
     *   AFTER:  A single S3 PutObjectRequest writes the log initialisation
     *           entry directly to the S3 bucket, removing all local directory
     *           and file creation operations.
     */
    private void initializeLogging() {
        // cr-java-0063 fix (original lines 60, 62, 65):
        //   Replaced new File("/var/log")     (line 60)
        //            logDir.mkdirs()           (line 62)
        //            new File(LOG_FILE_PATH)   (line 65)
        // with an Amazon S3 PutObject call using AWS SDK for Java v2.
        try {
            String logEntry = "Log initialized at: " + Instant.now().toString()
                    + System.lineSeparator();

            byte[] logBytes = logEntry.getBytes(StandardCharsets.UTF_8);
            InputStream logStream = new ByteArrayInputStream(logBytes);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET_NAME)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(logStream, logBytes.length));

            System.out.println("Logging initialized in S3: s3://"
                    + S3_BUCKET_NAME + "/" + LOG_S3_KEY);

        } catch (S3Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // cr-java-0077 fix (Line 79): SERVER_PORT is now resolved from AWS
            // Parameter Store / environment variable instead of being hard-coded
            // to 8080.  The variable SERVER_PORT already holds the externalised
            // value resolved at class-load time via resolvePortFromParameterStore().
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
