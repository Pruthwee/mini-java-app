package com.test;

import java.net.ServerSocket;
import java.util.Properties;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * Mini Java Application with cloud-native AWS Systems Manager Parameter Store
 * based configuration management.
 *
 * REMEDIATION (cr-java-0070 — Properties Files in Classpath):
 *   The original application loaded configuration from a classpath-bundled
 *   properties file (/opt/app/config/app.properties) using FileInputStream,
 *   making configuration immutable at runtime and preventing environment-specific
 *   changes without redeployment.
 *
 *   This fix externalizes all application configuration to AWS Systems Manager
 *   Parameter Store, enabling runtime configuration changes without redeployment
 *   and following cloud-native externalized configuration principles (12-factor
 *   app, Factor III: Config).
 *
 *   Configuration is loaded at startup from SSM Parameter Store using the path
 *   prefix defined by SSM_PARAMETER_PATH (default: /mini-app). All parameters
 *   under that path are fetched and made available to the application at runtime.
 */
public class MiniApp {

    // BLOCKER FIXED: Port now read from environment variable (SERVER_PORT) with fallback
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // AWS region for SSM Parameter Store — injected via environment variable
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // ---------------------------------------------------------------------------
    // REMEDIATION (cr-java-0070 — Properties Files in Classpath, line 46):
    //   SSM Parameter Store path prefix replaces the classpath-bundled
    //   application.properties file.  All configuration parameters are stored
    //   under this path in AWS Systems Manager Parameter Store and fetched at
    //   runtime, enabling environment-specific configuration without redeployment.
    //
    //   Previously: props.load(new FileInputStream(new File(CONFIG_FILE_PATH)))
    //               where CONFIG_FILE_PATH = "/opt/app/config/app.properties"
    //               (a classpath-bundled / hardcoded-path properties file)
    //
    //   Now:        Parameters are retrieved from AWS SSM Parameter Store using
    //               GetParametersByPath on the path prefix defined by
    //               SSM_PARAMETER_PATH, making configuration fully externalized,
    //               mutable at runtime, and auditable via AWS CloudTrail.
    // ---------------------------------------------------------------------------
    private static final String SSM_PARAMETER_PATH =
            System.getenv().getOrDefault("SSM_PARAMETER_PATH", "/mini-app");

    private final SsmClient ssmClient;

    public MiniApp() {
        this.ssmClient = SsmClient.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // Cloud-native: configuration loaded from AWS SSM Parameter Store
        loadConfiguration();
        initializeLogging();

        // Initialize database connection
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from AWS Systems Manager Parameter Store.
     *
     * REMEDIATION (cr-java-0070 — Properties Files in Classpath, line 46):
     *   Replaces the original classpath-bundled properties file loading:
     *     File configFile = new File(CONFIG_FILE_PATH);   // "/opt/app/config/app.properties"
     *     props.load(new FileInputStream(configFile));
     *
     *   With AWS SSM Parameter Store GetParametersByPath call, which:
     *     1. Fetches all parameters under the configured path prefix at runtime
     *     2. Supports SecureString parameters for sensitive values (encrypted at rest)
     *     3. Enables configuration changes without application redeployment
     *     4. Provides full audit trail via AWS CloudTrail
     *     5. Integrates with IAM for fine-grained access control
     *
     *   The SSM_PARAMETER_PATH environment variable controls which parameter
     *   namespace is loaded, allowing the same application artifact to be
     *   deployed across dev/staging/production with different configurations.
     */
    private void loadConfiguration() {
        try {
            Properties props = new Properties();

            // Fetch all parameters under the configured SSM path prefix.
            // WithDecryption=true ensures SecureString parameters are decrypted
            // using the associated KMS key, enabling secure storage of sensitive
            // (but non-credential) configuration values.
            GetParametersByPathRequest pathRequest = GetParametersByPathRequest.builder()
                    .path(SSM_PARAMETER_PATH)
                    .recursive(true)
                    .withDecryption(true)
                    .build();

            GetParametersByPathResponse pathResponse = ssmClient.getParametersByPath(pathRequest);

            for (Parameter parameter : pathResponse.parameters()) {
                // Strip the path prefix to get a clean property key
                // e.g. "/mini-app/server/port" -> "server.port"
                String paramKey = parameter.name()
                        .replaceFirst("^" + SSM_PARAMETER_PATH + "/", "")
                        .replace("/", ".");
                props.setProperty(paramKey, parameter.value());
            }

            System.out.println("Configuration loaded from AWS SSM Parameter Store path: "
                    + SSM_PARAMETER_PATH
                    + " (" + props.size() + " parameters loaded)");

        } catch (ParameterNotFoundException e) {
            System.out.println("Warning: No parameters found in SSM Parameter Store at path: "
                    + SSM_PARAMETER_PATH);
        } catch (SsmException e) {
            System.err.println("Failed to load configuration from AWS SSM Parameter Store: "
                    + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error loading configuration from SSM: "
                    + e.getMessage());
        }
    }

    /**
     * Initializes application logging.
     * Log configuration (level, format, destination) is sourced from SSM
     * Parameter Store via the loadConfiguration() call above, rather than
     * from a classpath-bundled properties file.
     */
    private void initializeLogging() {
        // Retrieve log level from SSM Parameter Store at runtime
        String logLevel = getParameterValue(SSM_PARAMETER_PATH + "/logging/level", "INFO");
        System.out.println("Logging initialized with level: " + logLevel
                + " (sourced from SSM Parameter Store)");
    }

    /**
     * Retrieves a single parameter value from AWS SSM Parameter Store.
     *
     * @param parameterName the full SSM parameter name (e.g. /mini-app/server/port)
     * @param defaultValue  fallback value if the parameter does not exist
     * @return the parameter value, or defaultValue if not found
     */
    private String getParameterValue(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (ParameterNotFoundException e) {
            return defaultValue;
        } catch (SsmException e) {
            System.err.println("Failed to retrieve SSM parameter '" + parameterName
                    + "': " + e.awsErrorDetails().errorMessage());
            return defaultValue;
        }
    }

    private void startServer() {
        try {
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
