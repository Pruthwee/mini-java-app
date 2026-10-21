package com.test;

import java.net.ServerSocket;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {

    // Externalized port via environment variable with old value as default (cr-java-0077)
    private static final int SERVER_PORT = Integer.parseInt(
            System.getenv().getOrDefault("SERVER_PORT", "8080"));

    // Externalized S3 bucket and key names via environment variables (cr-java-0061, cr-java-0063)
    private static final String LOG_BUCKET = System.getenv("LOG_S3_BUCKET");
    private static final String LOG_S3_KEY = System.getenv().getOrDefault("LOG_S3_KEY", "mini-app.log");

    // Externalized Parameter Store path for configuration (cr-java-0070)
    private static final String CONFIG_PARAMETER_PATH = System.getenv().getOrDefault("APP_PARAMETER_PATH", "/mini-app/config/app.properties");

    // S3 client, lazily initialized on first use (cr-java-0061, cr-java-0063)
    private S3Client s3Client;

    // SSM client, lazily initialized on first use (cr-java-0070)
    private SsmClient ssmClient;

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // Load configuration from Parameter Store (cr-java-0070)
        loadConfiguration();

        // Initialize logging via S3 (cr-java-0061, cr-java-0063)
        initializeLogging();

        // Initialize database connection with externalized values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        try {
            // Load configuration from AWS Systems Manager Parameter Store (cr-java-0070)
            if (ssmClient == null) {
                ssmClient = SsmClient.builder().build();
            }
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(CONFIG_PARAMETER_PATH)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            System.out.println("Configuration loaded from Parameter Store: " + CONFIG_PARAMETER_PATH);
        } catch (SdkException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }

    private void initializeLogging() {
        try {
            // Write log file to S3 instead of local filesystem (cr-java-0061, cr-java-0063)
            if (s3Client == null) {
                s3Client = S3Client.builder().build();
            }
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(LOG_BUCKET)
                    .key(LOG_S3_KEY)
                    .contentType("text/plain")
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromString("Logging initialized"));
            System.out.println("Logging initialized at S3 bucket: " + LOG_BUCKET + ", key: " + LOG_S3_KEY);
        } catch (SdkException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            // Externalized port via environment variable (cr-java-0077)
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
