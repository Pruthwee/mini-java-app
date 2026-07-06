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
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Mini Java Application — cloud-native version.
 *
 * Changes applied:
 *  - blocker-1, blocker-2, blocker-3: hard-coded absolute file paths removed;
 *    config and log objects are now stored/retrieved via Amazon S3 (AWS SDK v2).
 *  - blocker-4, blocker-5, blocker-6, blocker-7: java.io.File usage replaced
 *    with Amazon S3 client calls.
 *  - blocker-14, blocker-15: hard-coded port 8080 replaced with environment
 *    variable SERVER_PORT (injected via ECS/EKS/Elastic Beanstalk) with SSM
 *    Parameter Store as the authoritative source.
 *  - blocker-20: classpath properties file replaced with AWS Systems Manager
 *    Parameter Store for runtime-configurable, environment-specific settings.
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // AWS configuration — resolved from environment variables
    // -----------------------------------------------------------------------

    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    /** S3 bucket that holds application config and log objects (blocker-1..7). */
    private static final String APP_BUCKET =
            System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-storage");

    /** S3 object key for the application configuration (replaces /opt/app/config/app.properties). */
    private static final String CONFIG_S3_KEY =
            System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");

    /** S3 object key prefix for application log entries (replaces /var/log/mini-app.log). */
    private static final String LOG_S3_KEY_PREFIX =
            System.getenv().getOrDefault("LOG_S3_KEY_PREFIX", "logs/mini-app/");

    /**
     * SSM Parameter Store path prefix for application configuration (blocker-20).
     * All parameters under this path are loaded as application properties.
     */
    private static final String SSM_CONFIG_PATH =
            System.getenv().getOrDefault("SSM_CONFIG_PATH", "/mini-app/config");

    /**
     * SSM Parameter Store key for the server port (blocker-14, blocker-15).
     * Falls back to the SERVER_PORT environment variable, then to 8080.
     */
    private static final String SERVER_PORT_PARAM =
            System.getenv().getOrDefault("SERVER_PORT_PARAM", "/mini-app/server/port");

    // -----------------------------------------------------------------------
    // AWS SDK clients
    // -----------------------------------------------------------------------
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        Region region = Region.of(AWS_REGION);

        this.s3Client = S3Client.builder()
                .region(region)
                .build();

        this.ssmClient = SsmClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }

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
        loadConfiguration();
        initializeLogging();

        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Loads application configuration from AWS Systems Manager Parameter Store
     * (blocker-20) and, as a secondary source, from an Amazon S3 object
     * (blocker-1, blocker-4 — replaces new File(CONFIG_FILE_PATH)).
     */
    private void loadConfiguration() {
        // Primary: AWS SSM Parameter Store (blocker-20)
        try {
            Properties props = loadPropertiesFromSsm();
            System.out.println("Configuration loaded from SSM Parameter Store path: " + SSM_CONFIG_PATH);
            System.out.println("Loaded " + props.size() + " configuration parameter(s).");
        } catch (Exception ssmEx) {
            System.out.println("SSM config unavailable (" + ssmEx.getMessage()
                    + "), falling back to S3 config object.");

            // Secondary: Amazon S3 object (blocker-1, blocker-4)
            // Replaces: new File(CONFIG_FILE_PATH) / FileInputStream(configFile)
            try {
                GetObjectRequest getReq = GetObjectRequest.builder()
                        .bucket(APP_BUCKET)
                        .key(CONFIG_S3_KEY)
                        .build();

                try (ResponseInputStream<GetObjectResponse> s3Stream =
                             s3Client.getObject(getReq)) {

                    Properties props = new Properties();
                    props.load(s3Stream);
                    System.out.println("Configuration loaded from S3: s3://"
                            + APP_BUCKET + "/" + CONFIG_S3_KEY);
                }
            } catch (IOException | software.amazon.awssdk.core.exception.SdkException e) {
                System.err.println("Failed to load configuration from S3: " + e.getMessage());
            }
        }
    }

    /**
     * Initialises logging by writing a startup log entry to Amazon S3
     * (blocker-2, blocker-3, blocker-5, blocker-6, blocker-7).
     *
     * Replaces:
     *   new File("/var/log")          (blocker-3, blocker-6)
     *   new File(LOG_FILE_PATH)       (blocker-2, blocker-5)
     *   logFile.createNewFile()       (blocker-7)
     */
    private void initializeLogging() {
        try {
            String logEntry = "Application started at: " + java.time.Instant.now() + "\n";
            String logKey = LOG_S3_KEY_PREFIX
                    + "startup-" + System.currentTimeMillis() + ".log";

            // blocker-2, blocker-3, blocker-5, blocker-6, blocker-7:
            // Write log entry to S3 instead of local file system
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(APP_BUCKET)
                    .key(logKey)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putReq,
                    RequestBody.fromBytes(logEntry.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialised — log entry written to S3: s3://"
                    + APP_BUCKET + "/" + logKey);

        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            System.err.println("Failed to initialise S3 logging: " + e.getMessage());
        }
    }

    /**
     * Starts the server on a port resolved from AWS SSM Parameter Store or the
     * SERVER_PORT environment variable (blocker-14, blocker-15).
     *
     * Replaces: private static final int SERVER_PORT = 8080;
     */
    private void startServer() {
        int port = resolveServerPort();
        try {
            // blocker-14, blocker-15: port is now dynamic, not hard-coded
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + port);
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
     * Resolves the server port from (in priority order):
     *  1. AWS SSM Parameter Store  (blocker-14, blocker-15)
     *  2. SERVER_PORT environment variable
     *  3. Default value 8080
     */
    private int resolveServerPort() {
        // 1. SSM Parameter Store
        try {
            GetParameterRequest req = GetParameterRequest.builder()
                    .name(SERVER_PORT_PARAM)
                    .withDecryption(false)
                    .build();
            GetParameterResponse resp = ssmClient.getParameter(req);
            int port = Integer.parseInt(resp.parameter().value());
            System.out.println("Server port resolved from SSM [" + SERVER_PORT_PARAM + "]: " + port);
            return port;
        } catch (Exception e) {
            System.out.println("SSM port lookup failed, falling back to env var SERVER_PORT.");
        }

        // 2. Environment variable
        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                return Integer.parseInt(envPort);
            } catch (NumberFormatException nfe) {
                System.err.println("Invalid SERVER_PORT env var value: " + envPort);
            }
        }

        // 3. Default
        return 8080;
    }

    /**
     * Loads all parameters under {@code SSM_CONFIG_PATH} from AWS Systems
     * Manager Parameter Store and returns them as a {@link Properties} object.
     *
     * blocker-20: replaces classpath-bundled properties file.
     */
    private Properties loadPropertiesFromSsm() {
        Properties props = new Properties();
        String nextToken = null;

        do {
            GetParametersByPathRequest.Builder reqBuilder = GetParametersByPathRequest.builder()
                    .path(SSM_CONFIG_PATH)
                    .recursive(true)
                    .withDecryption(true);

            if (nextToken != null) {
                reqBuilder.nextToken(nextToken);
            }

            GetParametersByPathResponse response =
                    ssmClient.getParametersByPath(reqBuilder.build());

            for (Parameter param : response.parameters()) {
                // Strip the path prefix to get a short property key
                String key = param.name().replace(SSM_CONFIG_PATH + "/", "");
                props.setProperty(key, param.value());
            }

            nextToken = response.nextToken();
        } while (nextToken != null);

        return props;
    }
}
