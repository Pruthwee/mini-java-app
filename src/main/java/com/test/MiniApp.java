package com.test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {

    // blocker-14 (cr-java-0077): replaced hard-coded port 8080 with SERVER_PORT env var; default 8080 kept
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // APP_S3_BUCKET: S3 bucket name for config/log objects (blocker-1..3, 4..7 cr-java-0061, cr-java-0063)
    // APP_PARAMETER_PATH: SSM Parameter Store path prefix for app config (blocker-20 cr-java-0070)
    private S3Client s3Client;
    private SsmClient ssmClient;

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // Load configuration from SSM Parameter Store
        loadConfiguration();

        // Initialize log file object in S3
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        // blocker-1 (cr-java-0061), blocker-4 (cr-java-0063): removed new File(CONFIG_FILE_PATH)
        // blocker-20 (cr-java-0070): replaced classpath/file-based Properties loading with
        //   AWS Systems Manager Parameter Store; parameters loaded from APP_PARAMETER_PATH prefix.
        //   Overlapping remediations on lines 44/46: SSM Parameter Store applied first (secret store),
        //   then S3 object store for the file itself; cr-java-0061 and cr-java-0063 applied together.
        try {
            String paramPath = System.getenv("APP_PARAMETER_PATH");
            if (paramPath == null || paramPath.isEmpty()) {
                System.out.println("Warning: APP_PARAMETER_PATH not set; skipping parameter store config load");
                return;
            }
            ssmClient = SsmClient.builder().build();
            Properties props = new Properties();
            String nextToken = null;
            do {
                GetParametersByPathRequest.Builder reqBuilder = GetParametersByPathRequest.builder()
                        .path(paramPath)
                        .recursive(true)
                        .withDecryption(true);
                if (nextToken != null) {
                    reqBuilder.nextToken(nextToken);
                }
                GetParametersByPathResponse response = ssmClient.getParametersByPath(reqBuilder.build());
                response.parameters().forEach(param -> {
                    String key = param.name().replace(paramPath, "").replaceFirst("^/", "");
                    props.setProperty(key, param.value());
                });
                nextToken = response.nextToken();
            } while (nextToken != null);
            System.out.println("Configuration loaded from Parameter Store path: " + paramPath);
        } catch (SdkException e) {
            System.err.println("Failed to load configuration from Parameter Store: " + e.getMessage());
        }
    }

    private void initializeLogging() {
        // blocker-2 (cr-java-0061), blocker-5 (cr-java-0063): removed new File("/var/log")
        // blocker-3 (cr-java-0061), blocker-6 (cr-java-0063), blocker-7 (cr-java-0063):
        //   removed new File(LOG_FILE_PATH) and logFile.createNewFile();
        //   log file initialisation replaced with an S3 PutObject to create the log object.
        //   Bucket from APP_S3_BUCKET env var; key "logs/mini-app.log".
        try {
            String bucketName = System.getenv("APP_S3_BUCKET");
            if (bucketName == null || bucketName.isEmpty()) {
                System.out.println("Warning: APP_S3_BUCKET not set; skipping S3 log initialisation");
                return;
            }
            s3Client = S3Client.builder().build();
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("logs/mini-app.log")
                    .contentType("text/plain")
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromString(""));
            System.out.println("Logging initialised in S3 bucket: " + bucketName);
        } catch (SdkException e) {
            System.err.println("Failed to initialise logging in S3: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // blocker-15 (cr-java-0077): SERVER_PORT resolved from env var above; default 8080 kept
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
