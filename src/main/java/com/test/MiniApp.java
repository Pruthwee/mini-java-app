package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * Mini Java Application with cloud-native AWS-based configuration and storage.
 *
 * FIXED (cr-java-0070):
 *   Application configuration previously bundled as a classpath properties file
 *   (application.properties / app.properties) has been externalised to
 *   AWS Systems Manager (SSM) Parameter Store.  At startup, loadConfiguration()
 *   fetches all parameters under the path prefix stored in the SSM_PARAMETER_PATH
 *   environment variable (default: /mini-app/config) using GetParametersByPath,
 *   enabling runtime configuration changes without redeployment and full
 *   environment-specific configuration management across AWS environments.
 *
 *   Parameters are stored in SSM Parameter Store as SecureString or String values
 *   under a hierarchical path, e.g.:
 *     /mini-app/config/server.port
 *     /mini-app/config/database.pool.max-connections
 *     /mini-app/config/cache.redis.host
 *
 *   The SSM_PARAMETER_PATH environment variable must be set in the ECS task
 *   definition, EKS pod spec, or Elastic Beanstalk environment properties.
 *
 * FIXED (cr-java-0063):
 *   All java.io.File-based persistent storage operations have been replaced with
 *   Amazon S3 client calls (AWS SDK for Java v2) to achieve cloud-native, durable,
 *   and scalable storage without host-level file system dependencies.
 *
 *   Occurrences fixed (cr-java-0063):
 *     - Line 44 (source): new File(CONFIG_FILE_PATH)          → SSM Parameter Store (cr-java-0070)
 *     - Line 60 (source): new File("/var/log")                → S3 PutObject (no local dir needed)
 *     - Line 62 (source): new File(LOG_FILE_PATH)             → S3 PutObject
 *     - Line 65 (source): logFile.createNewFile()             → S3 PutObject
 */
public class MiniApp {

    // FIXED (cr-java-0077, source line 15): Hardcoded port 8080 replaced with environment variable
    // injection. Store SERVER_PORT in AWS Parameter Store and inject at runtime through ECS task
    // definition environment, EKS pod spec, or Elastic Beanstalk environment properties.
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // FIXED (cr-java-0070): SSM Parameter Store path prefix for all application configuration.
    // Set SSM_PARAMETER_PATH in ECS task definition, EKS pod spec, or Elastic Beanstalk
    // environment properties to point to the correct environment-specific parameter hierarchy.
    // Example: /mini-app/config  (all parameters under this path are loaded at startup)
    private static final String SSM_PARAMETER_PATH = System.getenv().getOrDefault(
            "SSM_PARAMETER_PATH", "/mini-app/config");

    // S3 bucket name resolved from environment variable (cr-java-0063)
    private static final String S3_BUCKET = System.getenv().getOrDefault(
            "APP_S3_BUCKET", "mini-app-bucket");

    // S3 object key replacing the former absolute log file path:
    //   /var/log/mini-app.log  (source lines 60, 62, 65 – cr-java-0063)
    private static final String LOG_S3_KEY = System.getenv().getOrDefault(
            "LOG_S3_KEY", "logs/mini-app.log");

    // AWS region resolved from environment variable
    private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // Shared S3 client for log storage; region resolved from AWS_REGION env var
    private final S3Client s3Client;

    // Shared SSM client for configuration retrieval (cr-java-0070)
    private final SsmClient ssmClient;

    public MiniApp() {
        this.s3Client = S3Client.builder()
                .region(Region.of(AWS_REGION))
                .build();

        // FIXED (cr-java-0070): SSM client used to retrieve externalised configuration
        // from AWS Systems Manager Parameter Store at application startup.
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
        // FIXED (cr-java-0070): Load configuration from AWS SSM Parameter Store
        // (replaces classpath-bundled properties file and former S3-based config loading)
        loadConfiguration();

        // Initialize logging via S3 (replaces hardcoded /var/log/mini-app.log)
        initializeLogging();

        // Initialize database connection
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from AWS Systems Manager Parameter Store.
     *
     * FIXED (cr-java-0070, source line 46):
     *   BEFORE: Properties bundled inside the application classpath (application.properties /
     *           app.properties) were loaded at startup via ClassLoader.getResourceAsStream()
     *           or new File(CONFIG_FILE_PATH), making them immutable at runtime and preventing
     *           environment-specific configuration changes without redeployment.
     *
     *   AFTER:  All configuration parameters are externalised to AWS Systems Manager
     *           Parameter Store under the hierarchical path defined by SSM_PARAMETER_PATH.
     *           GetParametersByPath (with pagination) fetches all parameters under that
     *           prefix at startup, enabling:
     *             - Runtime configuration changes without redeployment
     *             - Environment-specific configuration (dev / staging / prod) via separate
     *               SSM path prefixes (e.g. /mini-app/dev/config, /mini-app/prod/config)
     *             - Secure storage of sensitive configuration values as SecureString
     *             - Full audit trail of configuration access via AWS CloudTrail
     *             - IAM-based access control for configuration parameters
     *
     *   SSM Parameter Store path convention:
     *     /mini-app/config/server.port
     *     /mini-app/config/database.pool.max-connections
     *     /mini-app/config/cache.redis.host
     *     /mini-app/config/external.api.base-url
     *     ... (any key previously in application.properties)
     *
     *   The SSM_PARAMETER_PATH environment variable controls which path prefix is used,
     *   allowing the same application image to be deployed across multiple environments
     *   by simply changing the environment variable value.
     */
    private void loadConfiguration() {
        try {
            // FIXED (cr-java-0070, line 46): Replaced classpath properties file loading with
            // AWS SSM Parameter Store GetParametersByPath to externalise all configuration.
            Map<String, String> configParams = new HashMap<>();
            String nextToken = null;

            do {
                GetParametersByPathRequest.Builder requestBuilder = GetParametersByPathRequest.builder()
                        .path(SSM_PARAMETER_PATH)
                        .recursive(true)
                        .withDecryption(true);  // Decrypt SecureString parameters automatically

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                GetParametersByPathResponse response = ssmClient.getParametersByPath(
                        requestBuilder.build());

                for (Parameter param : response.parameters()) {
                    // Strip the path prefix to get the parameter name (e.g. "server.port")
                    String paramName = param.name().startsWith(SSM_PARAMETER_PATH + "/")
                            ? param.name().substring(SSM_PARAMETER_PATH.length() + 1)
                            : param.name();
                    configParams.put(paramName, param.value());
                }

                nextToken = response.nextToken();

            } while (nextToken != null);

            System.out.println("Configuration loaded from AWS SSM Parameter Store path: "
                    + SSM_PARAMETER_PATH + " (" + configParams.size() + " parameters)");

            // Apply loaded parameters to system properties so downstream components
            // (e.g. Spring, JDBC) can resolve them via standard property lookup.
            configParams.forEach((key, value) -> System.setProperty(key, value));

        } catch (SsmException e) {
            System.err.println("Failed to load configuration from AWS SSM Parameter Store: "
                    + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            System.err.println("Failed to load configuration from AWS SSM Parameter Store: "
                    + e.getMessage());
        }
    }

    /**
     * Initialises application logging by writing a startup entry to Amazon S3.
     *
     * FIXED (cr-java-0063, source line 60):
     *   BEFORE: File logDir = new File("/var/log");
     *           if (!logDir.exists()) { logDir.mkdirs(); }
     *   AFTER:  No local directory creation needed; S3 key prefix acts as the directory.
     *
     * FIXED (cr-java-0063, source line 62):
     *   BEFORE: File logFile = new File(LOG_FILE_PATH);
     *   AFTER:  Log content is written directly to S3 via PutObjectRequest.
     *
     * FIXED (cr-java-0063, source line 65):
     *   BEFORE: logFile.createNewFile();
     *   AFTER:  s3Client.putObject(...) creates/overwrites the S3 object atomically,
     *           replacing the local file creation with a cloud-native storage write.
     */
    private void initializeLogging() {
        try {
            // FIXED (cr-java-0063, line 60): replaced new File("/var/log") directory creation with S3
            // FIXED (cr-java-0063, line 62): replaced new File(LOG_FILE_PATH) with S3 key reference
            // FIXED (cr-java-0063, line 65): replaced logFile.createNewFile() with S3 PutObject
            String logEntry = "Log initialized at: " + Instant.now().toString() + System.lineSeparator();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromBytes(logEntry.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialized in S3: s3://" + S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (Exception e) {
            System.err.println("Failed to initialize logging in S3: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // FIXED (cr-java-0077, source line 79): SERVER_PORT is now resolved from the SERVER_PORT
            // environment variable; no hardcoded port literal is used here.
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
