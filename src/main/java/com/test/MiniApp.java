package com.test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Mini Java Application — cloud-native version.
 *
 * Changes applied:
 *  - Hard-coded port replaced with environment variable / AWS SSM Parameter
 *    Store lookup (blocker-14, blocker-15: cr-java-0077).
 *  - Hard-coded absolute file paths removed; configuration is loaded from
 *    AWS S3 (blocker-1, blocker-2, blocker-3: cr-java-0061).
 *  - java.io.File operations replaced with Amazon S3 SDK v2 calls
 *    (blocker-4, blocker-5, blocker-6, blocker-7: cr-java-0063).
 *  - Classpath-bundled properties file replaced with AWS Systems Manager
 *    Parameter Store (blocker-20: cr-java-0070).
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // AWS / environment configuration — no hard-coded values
    // -----------------------------------------------------------------------

    /** AWS region injected at deploy time via environment variable. */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    /**
     * S3 bucket that holds application configuration and log objects.
     * Replaces hard-coded /opt/app/config and /var/log paths
     * (blocker-1 to blocker-7: cr-java-0061, cr-java-0063).
     */
    private static final String APP_BUCKET =
            System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-config-bucket");

    /** S3 object key for the application configuration file (replaces /opt/app/config/app.properties). */
    private static final String CONFIG_S3_KEY =
            System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");

    /** S3 object key prefix for log entries (replaces /var/log/mini-app.log). */
    private static final String LOG_S3_KEY =
            System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");

    /**
     * SSM Parameter Store path for the server port.
     * Replaces hard-coded SERVER_PORT = 8080 (blocker-14, blocker-15: cr-java-0077).
     */
    private static final String SERVER_PORT_PARAM =
            System.getenv().getOrDefault("SERVER_PORT_PARAM", "/mini-app/server/port");

    /**
     * SSM Parameter Store path for application-level configuration properties.
     * Replaces classpath-bundled application.properties (blocker-20: cr-java-0070).
     */
    private static final String APP_CONFIG_PARAM_PREFIX =
            System.getenv().getOrDefault("APP_CONFIG_PARAM_PREFIX", "/mini-app/config");

    // -----------------------------------------------------------------------
    // Shared AWS clients
    // -----------------------------------------------------------------------

    private S3Client  s3Client;
    private SsmClient ssmClient;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    private void initializeApplication() {
        s3Client  = S3Client.builder().region(Region.of(AWS_REGION)).build();
        ssmClient = SsmClient.builder().region(Region.of(AWS_REGION)).build();

        loadConfiguration();
        initializeLogging();

        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from Amazon S3.
     *
     * Replaces the hard-coded absolute path /opt/app/config/app.properties
     * (blocker-1: cr-java-0061) and the java.io.File read operation
     * (blocker-4: cr-java-0063).
     *
     * Additionally, key/value parameters are fetched from AWS SSM Parameter
     * Store to replace the classpath-bundled properties file
     * (blocker-20: cr-java-0070).
     */
    private void loadConfiguration() {
        // --- Load config file from S3 (replaces File + FileInputStream on hard-coded path) ---
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(APP_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            Properties props = new Properties();
            props.load(s3Object);
            s3Object.close();

            System.out.println("Configuration loaded from S3: s3://" + APP_BUCKET + "/" + CONFIG_S3_KEY);

        } catch (Exception e) {
            System.err.println("WARNING: Could not load configuration from S3 ("
                    + e.getMessage() + "). Continuing with defaults.");
        }

        // --- Load runtime parameters from AWS SSM Parameter Store (blocker-20) ---
        try {
            GetParameterRequest paramRequest = GetParameterRequest.builder()
                    .name(APP_CONFIG_PARAM_PREFIX + "/app-name")
                    .withDecryption(true)
                    .build();
            GetParameterResponse paramResponse = ssmClient.getParameter(paramRequest);
            System.out.println("SSM parameter loaded — app-name: "
                    + paramResponse.parameter().value());
        } catch (Exception e) {
            System.err.println("WARNING: Could not load SSM parameters ("
                    + e.getMessage() + "). Continuing with defaults.");
        }
    }

    /**
     * Initialises logging by writing a startup entry to Amazon S3.
     *
     * Replaces the hard-coded absolute path /var/log/mini-app.log
     * (blocker-2, blocker-3: cr-java-0061) and the java.io.File mkdir /
     * createNewFile operations (blocker-5, blocker-6, blocker-7: cr-java-0063).
     */
    private void initializeLogging() {
        try {
            String logEntry = "Application started at: " + java.time.Instant.now() + "\n";

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(APP_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromBytes(logEntry.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialised — log entry written to S3: s3://"
                    + APP_BUCKET + "/" + LOG_S3_KEY);

        } catch (Exception e) {
            System.err.println("WARNING: Could not initialise S3 logging ("
                    + e.getMessage() + "). Falling back to stdout logging.");
        }
    }

    /**
     * Starts the server on a port resolved from AWS SSM Parameter Store or
     * the SERVER_PORT environment variable.
     *
     * Replaces hard-coded SERVER_PORT = 8080 (blocker-14, blocker-15: cr-java-0077).
     */
    private void startServer() {
        int serverPort = resolveServerPort();
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            System.out.println("Server started on port: " + serverPort);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves the server port from AWS SSM Parameter Store, falling back to
     * the SERVER_PORT environment variable, and finally to 8080.
     * (blocker-14, blocker-15: cr-java-0077)
     */
    private int resolveServerPort() {
        // 1. Try SSM Parameter Store
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(SERVER_PORT_PARAM)
                    .withDecryption(false)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return Integer.parseInt(response.parameter().value().trim());
        } catch (Exception e) {
            System.err.println("WARNING: Could not retrieve server port from SSM ("
                    + e.getMessage() + "). Checking environment variable.");
        }

        // 2. Fall back to SERVER_PORT environment variable
        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException nfe) {
                System.err.println("WARNING: Invalid SERVER_PORT env value '" + envPort
                        + "'. Using default 8080.");
            }
        }

        // 3. Final default
        return 8080;
    }
}
