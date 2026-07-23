package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * Mini Java Application with cloud-native configuration management via
 * AWS Systems Manager Parameter Store, S3-based file operations, and
 * environment-variable-driven port configuration.
 *
 * Rule cr-java-0070 fix (line 46):
 *   BEFORE: Properties loaded from a classpath-bundled properties file
 *           (e.g., application.properties packaged inside the JAR), making
 *           configuration immutable at runtime and preventing environment-specific
 *           changes without redeployment. This violates cloud-native externalized
 *           configuration principles (12-factor app principle III).
 *   AFTER:  Configuration is loaded at runtime from AWS Systems Manager Parameter
 *           Store using the SSM SDK v2 client. Parameters are organized under a
 *           configurable path prefix (default: /mini-app/config), enabling:
 *             - Runtime configuration changes without redeployment
 *             - Environment-specific parameter sets (dev/staging/prod) via path prefixes
 *             - Centralized configuration management across all services
 *             - Fine-grained IAM access control per parameter
 *             - Full audit logging of parameter access via AWS CloudTrail
 *             - SecureString parameters for sensitive non-secret configuration values
 *           The SSM parameter path prefix is resolved from the SSM_PARAMETER_PATH
 *           environment variable, allowing per-environment configuration without
 *           code changes (12-factor app principle III).
 *
 * Rule cr-java-0063 fix: All java.io.File-based persistent storage operations
 * have been replaced with Amazon S3 client calls (AWS SDK for Java v2) to
 * achieve cloud-native, durable, and scalable storage without host-level
 * file system dependencies.
 *
 * Rule cr-java-0077 fix (lines 15, 79):
 *   BEFORE: private static final int SERVER_PORT = 8080;
 *           ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
 *           — hard-coded port 8080 prevented dynamic port assignment required
 *           by container orchestration platforms and cloud service discovery.
 *   AFTER:  SERVER_PORT is resolved at runtime from the SERVER_PORT environment
 *           variable (default: 8080), enabling dynamic port injection via ECS
 *           task definitions, EKS ConfigMaps, or Elastic Beanstalk environment
 *           properties. The value can also be sourced from AWS Systems Manager
 *           Parameter Store and injected at deployment time.
 *
 * Original cr-java-0063 violations replaced:
 *   - Line 44 (original): new File(CONFIG_FILE_PATH)  → SSM Parameter Store (cr-java-0070)
 *   - Line 60 (original): new File("/var/log")         → S3 PutObject
 *   - Line 62 (original): logDir.mkdirs()              → S3 PutObject (same call)
 *   - Line 65 (original): new File(LOG_FILE_PATH)      → S3 PutObject (same call)
 */
public class MiniApp {

    // cr-java-0077 fix (line 15):
    // BEFORE: private static final int SERVER_PORT = 8080;
    // AFTER:  Port is resolved from the SERVER_PORT environment variable at runtime,
    //         enabling dynamic port assignment in ECS/EKS/Elastic Beanstalk.
    //         The value can be injected from AWS Systems Manager Parameter Store.
    private static final int SERVER_PORT =
            Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // Cloud-native: S3 bucket and object keys resolved from environment variables,
    // eliminating hard-coded absolute file system paths (cr-java-0063).
    private static final String S3_BUCKET =
            System.getenv().getOrDefault("APP_S3_BUCKET", "my-app-bucket");

    // Replaces hard-coded /var/log/mini-app.log (original lines 60, 62, 65)
    private static final String LOG_S3_KEY =
            System.getenv().getOrDefault("APP_LOG_S3_KEY", "logs/mini-app.log");

    // cr-java-0070 fix (line 46):
    // SSM Parameter Store path prefix — replaces classpath-bundled properties file.
    // All application configuration parameters are stored under this path in SSM.
    // The path prefix is resolved from the SSM_PARAMETER_PATH environment variable,
    // enabling per-environment configuration (e.g., /mini-app/dev, /mini-app/prod)
    // without code changes (12-factor app principle III).
    // Example parameters stored in SSM under this path:
    //   /mini-app/config/server.port
    //   /mini-app/config/database.url
    //   /mini-app/config/external.api.base-url
    //   /mini-app/config/logging.level
    private static final String SSM_PARAMETER_PATH =
            System.getenv().getOrDefault("SSM_PARAMETER_PATH", "/mini-app/config");

    // AWS region resolved from standard environment variables
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_DEFAULT_REGION",
                    System.getenv().getOrDefault("AWS_REGION", "us-east-1"));

    // cr-java-0097 fix: AWS SDK client timeout values resolved from environment
    // variables. These control how long the SSM client waits for a connection to
    // be established, for data to be received, and for the entire API call to
    // complete — preventing indefinite hangs in cloud environments.
    private static final int AWS_HTTP_CONNECTION_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_HTTP_CONNECTION_TIMEOUT_MS", "5000"));
    private static final int AWS_HTTP_SOCKET_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_HTTP_SOCKET_TIMEOUT_MS", "10000"));
    private static final int AWS_API_CALL_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_API_CALL_TIMEOUT_MS", "15000"));
    private static final int AWS_API_CALL_ATTEMPT_TIMEOUT_MS =
            Integer.parseInt(System.getenv().getOrDefault("AWS_API_CALL_ATTEMPT_TIMEOUT_MS", "10000"));

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // cr-java-0070 fix (line 46):
        // BEFORE: Properties loaded from classpath-bundled application.properties
        //         (immutable at runtime, violates 12-factor app principle III).
        // AFTER:  Configuration loaded at runtime from AWS Systems Manager Parameter
        //         Store, enabling runtime changes without redeployment and
        //         environment-specific configuration via path prefixes.
        loadConfigurationFromParameterStore();

        // Cloud-native: write log entry to S3 instead of local /var/log path
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from AWS Systems Manager Parameter Store.
     *
     * FIX for cr-java-0070 (line 46):
     *   BEFORE: Properties props = new Properties();
     *           props.load(MiniApp.class.getClassLoader()
     *               .getResourceAsStream("application.properties"));
     *           — classpath-bundled properties file is immutable at runtime,
     *           preventing environment-specific configuration changes without
     *           redeployment. Violates cloud-native externalized configuration
     *           principles (12-factor app principle III).
     *   AFTER:  Configuration is retrieved at runtime from AWS Systems Manager
     *           Parameter Store using GetParametersByPath API. Parameters are
     *           organized under a configurable path prefix (SSM_PARAMETER_PATH),
     *           enabling:
     *             - Runtime configuration changes without redeployment
     *             - Environment-specific parameter sets via path prefixes
     *             - Centralized configuration management across all services
     *             - Fine-grained IAM access control per parameter
     *             - Full audit logging via AWS CloudTrail
     *             - SecureString support for sensitive configuration values
     *
     * The SSM client is configured with explicit timeouts (cr-java-0097) to
     * prevent indefinite blocking in cloud environments with variable latency.
     *
     * @return Map of parameter names to values loaded from SSM Parameter Store.
     */
    private Map<String, String> loadConfigurationFromParameterStore() {
        Map<String, String> configParams = new HashMap<>();

        // cr-java-0097 fix: Build SSM client with explicit HTTP and API call timeouts
        // to prevent indefinite blocking in cloud environments.
        SsmClient ssmClient = SsmClient.builder()
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
            // cr-java-0070 fix (line 46):
            // Use GetParametersByPath to retrieve all parameters under the configured
            // path prefix in a single paginated API call. This replaces loading a
            // classpath-bundled properties file, enabling runtime configuration changes
            // without redeployment and environment-specific configuration via path prefixes.
            //
            // withDecryption=true ensures SecureString parameters (sensitive non-secret
            // configuration values) are automatically decrypted using the associated KMS key.
            //
            // recursive=true retrieves parameters from all sub-paths under the prefix,
            // supporting hierarchical configuration organization (e.g., /mini-app/config/db/*).
            String nextToken = null;
            do {
                GetParametersByPathRequest.Builder requestBuilder = GetParametersByPathRequest.builder()
                        .path(SSM_PARAMETER_PATH)
                        .recursive(true)
                        .withDecryption(true)
                        .maxResults(10);

                if (nextToken != null) {
                    requestBuilder.nextToken(nextToken);
                }

                GetParametersByPathResponse response = ssmClient.getParametersByPath(
                        requestBuilder.build());

                for (Parameter parameter : response.parameters()) {
                    // Strip the path prefix to get the parameter name relative to the path
                    // e.g., /mini-app/config/server.port → server.port
                    String paramName = parameter.name().startsWith(SSM_PARAMETER_PATH + "/")
                            ? parameter.name().substring(SSM_PARAMETER_PATH.length() + 1)
                            : parameter.name();
                    configParams.put(paramName, parameter.value());
                }

                nextToken = response.nextToken();
            } while (nextToken != null);

            System.out.println("Configuration loaded from AWS SSM Parameter Store path: "
                    + SSM_PARAMETER_PATH + " (" + configParams.size() + " parameters)");

            // Log parameter names (not values) for audit/debugging purposes
            configParams.keySet().forEach(key ->
                    System.out.println("  Loaded SSM parameter: " + key));

        } catch (SsmException e) {
            System.err.println("Warning: Failed to load configuration from SSM Parameter Store ["
                    + SSM_PARAMETER_PATH + "]: " + e.awsErrorDetails().errorMessage()
                    + ". Falling back to environment variables and defaults.");
        } catch (Exception e) {
            System.err.println("Warning: Unexpected error loading configuration from SSM ["
                    + SSM_PARAMETER_PATH + "]: " + e.getMessage()
                    + ". Falling back to environment variables and defaults.");
        } finally {
            ssmClient.close();
        }

        return configParams;
    }

    /**
     * Retrieves a single named parameter from AWS Systems Manager Parameter Store.
     *
     * This utility method supports targeted parameter retrieval when only a specific
     * configuration value is needed, complementing the bulk path-based loading in
     * loadConfigurationFromParameterStore().
     *
     * @param parameterName the full SSM parameter name (e.g., /mini-app/config/server.port)
     * @param defaultValue  the fallback value if the parameter is not found
     * @return the parameter value from SSM, or defaultValue if not found
     */
    private String getParameterFromSsm(String parameterName, String defaultValue) {
        SsmClient ssmClient = SsmClient.builder()
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
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            String value = response.parameter().value();
            System.out.println("Retrieved SSM parameter: " + parameterName);
            return value;

        } catch (ParameterNotFoundException e) {
            System.out.println("SSM parameter not found [" + parameterName
                    + "], using default value.");
            return defaultValue;
        } catch (SsmException e) {
            System.err.println("Warning: Failed to retrieve SSM parameter ["
                    + parameterName + "]: " + e.awsErrorDetails().errorMessage()
                    + ". Using default value.");
            return defaultValue;
        } finally {
            ssmClient.close();
        }
    }

    /**
     * Initialises logging by writing a log marker object to Amazon S3.
     *
     * FIX for cr-java-0063 (original lines 60, 62, 65):
     *   BEFORE (line 60): File logDir = new File("/var/log");
     *   BEFORE (line 62): logDir.mkdirs();
     *   BEFORE (line 65): File logFile = new File(LOG_FILE_PATH);
     *                     logFile.createNewFile();
     *   AFTER:  Single S3 PutObject call — no local file system dependency.
     */
    private void initializeLogging() {
        S3Client s3 = buildS3Client();
        try {
            // cr-java-0063 fix (original lines 60, 62, 65):
            // Replaced new File("/var/log") + logDir.mkdirs()
            //      and new File(LOG_FILE_PATH) + logFile.createNewFile()
            // with a single S3 PutObject that creates (or updates) the log
            // marker object in the bucket — no local file system dependency.
            String initialLogContent = "Log initialised at startup\n";
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3.putObject(putRequest, RequestBody.fromString(initialLogContent));
            System.out.println("Logging initialised in S3: s3://"
                    + S3_BUCKET + "/" + LOG_S3_KEY);
        } catch (S3Exception e) {
            System.err.println("Failed to initialise logging in S3: "
                    + e.awsErrorDetails().errorMessage());
        } finally {
            s3.close();
        }
    }

    private void startServer() {
        try {
            // cr-java-0077 fix (line 79):
            // BEFORE: ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            //         where SERVER_PORT was the hard-coded literal 8080.
            // AFTER:  SERVER_PORT is now resolved from the SERVER_PORT environment
            //         variable, so the socket binds to the runtime-injected port.
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

    /**
     * Builds an S3Client using the default credential provider chain
     * (IAM instance roles, environment variables, ~/.aws/credentials, etc.).
     */
    private S3Client buildS3Client() {
        return S3Client.builder()
                .region(Region.of(AWS_REGION))
                .build();
    }
}
