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
import java.util.HashMap;
import java.util.Map;

/**
 * Mini Java Application – cloud-ready version.
 *
 * Fixes applied:
 *  - blocker-1,2,3   : Hard-coded absolute file paths replaced with S3 object keys / env vars
 *  - blocker-4,5,6,7 : java.io.File operations replaced with Amazon S3 (AWS SDK v2)
 *  - blocker-14,15   : Hard-coded port numbers replaced with environment variable injection
 *  - blocker-20      : Classpath properties file replaced with AWS SSM Parameter Store
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // AWS region – injected via environment variable (12-factor)
    // -----------------------------------------------------------------------
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // blocker-14 : Hard-coded port 8080 replaced with environment variable
    // -----------------------------------------------------------------------
    private static final int SERVER_PORT =
            Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // -----------------------------------------------------------------------
    // blocker-1,2,3 : Hard-coded absolute file paths replaced with S3 config
    // S3 bucket name and object keys are injected via environment variables.
    // -----------------------------------------------------------------------
    private static final String S3_BUCKET =
            System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-storage");

    /** S3 object key for the application configuration object (replaces /opt/app/config/app.properties) */
    private static final String CONFIG_S3_KEY =
            System.getenv().getOrDefault("CONFIG_S3_KEY", "config/app.properties");

    /** S3 object key prefix for log objects (replaces /var/log/mini-app.log) */
    private static final String LOG_S3_KEY =
            System.getenv().getOrDefault("LOG_S3_KEY", "logs/mini-app.log");

    // -----------------------------------------------------------------------
    // blocker-20 : SSM Parameter Store path for application configuration
    // -----------------------------------------------------------------------
    private static final String SSM_CONFIG_PATH =
            System.getenv().getOrDefault("SSM_CONFIG_PATH", "/mini-app/config");

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        Region region = Region.of(AWS_REGION);
        // blocker-4,5,6,7 – S3 client replaces java.io.File operations
        this.s3Client = S3Client.builder()
                .region(region)
                .build();
        // blocker-20 – SSM client for externalised configuration
        this.ssmClient = SsmClient.builder()
                .region(region)
                .build();
    }

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();

        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    // -----------------------------------------------------------------------
    // blocker-1,4 : loadConfiguration
    //   Old: new File(CONFIG_FILE_PATH) + FileInputStream (absolute path)
    //   New: AWS SSM Parameter Store (primary) + S3 fallback
    // blocker-20 : classpath properties replaced with SSM Parameter Store
    // -----------------------------------------------------------------------
    private void loadConfiguration() {
        // Primary: load configuration from AWS SSM Parameter Store (blocker-20)
        try {
            Map<String, String> config = loadConfigFromParameterStore();
            if (!config.isEmpty()) {
                System.out.println("Configuration loaded from AWS SSM Parameter Store path: " + SSM_CONFIG_PATH);
                config.forEach((k, v) -> System.out.println("  " + k + " = " + v));
                return;
            }
        } catch (Exception e) {
            System.err.println("SSM Parameter Store unavailable, falling back to S3: " + e.getMessage());
        }

        // Fallback: load configuration object from Amazon S3 (blocker-1,4)
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(CONFIG_S3_KEY)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            byte[] content = s3Object.readAllBytes();
            System.out.println("Configuration loaded from S3: s3://" + S3_BUCKET + "/" + CONFIG_S3_KEY
                    + " (" + content.length + " bytes)");

        } catch (IOException e) {
            System.err.println("Failed to read configuration from S3: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Configuration object not found in S3 at key '"
                    + CONFIG_S3_KEY + "': " + e.getMessage());
        }
    }

    /**
     * Loads all parameters under the SSM path hierarchy.
     * blocker-20 – replaces classpath-bundled properties file.
     */
    private Map<String, String> loadConfigFromParameterStore() {
        Map<String, String> config = new HashMap<>();
        String nextToken = null;

        do {
            GetParametersByPathRequest.Builder requestBuilder = GetParametersByPathRequest.builder()
                    .path(SSM_CONFIG_PATH)
                    .recursive(true)
                    .withDecryption(true);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            GetParametersByPathResponse response = ssmClient.getParametersByPath(requestBuilder.build());
            for (Parameter param : response.parameters()) {
                config.put(param.name(), param.value());
            }
            nextToken = response.nextToken();
        } while (nextToken != null);

        return config;
    }

    // -----------------------------------------------------------------------
    // blocker-2,3,5,6,7 : initializeLogging
    //   Old: new File("/var/log") + logFile.createNewFile() (absolute paths)
    //   New: write a log initialisation marker object to Amazon S3
    // -----------------------------------------------------------------------
    private void initializeLogging() {
        try {
            // blocker-2,3,5,6,7 – replace local file creation with S3 object write
            String logInitContent = "Log initialised at: " + java.time.Instant.now().toString();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromBytes(logInitContent.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialised – log marker written to S3: s3://"
                    + S3_BUCKET + "/" + LOG_S3_KEY);

        } catch (Exception e) {
            System.err.println("Failed to initialise logging via S3: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // blocker-14,15 : startServer
    //   Old: new ServerSocket(8080) – hard-coded port
    //   New: port resolved from SERVER_PORT environment variable
    // -----------------------------------------------------------------------
    private void startServer() {
        try {
            // blocker-14,15 – port injected via environment variable
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
